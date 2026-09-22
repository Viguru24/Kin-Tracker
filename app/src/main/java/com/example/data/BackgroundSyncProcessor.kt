package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackgroundSyncProcessor {
    private val cloudService = CloudSyncService.create()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val payloadAdapter = moshi.adapter(CloudGroupPayload::class.java)

    suspend fun processLocationUpdate(context: Context, location: Location) {
        val database = AppDatabase.getDatabase(context.applicationContext)
        val repository = FamilyRepository(database.familyDao())

        // 1. Get battery status
        var batteryPct = 85
        var isCharging = false
        try {
            val batteryStatusIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (batteryStatusIntent != null) {
                val level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100 / scale)
                }
                val status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {}

        // 2. Read home position from shared preferences
        val prefs = context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        val homeLat = prefs.getFloat("homeLat", AppConfig.DEFAULT_HOME_LAT.toFloat()).toDouble()
        val homeLng = prefs.getFloat("homeLng", AppConfig.DEFAULT_HOME_LNG.toFloat()).toDouble()

        val current = repository.getFamilyMembersOnce()
        val me = current.firstOrNull { it.id == "me" } ?: return

        // Distance calculation
        val latDiff = location.latitude - homeLat
        val lngDiff = location.longitude - homeLng
        val xDistanceKm = lngDiff * 111.0 * Math.cos(Math.toRadians(homeLat))
        val yDistanceKm = latDiff * 111.0
        val distanceTotalKm = Math.hypot(xDistanceKm, yDistanceKm)
        val isAtHome = distanceTotalKm <= 0.12 // 120 meters realistic residential geofence

        val speedMph = Math.round((location.speed * 2.23694f) * 10.0) / 10.0

        val status = if (isAtHome) {
            "At Home (Live GPS)"
        } else {
            "Live GPS tracking (${String.format(Locale.US, "%.2f", distanceTotalKm)} km away)"
        }



        val updatedMe = me.copy(
            x = if (isAtHome) homeLng else location.longitude,
            y = if (isAtHome) homeLat else location.latitude,
            batteryPercentage = batteryPct,
            isCharging = isCharging,
            speedMph = if (isAtHome) 0.0 else speedMph,
            statusText = status
        )
        repository.updateMember(updatedMe)
        repository.recordBreadcrumbThrottled(
            memberId = "me",
            latitude = if (isAtHome) homeLat else location.latitude,
            longitude = if (isAtHome) homeLng else location.longitude,
            speedMph = if (isAtHome) 0.0 else speedMph
        )
        if (!isAtHome) {
            RailwayTransitDetector.checkRailwayCorridorAsync("me", location.latitude, location.longitude, speedMph)
        }


        // 3. Sync and merge in background
        backgroundCloudSync(context, repository, prefs, location, batteryPct, isCharging, speedMph, status)
    }

    private suspend fun backgroundCloudSync(
        context: Context,
        repository: FamilyRepository,
        prefs: android.content.SharedPreferences,
        location: Location,
        batteryPct: Int,
        isCharging: Boolean,
        speedMph: Double,
        status: String
    ) {
        val isCloudSyncEnabled = prefs.getBoolean("isCloudSyncEnabled", true)
        var token = prefs.getString("groupSyncToken", "") ?: ""
        if (token.isBlank()) {
            val activeGroup = repository.getActiveGroupPinMappingOnce()
            if (activeGroup != null && activeGroup.groupToken.isNotBlank()) {
                token = activeGroup.groupToken
                prefs.edit().putString("groupSyncToken", token).putString("activeGroupPinCode", activeGroup.pinCode).apply()
            }
        }
        if (!isCloudSyncEnabled || token.isBlank()) return

        val myName = prefs.getString("myDeviceName", "Dad") ?: "Dad"
        val myColor = prefs.getString("myDeviceColor", "#AA22FF") ?: "#AA22FF"
        val myEmoji = prefs.getString("myDeviceEmoji", "👨") ?: "👨"
        var dUuid = prefs.getString("myDeviceUUID", "") ?: ""
        if (dUuid.isBlank()) {
            dUuid = java.util.UUID.randomUUID().toString().substring(0, 6)
            prefs.edit().putString("myDeviceUUID", dUuid).apply()
        }

        try {
            val lastActiveTimestamp = System.currentTimeMillis()
            val myCloudId = "device_" + myName.lowercase().replace("\\s".toRegex(), "") + "_" + dUuid

            // 1. GET Current Group Data
            val response = cloudService.getGroupData(token)
            var payload: CloudGroupPayload? = null
            var fetchSuccess = false

            if (response.isSuccessful) {
                val jsonString = response.body()?.string() ?: ""
                if (jsonString.isNotBlank() && jsonString != "null" && jsonString != "{}") {
                    try {
                        payload = payloadAdapter.fromJson(jsonString)
                    } catch (e: Exception) {}
                }
                fetchSuccess = true
            } else if (response.code() == 404) {
                payload = null
                fetchSuccess = true
            }

            if (!fetchSuccess) return

            // 2. Check alarm
            val matchesMe = payload?.members?.values?.any { member ->
                (member.id == myCloudId || member.name.equals(myName, ignoreCase = true)) && member.statusText == "🚨 ALARM"
            } ?: false
            if (matchesMe) {
                AlarmHelper.triggerAlarm(context)
            } else if (!matchesMe && AlarmHelper.isRinging) {
                AlarmHelper.stopAlarm()
            }

            val myEntry = payload?.members?.values?.firstOrNull {
                it.id == myCloudId || it.name.trim().equals(myName.trim(), ignoreCase = true)
            }
            if (myEntry != null && myEntry.isLocationPaused) {
                prefs.edit().putBoolean("is_location_paused", true).apply()
                BackgroundLocationService.stopService(context)
                return
            }

            val prefsHomeLat = prefs.getFloat("homeLat", AppConfig.DEFAULT_HOME_LAT.toFloat()).toDouble()
            val prefsHomeLng = prefs.getFloat("homeLng", AppConfig.DEFAULT_HOME_LNG.toFloat()).toDouble()
            val xDist = (location.longitude - prefsHomeLng) * 111.0 * Math.cos(Math.toRadians(prefsHomeLat))
            val yDist = (location.latitude - prefsHomeLat) * 111.0
            val distTotal = Math.hypot(xDist, yDist)
            val isAtHome = distTotal <= 0.12 // 120 meters realistic residential geofence

            val targetX = if (isAtHome) prefsHomeLng else location.longitude
            val targetY = if (isAtHome) prefsHomeLat else location.latitude

            val savedLocationSince = prefs.getLong("my_location_since", 0L)
            val anchorLat = prefs.getFloat("anchor_lat", 0f).toDouble()
            val anchorLng = prefs.getFloat("anchor_lng", 0f).toDouble()
            val distFromAnchorKm = if (anchorLat != 0.0 && anchorLng != 0.0) {
                Math.hypot((targetX - anchorLng) * 111.0 * Math.cos(Math.toRadians(targetY)), (targetY - anchorLat) * 111.0)
            } else 0.0
            val isMoving = if (isAtHome) false else (speedMph >= 1.2 || distFromAnchorKm > 0.15)
            val now = System.currentTimeMillis()

            val resolvedLocationSince = if (isMoving) {
                prefs.edit()
                    .putLong("my_location_since", 0L)
                    .putFloat("anchor_lat", targetY.toFloat())
                    .putFloat("anchor_lng", targetX.toFloat())
                    .apply()
                0L
            } else {
                if (isAtHome) {
                    if (savedLocationSince > 0L) {
                        savedLocationSince
                    } else {
                        prefs.edit()
                            .putLong("my_location_since", now)
                            .putFloat("anchor_lat", prefsHomeLat.toFloat())
                            .putFloat("anchor_lng", prefsHomeLng.toFloat())
                            .apply()
                        now
                    }
                } else {
                    if (distFromAnchorKm > 0.10 || savedLocationSince == 0L || anchorLat == 0.0) {
                        prefs.edit()
                            .putLong("my_location_since", now)
                            .putFloat("anchor_lat", targetY.toFloat())
                            .putFloat("anchor_lng", targetX.toFloat())
                            .apply()
                        now
                    } else {
                        savedLocationSince
                    }
                }
            }

            val ghostExpiry = prefs.getLong("ghostModeExpiryTime", 0L)
            val isGhostMode = System.currentTimeMillis() < ghostExpiry

            val isLocPaused = prefs.getBoolean("is_location_paused", false)
            val myCloudMember = CloudMember(
                id = myCloudId,
                name = myName,
                avatarColorHex = myColor,
                x = if (isGhostMode) 0.0 else targetX,
                y = if (isGhostMode) 0.0 else targetY,
                batteryPercentage = batteryPct,
                isCharging = isCharging,
                speedMph = if (isGhostMode || isAtHome || isLocPaused) 0.0 else speedMph,
                statusText = if (isLocPaused) "⏸️ Paused (Battery Saver)" else if (isGhostMode) "Ghost Mode Active (Location Paused)" else status,
                isComingHome = false,
                etaMinutes = 0,
                lastActive = lastActiveTimestamp,
                avatarEmoji = myEmoji,
                locationSince = resolvedLocationSince,
                isLocationPaused = isLocPaused
            )

            // 3. Sync and Merge Shopping items in background with cloud tombstones
            val localShoppingItems = repository.getShoppingItemsOnce()
            val deletionPrefs = context.getSharedPreferences("shopping_deletions", Context.MODE_PRIVATE)
            val incomingShoppingItems = payload?.shoppingItems ?: emptyList()
            val incomingDeletions = payload?.deletedShoppingItems ?: emptyMap()

            val mergedDeletions = mutableMapOf<String, Long>()
            val nowMs = System.currentTimeMillis()
            val cutoff = nowMs - (30L * 24 * 60 * 60 * 1000L) // 30 days retention

            for ((k, v) in deletionPrefs.all) {
                if (v is Long && v > cutoff) {
                    mergedDeletions[k] = v
                }
            }
            for ((k, v) in incomingDeletions) {
                if (v > cutoff) {
                    val existing = mergedDeletions[k] ?: 0L
                    if (v > existing) {
                        mergedDeletions[k] = v
                    }
                }
            }
            val delEditor = deletionPrefs.edit()
            for ((k, v) in mergedDeletions) {
                delEditor.putLong(k, v)
            }
            delEditor.apply()

            fun getDeletionTimestamp(name: String): Long {
                val normKey = name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                val rawKey = name.lowercase().trim()
                return maxOf(mergedDeletions[normKey] ?: 0L, mergedDeletions[rawKey] ?: 0L)
            }

            val mergedShoppingMap = LinkedHashMap<String, CloudShoppingItem>()

            for (localItem in localShoppingItems) {
                val normKey = localItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                if (normKey.isBlank()) continue
                val delTime = getDeletionTimestamp(localItem.name)
                if (delTime == 0L || localItem.timestamp > delTime) {
                    val existing = mergedShoppingMap[normKey]
                    if (existing == null || localItem.timestamp > existing.timestamp) {
                        mergedShoppingMap[normKey] = CloudShoppingItem(
                            name = localItem.name,
                            isChecked = localItem.isChecked,
                            addedByMemberId = localItem.addedByMemberId,
                            addedByMemberName = localItem.addedByMemberName,
                            timestamp = localItem.timestamp
                        )
                    }
                }
            }

            for (cloudItem in incomingShoppingItems) {
                val normKey = cloudItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                if (normKey.isBlank()) continue
                val delTime = getDeletionTimestamp(cloudItem.name)
                if (delTime > 0L && cloudItem.timestamp <= delTime) {
                    mergedShoppingMap.remove(normKey)
                } else if (delTime == 0L || cloudItem.timestamp > delTime) {
                    val localMatch = mergedShoppingMap[normKey]
                    if (localMatch == null || cloudItem.timestamp > localMatch.timestamp) {
                        mergedShoppingMap[normKey] = cloudItem
                    }
                }
            }

            val finalShoppingList = mergedShoppingMap.values.toList()
            val seenKeys = mutableSetOf<String>()

            for (localItem in localShoppingItems) {
                val normKey = localItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                val matchedCloud = mergedShoppingMap[normKey]
                if (matchedCloud == null || seenKeys.contains(normKey)) {
                    repository.deleteShoppingItem(localItem)
                } else {
                    seenKeys.add(normKey)
                    if (localItem.isChecked != matchedCloud.isChecked && matchedCloud.timestamp > localItem.timestamp) {
                        repository.updateShoppingItem(localItem.copy(isChecked = matchedCloud.isChecked, timestamp = matchedCloud.timestamp))
                    }
                }
            }

            for (cloudItem in finalShoppingList) {
                val normKey = cloudItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                if (!seenKeys.contains(normKey)) {
                    repository.insertShoppingItem(
                        ShoppingItem(
                            name = cloudItem.name,
                            isChecked = cloudItem.isChecked,
                            addedByMemberId = cloudItem.addedByMemberId,
                            addedByMemberName = cloudItem.addedByMemberName,
                            timestamp = cloudItem.timestamp
                        )
                    )
                }
            }

            // 4. Clean up stale / duplicate devices in cloud payload
            val updatedMembers = payload?.members?.toMutableMap() ?: mutableMapOf()
            val cleanMyName = myName.lowercase().trim()
            val myCleanNameNoRole = cleanMyName.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()

            val keysToRemove = updatedMembers.filter { entry ->
                val entryId = entry.key
                val isStale = (System.currentTimeMillis() - entry.value.lastActive) > 30 * 60 * 1000L
                entryId != myCloudId && isStale && entryId.endsWith("_" + dUuid)
            }.keys
            for (k in keysToRemove) {
                updatedMembers.remove(k)
            }

            updatedMembers[myCloudId] = myCloudMember

            val newPayload = if (payload != null) {
                payload.copy(
                    lastUpdated = lastActiveTimestamp,
                    members = updatedMembers,
                    shoppingItems = finalShoppingList,
                    deletedShoppingItems = mergedDeletions
                )
            } else {
                CloudGroupPayload(
                    homeLat = prefsHomeLat,
                    homeLng = prefsHomeLng,
                    isHomeCalibrated = true,
                    lastUpdated = lastActiveTimestamp,
                    members = mapOf(myCloudId to myCloudMember),
                    shoppingItems = finalShoppingList,
                    deletedShoppingItems = mergedDeletions
                )
            }

            // 5. PUT updated payload
            val payloadJson = payloadAdapter.toJson(newPayload)
            val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
            cloudService.updateGroupData(token, requestBody)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
