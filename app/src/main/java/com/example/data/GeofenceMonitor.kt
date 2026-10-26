package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import java.util.Locale

object GeofenceMonitor {

    data class MonitoredPlace(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val iconName: String = "home",
        val isHome: Boolean = false,
        val isWork: Boolean = false
    )

    private fun getStatusPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("geofence_status_prefs", Context.MODE_PRIVATE)
    }

    suspend fun evaluateGeofences(
        context: Context,
        repository: FamilyRepository,
        prefs: SharedPreferences,
        members: List<FamilyMember>,
        myLocation: Location?,
        onUiEvent: ((String) -> Unit)? = null
    ) {
        if (members.isEmpty()) return

        val isDepartureAlertsEnabled = prefs.getBoolean("isDepartureAlertsEnabled", true)
        val homeLat = prefs.getFloat("homeLat", AppConfig.DEFAULT_HOME_LAT.toFloat()).toDouble()
        val homeLng = prefs.getFloat("homeLng", AppConfig.DEFAULT_HOME_LNG.toFloat()).toDouble()
        val homeRadius = prefs.getFloat("homeRadiusMeters", AppConfig.DEFAULT_HOME_RADIUS_METERS.toFloat()).toDouble()

        val workLat = prefs.getFloat("workLat", 0f).toDouble()
        val workLng = prefs.getFloat("workLng", 0f).toDouble()
        val isWorkCalibrated = prefs.getBoolean("isWorkCalibrated", false)
        val workRadius = prefs.getFloat("workRadiusMeters", AppConfig.DEFAULT_WORK_RADIUS_METERS.toFloat()).toDouble()

        val customZones: List<SafeZone> = GeoUtils.withDefaultSafeZones(
            try {
                repository.getAllSafeZonesOnce()
            } catch (_: Exception) {
                emptyList()
            }
        )

        val rawPlaces = mutableListOf<MonitoredPlace>()
        if (homeLat != 0.0 && homeLng != 0.0) {
            rawPlaces.add(MonitoredPlace("place_home", "Home", homeLat, homeLng, homeRadius, "home", isHome = true))
        }
        if (isWorkCalibrated && workLat != 0.0 && workLng != 0.0) {
            val isWoodcote = Math.hypot((workLat - 51.3280) * 111.0, (workLng - (-0.1405)) * 111.0) < 0.6
            val placeName = if (isWoodcote) "Woodcote Primary School" else "Work"
            val placeIcon = if (isWoodcote) "school" else "work"
            rawPlaces.add(MonitoredPlace("place_work", placeName, workLat, workLng, workRadius, placeIcon, isWork = !isWoodcote))
        }
        customZones.forEach { zone ->
            if (zone.iconName.lowercase() != "home" && !zone.name.lowercase().contains("home")) {
                rawPlaces.add(MonitoredPlace(zone.id, zone.name, zone.latitude, zone.longitude, zone.radiusMeters, zone.iconName))
            }
        }

        // Deduplicate overlapping places within 120m of each other (prefer named custom zones over generic place_work)
        val allPlaces = mutableListOf<MonitoredPlace>()
        for (place in rawPlaces) {
            val duplicateIdx = allPlaces.indexOfFirst { existing ->
                val distM = Math.hypot(
                    (place.longitude - existing.longitude) * 111.0 * Math.cos(Math.toRadians(existing.latitude)),
                    (place.latitude - existing.latitude) * 111.0
                ) * 1000.0
                distM < 120.0
            }
            if (duplicateIdx >= 0) {
                // If the new place is a custom named zone and existing was generic work, replace it
                if (!place.id.startsWith("place_") && allPlaces[duplicateIdx].id.startsWith("place_")) {
                    allPlaces[duplicateIdx] = place
                }
            } else {
                allPlaces.add(place)
            }
        }

        if (allPlaces.isEmpty()) return

        val myName = prefs.getString("myDeviceName", "Dad") ?: "Dad"
        val myUUID = prefs.getString("myDeviceUUID", "") ?: ""
        val statusPrefs = getStatusPrefs(context)
        val statusEditor = statusPrefs.edit()
        val now = System.currentTimeMillis()

        val me = members.firstOrNull {
            it.id == "me" ||
            (myUUID.isNotBlank() && it.id == myUUID) ||
            (it.id.startsWith("device_") && myUUID.isNotBlank() && it.id.endsWith("_$myUUID")) ||
            (myName.isNotBlank() && it.name.equals(myName, ignoreCase = true)) ||
            it.name.contains("(You)", ignoreCase = true)
        }

        val myLat = myLocation?.latitude ?: me?.y ?: 0.0
        val myLng = myLocation?.longitude ?: me?.x ?: 0.0
        val hasMyGps = myLat != 0.0 && myLng != 0.0

        val homePlace = allPlaces.firstOrNull { it.isHome }
        val distMeToHomeMeters = if (hasMyGps && homePlace != null) {
            val x = (myLng - homePlace.longitude) * 111.0 * Math.cos(Math.toRadians(homePlace.latitude))
            val y = (myLat - homePlace.latitude) * 111.0
            Math.hypot(x, y) * 1000.0
        } else Double.MAX_VALUE
        val isMeOutsideHome = hasMyGps && distMeToHomeMeters > 200.0

        // Deduplicate incoming member records to single canonical person entries (prioritizing primary carried phone over secondary tablet/iPad)
        val deduplicatedMembers = members
            .filter { it.x != 0.0 && it.y != 0.0 }
            .groupBy { IdentityUtils.getCanonicalPersonKey(it.name, it.id) }
            .mapValues { (_, group) ->
                val primaryPhone = group.firstOrNull { m ->
                    !m.name.contains("tab", ignoreCase = true) &&
                    !m.name.contains("tablet", ignoreCase = true) &&
                    !m.name.contains("ipad", ignoreCase = true) &&
                    !m.id.contains("tab", ignoreCase = true)
                }
                primaryPhone ?: group.maxByOrNull { it.lastActive } ?: group.first()
            }
            .values

        deduplicatedMembers.forEach { member ->
            if (member.x == 0.0 && member.y == 0.0) return@forEach

            // Never notify the user about their own arrival/departure
            val isSelf = member.id == "me" ||
                    (myUUID.isNotBlank() && member.id == myUUID) ||
                    (member.id.startsWith("device_") && myUUID.isNotBlank() && member.id.endsWith("_$myUUID")) ||
                    member.name.equals(myName, ignoreCase = true) ||
                    member.name.contains("(You)", ignoreCase = true)
            if (isSelf) return@forEach

            // Check if member is paused
            val isPaused = member.isLocationPaused ||
                    prefs.getBoolean("is_member_paused_${member.id}", false) ||
                    member.statusText.contains("Paused", ignoreCase = true)
            if (isPaused) return@forEach

            val distToMeMeters = if (hasMyGps) {
                val xDistMe = (member.x - myLng) * 111.0 * Math.cos(Math.toRadians(member.y))
                val yDistMe = (member.y - myLat) * 111.0
                Math.hypot(xDistMe, yDistMe) * 1000.0
            } else Double.MAX_VALUE

            val distMemberToHomeMeters = if (homePlace != null) {
                val x = (member.x - homePlace.longitude) * 111.0 * Math.cos(Math.toRadians(homePlace.latitude))
                val y = (member.y - homePlace.latitude) * 111.0
                Math.hypot(x, y) * 1000.0
            } else Double.MAX_VALUE
            val isMemberOutsideHome = distMemberToHomeMeters > 200.0

            if (hasMyGps && distToMeMeters <= 250.0) {
                statusEditor.putLong("colocated_${member.id}", now)
                if (isMeOutsideHome && isMemberOutsideHome) {
                    statusEditor.putBoolean("traveling_with_me_${member.id}", true)
                }
            }

            val isTablet = member.name.contains("tab", ignoreCase = true) || member.name.contains("tablet", ignoreCase = true) || member.name.contains("ipad", ignoreCase = true)
            val cleanMemberName = if (isTablet) member.name.trim() else member.name
                .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("('s|s)?\\s*(iPhone|iPad|Tab|Tablet|Phone|Device)", RegexOption.IGNORE_CASE), "")
                .trim()
            val personKey = IdentityUtils.getCanonicalPersonKey(member.name, member.id)

            allPlaces.forEach { place ->
                val xDist = (member.x - place.longitude) * 111.0 * Math.cos(Math.toRadians(place.latitude))
                val yDist = (member.y - place.latitude) * 111.0
                val distMeters = Math.hypot(xDist, yDist) * 1000.0

                val key = "${personKey}_${place.id}"
                val lastStatus = statusPrefs.getString("status_$key", null)
                val inCount = statusPrefs.getInt("incount_$key", 0)
                val outCount = statusPrefs.getInt("outcount_$key", 0)

                val isSchoolOrSafeZone = !place.isHome && !place.isWork
                val effectiveRadius = when {
                    place.isHome -> maxOf(place.radiusMeters, 160.0)
                    isSchoolOrSafeZone -> GeoUtils.effectiveSafeZoneRadiusMeters(
                        SafeZone(place.id, place.name, place.latitude, place.longitude, place.radiusMeters, place.iconName)
                    )
                    else -> place.radiusMeters
                }
                val exitHysteresis = if (place.isHome || isSchoolOrSafeZone) 70.0 else AppConfig.EXIT_HYSTERESIS_METERS

                // 1. INSIDE BOUNDARY CHECK: Within defined place radius (160m for Home, 180m for High School)
                if (distMeters <= effectiveRadius) {
                    statusEditor.putInt("outcount_$key", 0)
                    val newInCount = inCount + 1
                    statusEditor.putInt("incount_$key", newInCount)

                    if (lastStatus == null) {
                        // Initial startup state: member was already inside this zone
                        statusEditor.putString("status_$key", "inside")
                        statusEditor.putInt("incount_$key", AppConfig.ARRIVAL_CONFIRMATION_CHECKS)
                    } else if (lastStatus == "outside") {
                        // Confirm arrival only after consecutive verified stable readings inside boundary
                        if (newInCount >= AppConfig.ARRIVAL_CONFIRMATION_CHECKS) {
                            statusEditor.putString("status_$key", "inside")
                            statusEditor.putInt("incount_$key", 0)
                            val lastAlert = statusPrefs.getLong("alert_arr_$key", 0L)
                            val lastGlobalPersonAlert = statusPrefs.getLong("global_arr_$personKey", 0L)

                            // Enforce both per-zone and global per-person arrival cooldown to prevent multi-place or repeating spam
                            if (now - lastAlert > AppConfig.GEOFENCE_COOLDOWN_MS && now - lastGlobalPersonAlert > AppConfig.GEOFENCE_COOLDOWN_MS) {
                                statusEditor.putLong("alert_arr_$key", now)
                                statusEditor.putLong("global_arr_$personKey", now)
                                val placeDisplayName = if (place.isHome) "Home" else place.name

                                val lastCoLocated = statusPrefs.getLong("colocated_${member.id}", 0L)
                                val wasTraveling = statusPrefs.getBoolean("traveling_with_me_${member.id}", false)
                                val isWithMeNow = hasMyGps && distToMeMeters <= 200.0
                                val recentlyWithMe = (now - lastCoLocated) < 5 * 60 * 1000L
                                val traveledTogether = wasTraveling && recentlyWithMe
                                val isArrivingWithMe = (place.isHome && (isWithMeNow || traveledTogether)) || (!place.isHome && isWithMeNow)

                                if (isArrivingWithMe) {
                                    repository.insertLog(
                                        ActivityLog(
                                            memberId = member.id,
                                            memberName = member.name,
                                            actionText = "arrived at $placeDisplayName (with you)",
                                            iconName = "check_in"
                                        )
                                    )
                                    if (place.isHome) {
                                        statusEditor.putBoolean("traveling_with_me_${member.id}", false)
                                    }
                                } else {
                                    repository.insertLog(
                                        ActivityLog(
                                            memberId = member.id,
                                            memberName = member.name,
                                            actionText = "arrived at $placeDisplayName",
                                            iconName = "check_in"
                                        )
                                    )
                                    val alertTitle = "📍 Arrival Notice"
                                    val alertMessage = "$cleanMemberName has arrived at $placeDisplayName!"
                                    AlertNotificationHelper.postAlertNotification(context, alertTitle, alertMessage)
                                    onUiEvent?.invoke("📍 Arrival Notice: $alertMessage")
                                    VoiceAnnouncementManager.announceIfEnabled(context, "📍 Arrival Notice: $alertMessage")
                                }
                            }
                        }
                    }
                }
                // 2. OUTSIDE / DEPARTURE BOUNDARY CHECK: Beyond radius + buffer
                else if (distMeters > (effectiveRadius + exitHysteresis)) {
                    // Suppress false departure if member is explicitly reporting "At Home" or "At <SafeZone>"
                    if ((place.isHome && member.statusText.contains("At Home", ignoreCase = true)) ||
                        (!place.isHome && member.statusText.contains("At ${place.name}", ignoreCase = true))) {
                        statusEditor.putInt("outcount_$key", 0)
                        return@forEach
                    }

                    // Extra protection for school office drawers: if stationary (speed < 1.8 mph) and within cell tower jitter range,
                    // suppress departure
                    val isSchool = !place.isHome && !place.isWork
                    val isStationaryInDrawer = member.speedMph < AppConfig.MIN_EXIT_SPEED_MPH
                    if (isSchool && isStationaryInDrawer && distMeters <= (effectiveRadius + 200.0)) {
                        statusEditor.putInt("outcount_$key", 0)
                        return@forEach
                    }

                    statusEditor.putInt("incount_$key", 0)
                    if (lastStatus == "inside") {
                        val newOutCount = outCount + 1
                        statusEditor.putInt("outcount_$key", newOutCount)

                        val isConfirmedDeparture = if (isStationaryInDrawer) {
                            // When stationary (phone in office drawer), require sustained distance far outside campus (> effectiveRadius + 250m)
                            // AND require at least 6 consecutive confirmation checks (30+ seconds)
                            newOutCount >= (AppConfig.DEPARTURE_CONFIRMATION_CHECKS * 2) && distMeters > (effectiveRadius + 250.0)
                        } else {
                            newOutCount >= AppConfig.DEPARTURE_CONFIRMATION_CHECKS &&
                                (member.speedMph >= AppConfig.MIN_EXIT_SPEED_MPH || distMeters > (effectiveRadius + 120.0))
                        }

                        if (isConfirmedDeparture) {
                            statusEditor.putString("status_$key", "outside")
                            statusEditor.putInt("outcount_$key", 0)

                            val lastAlert = statusPrefs.getLong("alert_dep_$key", 0L)
                            if (now - lastAlert > AppConfig.GEOFENCE_COOLDOWN_MS) {
                                statusEditor.putLong("alert_dep_$key", now)
                                val placeDisplayName = if (place.isHome) "the house" else place.name
                                val isDepartingWithMe = hasMyGps && distToMeMeters <= 200.0

                                if (isDepartingWithMe) {
                                    repository.insertLog(
                                        ActivityLog(
                                            memberId = member.id,
                                            memberName = member.name,
                                            actionText = "left ${place.name} (with you)",
                                            iconName = "away"
                                        )
                                    )
                                } else {
                                    val isRailway = RailwayTransitDetector.isMemberOnRailway(member.id)
                                    val transitMode = classifyTransitMode(member.speedMph, member.statusText, member.id)
                                    val destination = TripTransitEngine.determineHeadingDestination(
                                        curLat = member.y,
                                        curLng = member.x,
                                        prevLat = place.latitude,
                                        prevLng = place.longitude,
                                        safeZones = customZones,
                                        homeLat = homeLat,
                                        homeLng = homeLng,
                                        workLat = workLat,
                                        workLng = workLng,
                                        speedMph = member.speedMph,
                                        isRailway = isRailway
                                    )
                                    val warningMsg = TripTransitEngine.formatDepartureMessage(
                                        memberName = cleanMemberName,
                                        originName = placeDisplayName,
                                        transitMode = transitMode,
                                        destinationName = destination,
                                        speedMph = member.speedMph
                                    )
                                    repository.insertLog(
                                        ActivityLog(
                                            memberId = member.id,
                                            memberName = member.name,
                                            actionText = warningMsg.removePrefix("$cleanMemberName ").trim(),
                                            iconName = "away"
                                        )
                                    )
                                    if (isDepartureAlertsEnabled) {
                                        val alertTitle = "🚪 Departure Warning"
                                        AlertNotificationHelper.postAlertNotification(context, alertTitle, warningMsg)
                                        onUiEvent?.invoke("🚪 Departure Warning: $warningMsg")
                                        VoiceAnnouncementManager.announceIfEnabled(context, "🚪 Departure Warning: $warningMsg")
                                    }
                                }
                            }
                        }
                    } else if (lastStatus == null) {
                        // Initial startup state: member was already outside
                        statusEditor.putString("status_$key", "outside")
                        statusEditor.putInt("outcount_$key", 0)
                    }
                } else {
                    // In hysteresis buffer zone: retain current state, reset confirmation counts
                    statusEditor.putInt("incount_$key", 0)
                    statusEditor.putInt("outcount_$key", 0)
                }
            }

            // Also evaluate non-geofenced arbitrary journeys (e.g. departing station, hotel, friend's house)
            TripTransitEngine.evaluateMemberJourney(
                context = context,
                repository = repository,
                prefs = prefs,
                member = member,
                safeZones = customZones,
                homeLat = homeLat,
                homeLng = homeLng,
                workLat = workLat,
                workLng = workLng,
                onUiEvent = onUiEvent
            )
        }
        statusEditor.apply()
    }
}
