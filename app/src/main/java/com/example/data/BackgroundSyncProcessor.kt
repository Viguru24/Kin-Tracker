package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import com.example.data.sync.SyncResolutionHelper
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

    // Battery Optimization Cache: prevents cellular modem churn when stationary
    @Volatile private var lastSyncTimestamp = 0L
    @Volatile private var lastSyncLat = 0.0
    @Volatile private var lastSyncLng = 0.0
    @Volatile private var lastSyncBattery = -1
    @Volatile private var lastSyncCharging: Boolean? = null
    @Volatile private var lastSyncStatus = ""

    suspend fun processLocationUpdate(context: Context, location: Location, forceSync: Boolean = false) {
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

        // Distance calculation with GPS accuracy margin
        val latDiff = location.latitude - homeLat
        val lngDiff = location.longitude - homeLng
        val xDistanceKm = lngDiff * 111.0 * Math.cos(Math.toRadians(homeLat))
        val yDistanceKm = latDiff * 111.0
        val distanceTotalKm = Math.hypot(xDistanceKm, yDistanceKm)
        val distanceMeters = distanceTotalKm * 1000.0
        val accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else 20.0
        val speedMph = Math.round((location.speed * 2.23694f) * 10.0) / 10.0
        val homeRadiusSetting = prefs.getFloat("homeRadiusMeters", AppConfig.DEFAULT_HOME_RADIUS_METERS.toFloat()).toDouble()
        val wasAtHome = me.statusText.contains("At Home", ignoreCase = true)
        val isAtHome = (homeLat != 0.0 || homeLng != 0.0) && GeoUtils.isDistanceWithinHomeGeofence(
            distanceMeters = distanceMeters,
            homeRadiusMeters = homeRadiusSetting,
            wasAlreadyAtHome = wasAtHome,
            speedMph = speedMph,
            accuracyMeters = accuracyMeters
        )
        val customSafeZones = try { repository.getAllSafeZonesOnce() } catch (_: Exception) { emptyList() }
        val matchedSelfZone = if (!isAtHome) {
            GeoUtils.matchSafeZone(
                lat = location.latitude,
                lng = location.longitude,
                customZones = customSafeZones,
                currentStatusText = me.statusText,
                speedMph = speedMph
            )
        } else null
        val isAtSafeZone = matchedSelfZone != null && !isAtHome && speedMph < 3.5

        val status = when {
            isAtHome -> "At Home (Live GPS)"
            isAtSafeZone -> "At ${matchedSelfZone!!.name}"
            else -> "Live GPS tracking (${String.format(Locale.US, "%.2f", distanceTotalKm)} km away)"
        }

        val resolvedSelfX = when {
            isAtHome -> homeLng
            isAtSafeZone -> matchedSelfZone!!.longitude
            else -> location.longitude
        }
        val resolvedSelfY = when {
            isAtHome -> homeLat
            isAtSafeZone -> matchedSelfZone!!.latitude
            else -> location.latitude
        }
        val resolvedSelfSpeed = if (isAtHome || isAtSafeZone) 0.0 else speedMph

        val updatedMe = me.copy(
            x = resolvedSelfX,
            y = resolvedSelfY,
            batteryPercentage = batteryPct,
            isCharging = isCharging,
            speedMph = resolvedSelfSpeed,
            statusText = status
        )
        repository.updateMember(updatedMe)
        repository.recordBreadcrumbThrottled(
            memberId = "me",
            latitude = resolvedSelfY,
            longitude = resolvedSelfX,
            speedMph = resolvedSelfSpeed
        )
        if (!isAtHome && !isAtSafeZone) {
            InertialActivityDetector.maybeSampleBurst(context, speedMph)
            RailwayTransitDetector.checkRailwayCorridorAsync("me", location.latitude, location.longitude, speedMph)
        }


        // 3. Sync and merge in background
        backgroundCloudSync(context, repository, prefs, location, batteryPct, isCharging, speedMph, status, forceSync)
    }

    private suspend fun backgroundCloudSync(
        context: Context,
        repository: FamilyRepository,
        prefs: android.content.SharedPreferences,
        location: Location,
        batteryPct: Int,
        isCharging: Boolean,
        speedMph: Double,
        status: String,
        forceSync: Boolean = false
    ) {
        if (BackgroundLocationService.isInForeground && !forceSync) return
        val isCloudSyncEnabled = prefs.getBoolean("isCloudSyncEnabled", true)
        var token = prefs.getString("groupSyncToken", "") ?: ""
        if (token.isBlank()) {
            val activeGroup = repository.getActiveGroupPinMappingOnce()
            if (activeGroup != null && activeGroup.groupToken.isNotBlank()) {
                token = activeGroup.groupToken
                prefs.edit().putString("groupSyncToken", token).putString("activeGroupPinCode", activeGroup.pinCode).apply()
            }
        }
        if (!isCloudSyncEnabled || !SyncResolutionHelper.isValidSyncToken(token)) return

        // --- Smart Battery & Mobile Data Optimization Throttling ---
        val now = System.currentTimeMillis()
        val timeSinceLastSync = now - lastSyncTimestamp

        val distMovedM = if (lastSyncLat != 0.0 && lastSyncLng != 0.0) {
            val x = (location.longitude - lastSyncLng) * 111000.0 * Math.cos(Math.toRadians(location.latitude))
            val y = (location.latitude - lastSyncLat) * 111000.0
            Math.hypot(x, y)
        } else 999.0

        val isMetered = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.isActiveNetworkMetered == true
        } catch (_: Exception) {
            false
        }

        fun extractStatusCategory(s: String): String {
            return when {
                s.startsWith("At Home") -> "HOME"
                s.startsWith("At ") -> s
                s.startsWith("Live GPS tracking") -> "TRANSIT"
                s.contains("Paused") -> "PAUSED"
                s.contains("ALARM") -> "ALARM"
                else -> s
            }
        }

        val hasStatusCategoryChanged = extractStatusCategory(status) != extractStatusCategory(lastSyncStatus)
        val isUserMovingFast = speedMph >= 1.5 || distMovedM >= 60.0

        // Adaptive moving interval: 30s on Wi-Fi, 45s on cellular data to preserve mobile data caps
        val minMovingInterval = if (isMetered) 45_000L else 30_000L
        val isMovingThrottled = isUserMovingFast && timeSinceLastSync < minMovingInterval

        // Stationary threshold: ignore minor indoor GPS drift under 60m (80m on metered cellular)
        val minStationaryDist = if (isMetered) 80.0 else 60.0
        val hasMovedSignificantly = if (isUserMovingFast) distMovedM >= 25.0 else distMovedM >= minStationaryDist

        // Battery threshold: only wake network on >= 5% change or charging toggle
        val hasBatteryChangedSignificantly = (lastSyncBattery == -1 || Math.abs(batteryPct - lastSyncBattery) >= 5 || isCharging != lastSyncCharging)
        val isHeartbeatDue = timeSinceLastSync >= 15 * 60 * 1000L // 15-minute relaxed stationary heartbeat

        if (!forceSync && !hasStatusCategoryChanged && !hasBatteryChangedSignificantly && !isHeartbeatDue) {
            if (!hasMovedSignificantly || isMovingThrottled) {
                // Skip network request to conserve cellular data and battery modem power
                return
            }
        }

        val myName = prefs.getString("myDeviceName", "Dad") ?: "Dad"
        val myColor = prefs.getString("myDeviceColor", "#10B981") ?: "#10B981"
        val myEmoji = prefs.getString("myDeviceEmoji", "👨") ?: "👨"
        var dUuid = prefs.getString("myDeviceUUID", "") ?: ""
        if (dUuid.isBlank()) {
            dUuid = java.util.UUID.randomUUID().toString().substring(0, 6)
            prefs.edit().putString("myDeviceUUID", dUuid).apply()
        }

        try {
            val lastActiveTimestamp = System.currentTimeMillis()
            val myCloudId = SyncResolutionHelper.buildMyCloudId(myName, dUuid)

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
            val matchesMe = payload?.members?.values?.let { members ->
                SyncResolutionHelper.shouldTriggerLocalAlarm(
                    members = members,
                    myCloudId = myCloudId,
                    myDeviceUuid = dUuid,
                    myDeviceName = myName,
                    nowMs = lastActiveTimestamp
                )
            } ?: false
            if (matchesMe) {
                AlarmHelper.triggerAlarm(context)
            } else if (!matchesMe && AlarmHelper.isRinging) {
                AlarmHelper.stopAlarm()
            }

            val myEntry = payload?.members?.values?.firstOrNull {
                SyncResolutionHelper.isMyCloudEntry(it, myCloudId, dUuid, myName)
            }
            if (myEntry != null && myEntry.name.isNotBlank() && !myEntry.name.trim().equals(myName.trim(), ignoreCase = false)) {
                val newSyncedName = myEntry.name.trim()
                prefs.edit().putString("myDeviceName", newSyncedName).apply()
                val meLocal = repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }
                if (meLocal != null) {
                    repository.updateMember(meLocal.copy(name = newSyncedName))
                }
            }
            if (myEntry != null && myEntry.isLocationPaused) {
                prefs.edit()
                    .putBoolean("isLocationPaused", true)
                    .putBoolean("is_location_paused", true)
                    .apply()
                BackgroundLocationService.stopService(context)
                return
            }

            val prefsHomeLat = prefs.getFloat("homeLat", AppConfig.DEFAULT_HOME_LAT.toFloat()).toDouble()
            val prefsHomeLng = prefs.getFloat("homeLng", AppConfig.DEFAULT_HOME_LNG.toFloat()).toDouble()
            val distMeters = GeoUtils.distanceMeters(location.latitude, location.longitude, prefsHomeLat, prefsHomeLng)
            val distTotal = distMeters / 1000.0
            val homeRadiusSetting = prefs.getFloat("homeRadiusMeters", AppConfig.DEFAULT_HOME_RADIUS_METERS.toFloat()).toDouble()
            val accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else 20.0
            val isAtHome = (prefsHomeLat != 0.0 || prefsHomeLng != 0.0) && GeoUtils.isDistanceWithinHomeGeofence(
                distanceMeters = distMeters,
                homeRadiusMeters = homeRadiusSetting,
                wasAlreadyAtHome = status.contains("At Home", ignoreCase = true),
                speedMph = speedMph,
                accuracyMeters = accuracyMeters
            )

            val customSafeZones = try { repository.getAllSafeZonesOnce() } catch (_: Exception) { emptyList() }
            val matchedSelfZone = if (!isAtHome) {
                GeoUtils.matchSafeZone(
                    lat = location.latitude,
                    lng = location.longitude,
                    customZones = customSafeZones,
                    currentStatusText = status,
                    speedMph = speedMph
                )
            } else null
            val isAtSafeZone = matchedSelfZone != null && !isAtHome && speedMph < 3.5

            val targetX = when {
                isAtHome -> prefsHomeLng
                isAtSafeZone -> matchedSelfZone!!.longitude
                else -> location.longitude
            }
            val targetY = when {
                isAtHome -> prefsHomeLat
                isAtSafeZone -> matchedSelfZone!!.latitude
                else -> location.latitude
            }

            val savedLocationSince = prefs.getLong("my_location_since", 0L)
            val anchorLat = prefs.getFloat("anchor_lat", 0f).toDouble()
            val anchorLng = prefs.getFloat("anchor_lng", 0f).toDouble()
            val distFromAnchorKm = if (anchorLat != 0.0 && anchorLng != 0.0) {
                Math.hypot((targetX - anchorLng) * 111.0 * Math.cos(Math.toRadians(targetY)), (targetY - anchorLat) * 111.0)
            } else 0.0
            val isMoving = if (isAtHome || isAtSafeZone) false else (speedMph >= 1.2 || distFromAnchorKm > 0.15)
            val now = System.currentTimeMillis()

            val resolvedLocationSince = if (isMoving) {
                prefs.edit()
                    .putLong("my_location_since", 0L)
                    .putFloat("anchor_lat", targetY.toFloat())
                    .putFloat("anchor_lng", targetX.toFloat())
                    .apply()
                0L
            } else {
                if (isAtHome || isAtSafeZone) {
                    if (savedLocationSince > 0L && distFromAnchorKm <= 0.20 && anchorLat != 0.0) {
                        savedLocationSince
                    } else {
                        prefs.edit()
                            .putLong("my_location_since", now)
                            .putFloat("anchor_lat", targetY.toFloat())
                            .putFloat("anchor_lng", targetX.toFloat())
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

            val isLocPaused = prefs.getBoolean("isLocationPaused", prefs.getBoolean("is_location_paused", false))
            val selfEffectiveSpeedMph = if (isGhostMode || isAtHome || isAtSafeZone || isLocPaused) 0.0 else speedMph
            val selfEffectiveStatus = if (isLocPaused) "⏸️ Paused (Battery Saver)" else if (isGhostMode) "Ghost Mode Active (Location Paused)" else status
            val selfTransitMode = if (isAtHome || isAtSafeZone || isLocPaused || isGhostMode) {
                TransitMode.STATIONARY
            } else {
                TransitClassifier.observeSample(
                    memberId = "me",
                    lat = targetY,
                    lng = targetX,
                    reportedSpeedMph = selfEffectiveSpeedMph,
                    statusText = selfEffectiveStatus,
                    isRailway = RailwayTransitDetector.isMemberOnRailway("me"),
                    inertialHint = InertialActivityDetector.getRecentHint()
                )
            }

            val myCloudMember = CloudMember(
                id = myCloudId,
                name = myName,
                avatarColorHex = myColor,
                x = if (isGhostMode) 0.0 else targetX,
                y = if (isGhostMode) 0.0 else targetY,
                batteryPercentage = batteryPct,
                isCharging = isCharging,
                speedMph = selfEffectiveSpeedMph,
                statusText = selfEffectiveStatus,
                isComingHome = false,
                etaMinutes = 0,
                lastActive = lastActiveTimestamp,
                avatarEmoji = myEmoji,
                locationSince = resolvedLocationSince,
                localIp = RoomAudioStreamManager.getLocalIpAddress(context),
                isAudioTransmitter = RoomAudioStreamManager.isTransmitterActive.value,
                isLocationPaused = isLocPaused,
                transitMode = selfTransitMode.name
            )

            // 3. Sync and Merge Shopping items in background with cloud tombstones
            val deletionPrefs = context.getSharedPreferences("shopping_deletions", Context.MODE_PRIVATE)
            val shoppingSyncResult = SyncResolutionHelper.syncShoppingListWithDatabase(
                repository = repository,
                deletionPrefs = deletionPrefs,
                incomingShoppingItems = payload?.shoppingItems ?: emptyList(),
                incomingDeletions = payload?.deletedShoppingItems ?: emptyMap()
            )
            val finalShoppingList = shoppingSyncResult.finalItems
            val mergedDeletions = shoppingSyncResult.mergedDeletions

            // 3b. Sync and Merge custom Safe Zones across the circle using LWW timestamps + tombstones
            val safeZoneMetaPrefs = context.getSharedPreferences("safe_zone_sync_meta", Context.MODE_PRIVATE)
            val safeZoneSyncResult = SyncResolutionHelper.syncSafeZonesWithDatabase(
                repository = repository,
                metaPrefs = safeZoneMetaPrefs,
                incomingZones = payload?.safeZones ?: emptyList(),
                incomingDeletions = payload?.deletedSafeZones ?: emptyMap()
            )
            val syncedCustomSafeZones = try { repository.getAllSafeZonesOnce() } catch (_: Exception) { customSafeZones }

            // 4. Clean up stale / duplicate devices in cloud payload
            val updatedMembers = payload?.members?.toMutableMap() ?: mutableMapOf()

            payload?.members?.values?.forEach { cloudM ->
                RoomAudioStreamManager.registerMemberAudioState(
                    memberId = cloudM.id,
                    ip = cloudM.localIp,
                    isTransmitting = cloudM.isAudioTransmitter,
                    memberName = cloudM.name
                )
            }

            val cleanMyName = myName.lowercase().trim()
            val myCleanNameNoRole = cleanMyName.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()

            val deletedMembersPrefs = context.getSharedPreferences("deleted_members", Context.MODE_PRIVATE)
            val keysToRemove = updatedMembers.filter { entry ->
                val entryId = entry.key
                val isMyOldDeviceKey = entryId != myCloudId && entryId.endsWith("_" + dUuid)
                val isOldMock = entryId.contains("abc123") || entryId.startsWith("mock_")
                val isExplicitlyDeleted = entryId != myCloudId && !entryId.endsWith("_$dUuid") &&
                        (deletedMembersPrefs.getBoolean("deleted_$entryId", false) || deletedMembersPrefs.getBoolean("deleted_member_$entryId", false))
                isMyOldDeviceKey || isExplicitlyDeleted || isOldMock
            }.keys
            for (k in keysToRemove) {
                updatedMembers.remove(k)
            }

            // Preserve newer timestamps for all other members before uploading
            val existingLocalBeforePut = repository.getFamilyMembersOnce()
            for ((k, cloudM) in updatedMembers) {
                if (k != myCloudId) {
                    val localM = existingLocalBeforePut.firstOrNull { it.id == k }
                    if (localM != null && localM.lastActive > cloudM.lastActive && localM.x != 0.0 && localM.y != 0.0) {
                        updatedMembers[k] = cloudM.copy(
                            x = localM.x,
                            y = localM.y,
                            speedMph = localM.speedMph,
                            statusText = localM.statusText,
                            lastActive = localM.lastActive
                        )
                    }
                }
            }

            updatedMembers[myCloudId] = myCloudMember

            val newPayload = if (payload != null) {
                payload.copy(
                    lastUpdated = lastActiveTimestamp,
                    members = updatedMembers,
                    shoppingItems = finalShoppingList,
                    deletedShoppingItems = mergedDeletions,
                    safeZones = safeZoneSyncResult.finalZones,
                    deletedSafeZones = safeZoneSyncResult.mergedDeletions,
                    completedTrips = emptyList() // CRITICAL: Never re-upload 90KB completed trip breadcrumbs
                )
            } else {
                CloudGroupPayload(
                    homeLat = prefsHomeLat,
                    homeLng = prefsHomeLng,
                    isHomeCalibrated = true,
                    lastUpdated = lastActiveTimestamp,
                    members = mapOf(myCloudId to myCloudMember),
                    shoppingItems = finalShoppingList,
                    deletedShoppingItems = mergedDeletions,
                    safeZones = safeZoneSyncResult.finalZones,
                    deletedSafeZones = safeZoneSyncResult.mergedDeletions,
                    completedTrips = emptyList()
                )
            }

            // 5. PUT updated payload
            val payloadJson = payloadAdapter.toJson(newPayload)
            val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
            cloudService.updateGroupData(token, requestBody)

            lastSyncTimestamp = lastActiveTimestamp
            lastSyncLat = location.latitude
            lastSyncLng = location.longitude
            lastSyncBattery = batteryPct
            lastSyncCharging = isCharging
            lastSyncStatus = status

            // 6. Ingest incoming circle members and evaluate background geofence arrival/departure alerts
            val contactsPrefs = context.getSharedPreferences("kintracker_contacts", Context.MODE_PRIVATE)
            val existingLocal = repository.getFamilyMembersOnce()
            val evaluatedMembers = mutableListOf<FamilyMember>()

            val meLocal = existingLocal.firstOrNull { it.id == "me" }
            if (meLocal != null) evaluatedMembers.add(meLocal)

            payload?.members?.values?.forEach { cloudM ->
                if (cloudM.id == myCloudId || (dUuid.length >= 4 && cloudM.id.endsWith("_$dUuid"))) return@forEach
                if (cloudM.id.contains("abc123") || cloudM.id.startsWith("mock_")) return@forEach

                val cleanCloudName = cloudM.name.lowercase().trim()
                val cleanKey = cleanCloudName
                    .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "")
                    .trim()

                // Check if user explicitly deleted this member locally
                if (deletedMembersPrefs.getBoolean("deleted_${cloudM.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_$cleanKey", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_${cloudM.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_$cleanKey", false)) {
                    return@forEach
                }

                val isLocallyPaused = prefs.getBoolean("is_member_paused_${cloudM.id}", false) ||
                        prefs.getBoolean("is_member_paused_$cleanKey", false)
                val isMemberLocPaused = cloudM.isLocationPaused || isLocallyPaused || cloudM.statusText.contains("Paused", ignoreCase = true)

                val locallyEditedName = contactsPrefs.getString("edited_name_${cloudM.id}", "")?.trim()?.takeIf { it.isNotBlank() }
                val resolvedName = when {
                    !locallyEditedName.isNullOrBlank() -> locallyEditedName
                    cloudM.name.trim().equals(myName.trim(), ignoreCase = true) -> "${cloudM.name} (Other Device)"
                    else -> cloudM.name
                }

                val matchingLocal = existingLocal.firstOrNull { it.id == cloudM.id }
                val hasNewerLocal = matchingLocal != null && matchingLocal.lastActive > cloudM.lastActive && matchingLocal.x != 0.0 && matchingLocal.y != 0.0
                val effX = if (hasNewerLocal) matchingLocal!!.x else cloudM.x
                val effY = if (hasNewerLocal) matchingLocal!!.y else cloudM.y
                val effLastActive = if (hasNewerLocal) matchingLocal!!.lastActive else cloudM.lastActive

                val homeRadiusSetting = prefs.getFloat("homeRadiusMeters", AppConfig.DEFAULT_HOME_RADIUS_METERS.toFloat()).toDouble()
                val distToHomeKm = if (prefsHomeLat != 0.0 && prefsHomeLng != 0.0 && effX != 0.0 && effY != 0.0) {
                    GeoUtils.fastDistanceKm(effY, effX, prefsHomeLat, prefsHomeLng)
                } else 999.0
                val wasMemberAtHome = matchingLocal?.statusText?.contains("At Home", ignoreCase = true) == true
                val effSpeed = if (hasNewerLocal) matchingLocal!!.speedMph else cloudM.speedMph
                val effStatus = if (hasNewerLocal) matchingLocal!!.statusText else cloudM.statusText
                val isMemberAtHome = GeoUtils.isMemberConsideredAtHome(
                    lat = effY,
                    lng = effX,
                    homeLat = prefsHomeLat,
                    homeLng = prefsHomeLng,
                    homeRadiusMeters = homeRadiusSetting,
                    wasAlreadyAtHome = wasMemberAtHome,
                    incomingStatusText = effStatus,
                    speedMph = effSpeed
                )

                val prevOrCurrentStatus = if (matchingLocal?.statusText?.startsWith("At ", ignoreCase = true) == true) {
                    matchingLocal.statusText
                } else {
                    cloudM.statusText
                }
                val matchedSafeZone = GeoUtils.matchSafeZone(
                    lat = effY,
                    lng = effX,
                    customZones = syncedCustomSafeZones,
                    currentStatusText = prevOrCurrentStatus,
                    speedMph = cloudM.speedMph
                )
                val isMemberAtSafeZone = matchedSafeZone != null && !isMemberAtHome
                val isStationaryInBarrier = (isMemberAtHome || isMemberAtSafeZone) && cloudM.speedMph < 3.5

                val finalStatus = when {
                    isMemberLocPaused -> "⏸️ Paused (Home Sleep)"
                    isMemberAtHome && isStationaryInBarrier -> "At Home (Live GPS)"
                    isMemberAtSafeZone && isStationaryInBarrier -> "At ${matchedSafeZone!!.name}"
                    else -> cloudM.statusText
                }
                val finalX = when {
                    isMemberAtHome && isStationaryInBarrier -> prefsHomeLng
                    isMemberAtSafeZone && isStationaryInBarrier -> matchedSafeZone!!.longitude
                    else -> effX
                }
                val finalY = when {
                    isMemberAtHome && isStationaryInBarrier -> prefsHomeLat
                    isMemberAtSafeZone && isStationaryInBarrier -> matchedSafeZone!!.latitude
                    else -> effY
                }
                val finalSpeedMph = if (isMemberLocPaused || isStationaryInBarrier) 0.0 else cloudM.speedMph
                val isMoving = if (isStationaryInBarrier) false else (finalSpeedMph >= 1.2)

                val physicalDistKm = if (matchingLocal != null && matchingLocal.x != 0.0 && matchingLocal.y != 0.0 && effX != 0.0 && effY != 0.0) {
                    Math.hypot((matchingLocal.x - effX) * 111.0 * Math.cos(Math.toRadians(effY)), (matchingLocal.y - effY) * 111.0)
                } else 0.0

                val wasMemberAtThisSafeZone = matchedSafeZone != null && matchingLocal?.statusText?.contains("At ${matchedSafeZone.name}", ignoreCase = true) == true
                val isSamePlace = (isMemberAtHome && wasMemberAtHome) || (isMemberAtSafeZone && wasMemberAtThisSafeZone)
                val hasRelocated = !isSamePlace || physicalDistKm > 0.15

                val now = System.currentTimeMillis()
                val finalLocationSince = if (isMoving) {
                    0L
                } else if (hasRelocated) {
                    if (cloudM.locationSince > 0L && cloudM.locationSince <= now + 60_000L &&
                        (matchingLocal == null || matchingLocal.lastActive <= 0L || cloudM.locationSince >= matchingLocal.lastActive - 120_000L)) {
                        minOf(cloudM.locationSince, now)
                    } else {
                        now
                    }
                } else {
                    when {
                        matchingLocal != null && matchingLocal.locationSince > 0L -> matchingLocal.locationSince
                        cloudM.locationSince > 0L && cloudM.locationSince <= now + 60_000L -> minOf(cloudM.locationSince, now)
                        else -> now
                    }
                }

                val locallyEditedColor = contactsPrefs.getString("color_${cloudM.id}", "")?.takeIf { it.isNotBlank() }
                    ?: contactsPrefs.getString("color_$cleanKey", "")?.takeIf { it.isNotBlank() }
                val resolvedColor = locallyEditedColor ?: cloudM.avatarColorHex.takeIf { it.isNotBlank() } ?: matchingLocal?.avatarColorHex ?: "#0061A4"

                val locallyEditedEmoji = contactsPrefs.getString("emoji_${cloudM.id}", "")?.takeIf { it.isNotBlank() }
                    ?: contactsPrefs.getString("emoji_$cleanKey", "")?.takeIf { it.isNotBlank() }
                val resolvedEmoji = locallyEditedEmoji ?: com.example.data.IdentityUtils.sanitizeAvatarEmoji(cloudM.avatarEmoji, resolvedName, cloudM.id)

                val locallyEditedPhoto = contactsPrefs.getString("photo_${cloudM.id}", "")?.takeIf { it.isNotBlank() }
                    ?: contactsPrefs.getString("photo_$cleanKey", "")?.takeIf { it.isNotBlank() }
                val resolvedPhoto = matchingLocal?.photoPath?.takeIf { it.isNotBlank() } ?: locallyEditedPhoto ?: ""

                val mappedMember = FamilyMember(
                    id = cloudM.id,
                    name = resolvedName,
                    avatarColorHex = resolvedColor,
                    x = finalX,
                    y = finalY,
                    batteryPercentage = cloudM.batteryPercentage,
                    isCharging = cloudM.isCharging,
                    speedMph = finalSpeedMph,
                    statusText = finalStatus,
                    isComingHome = if (isMemberLocPaused || isStationaryInBarrier) false else cloudM.isComingHome,
                    etaMinutes = if (isMemberLocPaused || isStationaryInBarrier) 0 else cloudM.etaMinutes,
                    avatarEmoji = resolvedEmoji,
                    phoneNumber = matchingLocal?.phoneNumber ?: "",
                    photoPath = resolvedPhoto,
                    lastActive = effLastActive,
                    locationSince = finalLocationSince,
                    isLocationPaused = isMemberLocPaused
                )

                if (matchingLocal != null) {
                    repository.updateMember(mappedMember)
                } else {
                    repository.insertFamilyMembers(listOf(mappedMember))
                }
                if (mappedMember.x != 0.0 && mappedMember.y != 0.0) {
                    TransitClassifier.observeSample(
                        memberId = mappedMember.id,
                        lat = mappedMember.y,
                        lng = mappedMember.x,
                        reportedSpeedMph = mappedMember.speedMph,
                        statusText = mappedMember.statusText,
                        isRailway = RailwayTransitDetector.isMemberOnRailway(mappedMember.id),
                        cloudTransitMode = cloudM.transitMode
                    )
                    RailwayTransitDetector.checkRailwayCorridorAsync(
                        memberId = mappedMember.id,
                        lat = mappedMember.y,
                        lng = mappedMember.x,
                        speedMph = mappedMember.speedMph
                    )
                }
                evaluatedMembers.add(mappedMember)
            }

            // Process any server-computed alerts and completed trips
            payload?.let { p ->
                if (p.completedTrips.isNotEmpty()) {
                    TelemetryWebSocketManager.updateCompletedTrips(p.completedTrips)
                }
                if (p.recentAlerts.isNotEmpty()) {
                    TelemetryWebSocketManager.processServerAlerts(
                        context = context,
                        repository = repository,
                        alerts = p.recentAlerts,
                        myDeviceName = myName,
                        myDeviceUUID = dUuid
                    )
                }
            }

            // Run 24/7 background geofence presence detection and alert notifications
            GeofenceMonitor.evaluateGeofences(context, repository, prefs, evaluatedMembers, location)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
