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
        val isAtHome = distanceTotalKm < 0.035 // 35 meters threshold

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
            }

            val prefsHomeLat = prefs.getFloat("homeLat", AppConfig.DEFAULT_HOME_LAT.toFloat()).toDouble()
            val prefsHomeLng = prefs.getFloat("homeLng", AppConfig.DEFAULT_HOME_LNG.toFloat()).toDouble()
            val xDist = (location.longitude - prefsHomeLng) * 111.0 * Math.cos(Math.toRadians(prefsHomeLat))
            val yDist = (location.latitude - prefsHomeLat) * 111.0
            val distTotal = Math.hypot(xDist, yDist)
            val isAtHome = distTotal < 0.035 // 35 meters threshold

            val targetX = if (isAtHome) prefsHomeLng else location.longitude
            val targetY = if (isAtHome) prefsHomeLat else location.latitude

            val savedLocationSince = prefs.getLong("my_location_since", 0L)
            val anchorLat = prefs.getFloat("anchor_lat", 0f).toDouble()
            val anchorLng = prefs.getFloat("anchor_lng", 0f).toDouble()
            val distFromAnchorKm = if (anchorLat != 0.0 && anchorLng != 0.0) {
                Math.hypot((targetX - anchorLng) * 111.0 * Math.cos(Math.toRadians(targetY)), (targetY - anchorLat) * 111.0)
            } else 0.0
            val hasDeparted = distFromAnchorKm > 0.15 && speedMph > 2.5

            val resolvedLocationSince = if (savedLocationSince > 0L && !hasDeparted) {
                savedLocationSince
            } else {
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putLong("my_location_since", now)
                    .putFloat("anchor_lat", targetY.toFloat())
                    .putFloat("anchor_lng", targetX.toFloat())
                    .apply()
                now
            }

            val ghostExpiry = prefs.getLong("ghostModeExpiryTime", 0L)
            val isGhostMode = System.currentTimeMillis() < ghostExpiry

            val myCloudMember = CloudMember(
                id = myCloudId,
                name = myName,
                avatarColorHex = myColor,
                x = if (isGhostMode) 0.0 else targetX,
                y = if (isGhostMode) 0.0 else targetY,
                batteryPercentage = batteryPct,
                isCharging = isCharging,
                speedMph = if (isGhostMode || isAtHome) 0.0 else speedMph,
                statusText = if (isGhostMode) "Ghost Mode Active (Location Paused)" else status,
                isComingHome = false,
                etaMinutes = 0,
                lastActive = lastActiveTimestamp,
                avatarEmoji = myEmoji,
                locationSince = resolvedLocationSince
            )

            // 3. Sync and Merge Shopping items in background
            val localShoppingItems = repository.getShoppingItemsOnce()
            val deletionPrefs = context.getSharedPreferences("shopping_deletions", Context.MODE_PRIVATE)
            val incomingShoppingItems = payload?.shoppingItems ?: emptyList()

            val mergedShoppingMap = mutableMapOf<String, CloudShoppingItem>()

            for (localItem in localShoppingItems) {
                val key = localItem.name.lowercase().trim()
                mergedShoppingMap[key] = CloudShoppingItem(
                    name = localItem.name,
                    isChecked = localItem.isChecked,
                    addedByMemberId = localItem.addedByMemberId,
                    addedByMemberName = localItem.addedByMemberName,
                    timestamp = localItem.timestamp
                )
            }

            for (cloudItem in incomingShoppingItems) {
                val key = cloudItem.name.lowercase().trim()
                val localDeletionTime = deletionPrefs.getLong(key, 0L)

                if (localDeletionTime > cloudItem.timestamp) {
                    mergedShoppingMap.remove(key)
                } else {
                    val localMatch = mergedShoppingMap[key]
                    if (localMatch != null) {
                        if (cloudItem.timestamp > localMatch.timestamp) {
                            mergedShoppingMap[key] = cloudItem
                        }
                    } else {
                        if (localDeletionTime == 0L || cloudItem.timestamp > localDeletionTime) {
                            mergedShoppingMap[key] = cloudItem
                        }
                    }
                }
            }

            val finalShoppingList = mergedShoppingMap.values.toList()
            for (cloudItem in finalShoppingList) {
                val key = cloudItem.name.lowercase().trim()
                val localMatch = localShoppingItems.firstOrNull { it.name.lowercase().trim() == key }
                if (localMatch == null) {
                    repository.insertShoppingItem(
                        ShoppingItem(
                            name = cloudItem.name,
                            isChecked = cloudItem.isChecked,
                            addedByMemberId = cloudItem.addedByMemberId,
                            addedByMemberName = cloudItem.addedByMemberName,
                            timestamp = cloudItem.timestamp
                        )
                    )
                } else {
                    if (localMatch.isChecked != cloudItem.isChecked && cloudItem.timestamp > localMatch.timestamp) {
                        repository.updateShoppingItem(
                            localMatch.copy(
                                isChecked = cloudItem.isChecked,
                                timestamp = cloudItem.timestamp
                            )
                        )
                    }
                }
            }

            for (localItem in localShoppingItems) {
                val key = localItem.name.lowercase().trim()
                val inIncoming = incomingShoppingItems.any { it.name.lowercase().trim() == key }
                val inMerged = mergedShoppingMap.containsKey(key)
                if (inIncoming && !inMerged) {
                    repository.deleteShoppingItem(localItem)
                }
            }

            // 4. Clean up stale / duplicate devices in cloud payload
            val updatedMembers = payload?.members?.toMutableMap() ?: mutableMapOf()
            val cleanMyName = myName.lowercase().trim()
            val myCleanNameNoRole = cleanMyName.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()

            val keysToRemove = updatedMembers.filter { entry ->
                val entryId = entry.key
                val entryName = entry.value.name.lowercase().trim()
                val entryCleanNameNoRole = entryName.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()
                val isStale = (System.currentTimeMillis() - entry.value.lastActive) > 5 * 60 * 1000L
                
                entryId != myCloudId && isStale && (
                    entryCleanNameNoRole == myCleanNameNoRole ||
                    (myCleanNameNoRole.contains("louis") && entryCleanNameNoRole.contains("louis")) ||
                    (myCleanNameNoRole.contains("dad") && entryCleanNameNoRole.contains("dad")) ||
                    (myCleanNameNoRole.contains("annette") && entryCleanNameNoRole.contains("annette")) ||
                    (myCleanNameNoRole.contains("wife") && entryCleanNameNoRole.contains("wife")) ||
                    (myCleanNameNoRole.contains("isabel") && entryCleanNameNoRole.contains("isabel")) ||
                    (myCleanNameNoRole.contains("eloise") && entryCleanNameNoRole.contains("eloise"))
                )
            }.keys
            for (k in keysToRemove) {
                updatedMembers.remove(k)
            }

            updatedMembers[myCloudId] = myCloudMember

            val newPayload = if (payload != null) {
                payload.copy(
                    lastUpdated = lastActiveTimestamp,
                    members = updatedMembers,
                    shoppingItems = finalShoppingList
                )
            } else {
                CloudGroupPayload(
                    homeLat = prefsHomeLat,
                    homeLng = prefsHomeLng,
                    isHomeCalibrated = true,
                    lastUpdated = lastActiveTimestamp,
                    members = mapOf(myCloudId to myCloudMember),
                    shoppingItems = finalShoppingList
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
