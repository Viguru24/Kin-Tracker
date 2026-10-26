package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

object TripTransitEngine {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Tracks last stationary location per person: Pair(lat, lng)
    val lastStationaryCoords = ConcurrentHashMap<String, Pair<Double, Double>>()

    // Timestamp when member became stationary at those coordinates
    val stationarySinceTime = ConcurrentHashMap<String, Long>()

    // Last known coordinates (for bearing / heading calculation): Pair(lat, lng)
    val lastKnownCoords = ConcurrentHashMap<String, Pair<Double, Double>>()

    // Consecutive movement checks at transit speeds (>12 mph or train)
    val consecutiveTransitChecks = ConcurrentHashMap<String, Int>()

    // Timestamp of last emitted trip / journey alert per member (cooldown latch)
    val lastTripAlertTimestamp = ConcurrentHashMap<String, Long>()

    // Spatial cache for reverse-geocoded place names (gridKey -> placeName)
    private val reverseGeocodeCache = ConcurrentHashMap<String, String>()

    // Well-known transport hubs and major destinations in UK / London / Surrey / Sussex
    data class KnownHub(val name: String, val lat: Double, val lng: Double, val radiusMeters: Double = 1600.0)

    val KNOWN_HUBS = listOf(
        KnownHub("London Victoria", 51.4952, -0.1439, 2000.0),
        KnownHub("London Bridge", 51.5050, -0.0860, 2000.0),
        KnownHub("London Waterloo", 51.5031, -0.1132, 2000.0),
        KnownHub("London Blackfriars", 51.5118, -0.1037, 1800.0),
        KnownHub("St Pancras International", 51.5314, -0.1261, 2000.0),
        KnownHub("Central London", 51.5074, -0.1278, 5000.0),
        KnownHub("Clapham Junction", 51.4632, -0.1702, 1600.0),
        KnownHub("East Croydon", 51.3754, -0.0926, 1200.0),
        KnownHub("West Croydon", 51.3787, -0.1026, 1000.0),
        KnownHub("South Croydon", 51.3615, -0.0967, 1000.0),
        KnownHub("Purley", 51.3375, -0.1147, 1200.0),
        KnownHub("Coulsdon", 51.3208, -0.1402, 1200.0),
        KnownHub("Kenley", 51.3235, -0.0955, 1000.0),
        KnownHub("Caterham", 51.2798, -0.0768, 1400.0),
        KnownHub("Redhill", 51.2405, -0.1700, 1600.0),
        KnownHub("Gatwick Airport", 51.1560, -0.1610, 2500.0),
        KnownHub("Three Bridges", 51.1170, -0.1610, 1400.0),
        KnownHub("Haywards Heath", 51.0040, -0.1440, 1600.0),
        KnownHub("Brighton", 50.8290, -0.1410, 3000.0)
    )

    fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    fun bearingDifference(b1: Double, b2: Double): Double {
        val phi = abs(b1 - b2) % 360.0
        return if (phi > 180.0) 360.0 - phi else phi
    }

    fun bearingToCardinal(bearingDeg: Double): String {
        val idx = ((((bearingDeg + 22.5) % 360.0) + 360.0) % 360.0 / 45.0).toInt()
        return when (idx) {
            0 -> "north"
            1 -> "north-east"
            2 -> "east"
            3 -> "south-east"
            4 -> "south"
            5 -> "south-west"
            6 -> "west"
            7 -> "north-west"
            else -> "north"
        }
    }

    fun formatTransitPhrase(mode: TransitMode): String {
        return when (mode) {
            TransitMode.TRAIN -> "on a train"
            TransitMode.DRIVING -> "in a car"
            TransitMode.CYCLING -> "cycling"
            TransitMode.WALKING -> "walking"
            TransitMode.STATIONARY -> ""
        }
    }

    /**
     * Resolves the human-readable name of an origin location.
     * Prioritizes SafeZones, Home, known rail hubs, and reverse geocoding.
     */
    fun resolveOriginName(
        lat: Double,
        lng: Double,
        safeZones: List<SafeZone> = emptyList(),
        homeLat: Double = 0.0,
        homeLng: Double = 0.0,
        workLat: Double = 0.0,
        workLng: Double = 0.0
    ): String {
        if (lat == 0.0 && lng == 0.0) return "their location"

        // 1. Check Home
        if (homeLat != 0.0 && homeLng != 0.0) {
            val distHome = GeoUtils.distanceMeters(lat, lng, homeLat, homeLng)
            if (distHome <= 220.0) return "the house"
        }

        // 2. Check SafeZones
        val matchingZone = safeZones.firstOrNull { zone ->
            GeoUtils.distanceMeters(lat, lng, zone.latitude, zone.longitude) <= zone.radiusMeters + 60.0
        }
        if (matchingZone != null) {
            return matchingZone.name
        }

        // 3. Check Work
        if (workLat != 0.0 && workLng != 0.0) {
            val distWork = GeoUtils.distanceMeters(lat, lng, workLat, workLng)
            if (distWork <= 120.0) return "Work"
        }

        // 4. Check Known Hubs / Stations
        val hub = KNOWN_HUBS.firstOrNull {
            GeoUtils.distanceMeters(lat, lng, it.lat, it.lng) <= it.radiusMeters
        }
        if (hub != null) {
            return hub.name
        }

        // 5. Check reverse geocode cache (~1km grid)
        val gridKey = String.format(Locale.US, "%.2f_%.2f", lat, lng)
        val cached = reverseGeocodeCache[gridKey]
        if (cached != null) {
            return cached
        }

        // Trigger background lookup for future events
        fetchReverseGeocodeAsync(lat, lng, gridKey)

        return "their location"
    }

    private fun fetchReverseGeocodeAsync(lat: Double, lng: Double, gridKey: String) {
        engineScope.launch {
            try {
                val urlStr = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&zoom=14"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "KinTrackerApp/1.0")
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val address = json.optJSONObject("address")
                    val name = address?.optString("suburb")?.ifBlank { null }
                        ?: address?.optString("town")?.ifBlank { null }
                        ?: address?.optString("city")?.ifBlank { null }
                        ?: address?.optString("village")?.ifBlank { null }
                        ?: json.optString("name").ifBlank { null }

                    if (!name.isNullOrBlank()) {
                        reverseGeocodeCache[gridKey] = name
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Determines which destination a member is heading towards.
     * Evaluates SafeZones, Home, Work, and major transport termini based on travel bearing and distance reduction.
     */
    fun determineHeadingDestination(
        curLat: Double,
        curLng: Double,
        prevLat: Double,
        prevLng: Double,
        safeZones: List<SafeZone> = emptyList(),
        homeLat: Double = 0.0,
        homeLng: Double = 0.0,
        workLat: Double = 0.0,
        workLng: Double = 0.0,
        speedMph: Double = 0.0,
        isRailway: Boolean = false
    ): String? {
        if (curLat == 0.0 || curLng == 0.0 || prevLat == 0.0 || prevLng == 0.0) return null

        val distMovedM = GeoUtils.distanceMeters(curLat, curLng, prevLat, prevLng)
        if (distMovedM < 15.0 && speedMph < 1.0) return null

        val travelBearing = calculateBearingDegrees(prevLat, prevLng, curLat, curLng)

        data class DestinationCandidate(val name: String, val distM: Double, val angleDiff: Double, val isSafeZone: Boolean)

        val candidates = mutableListOf<DestinationCandidate>()

        // A. Candidate: Home
        if (homeLat != 0.0 && homeLng != 0.0) {
            val distCur = GeoUtils.distanceMeters(curLat, curLng, homeLat, homeLng)
            val distPrev = GeoUtils.distanceMeters(prevLat, prevLng, homeLat, homeLng)
            if (distCur < distPrev - 15.0 && distCur > 120.0) {
                val bearingToHome = calculateBearingDegrees(curLat, curLng, homeLat, homeLng)
                val diff = bearingDifference(travelBearing, bearingToHome)
                if (diff <= 55.0) {
                    candidates.add(DestinationCandidate("Home", distCur, diff, true))
                }
            }
        }

        // B. Candidates: SafeZones
        safeZones.forEach { zone ->
            val distCur = GeoUtils.distanceMeters(curLat, curLng, zone.latitude, zone.longitude)
            val distPrev = GeoUtils.distanceMeters(prevLat, prevLng, zone.latitude, zone.longitude)
            if (distCur < distPrev - 15.0 && distCur > 120.0) {
                val bearingToZone = calculateBearingDegrees(curLat, curLng, zone.latitude, zone.longitude)
                val diff = bearingDifference(travelBearing, bearingToZone)
                if (diff <= 55.0) {
                    candidates.add(DestinationCandidate(zone.name, distCur, diff, true))
                }
            }
        }

        // C. Candidate: Work
        if (workLat != 0.0 && workLng != 0.0) {
            val distCur = GeoUtils.distanceMeters(curLat, curLng, workLat, workLng)
            val distPrev = GeoUtils.distanceMeters(prevLat, prevLng, workLat, workLng)
            if (distCur < distPrev - 15.0 && distCur > 100.0) {
                val bearingToWork = calculateBearingDegrees(curLat, curLng, workLat, workLng)
                val diff = bearingDifference(travelBearing, bearingToWork)
                if (diff <= 55.0) {
                    candidates.add(DestinationCandidate("Work", distCur, diff, true))
                }
            }
        }

        // D. Candidates: Known Rail Hubs & Cities
        KNOWN_HUBS.forEach { hub ->
            val distCur = GeoUtils.distanceMeters(curLat, curLng, hub.lat, hub.lng)
            val distPrev = GeoUtils.distanceMeters(prevLat, prevLng, hub.lat, hub.lng)
            if (distCur < distPrev - 30.0 && distCur > hub.radiusMeters) {
                val bearingToHub = calculateBearingDegrees(curLat, curLng, hub.lat, hub.lng)
                val diff = bearingDifference(travelBearing, bearingToHub)
                if (diff <= 45.0) {
                    candidates.add(DestinationCandidate(hub.name, distCur, diff, false))
                }
            }
        }

        if (candidates.isNotEmpty()) {
            // Prioritize SafeZones / Home first, then closest aligned hub
            val bestCandidate = candidates
                .sortedWith(compareBy({ !it.isSafeZone }, { it.angleDiff }, { it.distM }))
                .firstOrNull()

            if (bestCandidate != null) {
                return bestCandidate.name
            }
        }

        // Fallback: If traveling on a railway corridor without a specific target
        if (isRailway) {
            return when {
                travelBearing in 315.0..360.0 || travelBearing in 0.0..45.0 -> "London"
                travelBearing in 135.0..225.0 -> "Brighton"
                travelBearing in 45.0..135.0 -> "Kent"
                else -> "Surrey"
            }
        }

        // General road travel: compass direction
        val cardinal = bearingToCardinal(travelBearing)
        return "$cardinal direction"
    }

    /**
     * Constructs the departure announcement message according to the user's requested specification:
     * e.g. "Isabel has left Brighton and is on a train heading towards London!"
     * e.g. "Isabel has left the house and is in a car heading towards Woodcote High School!"
     */
    fun formatDepartureMessage(
        memberName: String,
        originName: String,
        transitMode: TransitMode,
        destinationName: String?,
        speedMph: Double = 0.0
    ): String {
        val modePhrase = formatTransitPhrase(transitMode)
        val cleanDest = destinationName?.trim()

        return when {
            modePhrase.isNotBlank() && !cleanDest.isNullOrBlank() -> {
                "$memberName has left $originName and is $modePhrase heading towards $cleanDest!"
            }
            modePhrase.isNotBlank() -> {
                "$memberName has left $originName and is $modePhrase!"
            }
            else -> {
                "$memberName has left $originName!"
            }
        }
    }

    /**
     * Evaluates whether a member has departed an un-geofenced location after being stationary
     * and has entered active transit (train or car), emitting a smart journey departure alert.
     */
    suspend fun evaluateMemberJourney(
        context: Context,
        repository: FamilyRepository,
        prefs: android.content.SharedPreferences,
        member: FamilyMember,
        safeZones: List<SafeZone>,
        homeLat: Double,
        homeLng: Double,
        workLat: Double,
        workLng: Double,
        onUiEvent: ((String) -> Unit)? = null
    ) {
        val cleanId = member.id.lowercase().trim()
        val now = System.currentTimeMillis()

        val isPaused = member.isLocationPaused ||
                prefs.getBoolean("is_member_paused_${member.id}", false) ||
                member.statusText.contains("Paused", ignoreCase = true)
        if (isPaused || (member.x == 0.0 && member.y == 0.0)) return

        val prevCoords = lastKnownCoords[cleanId]
        lastKnownCoords[cleanId] = Pair(member.y, member.x)

        val isRailway = RailwayTransitDetector.isMemberOnRailway(member.id)
        val isMovingFast = member.speedMph >= 12.0 || isRailway
        val stationaryCoords = lastStationaryCoords[cleanId]
        val stationarySince = stationarySinceTime[cleanId] ?: 0L

        // If stationary, record resting origin
        if (member.speedMph < 1.5) {
            consecutiveTransitChecks[cleanId] = 0
            if (stationaryCoords == null || GeoUtils.distanceMeters(member.y, member.x, stationaryCoords.first, stationaryCoords.second) > 40.0) {
                lastStationaryCoords[cleanId] = Pair(member.y, member.x)
                stationarySinceTime[cleanId] = now
            }
            return
        }

        // If moving at vehicular speeds from a stationary point
        if (isMovingFast && stationaryCoords != null) {
            val distFromOrigin = GeoUtils.distanceMeters(member.y, member.x, stationaryCoords.first, stationaryCoords.second)
            val dwellTimeMs = now - stationarySince

            // If stationary for >= 2 minutes and now traveled > 250m away
            if (distFromOrigin >= 250.0 && dwellTimeMs >= 120_000L) {
                val checks = (consecutiveTransitChecks[cleanId] ?: 0) + 1
                consecutiveTransitChecks[cleanId] = checks

                if (checks >= 2) {
                    val lastAlert = lastTripAlertTimestamp[cleanId] ?: 0L
                    if (now - lastAlert > AppConfig.GEOFENCE_COOLDOWN_MS) {
                        lastTripAlertTimestamp[cleanId] = now
                        lastStationaryCoords.remove(cleanId)

                        val mode = classifyTransitMode(member.speedMph, member.statusText, member.id)
                        val originName = resolveOriginName(
                            lat = stationaryCoords.first,
                            lng = stationaryCoords.second,
                            safeZones = safeZones,
                            homeLat = homeLat,
                            homeLng = homeLng,
                            workLat = workLat,
                            workLng = workLng
                        )
                        val destination = determineHeadingDestination(
                            curLat = member.y,
                            curLng = member.x,
                            prevLat = prevCoords?.first ?: stationaryCoords.first,
                            prevLng = prevCoords?.second ?: stationaryCoords.second,
                            safeZones = safeZones,
                            homeLat = homeLat,
                            homeLng = homeLng,
                            workLat = workLat,
                            workLng = workLng,
                            speedMph = member.speedMph,
                            isRailway = isRailway
                        )

                        val cleanMemberName = member.name
                            .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("('s|s)?\\s*(iPhone|iPad|Tab|Tablet|Phone|Device)", RegexOption.IGNORE_CASE), "")
                            .trim()

                        val message = formatDepartureMessage(
                            memberName = cleanMemberName,
                            originName = originName,
                            transitMode = mode,
                            destinationName = destination,
                            speedMph = member.speedMph
                        )

                        val isDepartureAlertsEnabled = prefs.getBoolean("isDepartureAlertsEnabled", true)
                        if (isDepartureAlertsEnabled) {
                            val alertTitle = if (mode == TransitMode.TRAIN) "🚆 Journey Notice" else "🚗 Trip Notice"
                            AlertNotificationHelper.postAlertNotification(context, alertTitle, message)
                            onUiEvent?.invoke("$alertTitle: $message")
                            VoiceAnnouncementManager.announceIfEnabled(context, "$alertTitle: $message")
                        }

                        repository.insertLog(
                            ActivityLog(
                                memberId = member.id,
                                memberName = member.name,
                                actionText = message.removePrefix("$cleanMemberName ").trim(),
                                iconName = "away"
                            )
                        )
                    }
                }
            }
        }
    }
}
