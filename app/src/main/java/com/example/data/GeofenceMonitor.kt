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

        val customZones: List<SafeZone> = try {
            repository.getAllSafeZonesOnce()
        } catch (_: Exception) {
            emptyList()
        }

        val allPlaces = mutableListOf<MonitoredPlace>()
        if (homeLat != 0.0 && homeLng != 0.0) {
            allPlaces.add(MonitoredPlace("place_home", "Home", homeLat, homeLng, homeRadius, "home", isHome = true))
        }
        if (isWorkCalibrated && workLat != 0.0 && workLng != 0.0) {
            val isWoodcote = Math.hypot((workLat - 51.3280) * 111.0, (workLng - (-0.1405)) * 111.0) < 0.6
            val placeName = if (isWoodcote) "Woodcote Primary School" else "Work"
            val placeIcon = if (isWoodcote) "school" else "work"
            allPlaces.add(MonitoredPlace("place_work", placeName, workLat, workLng, workRadius, placeIcon, isWork = !isWoodcote))
        }
        customZones.forEach { zone ->
            if (zone.iconName.lowercase() != "home" && !zone.name.lowercase().contains("home")) {
                allPlaces.add(MonitoredPlace(zone.id, zone.name, zone.latitude, zone.longitude, zone.radiusMeters, zone.iconName))
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

        members.forEach { member ->
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

            val cleanMemberName = member.name
                .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "")
                .trim()

            allPlaces.forEach { place ->
                val xDist = (member.x - place.longitude) * 111.0 * Math.cos(Math.toRadians(place.latitude))
                val yDist = (member.y - place.latitude) * 111.0
                val distMeters = Math.hypot(xDist, yDist) * 1000.0

                val key = "${cleanMemberName.lowercase()}_${place.id}"
                val lastStatus = statusPrefs.getString("status_$key", null)
                val inCount = statusPrefs.getInt("incount_$key", 0)
                val outCount = statusPrefs.getInt("outcount_$key", 0)

                val effectiveRadius = if (place.isHome) maxOf(place.radiusMeters, 160.0) else place.radiusMeters
                val exitHysteresis = if (place.isHome) 70.0 else AppConfig.EXIT_HYSTERESIS_METERS

                // 1. INSIDE BOUNDARY CHECK: Within defined place radius (160m for Home)
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
                            if (now - lastAlert > AppConfig.GEOFENCE_COOLDOWN_MS) {
                                statusEditor.putLong("alert_arr_$key", now)
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
                                }
                            }
                        }
                    }
                }
                // 2. OUTSIDE / DEPARTURE BOUNDARY CHECK: Beyond radius + buffer (230m for Home)
                else if (distMeters > (effectiveRadius + exitHysteresis)) {
                    statusEditor.putInt("incount_$key", 0)
                    if (lastStatus == "inside") {
                        val newOutCount = outCount + 1
                        statusEditor.putInt("outcount_$key", newOutCount)

                        val isConfirmedDeparture = newOutCount >= AppConfig.DEPARTURE_CONFIRMATION_CHECKS &&
                                (member.speedMph >= AppConfig.MIN_EXIT_SPEED_MPH || distMeters > (effectiveRadius + 100.0))

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
                                    val warningMsg = "$cleanMemberName has left $placeDisplayName!"
                                    repository.insertLog(
                                        ActivityLog(
                                            memberId = member.id,
                                            memberName = member.name,
                                            actionText = "left ${place.name} (departed building)",
                                            iconName = "away"
                                        )
                                    )
                                    if (isDepartureAlertsEnabled) {
                                        val alertTitle = "🚪 Departure Warning"
                                        AlertNotificationHelper.postAlertNotification(context, alertTitle, warningMsg)
                                        onUiEvent?.invoke("🚪 Departure Warning: $warningMsg")
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
        }
        statusEditor.apply()
    }
}
