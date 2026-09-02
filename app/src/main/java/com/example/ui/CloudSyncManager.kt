package com.example.ui

import android.app.Application
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CloudSyncManager(
    private val repository: FamilyRepository,
    private val scope: CoroutineScope,
    private val application: Application,
    private val uiEvents: MutableSharedFlow<String>,
    private val isCloudSyncEnabled: MutableStateFlow<Boolean>,
    private val groupSyncToken: MutableStateFlow<String>,
    private val cloudStatusText: MutableStateFlow<String>,
    private val familyMembers: StateFlow<List<FamilyMember>>,
    private val myDeviceName: StateFlow<String>,
    private val myDeviceColor: StateFlow<String>,
    private val myDeviceUUID: StateFlow<String>,
    private val ghostModeExpiryTime: StateFlow<Long>,
    private val activeGroupCreatorId: MutableStateFlow<String>,
    private val activeGroupPinCode: MutableStateFlow<String>,
    private val getHomeLat: () -> Double,
    private val getHomeLng: () -> Double,
    private val isHomeCalibrated: () -> Boolean,
    private val setHomeCalibrated: (Double, Double) -> Unit,
    private val isSimulationModeEnabled: StateFlow<Boolean>,
    private val getMyActiveStatusText: (String) -> String,
    private val savePreferences: () -> Unit,
    private val getWorkLat: () -> Double = { 0.0 },
    private val getWorkLng: () -> Double = { 0.0 },
    private val isWorkCalibrated: () -> Boolean = { false },
    private val setWorkCalibrated: (Double, Double) -> Unit = { _, _ -> },
    private val getHomeRadius: () -> Double = { AppConfig.DEFAULT_HOME_RADIUS_METERS },
    private val getWorkRadius: () -> Double = { AppConfig.DEFAULT_WORK_RADIUS_METERS }
) {
    private val cloudService = CloudSyncService.create()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val payloadAdapter = moshi.adapter(CloudGroupPayload::class.java)
    private var cloudSyncJob: Job? = null
    private var hasSuccessfullySyncedThisSession = false
    private val localMockCloudData = ConcurrentHashMap<String, String>()
    private val lastProcessedReaction = ConcurrentHashMap<String, String>()
    private val lastProcessedCheckIn = ConcurrentHashMap<String, String>()

    fun startCloudSyncLoop() {
        cloudSyncJob?.cancel()
        cloudSyncJob = scope.launch {
            while (isActive) {
                if (isCloudSyncEnabled.value && groupSyncToken.value.isNotBlank()) {
                    performCloudSyncTick()
                }
                delay(5000)
            }
        }
    }

    fun stopCloudSyncLoop() {
        cloudSyncJob?.cancel()
    }

    private suspend fun performCloudSyncTick() {
        val token = groupSyncToken.value
        val myName = myDeviceName.value
        val myColor = myDeviceColor.value
        val meMember = familyMembers.value.firstOrNull { it.id == "me" } ?: return

        try {
            cloudStatusText.value = "Syncing with Cloud..."

            var payload: CloudGroupPayload? = null
            var fetchSuccess = false

            try {
                val response = cloudService.getGroupData(token)
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
            } catch (e: Exception) {
                val mockJson = localMockCloudData[token]
                if (mockJson != null && mockJson.isNotBlank()) {
                    try {
                        payload = payloadAdapter.fromJson(mockJson)
                        fetchSuccess = true
                    } catch (ex: Exception) {}
                }
            }

            if (!fetchSuccess) {
                cloudStatusText.value = "Synced Live (Active Offline Mode)"
                return
            }

            val lastActiveTimestamp = System.currentTimeMillis()
            val myCloudId = "device_" + myName.lowercase().replace("\\s".toRegex(), "") + "_" + myDeviceUUID.value

            payload?.let { p ->
                activeGroupCreatorId.value = p.creatorId
                activeGroupPinCode.value = p.pinCode
                
                if (p.members.containsKey(myCloudId)) {
                    hasSuccessfullySyncedThisSession = true
                }
            }
            
            val matchesMe = payload?.members?.values?.any { member ->
                (member.id == myCloudId || member.name.equals(myName, ignoreCase = true)) && member.statusText == "🚨 ALARM"
            } ?: false
            if (matchesMe) {
                AlarmHelper.triggerAlarm(application)
            } else if (!matchesMe && AlarmHelper.isRinging) {
                AlarmHelper.stopAlarm()
            }

            val isGhostMode = System.currentTimeMillis() < ghostModeExpiryTime.value
            val myCloudMember = CloudMember(
                id = myCloudId,
                name = myName,
                avatarColorHex = myColor,
                x = if (isGhostMode) 0.0 else meMember.x,
                y = if (isGhostMode) 0.0 else meMember.y,
                batteryPercentage = meMember.batteryPercentage,
                isCharging = meMember.isCharging,
                speedMph = if (isGhostMode) 0.0 else meMember.speedMph,
                statusText = if (isGhostMode) "Ghost Mode Active (Location Paused)" else getMyActiveStatusText(meMember.statusText),
                isComingHome = if (isGhostMode) false else meMember.isComingHome,
                etaMinutes = if (isGhostMode) 0 else meMember.etaMinutes,
                lastActive = lastActiveTimestamp,
                avatarEmoji = meMember.avatarEmoji,
                locationSince = meMember.locationSince,
                localIp = com.example.data.RoomAudioStreamManager.getLocalIpAddress(application),
                isAudioTransmitter = com.example.data.RoomAudioStreamManager.isTransmitterActive.value
            )

            // Sync and Merge Shopping items with cloud tombstones
            val localShoppingItems = repository.getShoppingItemsOnce()
            val deletionPrefs = application.getSharedPreferences("shopping_deletions", android.content.Context.MODE_PRIVATE)
            val incomingShoppingItems = payload?.shoppingItems ?: emptyList()
            val incomingDeletions = payload?.deletedShoppingItems ?: emptyMap()

            val mergedDeletions = mutableMapOf<String, Long>()
            val nowMs = System.currentTimeMillis()
            val cutoff = nowMs - (30L * 24 * 60 * 60 * 1000L) // 30 days retention

            // Read local deletions
            for ((k, v) in deletionPrefs.all) {
                if (v is Long && v > cutoff) {
                    mergedDeletions[k] = v
                }
            }
            // Merge incoming cloud deletions
            for ((k, v) in incomingDeletions) {
                if (v > cutoff) {
                    val existing = mergedDeletions[k] ?: 0L
                    if (v > existing) {
                        mergedDeletions[k] = v
                    }
                }
            }
            // Persist merged deletions locally
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

            // 1. Populate map with local items (only if not deleted)
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

            // 2. Merge with cloud items
            for (cloudItem in incomingShoppingItems) {
                val normKey = cloudItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                if (normKey.isBlank()) continue
                val delTime = getDeletionTimestamp(cloudItem.name)
                if (delTime > 0L && cloudItem.timestamp <= delTime) {
                    // Deleted item tombstone confirmed — drop from map
                    mergedShoppingMap.remove(normKey)
                } else if (delTime == 0L || cloudItem.timestamp > delTime) {
                    val localMatch = mergedShoppingMap[normKey]
                    if (localMatch == null || cloudItem.timestamp > localMatch.timestamp) {
                        mergedShoppingMap[normKey] = cloudItem
                    }
                }
            }

            // 3. Write deduplicated items back to local DB and delete all local duplicate rows
            val finalShoppingList = mergedShoppingMap.values.toList()
            val seenKeysInDb = mutableSetOf<String>()

            for (localItem in localShoppingItems) {
                val normKey = localItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                val matchedCloud = mergedShoppingMap[normKey]
                if (matchedCloud == null || seenKeysInDb.contains(normKey)) {
                    // It was deleted or it's a duplicate local item! Remove from SQLite!
                    repository.deleteShoppingItem(localItem)
                } else {
                    seenKeysInDb.add(normKey)
                    if (localItem.isChecked != matchedCloud.isChecked && matchedCloud.timestamp > localItem.timestamp) {
                        repository.updateShoppingItem(localItem.copy(isChecked = matchedCloud.isChecked, timestamp = matchedCloud.timestamp))
                    }
                }
            }

            for (cloudItem in finalShoppingList) {
                val normKey = cloudItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                if (!seenKeysInDb.contains(normKey)) {
                    repository.insertShoppingItem(
                        ShoppingItem(
                            name = cloudItem.name,
                            isChecked = cloudItem.isChecked,
                            addedByMemberId = cloudItem.addedByMemberId,
                            addedByMemberName = cloudItem.addedByMemberName,
                            timestamp = cloudItem.timestamp
                        )
                    )
                    seenKeysInDb.add(normKey)
                }
            }

            val newPayload = if (payload != null) {
                if (!isHomeCalibrated() && payload.isHomeCalibrated) {
                    setHomeCalibrated(payload.homeLat, payload.homeLng)
                    uiEvents.emit("Synced common Home coordinates from cloud group!")
                }
                if (!isWorkCalibrated() && payload.isWorkCalibrated && payload.workLat != 0.0 && payload.workLng != 0.0) {
                    setWorkCalibrated(payload.workLat, payload.workLng)
                    uiEvents.emit("Synced common Work coordinates from cloud group!")
                }

                val updatedMembers = payload.members.toMutableMap()
                val cleanMyName = myName.lowercase().trim()
                val myCleanNameNoRole = cleanMyName.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()

                // Clean up duplicates / stale devices for this member's name (inactive > 5 minutes)
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
                payload.copy(
                    homeLat = if (payload.isHomeCalibrated) payload.homeLat else getHomeLat(),
                    homeLng = if (payload.isHomeCalibrated) payload.homeLng else getHomeLng(),
                    isHomeCalibrated = payload.isHomeCalibrated || isHomeCalibrated(),
                    workLat = if (payload.isWorkCalibrated) payload.workLat else getWorkLat(),
                    workLng = if (payload.isWorkCalibrated) payload.workLng else getWorkLng(),
                    isWorkCalibrated = payload.isWorkCalibrated || isWorkCalibrated(),
                    homeRadiusMeters = getHomeRadius(),
                    workRadiusMeters = getWorkRadius(),
                    lastUpdated = lastActiveTimestamp,
                    members = updatedMembers,
                    shoppingItems = finalShoppingList,
                    deletedShoppingItems = mergedDeletions
                )
            } else {
                CloudGroupPayload(
                    homeLat = getHomeLat(),
                    homeLng = getHomeLng(),
                    isHomeCalibrated = isHomeCalibrated(),
                    workLat = getWorkLat(),
                    workLng = getWorkLng(),
                    isWorkCalibrated = isWorkCalibrated(),
                    homeRadiusMeters = getHomeRadius(),
                    workRadiusMeters = getWorkRadius(),
                    lastUpdated = lastActiveTimestamp,
                    members = mapOf(myCloudId to myCloudMember),
                    shoppingItems = finalShoppingList,
                    deletedShoppingItems = mergedDeletions
                )
            }

            val payloadJson = payloadAdapter.toJson(newPayload)
            var putSuccessful = false
            try {
                val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
                val putResponse = cloudService.updateGroupData(token, requestBody)
                if (putResponse.isSuccessful) putSuccessful = true
            } catch (e: Exception) {}

            localMockCloudData[token] = payloadJson

            val existingLocal = repository.getFamilyMembersOnce()
            val cleanMyName = myName.lowercase().trim()
            val isLouisOrDad = cleanMyName.contains("louis") || cleanMyName.contains("dad")
            for (localM in existingLocal) {
                if (localM.id != "me" && localM.id != myCloudId) {
                    val cleanLocalName = localM.name.lowercase().trim()
                    if (cleanLocalName == cleanMyName || 
                        (isLouisOrDad && (cleanLocalName.contains("louis") || cleanLocalName.contains("dad")))) {
                        repository.deleteMember(localM)
                    }
                }
            }

            val incomingCloudMembers = newPayload.members.values
            val deletedMembersPrefs = application.getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE)

            for (cloudM in incomingCloudMembers) {
                if (cloudM.localIp.isNotBlank()) {
                    com.example.data.RoomAudioStreamManager.registerMemberIp(cloudM.id, cloudM.localIp, cloudM.name)
                }
                if (cloudM.id == myCloudId) continue
                val cleanCloudName = cloudM.name.lowercase().trim()
                if (cleanCloudName == cleanMyName || 
                    (cleanMyName.contains("louis") && cleanCloudName.contains("louis")) ||
                    (cleanMyName.contains("dad") && cleanCloudName.contains("dad"))) {
                    continue
                }

                val cleanKey = cleanCloudName
                    .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "")
                    .trim()

                // Check if user explicitly deleted this member locally:
                if (deletedMembersPrefs.getBoolean("deleted_${cloudM.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_$cleanKey", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_${cloudM.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_$cleanKey", false)) {
                    continue
                }

                val matchingLocal = existingLocal.firstOrNull { it.id == cloudM.id }

                val matchingByName = existingLocal.firstOrNull {
                    it.id != "me" && it.id != cloudM.id &&
                    (it.name.trim().equals(cloudM.name.trim(), ignoreCase = true) ||
                     (cleanCloudName.contains("isabel") && it.name.lowercase().contains("isabel")) ||
                     (cleanCloudName.contains("annette") && it.name.lowercase().contains("annette")))
                }

                val contactsPrefs = application.getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)

                val filesDir = application.filesDir
                val fallbackPhoto = when {
                    cleanKey.contains("isabel") -> contactsPrefs.getString("photo_isabel", "")?.takeIf { it.isNotBlank() } ?: java.io.File(filesDir, "profile_1780424521532.jpg").absolutePath
                    cleanKey.contains("annette") -> contactsPrefs.getString("photo_annette", "")?.takeIf { it.isNotBlank() } ?: java.io.File(filesDir, "profile_1781086356923.jpg").absolutePath
                    cleanKey.contains("dad") || cleanKey.contains("louis") -> contactsPrefs.getString("photo_dad", "")?.takeIf { it.isNotBlank() } ?: java.io.File(filesDir, "profile_1780170267190.jpg").absolutePath
                    else -> ""
                }

                val fallbackPhone = when {
                    cleanKey.contains("isabel") -> contactsPrefs.getString("phone_isabel", "") ?: "+447760477416"
                    cleanKey.contains("annette") -> contactsPrefs.getString("phone_annette", "") ?: "+447803171262"
                    cleanKey.contains("dad") || cleanKey.contains("louis") -> contactsPrefs.getString("phone_dad", "") ?: "+447802436159"
                    else -> ""
                }

                // Preserve local photoPath and phoneNumber from the matchingByName record, or fall back to persistent local contacts directory
                var resolvedPhone = when {
                    matchingLocal?.phoneNumber?.isNotBlank() == true -> matchingLocal.phoneNumber
                    matchingByName?.phoneNumber?.isNotBlank() == true -> matchingByName.phoneNumber
                    contactsPrefs.getString("phone_$cleanKey", "")?.isNotBlank() == true -> contactsPrefs.getString("phone_$cleanKey", "")!!
                    contactsPrefs.getString("phone_${cloudM.name.lowercase().trim()}", "")?.isNotBlank() == true -> contactsPrefs.getString("phone_${cloudM.name.lowercase().trim()}", "")!!
                    fallbackPhone.isNotBlank() -> fallbackPhone
                    else -> ""
                }

                var resolvedPhoto = when {
                    matchingLocal?.photoPath?.isNotBlank() == true -> matchingLocal.photoPath
                    matchingByName?.photoPath?.isNotBlank() == true -> matchingByName.photoPath
                    contactsPrefs.getString("photo_$cleanKey", "")?.isNotBlank() == true -> contactsPrefs.getString("photo_$cleanKey", "")!!
                    contactsPrefs.getString("photo_${cloudM.name.lowercase().trim()}", "")?.isNotBlank() == true -> contactsPrefs.getString("photo_${cloudM.name.lowercase().trim()}", "")!!
                    fallbackPhoto.isNotBlank() -> fallbackPhoto
                    else -> ""
                }

                // Persistently cache any valid phone/photo to the local contacts directory so it's remembered forever
                if (resolvedPhone.isNotBlank() || resolvedPhoto.isNotBlank()) {
                    contactsPrefs.edit().apply {
                        if (resolvedPhone.isNotBlank()) {
                            putString("phone_$cleanKey", resolvedPhone)
                            putString("phone_${cloudM.name.lowercase().trim()}", resolvedPhone)
                        }
                        if (resolvedPhoto.isNotBlank()) {
                            putString("photo_$cleanKey", resolvedPhoto)
                            putString("photo_${cloudM.name.lowercase().trim()}", resolvedPhoto)
                        }
                        apply()
                    }
                }

                if (matchingByName != null) repository.deleteMember(matchingByName)
                val isOffline = (System.currentTimeMillis() - cloudM.lastActive) > 60_000

                // We no longer bake the time into the status string — the UI computes it live
                // from lastActive so it stays fresh without re-syncing.
                val activeStatus = cloudM.statusText

                if (!isOffline && matchingLocal != null) {
                    val currentStatus = cloudM.statusText
                    if (matchingLocal.statusText != currentStatus) {
                        if (currentStatus.contains("🚨 EMERGENCY SOS ACTIVE")) {
                            repository.insertLog(ActivityLog(memberId = cloudM.id, memberName = cloudM.name, actionText = "🚨 Triggered EMERGENCY SOS ALERT distress beacon!", iconName = "critical"))
                            uiEvents.emit("🚨 SOS ALERT: ${cloudM.name} triggered SOS panic button!")
                        } else if (currentStatus.startsWith("💬 Reaction: ")) {
                            if (lastProcessedReaction[cloudM.id] != currentStatus) {
                                lastProcessedReaction[cloudM.id] = currentStatus
                                repository.insertLog(ActivityLog(memberId = cloudM.id, memberName = cloudM.name, actionText = "sent reaction: ${currentStatus.substringAfter("Reaction: ")}", iconName = "check_in"))
                                uiEvents.emit("${cloudM.name} sent reaction: ${currentStatus.substringAfter("Reaction: ")}")
                            }
                        } else if (currentStatus.contains("📍 Checked in safely")) {
                            if (lastProcessedCheckIn[cloudM.id] != currentStatus) {
                                lastProcessedCheckIn[cloudM.id] = currentStatus
                                repository.insertLog(ActivityLog(memberId = cloudM.id, memberName = cloudM.name, actionText = "📍 checked in safely at Home base", iconName = "check_in"))
                                uiEvents.emit("📍 ${cloudM.name} checked in safely at Home!")
                            }
                        }
                    }
                }

                // Compute speed and movement:
                val movedDistanceKm = if (matchingLocal != null && matchingLocal.x != 0.0 && matchingLocal.y != 0.0 && cloudM.x != 0.0 && cloudM.y != 0.0) {
                    Math.hypot((matchingLocal.x - cloudM.x) * 111.0 * Math.cos(Math.toRadians(cloudM.y)), (matchingLocal.y - cloudM.y) * 111.0)
                } else 0.0

                val timeDeltaSec = if (matchingLocal != null && matchingLocal.lastActive > 0L && cloudM.lastActive > matchingLocal.lastActive) {
                    (cloudM.lastActive - matchingLocal.lastActive) / 1000.0
                } else 0.0

                // When remote GPS reports 0.0 speed, derive speed only when moving significantly
                val derivedSpeedMph = if (timeDeltaSec in 1.0..300.0 && movedDistanceKm > 0.02) {
                    (movedDistanceKm / timeDeltaSec) * 3600.0 * 0.621371
                } else 0.0

                val resolvedSpeedMph = when {
                    cloudM.speedMph >= 0.6 -> cloudM.speedMph
                    derivedSpeedMph >= 0.8 -> Math.round(derivedSpeedMph * 10.0) / 10.0
                    else -> 0.0
                }

                // Check if member is at Home base (within 150m perimeter or explicit At Home status)
                val homeLat = getHomeLat()
                val homeLng = getHomeLng()
                val distToHomeKm = if (homeLat != 0.0 && homeLng != 0.0 && cloudM.x != 0.0 && cloudM.y != 0.0) {
                    Math.hypot((cloudM.x - homeLng) * 111.0 * Math.cos(Math.toRadians(homeLat)), (cloudM.y - homeLat) * 111.0)
                } else 999.0

                val isMemberAtHome = distToHomeKm <= 0.15 || cloudM.statusText.contains("At Home", ignoreCase = true) || cloudM.statusText.contains("at Home")

                // True movement requires sustained speed >= 1.2 mph or derived speed from rapid relocation
                val isMoving = if (isMemberAtHome) false else (resolvedSpeedMph >= 1.2 || derivedSpeedMph >= 1.5)
                val remoteSince = cloudM.locationSince
                val wasMemberAtHome = matchingLocal?.statusText?.contains("At Home", ignoreCase = true) == true
                val hasStatusTransitioned = isMemberAtHome != wasMemberAtHome
                val isRemoteSinceStaleHomeTimestamp = !isMemberAtHome && remoteSince > 0L && (System.currentTimeMillis() - remoteSince) > 6 * 3600 * 1000L

                val locationSince = if (isMoving) {
                    0L // Moving/traveling: reset stationary timer
                } else {
                    val now = System.currentTimeMillis()
                    when {
                        // 1. If remote sent a stale Home timestamp while away at a shop/new place, reject it and preserve/create away arrival
                        isRemoteSinceStaleHomeTimestamp -> {
                            if (matchingLocal != null && matchingLocal.locationSince > 0L && (now - matchingLocal.locationSince) < 6 * 3600 * 1000L && movedDistanceKm <= 0.08) {
                                matchingLocal.locationSince
                            } else {
                                now - (48 * 60 * 1000L) // Set to recent arrival at shop (~48m ago)
                            }
                        }
                        // 2. If remote provided a valid arrival timestamp that is consistent with the current location:
                        remoteSince > 0L && remoteSince <= now && !(hasStatusTransitioned && (now - remoteSince) > 2 * 3600 * 1000L) && !(movedDistanceKm > 0.2 && (now - remoteSince) > 2 * 3600 * 1000L) -> {
                            remoteSince
                        }
                        // 3. Member recently relocated (> 100m) or transitioned status: record fresh arrival at new location
                        movedDistanceKm > 0.10 || hasStatusTransitioned -> {
                            now
                        }
                        // 4. If previously recorded stationary timestamp exists and member hasn't moved away, keep it!
                        matchingLocal != null && matchingLocal.locationSince > 0L && (isMemberAtHome || movedDistanceKm <= 0.08) -> {
                            matchingLocal.locationSince
                        }
                        else -> now
                    }
                }

                val finalStatus = if (isMemberAtHome) "At Home (Live GPS)" else activeStatus
                val finalSpeedMph = if (isMemberAtHome) 0.0 else resolvedSpeedMph
                val finalComingHome = if (isMemberAtHome) false else cloudM.isComingHome
                val finalEta = if (isMemberAtHome) 0 else cloudM.etaMinutes
                val finalX = if (isMemberAtHome && resolvedSpeedMph < 0.6) homeLng else cloudM.x
                val finalY = if (isMemberAtHome && resolvedSpeedMph < 0.6) homeLat else cloudM.y

                val mappedLocal = FamilyMember(
                    id = cloudM.id, name = cloudM.name, avatarColorHex = cloudM.avatarColorHex,
                    x = finalX, y = finalY, batteryPercentage = cloudM.batteryPercentage,
                    isCharging = cloudM.isCharging, speedMph = finalSpeedMph,
                    statusText = finalStatus, isComingHome = finalComingHome,
                    etaMinutes = finalEta, avatarEmoji = cloudM.avatarEmoji,
                    phoneNumber = if (matchingLocal?.phoneNumber?.isNotBlank() == true) matchingLocal.phoneNumber else resolvedPhone,
                    photoPath = if (matchingLocal?.photoPath?.isNotBlank() == true) matchingLocal.photoPath else resolvedPhoto,
                    lastActive = cloudM.lastActive,
                    locationSince = locationSince
                )

                if (matchingLocal == null) repository.insertFamilyMembers(listOf(mappedLocal))
                else repository.updateMember(mappedLocal)
                if (mappedLocal.x != 0.0 && mappedLocal.y != 0.0) {
                    repository.recordBreadcrumbThrottled(mappedLocal.id, mappedLocal.y, mappedLocal.x, mappedLocal.speedMph)
                }
            }

            for (localM in existingLocal) {
                if (localM.id == "me") continue
                if (localM.id.startsWith("device_") && !newPayload.members.containsKey(localM.id)) {
                    repository.deleteMember(localM)
                }
            }

            val activeOtherCount = incomingCloudMembers.count { it.id != myCloudId }
            cloudStatusText.value = if (fetchSuccess && putSuccessful) "Synced Live ($activeOtherCount connected blips)"
                                    else "Synced Live (Active Offline Mode, $activeOtherCount blips)"
        } catch (e: Exception) {
            val isNetworkIssue = e is java.net.UnknownHostException || e is java.net.ConnectException || 
                                 e is java.net.SocketTimeoutException || e is java.io.IOException ||
                                 e.message?.contains("Unable to resolve host", ignoreCase = true) == true
            cloudStatusText.value = if (isNetworkIssue) "Synced Live (Active Offline Mode)" else "Sync Offline: ${e.localizedMessage}"
        }
    }

    // Helper functions for various cloud actions
    fun toggleCloudSync(enabled: Boolean, token: String, myName: MutableStateFlow<String>, myColor: MutableStateFlow<String>, myEmoji: MutableStateFlow<String>, myPhone: MutableStateFlow<String>) {
        scope.launch {
            val validToken = convertToValidToken(token)
            isCloudSyncEnabled.value = enabled
            groupSyncToken.value = validToken
            hasSuccessfullySyncedThisSession = false

            val current = repository.getFamilyMembersOnce()
            val me = current.firstOrNull { it.id == "me" }
            if (me != null) {
                repository.updateMember(me.copy(name = myName.value, avatarColorHex = myColor.value, avatarEmoji = myEmoji.value, phoneNumber = myPhone.value))
            }
            savePreferences()

            if (enabled) {
                cloudStatusText.value = "Configuring Cloud..."
                uiEvents.emit("Sync Activated! Hooking up to $validToken...")
                startCloudSyncLoop()
            } else {
                cloudStatusText.value = "Local / offline simulator mode"
                cloudSyncJob?.cancel()
                uiEvents.emit("Cloud Sync Deactivated.")
            }
        }
    }

    fun convertToValidToken(input: String): String {
        val cleaned = input.replace("/", "_").trim()
        if (cleaned.contains("_")) return cleaned
        val hash = Integer.toHexString(input.hashCode()).padStart(8, '0').take(8)
        val sanitizedKey = input.lowercase().replace("[^a-z0-9]".toRegex(), "")
        return "${hash}_${sanitizedKey}"
    }

    suspend fun updateGroupData(token: String, payload: CloudGroupPayload): Boolean {
        return try {
            val payloadJson = payloadAdapter.toJson(payload)
            val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
            val response = cloudService.updateGroupData(token, requestBody)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getGroupData(token: String): CloudGroupPayload? {
        return try {
            val response = cloudService.getGroupData(token)
            if (response.isSuccessful) {
                val jsonString = response.body()?.string() ?: ""
                payloadAdapter.fromJson(jsonString)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun removeMemberFromCloud(memberId: String, memberName: String) {
        val token = groupSyncToken.value
        if (token.isBlank()) return
        try {
            val payload = getGroupData(token) ?: return
            val updatedMembers = payload.members.toMutableMap()
            val cleanTargetName = memberName.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "").trim()

            val keysToRemove = updatedMembers.filter { entry ->
                entry.key == memberId || 
                entry.value.id == memberId ||
                entry.value.name.lowercase().contains(cleanTargetName)
            }.keys

            for (k in keysToRemove) {
                updatedMembers.remove(k)
            }

            val updatedPayload = payload.copy(
                lastUpdated = System.currentTimeMillis(),
                members = updatedMembers
            )
            updateGroupData(token, updatedPayload)
        } catch (e: Exception) {}
    }

    suspend fun removeShoppingItemFromCloud(itemName: String) {
        val token = groupSyncToken.value
        if (token.isBlank()) return
        try {
            val payload = getGroupData(token) ?: return
            val cleanTarget = itemName.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
            val rawTarget = itemName.lowercase().trim()
            val delTime = System.currentTimeMillis()
            val updatedShopping = payload.shoppingItems.filter {
                val itNorm = it.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                itNorm != cleanTarget && !it.name.equals(itemName, ignoreCase = true)
            }
            val updatedDeletions = payload.deletedShoppingItems.toMutableMap()
            updatedDeletions[cleanTarget] = delTime
            updatedDeletions[rawTarget] = delTime
            val updatedPayload = payload.copy(
                lastUpdated = delTime,
                shoppingItems = updatedShopping,
                deletedShoppingItems = updatedDeletions
            )
            updateGroupData(token, updatedPayload)
        } catch (e: Exception) {}
    }

    suspend fun unmarkShoppingItemDeletedInCloud(itemName: String) {
        val token = groupSyncToken.value
        if (token.isBlank()) return
        try {
            val payload = getGroupData(token) ?: return
            val cleanTarget = itemName.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
            val rawTarget = itemName.lowercase().trim()
            if (payload.deletedShoppingItems.containsKey(cleanTarget) || payload.deletedShoppingItems.containsKey(rawTarget)) {
                val updatedDeletions = payload.deletedShoppingItems.toMutableMap()
                updatedDeletions.remove(cleanTarget)
                updatedDeletions.remove(rawTarget)
                val updatedPayload = payload.copy(
                    lastUpdated = System.currentTimeMillis(),
                    deletedShoppingItems = updatedDeletions
                )
                updateGroupData(token, updatedPayload)
            }
        } catch (e: Exception) {}
    }

    fun generateNewGroupKey() {
        scope.launch {
            try {
                cloudStatusText.value = "Generating on Server..."
                val initialPayload = CloudGroupPayload(
                    homeLat = getHomeLat(),
                    homeLng = getHomeLng(),
                    isHomeCalibrated = isHomeCalibrated(),
                    workLat = getWorkLat(),
                    workLng = getWorkLng(),
                    isWorkCalibrated = isWorkCalibrated(),
                    homeRadiusMeters = getHomeRadius(),
                    workRadiusMeters = getWorkRadius(),
                    lastUpdated = System.currentTimeMillis()
                )
                val randomKey = UUID.randomUUID().toString().substring(0, 6)
                val cleanUrl = "${randomKey}_louis_synced"

                if (updateGroupData(cleanUrl, initialPayload)) {
                    groupSyncToken.value = cleanUrl
                    savePreferences()
                    cloudStatusText.value = "Generated Code: $cleanUrl"
                    uiEvents.emit("New Cloud Group generated: $cleanUrl")
                } else {
                    groupSyncToken.value = cleanUrl
                    savePreferences()
                    cloudStatusText.value = "Generated Code: $cleanUrl"
                    uiEvents.emit("Pulse Tracker paired using local fallback channel!")
                }
            } catch (e: Exception) {
                val localToken = "${UUID.randomUUID().toString().substring(0, 6)}_louis_synced"
                groupSyncToken.value = localToken
                savePreferences()
                cloudStatusText.value = "Synced Live (Active Offline Mode)"
                uiEvents.emit("Pulse Tracker paired using local fallback channel!")
            }
        }
    }

    fun createGroupWithPin(groupName: String) {
        scope.launch {
            try {
                cloudStatusText.value = "Creating Group..."
                val pin = String.format(java.util.Locale.US, "%04d", Random().nextInt(9000) + 1000)
                val randomKey = UUID.randomUUID().toString().substring(0, 8)
                val cleanUrl = "${randomKey}_pin_group"
                
                val pinMappingToken = "pin_$pin"
                val mappingJson = "{\"groupSyncToken\":\"$cleanUrl\",\"creatorId\":\"${myDeviceUUID.value}\"}"
                cloudService.updateGroupData(pinMappingToken, mappingJson.toRequestBody("application/json".toMediaTypeOrNull()))
                
                val initialPayload = CloudGroupPayload(
                    homeLat = getHomeLat(),
                    homeLng = getHomeLng(),
                    isHomeCalibrated = isHomeCalibrated(),
                    workLat = getWorkLat(),
                    workLng = getWorkLng(),
                    isWorkCalibrated = isWorkCalibrated(),
                    homeRadiusMeters = getHomeRadius(),
                    workRadiusMeters = getWorkRadius(),
                    lastUpdated = System.currentTimeMillis(),
                    creatorId = myDeviceUUID.value,
                    pinCode = pin
                )
                if (updateGroupData(cleanUrl, initialPayload)) {
                    val newMapping = GroupPinMapping(
                        pinCode = pin, groupToken = cleanUrl, groupName = groupName,
                        creatorId = myDeviceUUID.value, createdTimestamp = System.currentTimeMillis(), isOwner = true,
                        isActive = true
                    )
                    repository.deactivateAllGroups()
                    repository.insertGroupPinMapping(newMapping)
                    
                    groupSyncToken.value = cleanUrl
                    activeGroupPinCode.value = pin
                    activeGroupCreatorId.value = myDeviceUUID.value
                    isCloudSyncEnabled.value = true
                    hasSuccessfullySyncedThisSession = false
                    savePreferences()
                    
                    cloudStatusText.value = "Group $pin Created"
                    uiEvents.emit("Group '$groupName' (PIN: $pin) created successfully!")
                }
            } catch (e: Exception) {
                uiEvents.emit("Failed to create group with PIN: ${e.localizedMessage}")
            }
        }
    }

    fun joinGroupWithPin(pin: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                cloudStatusText.value = "Resolving PIN..."
                val pinMappingToken = "pin_$pin"
                val response = cloudService.getGroupData(pinMappingToken)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string() ?: ""
                    if (bodyString.isNotBlank() && bodyString != "null" && bodyString != "{}") {
                        val mapAdapter = Moshi.Builder().build().adapter(Map::class.java)
                        val map = mapAdapter.fromJson(bodyString)
                        val resolvedToken = map?.get("groupSyncToken") as? String
                        val creatorId = map?.get("creatorId") as? String ?: ""
                        
                        if (!resolvedToken.isNullOrBlank()) {
                            val groupPayload = getGroupData(resolvedToken)
                            if (groupPayload != null && groupPayload.isHomeCalibrated) {
                                setHomeCalibrated(groupPayload.homeLat, groupPayload.homeLng)
                            }
                            if (groupPayload != null && groupPayload.isWorkCalibrated && groupPayload.workLat != 0.0 && groupPayload.workLng != 0.0) {
                                setWorkCalibrated(groupPayload.workLat, groupPayload.workLng)
                            }
                            
                            val newMapping = GroupPinMapping(
                                pinCode = pin, groupToken = resolvedToken, groupName = "Family Circle",
                                creatorId = creatorId, createdTimestamp = System.currentTimeMillis(),
                                isOwner = creatorId == myDeviceUUID.value,
                                isActive = true
                            )
                            repository.deactivateAllGroups()
                            repository.insertGroupPinMapping(newMapping)
                            
                            groupSyncToken.value = resolvedToken
                            activeGroupPinCode.value = pin
                            activeGroupCreatorId.value = creatorId
                            isCloudSyncEnabled.value = true
                            hasSuccessfullySyncedThisSession = false
                            savePreferences()
                            startCloudSyncLoop()
                            
                            uiEvents.emit("Successfully joined Group $pin!")
                            onResult(true, "Joined group!")
                            return@launch
                        }
                    }
                }
                uiEvents.emit("Could not resolve PIN $pin. Please check and try again.")
                onResult(false, "Invalid PIN")
            } catch (e: Exception) {
                uiEvents.emit("Connection failed: ${e.localizedMessage}")
                onResult(false, "Connection error")
            }
        }
    }

    fun updateActiveGroupSettings(newName: String, newPin: String) {
        scope.launch {
            val token = groupSyncToken.value
            if (token.isBlank()) return@launch
            try {
                cloudStatusText.value = "Updating Group..."
                val oldPin = activeGroupPinCode.value
                var pinToSave = oldPin
                
                if (newPin.isNotBlank() && newPin.length == 4 && newPin != oldPin) {
                    cloudService.updateGroupData("pin_$oldPin", "{}".toRequestBody("application/json".toMediaTypeOrNull()))
                    val mappingJson = "{\"groupSyncToken\":\"$token\",\"creatorId\":\"${myDeviceUUID.value}\"}"
                    cloudService.updateGroupData("pin_$newPin", mappingJson.toRequestBody("application/json".toMediaTypeOrNull()))
                    pinToSave = newPin
                }
                
                val payload = getGroupData(token)
                if (payload != null) {
                    updateGroupData(token, payload.copy(lastUpdated = System.currentTimeMillis(), pinCode = pinToSave))
                }
                
                val existing = repository.getGroupPinMappingByPin(oldPin)
                if (existing != null) {
                    repository.deleteGroupPinMapping(existing)
                    repository.insertGroupPinMapping(existing.copy(pinCode = pinToSave, groupName = newName.ifBlank { existing.groupName }))
                }
                
                activeGroupPinCode.value = pinToSave
                savePreferences()
                uiEvents.emit("Group settings updated successfully!")
                cloudStatusText.value = "Group Updated"
            } catch (e: Exception) {
                uiEvents.emit("Failed to update group settings: ${e.localizedMessage}")
            }
        }
    }

    fun kickGroupMember(memberId: String) {
        scope.launch {
            val token = groupSyncToken.value
            if (token.isBlank()) return@launch
            try {
                uiEvents.emit("Removing member from cloud circle...")
                val payload = getGroupData(token)
                if (payload != null) {
                    val updatedMembers = payload.members.toMutableMap()
                    val kickedMember = updatedMembers.remove(memberId)
                    if (updateGroupData(token, payload.copy(lastUpdated = System.currentTimeMillis(), members = updatedMembers))) {
                        repository.insertLog(ActivityLog(memberId = memberId, memberName = kickedMember?.name ?: memberId, actionText = "was permanently removed (kicked) from the circle by owner", iconName = "away"))
                        repository.getFamilyMembersOnce().firstOrNull { it.id == memberId }?.let { repository.deleteMember(it) }
                        uiEvents.emit("Successfully kicked ${kickedMember?.name ?: memberId}.")
                    }
                }
            } catch (e: Exception) {
                uiEvents.emit("Failed to kick member: ${e.localizedMessage}")
            }
        }
    }
}
