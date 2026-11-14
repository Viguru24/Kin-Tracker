package com.example.data

import kotlin.math.*

/**
 * Single source of truth for geographical mathematics, distance calculations,
 * geofencing evaluation, and spatial utility functions across the Kin Tracker application.
 */
object GeoUtils {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val KM_PER_DEGREE_LAT = 111.0

    /**
     * Calculates the exact Haversine distance between two GPS coordinates in kilometers.
     */
    fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0 || lat2 == 0.0 && lon2 == 0.0) return 0.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Fast equirectangular approximation distance in kilometers (ideal for tight local loop checks).
     */
    fun fastDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0 || lat2 == 0.0 && lon2 == 0.0) return 0.0
        val xDistKm = (lon2 - lon1) * KM_PER_DEGREE_LAT * cos(Math.toRadians((lat1 + lat2) / 2.0))
        val yDistKm = (lat2 - lat1) * KM_PER_DEGREE_LAT
        return hypot(xDistKm, yDistKm)
    }

    /**
     * Distance in meters between two GPS coordinates.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return fastDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    /**
     * Evaluates if a given point is within a circular geofence boundary (in meters).
     */
    fun isInsideGeofence(pointLat: Double, pointLng: Double, centerLat: Double, centerLng: Double, radiusMeters: Double): Boolean {
        if (pointLat == 0.0 && pointLng == 0.0 || centerLat == 0.0 && centerLng == 0.0) return false
        return distanceMeters(pointLat, pointLng, centerLat, centerLng) <= radiusMeters
    }

    /**
     * Formats distance compactly (e.g. "350 m" or "1.4 km").
     */
    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
        } else {
            "${meters.toInt()} m"
        }
    }

    /**
     * Strict phone number sanitizer for international dialing (E.164-safe format).
     */
    fun sanitizePhoneNumber(raw: String): String {
        val digitsOnly = raw.replace(Regex("[^0-9+]"), "")
        return if (digitsOnly.startsWith("+")) digitsOnly else digitsOnly.replace("+", "")
    }

    val DEFAULT_WOODCOTE_HIGH_SCHOOL = SafeZone(
        id = "zone_woodcote_high_school",
        name = "Woodcote High School",
        latitude = 51.3301600,
        longitude = -0.1422829,
        radiusMeters = 220.0,
        iconName = "school"
    )

    val DEFAULT_WESTSIDE_CARE_HOME = SafeZone(
        id = "zone_westside_care_home",
        name = "Westside Care Home",
        latitude = 51.3432902,
        longitude = -0.1330773,
        radiusMeters = 150.0,
        iconName = "work"
    )

    val DEFAULT_WESTMINSTER_KINGSWAY_COLLEGE = SafeZone(
        id = "zone_westminster_college",
        name = "Westminster Kingsway College",
        latitude = 51.4948287,
        longitude = -0.1353383,
        radiusMeters = 180.0,
        iconName = "school"
    )

    val INITIAL_SEEDED_WORKPLACES: List<SafeZone> = listOf(
        DEFAULT_WESTSIDE_CARE_HOME,
        DEFAULT_WESTMINSTER_KINGSWAY_COLLEGE
    )

    /**
     * Computes a realistic effective radius for a SafeZone so large school campuses
     * and indoor Wi-Fi/cellular triangulation drift do not cause boundary bounce.
     */
    fun effectiveSafeZoneRadiusMeters(zone: SafeZone): Double {
        val isWoodcoteHigh = zone.id == "zone_woodcote_high_school" ||
            (zone.name.contains("Woodcote", ignoreCase = true) && zone.name.contains("High", ignoreCase = true)) ||
            zone.name.contains("Woodcoat", ignoreCase = true)
        val isSchool = zone.iconName.equals("school", ignoreCase = true) ||
            zone.name.contains("school", ignoreCase = true) ||
            zone.name.contains("college", ignoreCase = true) ||
            zone.name.contains("academy", ignoreCase = true)

        return when {
            isWoodcoteHigh -> maxOf(zone.radiusMeters, 220.0)
            isSchool -> maxOf(zone.radiusMeters, 180.0)
            else -> maxOf(zone.radiusMeters, 100.0)
        }
    }

    /**
     * Merges user-configured SafeZones with the default family school zones, filtering out
     * rogue/stale school zones (e.g. Byron or duplicate primary school), and upgrades
     * any legacy narrow school radii to their full campus effective radii.
     */
    fun withDefaultSafeZones(customZones: List<SafeZone>): List<SafeZone> {
        val result = customZones.filterNot { zone ->
            zone.id == "zone_oasis_academy_byron" ||
                zone.id == "zone_woodcote_school" ||
                zone.name.contains("Byron", ignoreCase = true) ||
                (zone.name.contains("Woodcote", ignoreCase = true) && zone.name.contains("Primary", ignoreCase = true))
        }.map { zone ->
            // If user has a legacy Woodcote High pin stuck at Byron Ave (51.3205711), auto-correct to real Woodcote High
            val correctedZone = if (zone.id == "zone_woodcote_high_school" && Math.abs(zone.latitude - 51.3205711) < 0.001) {
                zone.copy(latitude = DEFAULT_WOODCOTE_HIGH_SCHOOL.latitude, longitude = DEFAULT_WOODCOTE_HIGH_SCHOOL.longitude)
            } else zone
            correctedZone.copy(radiusMeters = effectiveSafeZoneRadiusMeters(correctedZone))
        }.toMutableList()

        val hasHighSchool = result.any {
            it.id == "zone_woodcote_high_school" ||
                (it.name.contains("Woodcote", ignoreCase = true) && it.name.contains("High", ignoreCase = true)) ||
                it.name.contains("Woodcoat", ignoreCase = true)
        }
        if (!hasHighSchool) {
            result.add(DEFAULT_WOODCOTE_HIGH_SCHOOL)
        }

        return result
    }

    /**
     * Evaluates whether a coordinate is inside any SafeZone, applying exit hysteresis when
     * the member's current status or previous barrier indicates they are already at that zone.
     * When stationary (e.g. phone in a drawer at school), hysteresis comfortably absorbs
     * indoor cellular tower / Wi-Fi multipath jitter so the person stays locked.
     */
    fun matchSafeZone(
        lat: Double,
        lng: Double,
        customZones: List<SafeZone>,
        currentStatusText: String = "",
        previousZoneId: String? = null,
        exitBufferMeters: Double = 60.0,
        speedMph: Double = 0.0
    ): SafeZone? {
        if (lat == 0.0 && lng == 0.0) return null
        val zones = withDefaultSafeZones(customZones)
        val matchingZones = zones.filter { zone ->
            val baseRadius = effectiveSafeZoneRadiusMeters(zone)
            val alreadyAtThisZone = previousZoneId == zone.id ||
                previousZoneId == "zone:${zone.id}" ||
                currentStatusText.contains("At ${zone.name}", ignoreCase = true)
            val isSchool = zone.iconName.equals("school", ignoreCase = true) ||
                zone.name.contains("school", ignoreCase = true) ||
                zone.name.contains("college", ignoreCase = true) ||
                zone.name.contains("academy", ignoreCase = true)

            // When a person is already at a school and stationary (phone in office drawer),
            // absorb indoor cell mast jumps up to 200m beyond the campus edge.
            val effectiveBuffer = if (alreadyAtThisZone && isSchool && speedMph < 1.8) {
                maxOf(exitBufferMeters, 180.0)
            } else if (alreadyAtThisZone) {
                exitBufferMeters
            } else {
                15.0
            }

            val limit = baseRadius + effectiveBuffer
            distanceMeters(lat, lng, zone.latitude, zone.longitude) <= limit
        }

        if (matchingZones.isEmpty()) return null

        // Priority 1: If already locked into an active zone, retain it for stability
        val existingActiveZone = matchingZones.firstOrNull { zone ->
            previousZoneId == zone.id ||
                previousZoneId == "zone:${zone.id}" ||
                currentStatusText.contains("At ${zone.name}", ignoreCase = true)
        }
        if (existingActiveZone != null) return existingActiveZone

        // Priority 2: School campus boundaries always take precedence over overlapping residential safe zones
        val schoolZone = matchingZones.firstOrNull { zone ->
            zone.iconName.equals("school", ignoreCase = true) ||
                zone.name.contains("school", ignoreCase = true) ||
                zone.name.contains("college", ignoreCase = true) ||
                zone.name.contains("academy", ignoreCase = true)
        }
        if (schoolZone != null) return schoolZone

        // Priority 3: Fall back to closest center pin
        return matchingZones.minByOrNull { zone ->
            distanceMeters(lat, lng, zone.latitude, zone.longitude)
        }
    }

    // --- Home Geofence Evaluation (Single Source of Truth) ---

    /**
     * Computes the effective residential Home radius in meters from the circle's configured
     * setting, respecting user-configured radii down to 35m instead of overriding with a
     * fixed 120m-150m residential limit.
     */
    fun effectiveHomeRadiusMeters(homeRadiusMeters: Double): Double {
        return maxOf(homeRadiusMeters, 35.0)
    }

    /**
     * Computes stationary Home exit hysteresis scaled proportionally to the configured Home radius
     * so small home perimeters (e.g. 60m) do not swallow nearby streets or buildings.
     */
    fun homeExitBufferMeters(
        homeRadiusMeters: Double,
        maxBufferMeters: Double = AppConfig.EXIT_HYSTERESIS_METERS
    ): Double {
        val effectiveRadius = effectiveHomeRadiusMeters(homeRadiusMeters)
        return minOf(maxBufferMeters, maxOf(25.0, effectiveRadius * 0.6))
    }

    /**
     * Evaluates whether a distance (in meters) from Home falls within the configured Home geofence,
     * applying stationary exit hysteresis only when the member was already at Home and not moving.
     */
    fun isDistanceWithinHomeGeofence(
        distanceMeters: Double,
        homeRadiusMeters: Double,
        wasAlreadyAtHome: Boolean = false,
        speedMph: Double = 0.0,
        accuracyMeters: Double = 0.0
    ): Boolean {
        val baseRadius = effectiveHomeRadiusMeters(homeRadiusMeters)
        val isStationaryInside = wasAlreadyAtHome && speedMph < AppConfig.MIN_EXIT_SPEED_MPH
        val limitMeters = if (isStationaryInside) {
            val accuracyAllowance = if (accuracyMeters in 1.0..50.0) minOf(accuracyMeters * 0.25, 15.0) else 0.0
            baseRadius + homeExitBufferMeters(baseRadius) + accuracyAllowance
        } else {
            baseRadius
        }
        return distanceMeters <= limitMeters
    }

    /**
     * Evaluates whether a coordinate is inside the configured Home geofence.
     */
    fun isWithinHomeGeofence(
        lat: Double,
        lng: Double,
        homeLat: Double,
        homeLng: Double,
        homeRadiusMeters: Double,
        wasAlreadyAtHome: Boolean = false,
        speedMph: Double = 0.0,
        accuracyMeters: Double = 0.0
    ): Boolean {
        if ((lat == 0.0 && lng == 0.0) || (homeLat == 0.0 && homeLng == 0.0)) return false
        val distMeters = distanceMeters(lat, lng, homeLat, homeLng)
        return isDistanceWithinHomeGeofence(
            distanceMeters = distMeters,
            homeRadiusMeters = homeRadiusMeters,
            wasAlreadyAtHome = wasAlreadyAtHome,
            speedMph = speedMph,
            accuracyMeters = accuracyMeters
        )
    }

    /**
     * Evaluates incoming member coordinates alongside optional status text so stale remote status
     * never locks a member at Home once they have moved clearly past the Home exit boundary.
     */
    fun isMemberConsideredAtHome(
        lat: Double,
        lng: Double,
        homeLat: Double,
        homeLng: Double,
        homeRadiusMeters: Double,
        wasAlreadyAtHome: Boolean,
        incomingStatusText: String,
        speedMph: Double
    ): Boolean {
        if ((lat == 0.0 && lng == 0.0) || (homeLat == 0.0 && homeLng == 0.0)) return false
        val distMeters = distanceMeters(lat, lng, homeLat, homeLng)
        if (isDistanceWithinHomeGeofence(distMeters, homeRadiusMeters, wasAlreadyAtHome, speedMph)) {
            return true
        }
        val baseRadius = effectiveHomeRadiusMeters(homeRadiusMeters)
        val maxStatusTolerance = baseRadius + homeExitBufferMeters(baseRadius)
        val hasAtHomeStatus = incomingStatusText.contains("At Home", ignoreCase = true)
        return hasAtHomeStatus && speedMph < AppConfig.MIN_EXIT_SPEED_MPH && distMeters <= maxStatusTolerance
    }

    // --- Location Quality & Provider Arbitration ---

    data class LocationSampleMeta(
        val provider: String,
        val accuracyMeters: Float,
        val hasAccuracy: Boolean,
        val timestampMs: Long,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val elapsedNanos: Long = 0L
    )

    private const val LOCATION_MAX_ACCEPTED_ACCURACY_M = 150f
    private const val LOCATION_MAX_INITIAL_ACCURACY_M = 250f
    private const val LOCATION_NETWORK_MAX_ACCURACY_M = 100f
    private const val LOCATION_NETWORK_INDOOR_FALLBACK_ACCURACY_M = 65f
    private const val LOCATION_GPS_OWNERSHIP_WINDOW_MS = 45_000L
    private const val LOCATION_NETWORK_COARSE_SUPPRESSION_MS = 120_000L

    @Volatile
    private var lastAcceptedLocationMeta: LocationSampleMeta? = null
    private val locationArbiterLock = Any()

    fun resetLocationArbiterForTests() {
        synchronized(locationArbiterLock) {
            lastAcceptedLocationMeta = null
        }
    }

    private fun android.location.Location.toSampleMeta(): LocationSampleMeta {
        return LocationSampleMeta(
            provider = provider ?: "",
            accuracyMeters = accuracy,
            hasAccuracy = hasAccuracy(),
            timestampMs = time,
            latitude = latitude,
            longitude = longitude,
            elapsedNanos = elapsedRealtimeNanos
        )
    }

    private fun timeDeltaBetweenSamplesMs(
        candidate: LocationSampleMeta,
        currentBest: LocationSampleMeta
    ): Long {
        return if (candidate.elapsedNanos > 0L && currentBest.elapsedNanos > 0L) {
            (candidate.elapsedNanos - currentBest.elapsedNanos) / 1_000_000L
        } else {
            candidate.timestampMs - currentBest.timestampMs
        }
    }

    /**
     * Pure arbitration rule deciding whether a newly reported location fix should advance
     * the currently active fix. Prevents coarse or stale Wi-Fi/cell NETWORK_PROVIDER fixes
     * from dragging a freshly acquired GPS position back to a nearby mast or Home router.
     */
    fun shouldAcceptLocationUpdate(
        candidate: LocationSampleMeta,
        currentBest: LocationSampleMeta?
    ): Boolean {
        if (!candidate.latitude.isFinite() || !candidate.longitude.isFinite()) return false
        if (candidate.latitude == 0.0 && candidate.longitude == 0.0) return false

        val acc = if (candidate.hasAccuracy) candidate.accuracyMeters else 20f
        if (!acc.isFinite() || acc <= 0f) return false

        val isNet = candidate.provider.equals("network", ignoreCase = true)
        val isGps = candidate.provider.equals("gps", ignoreCase = true) ||
            candidate.provider.equals("fused", ignoreCase = true)

        if (currentBest == null) {
            val initialLimit = if (isNet) LOCATION_NETWORK_MAX_ACCURACY_M else LOCATION_MAX_INITIAL_ACCURACY_M
            return acc <= initialLimit
        }

        if (acc > LOCATION_MAX_ACCEPTED_ACCURACY_M) return false
        if (isNet && acc > LOCATION_NETWORK_MAX_ACCURACY_M) return false

        val timeDeltaMs = timeDeltaBetweenSamplesMs(candidate, currentBest)
        if (timeDeltaMs < -2_000L) return false

        val bestIsGps = currentBest.provider.equals("gps", ignoreCase = true) ||
            currentBest.provider.equals("fused", ignoreCase = true)
        val bestAcc = if (currentBest.hasAccuracy) currentBest.accuracyMeters else 20f

        if (bestIsGps && isNet) {
            // While GPS is actively locking, reject coarse (> 65m) network triangles
            if (timeDeltaMs <= LOCATION_NETWORK_COARSE_SUPPRESSION_MS && acc > LOCATION_NETWORK_INDOOR_FALLBACK_ACCURACY_M) {
                return false
            }
            // Within 45s of GPS, let NETWORK override only when crisp RTT is materially sharper
            if (timeDeltaMs <= LOCATION_GPS_OWNERSHIP_WINDOW_MS) {
                return acc <= 40f && (acc + 15f) < bestAcc
            }
            // Past 45s without GPS, allow crisp (<= 65m) or post-120s (<= 100m) network fixes indoors
            val fallbackLimit = if (timeDeltaMs > LOCATION_NETWORK_COARSE_SUPPRESSION_MS) {
                LOCATION_NETWORK_MAX_ACCURACY_M
            } else {
                LOCATION_NETWORK_INDOOR_FALLBACK_ACCURACY_M
            }
            return acc <= fallbackLimit
        }

        // Prevent a momentary GPS multipath spike right after a sharp GPS lock
        if (!isNet && bestIsGps && timeDeltaMs in 0L..10_000L && bestAcc <= 30f && acc > 80f) {
            return false
        }

        if (candidate.provider.equals(currentBest.provider, ignoreCase = true) && timeDeltaMs < 0L) {
            return false
        }

        return isGps || timeDeltaMs >= 0L
    }

    /**
     * Thread-safe monotonic gate shared across MainActivity and BackgroundLocationService so
     * parallel location providers cannot race backwards in time or overwrite fresh GPS with coarse cell fixes.
     */
    fun acceptLocationUpdate(location: android.location.Location): Boolean {
        val candidate = location.toSampleMeta()
        synchronized(locationArbiterLock) {
            val current = lastAcceptedLocationMeta
            if (!shouldAcceptLocationUpdate(candidate, current)) {
                return false
            }
            lastAcceptedLocationMeta = candidate
            return true
        }
    }

    /**
     * Selects the stronger fix between two cached last-known locations (e.g. GPS vs NETWORK),
     * preferring accurate recent GPS over newer coarse Wi-Fi/cellular triangulation.
     */
    fun pickBetterLocation(
        first: android.location.Location?,
        second: android.location.Location?
    ): android.location.Location? {
        if (first == null) return second
        if (second == null) return first
        val meta1 = first.toSampleMeta()
        val meta2 = second.toSampleMeta()
        val valid1 = shouldAcceptLocationUpdate(meta1, null)
        val valid2 = shouldAcceptLocationUpdate(meta2, null)
        if (!valid1 && !valid2) {
            val isZero1 = meta1.latitude == 0.0 && meta1.longitude == 0.0
            val isZero2 = meta2.latitude == 0.0 && meta2.longitude == 0.0
            if (isZero1 && isZero2) return null
            if (isZero1) return second
            if (isZero2) return first
            return if (meta1.accuracyMeters <= meta2.accuracyMeters) first else second
        }
        if (!valid1) return second
        if (!valid2) return first

        val deltaMs = timeDeltaBetweenSamplesMs(meta2, meta1)
        return if (deltaMs >= 0L) {
            if (shouldAcceptLocationUpdate(meta2, meta1)) second else first
        } else {
            if (shouldAcceptLocationUpdate(meta1, meta2)) first else second
        }
    }
}
