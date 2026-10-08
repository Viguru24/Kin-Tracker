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
    private val myDeviceName: MutableStateFlow<String>,
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
    private val apiService = KinTrackerApiService.create()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val payloadAdapter = moshi.adapter(CloudGroupPayload::class.java)
    private var cloudSyncJob: Job? = null
    private var hasSuccessfullySyncedThisSession = false
    private val localMockCloudData = ConcurrentHashMap<String, String>()
    private val lastProcessedReaction = ConcurrentHashMap<String, String>()
    private val lastProcessedCheckIn = ConcurrentHashMap<String, String>()

    init {
        com.example.data.RoomAudioStreamManager.onTransmitterToggled = {
            triggerSyncNow()
        }
    }

    fun triggerSyncNow() {
        scope.launch {
            if (isCloudSyncEnabled.value && groupSyncToken.value.isNotBlank()) {
                performCloudSyncTick()
            }
        }
    }

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

        // Synchronize RoomAudioStreamManager with active circle token and device name
        com.example.data.RoomAudioStreamManager.setCircleContext(
            circleId = token,
            deviceName = myName,
            deviceId = meMember.id
        )

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
            
            val myNameClean = myName.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father|Other Device)\\)", RegexOption.IGNORE_CASE), "").trim()
            val matchesMe = payload?.members?.values?.any { member ->
                val memberNameClean = member.name.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father|Other Device)\\)", RegexOption.IGNORE_CASE), "").trim()
                val isSamePerson = member.id == myCloudId ||
                    (myDeviceUUID.value.length >= 4 && member.id.endsWith("_" + myDeviceUUID.value)) ||
                    member.id.contains(myDeviceUUID.value) ||
                    member.name.trim().equals(myName.trim(), ignoreCase = true) ||
                    (myNameClean.isNotBlank() && memberNameClean.isNotBlank() && (myNameClean == memberNameClean || myNameClean.contains(memberNameClean) || memberNameClean.contains(myNameClean)))
                
                isSamePerson && member.statusText == "🚨 ALARM"
            } ?: false
            if (matchesMe) {
                AlarmHelper.triggerAlarm(application)
            } else if (!matchesMe && AlarmHelper.isRinging) {
                AlarmHelper.stopAlarm()
            }

            val prefs = application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
            val myEntry = payload?.members?.values?.firstOrNull {
                it.id == myCloudId || (myDeviceUUID.value.length >= 4 && it.id.endsWith("_" + myDeviceUUID.value)) || it.name.trim().equals(myName.trim(), ignoreCase = true)
            }
            if (myEntry != null && myEntry.name.isNotBlank() && !myEntry.name.trim().equals(myName.trim(), ignoreCase = false)) {
                val newSyncedName = myEntry.name.trim()
                myDeviceName.value = newSyncedName
                prefs.edit().putString("myDeviceName", newSyncedName).apply()
                val meLocal = repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }
                if (meLocal != null) {
                    repository.updateMember(meLocal.copy(name = newSyncedName))
                }
                savePreferences()
                uiEvents.emit("📱 Device name updated to '$newSyncedName' by circle!")
            }
            val currentLocPaused = prefs.getBoolean("is_location_paused", false)
            if (myEntry != null && myEntry.isLocationPaused != currentLocPaused) {
                prefs.edit().putBoolean("is_location_paused", myEntry.isLocationPaused).apply()
                if (myEntry.isLocationPaused) {
                    com.example.data.BackgroundLocationService.stopService(application)
                    uiEvents.emit("⏸️ Location tracking paused remotely.")
                } else {
                    com.example.data.BackgroundLocationService.startService(application)
                    uiEvents.emit("🛰️ Location tracking resumed remotely.")
                }
            }

            val isLocPaused = prefs.getBoolean("is_location_paused", false)
            val isGhostMode = System.currentTimeMillis() < ghostModeExpiryTime.value
            val myCloudMember = CloudMember(
                id = myCloudId,
                name = myName,
                avatarColorHex = myColor,
                x = if (isGhostMode) 0.0 else meMember.x,
                y = if (isGhostMode) 0.0 else meMember.y,
                batteryPercentage = meMember.batteryPercentage,
                isCharging = meMember.isCharging,
                speedMph = if (isGhostMode || isLocPaused) 0.0 else meMember.speedMph,
                statusText = if (isLocPaused) "⏸️ Paused (Battery Saver)" else if (isGhostMode) "Ghost Mode Active (Location Paused)" else getMyActiveStatusText(meMember.statusText),
                isComingHome = if (isGhostMode || isLocPaused) false else meMember.isComingHome,
                etaMinutes = if (isGhostMode || isLocPaused) 0 else meMember.etaMinutes,
                lastActive = lastActiveTimestamp,
                avatarEmoji = meMember.avatarEmoji,
                locationSince = meMember.locationSince,
                localIp = com.example.data.RoomAudioStreamManager.getLocalIpAddress(application),
                isAudioTransmitter = com.example.data.RoomAudioStreamManager.isTransmitterActive.value,
                isLocationPaused = isLocPaused
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
                if (payload.isHomeCalibrated && payload.homeLat != 0.0 && payload.homeLng != 0.0) {
                    val currentHomeLat = getHomeLat()
                    val currentHomeLng = getHomeLng()
                    val distDiff = Math.hypot((payload.homeLng - currentHomeLng) * 111.0, (payload.homeLat - currentHomeLat) * 111.0)
                    if (!isHomeCalibrated() || distDiff > 0.03) { // >30m difference from group home
                        setHomeCalibrated(payload.homeLat, payload.homeLng)
                        uiEvents.emit("Synced common Home coordinates from cloud group!")
                    }
                }
                if (payload.isWorkCalibrated && payload.workLat != 0.0 && payload.workLng != 0.0) {
                    val currentWorkLat = getWorkLat()
                    val currentWorkLng = getWorkLng()
                    val distDiff = Math.hypot((payload.workLng - currentWorkLng) * 111.0, (payload.workLat - currentWorkLat) * 111.0)
                    if (!isWorkCalibrated() || distDiff > 0.03) {
                        setWorkCalibrated(payload.workLat, payload.workLng)
                        uiEvents.emit("Synced common Work coordinates from cloud group!")
                    }
                }

                val updatedMembers = payload.members.toMutableMap()
                val cleanMyName = myName.lowercase().trim()
                val myCleanNameNoRole = cleanMyName.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()
                val deletedMembersPrefs = application.getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE)

                // 1. DEDUPLICATE BY CANONICAL IDENTITY & HARDWARE UUID:
                val myCanonicalKey = com.example.data.IdentityUtils.getCanonicalPersonKey(myName, myDeviceUUID.value)
                val membersByCanonical = updatedMembers.values.groupBy { com.example.data.IdentityUtils.getCanonicalPersonKey(it.name, it.id) }

                for ((canonKey, list) in membersByCanonical) {
                    if (list.size > 1) {
                        val isMyIdentity = canonKey == myCanonicalKey
                        if (isMyIdentity) {
                            // My active phone takes absolute precedence for my identity; evict all other duplicates!
                            for (stale in list) {
                                if (stale.id != myCloudId) {
                                    updatedMembers.remove(stale.id)
                                }
                            }
                        } else {
                            val newest = list.maxWithOrNull(
                                compareBy<CloudMember> { it.id.startsWith("device_") }.thenBy { it.lastActive }
                            )
                            for (stale in list) {
                                if (stale.id != newest?.id) {
                                    updatedMembers.remove(stale.id)
                                }
                            }
                        }
                    }
                }

                // 3. REMOVE EXPLICITLY DELETED OR STALE MOCK/TEST ENTRIES:
                val knownFamilyKeywords = listOf("isabel", "eloise", "annette", "dad", "louis", "wife", "daughter", "mama", "mom", "mother", "tab", "tablet")
                val staleThresholdMs = 7 * 24 * 3600 * 1000L // 7 days before considering a device truly gone
                val keysToRemove = updatedMembers.filter { entry ->
                    val entryId = entry.key
                    val entryName = entry.value.name.lowercase().trim()
                    val isMyDevice = entryId == myCloudId || entryId.endsWith("_" + myDeviceUUID.value)
                    if (isMyDevice) return@filter false

                    val isExplicitlyDeleted = deletedMembersPrefs.getBoolean("deleted_$entryId", false) ||
                        deletedMembersPrefs.getBoolean("deleted_member_$entryId", false)
                    val isOldMock = entryId.contains("abc123") || entryId.startsWith("mock_")
                    val isKnownFamily = knownFamilyKeywords.any { entryName.contains(it) } || entryId.contains("isabel") || entryId.contains("eloise") || entryId.contains("tab")
                    val isStaleUnknown = !isKnownFamily && (System.currentTimeMillis() - entry.value.lastActive) > staleThresholdMs

                    isExplicitlyDeleted || isOldMock || isStaleUnknown
                }.keys
                // 4. PRESERVE NEWEST TIMESTAMPS FOR ALL OTHER MEMBERS:
                // Ensure we never downgrade other members' locations with stale local/cloud data
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
            // Clean up any legacy mock members, but never delete actual family devices!
            for (localM in existingLocal) {
                if (localM.id.startsWith("mock_")) {
                    repository.deleteMember(localM)
                }
            }

            val incomingCloudMembers = newPayload.members.values
            val deletedMembersPrefs = application.getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE)
            val cleanMyName = myName.lowercase().trim()
            val myCleanNameNoRole = cleanMyName.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()

            val myCanonicalKey = com.example.data.IdentityUtils.getCanonicalPersonKey(myName, myDeviceUUID.value)

            for (cloudM in incomingCloudMembers) {
                // Do not ingest self device (already tracked locally with GPS as "me")
                // AND REFUSE DUPLICATES: Never allow another duplicate of this phone to appear!
                val cloudCanonKey = com.example.data.IdentityUtils.getCanonicalPersonKey(cloudM.name, cloudM.id)
                val isSelfOrDuplicateOfMe = cloudM.id == myCloudId ||
                        (myDeviceUUID.value.length >= 4 && cloudM.id.endsWith("_" + myDeviceUUID.value)) ||
                        cloudCanonKey == myCanonicalKey

                if (isSelfOrDuplicateOfMe) {
                    val staleLocalDups = existingLocal.filter {
                        it.id != "me" && (it.id == cloudM.id || it.id.endsWith("_" + myDeviceUUID.value) || com.example.data.IdentityUtils.getCanonicalPersonKey(it.name, it.id) == myCanonicalKey)
                    }
                    staleLocalDups.forEach {
                        repository.deleteMember(it)
                        repository.clearBreadcrumbsForMember(it.id)
                    }
                    continue
                }

                // Register other family members' IP and transmitter state for the audio UI
                com.example.data.RoomAudioStreamManager.registerMemberAudioState(
                    memberId = cloudM.id,
                    ip = cloudM.localIp,
                    isTransmitting = cloudM.isAudioTransmitter,
                    memberName = cloudM.name
                )

                // Check if user explicitly deleted this member locally (never block THIS active device):
                val cleanKey = cloudM.name.lowercase()
                    .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father|Other Device)\\)", RegexOption.IGNORE_CASE), "")
                    .trim()
                val isCurrentDevice = cloudM.id == myCloudId || (myDeviceUUID.value.isNotBlank() && cloudM.id.endsWith("_" + myDeviceUUID.value))
                if (!isCurrentDevice) {
                    if (deletedMembersPrefs.getBoolean("deleted_${cloudM.id}", false) ||
                        deletedMembersPrefs.getBoolean("deleted_$cleanKey", false) ||
                        deletedMembersPrefs.getBoolean("deleted_member_${cloudM.id}", false) ||
                        deletedMembersPrefs.getBoolean("deleted_member_$cleanKey", false)) {
                        continue
                    }
                }

                val cloudDeviceUUID = if (cloudM.id.startsWith("device_") && cloudM.id.contains("_")) cloudM.id.substringAfterLast("_") else ""
                val matchingLocal = existingLocal.firstOrNull { it.id == cloudM.id }
                    ?: existingLocal.firstOrNull {
                        cloudDeviceUUID.length >= 4 && it.id.startsWith("device_") && it.id.endsWith("_$cloudDeviceUUID")
                    }

                val matchingByName = existingLocal.firstOrNull {
                    it.id != "me" && it.id != cloudM.id && com.example.data.IdentityUtils.getCanonicalPersonKey(it.name, it.id) == cloudCanonKey
                }

                val contactsPrefs = application.getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)

                val filesDir = application.filesDir
                // Fallback photos for all known family members — never auto-assign to unknown/new devices
                val isKnownFamilyMember = cleanKey.contains("isabel") || cleanKey.contains("annette") ||
                    cleanKey.contains("dad") || cleanKey.contains("louis") || cleanKey.contains("eloise")
                val fallbackPhoto = when {
                    cleanKey.contains("isabel") -> contactsPrefs.getString("photo_isabel", "")?.takeIf { it.isNotBlank() } ?: java.io.File(filesDir, "profile_1780424521532.jpg").absolutePath
                    cleanKey.contains("annette") -> contactsPrefs.getString("photo_annette", "")?.takeIf { it.isNotBlank() } ?: java.io.File(filesDir, "profile_1781086356923.jpg").absolutePath
                    cleanKey.contains("eloise") -> contactsPrefs.getString("photo_eloise", "")?.takeIf { it.isNotBlank() } ?: ""
                    cleanKey.contains("dad") || cleanKey.contains("louis") -> contactsPrefs.getString("photo_dad", "")?.takeIf { it.isNotBlank() } ?: java.io.File(filesDir, "profile_1780170267190.jpg").absolutePath
                    else -> "" // Unknown/new device — no fallback photo; user must set one manually
                }

                val fallbackPhone = when {
                    cleanKey.contains("isabel") -> contactsPrefs.getString("phone_isabel", "") ?: "+447760477416"
                    cleanKey.contains("annette") -> contactsPrefs.getString("phone_annette", "") ?: "+447803171262"
                    cleanKey.contains("eloise") -> contactsPrefs.getString("phone_eloise", "") ?: ""
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

                // Only apply fallback photo for known family members; new/unknown devices get no auto-photo
                var resolvedPhoto = when {
                    matchingLocal?.photoPath?.isNotBlank() == true -> matchingLocal.photoPath
                    matchingByName?.photoPath?.isNotBlank() == true -> matchingByName.photoPath
                    contactsPrefs.getString("photo_$cleanKey", "")?.isNotBlank() == true -> contactsPrefs.getString("photo_$cleanKey", "")!!
                    contactsPrefs.getString("photo_${cloudM.name.lowercase().trim()}", "")?.isNotBlank() == true -> contactsPrefs.getString("photo_${cloudM.name.lowercase().trim()}", "")!!
                    isKnownFamilyMember && fallbackPhoto.isNotBlank() -> fallbackPhoto
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

                // Prevent stale cloud snapshot from overwriting newer local coordinates
                val hasNewerLocalData = matchingLocal != null && matchingLocal.lastActive > cloudM.lastActive && matchingLocal.x != 0.0 && matchingLocal.y != 0.0
                val targetMemberX = if (hasNewerLocalData) matchingLocal!!.x else cloudM.x
                val targetMemberY = if (hasNewerLocalData) matchingLocal!!.y else cloudM.y
                val targetLastActive = if (hasNewerLocalData) matchingLocal!!.lastActive else cloudM.lastActive

                // Compute speed and movement:
                val movedDistanceKm = if (matchingLocal != null && matchingLocal.x != 0.0 && matchingLocal.y != 0.0 && targetMemberX != 0.0 && targetMemberY != 0.0) {
                    Math.hypot((matchingLocal.x - targetMemberX) * 111.0 * Math.cos(Math.toRadians(targetMemberY)), (matchingLocal.y - targetMemberY) * 111.0)
                } else 0.0

                val timeDeltaSec = if (matchingLocal != null && matchingLocal.lastActive > 0L && targetLastActive > matchingLocal.lastActive) {
                    (targetLastActive - matchingLocal.lastActive) / 1000.0
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
                val distToHomeKm = if (homeLat != 0.0 && homeLng != 0.0 && targetMemberX != 0.0 && targetMemberY != 0.0) {
                    Math.hypot((targetMemberX - homeLng) * 111.0 * Math.cos(Math.toRadians(homeLat)), (targetMemberY - homeLat) * 111.0)
                } else 999.0

                val isMemberAtHome = distToHomeKm <= 0.15 || cloudM.statusText.contains("At Home", ignoreCase = true) || cloudM.statusText.contains("at Home")

                // Check if member is inside a designated Safe Zone (e.g. Woodcote High School)
                val customSafeZones = try { repository.getAllSafeZonesOnce() } catch (_: Exception) { emptyList() }
                val matchedSafeZone = customSafeZones.firstOrNull { zone ->
                    targetMemberX != 0.0 && targetMemberY != 0.0 &&
                    com.example.data.GeoUtils.distanceMeters(targetMemberY, targetMemberX, zone.latitude, zone.longitude) <= (zone.radiusMeters + 15.0)
                }
                val isMemberAtSafeZone = matchedSafeZone != null && !isMemberAtHome

                // True movement requires sustained speed >= 1.2 mph or derived speed from rapid relocation
                val isMoving = if (isMemberAtHome || isMemberAtSafeZone) false else (resolvedSpeedMph >= 1.2 || derivedSpeedMph >= 1.5)
                val remoteSince = cloudM.locationSince
                val wasMemberAtHome = matchingLocal?.statusText?.contains("At Home", ignoreCase = true) == true
                val hasStatusTransitioned = isMemberAtHome != wasMemberAtHome
                val isRemoteSinceStaleHomeTimestamp = !isMemberAtHome && remoteSince > 0L && (System.currentTimeMillis() - remoteSince) > 6 * 3600 * 1000L

                val locationSince = if (isMoving) {
                    0L // Moving/traveling: reset stationary timer
                } else {
                    val now = System.currentTimeMillis()
                    when {
                        // 1. If remote sent a stale Home timestamp while away at a shop/school, reject it and preserve/create away arrival
                        isRemoteSinceStaleHomeTimestamp -> {
                            if (matchingLocal != null && matchingLocal.locationSince > 0L && (now - matchingLocal.locationSince) < 6 * 3600 * 1000L && movedDistanceKm <= 0.08) {
                                matchingLocal.locationSince
                            } else {
                                now - (48 * 60 * 1000L)
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
                        matchingLocal != null && matchingLocal.locationSince > 0L && (isMemberAtHome || isMemberAtSafeZone || movedDistanceKm <= 0.08) -> {
                            matchingLocal.locationSince
                        }
                        else -> now
                    }
                }

                val kPrefs = application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
                val isLocallyPaused = kPrefs.getBoolean("is_member_paused_${cloudM.id}", false) ||
                        kPrefs.getBoolean("is_member_paused_$cleanKey", false)
                val isMemberLocPaused = cloudM.isLocationPaused || isLocallyPaused || cloudM.statusText.contains("Paused", ignoreCase = true)
                val finalStatus = when {
                    isMemberLocPaused -> "⏸️ Paused (Home Sleep)"
                    isMemberAtHome -> "At Home (Live GPS)"
                    isMemberAtSafeZone && resolvedSpeedMph < 1.2 -> "At ${matchedSafeZone!!.name}"
                    else -> activeStatus
                }
                val finalSpeedMph = if (isMemberAtHome || isMemberLocPaused || (isMemberAtSafeZone && resolvedSpeedMph < 0.6)) 0.0 else resolvedSpeedMph
                val finalComingHome = if (isMemberAtHome || isMemberLocPaused || isMemberAtSafeZone) false else cloudM.isComingHome
                val finalEta = if (isMemberAtHome || isMemberLocPaused || isMemberAtSafeZone) 0 else cloudM.etaMinutes
                val finalX = if (isMemberAtHome && resolvedSpeedMph < 0.6) homeLng else if (isMemberAtSafeZone && resolvedSpeedMph < 0.6) matchedSafeZone!!.longitude else targetMemberX
                val finalY = if (isMemberAtHome && resolvedSpeedMph < 0.6) homeLat else if (isMemberAtSafeZone && resolvedSpeedMph < 0.6) matchedSafeZone!!.latitude else targetMemberY

                // Synchronize circle-wide device name.
                // IMPORTANT: respect any local rename the user has made on THIS device.
                // edited_name_${cloudM.id} is written whenever the user renames a member locally,
                // so we read it back here and prefer it over whatever the cloud sent.
                val locallyEditedName = contactsPrefs.getString("edited_name_${cloudM.id}", "")
                    ?.trim()?.takeIf { it.isNotBlank() }

                val resolvedName = when {
                    // User has renamed this member locally on this device — always honour it
                    !locallyEditedName.isNullOrBlank() -> locallyEditedName
                    // Use the cloud name as-is
                    cloudM.name.isNotBlank() -> cloudM.name
                    else -> cloudM.name
                }
                // Persist the resolved name so it survives app restarts
                if (resolvedName.isNotBlank()) {
                    contactsPrefs.edit().putString("edited_name_${cloudM.id}", resolvedName).apply()
                }

                val fallbackEmoji = when {
                    cleanKey.contains("dad") || cleanKey.contains("louis") -> "👨"
                    cleanKey.contains("isabel") -> "👩‍🎓"
                    cleanKey.contains("annette") -> "👩"
                    cleanKey.contains("eloise") -> "👧"
                    else -> ""
                }
                val rawEmoji = when {
                    cloudM.avatarEmoji.isNotBlank() -> cloudM.avatarEmoji
                    matchingLocal?.avatarEmoji?.isNotBlank() == true -> matchingLocal.avatarEmoji
                    matchingByName?.avatarEmoji?.isNotBlank() == true -> matchingByName.avatarEmoji
                    fallbackEmoji.isNotBlank() -> fallbackEmoji
                    else -> ""
                }
                val resolvedEmoji = com.example.data.IdentityUtils.sanitizeAvatarEmoji(rawEmoji, resolvedName, cloudM.id)

                val mappedLocal = FamilyMember(
                    id = cloudM.id, name = resolvedName, avatarColorHex = cloudM.avatarColorHex,
                    x = finalX, y = finalY, batteryPercentage = cloudM.batteryPercentage,
                    isCharging = cloudM.isCharging, speedMph = finalSpeedMph,
                    statusText = finalStatus, isComingHome = finalComingHome,
                    etaMinutes = finalEta, avatarEmoji = resolvedEmoji,
                    phoneNumber = if (matchingLocal?.phoneNumber?.isNotBlank() == true) matchingLocal.phoneNumber else resolvedPhone,
                    photoPath = if (matchingLocal?.photoPath?.isNotBlank() == true) matchingLocal.photoPath else resolvedPhoto,
                    lastActive = targetLastActive,
                    locationSince = locationSince,
                    isLocationPaused = isMemberLocPaused
                )

                if (matchingByName != null && matchingByName.id != cloudM.id) {
                    repository.deleteMember(matchingByName)
                    repository.clearBreadcrumbsForMember(matchingByName.id)
                }
                if (matchingLocal != null && matchingLocal.id != cloudM.id) {
                    repository.deleteMember(matchingLocal)
                    repository.clearBreadcrumbsForMember(matchingLocal.id)
                }
                repository.updateMember(mappedLocal)
                if (mappedLocal.x != 0.0 && mappedLocal.y != 0.0) {
                    repository.recordBreadcrumbThrottled(mappedLocal.id, mappedLocal.y, mappedLocal.x, mappedLocal.speedMph)
                    com.example.data.RailwayTransitDetector.checkRailwayCorridorAsync(
                        memberId = mappedLocal.id,
                        lat = mappedLocal.y,
                        lng = mappedLocal.x,
                        speedMph = mappedLocal.speedMph
                    )
                }
            }



            val validCloudIds = newPayload.members.keys.toSet()
            val validCloudUuids = validCloudIds.mapNotNull { if (it.contains("_")) it.substringAfterLast("_").takeIf { u -> u.length >= 4 } else null }.toSet()

            for (localM in existingLocal) {
                if (localM.id == "me") continue
                if (localM.id == myCloudId || localM.id.endsWith("_" + myDeviceUUID.value)) continue

                val localUuid = if (localM.id.contains("_")) localM.id.substringAfterLast("_") else ""
                val isExplicitlyDeleted = deletedMembersPrefs.getBoolean("deleted_${localM.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_${localM.id}", false)

                val localCanonKey = com.example.data.IdentityUtils.getCanonicalPersonKey(localM.name, localM.id)
                val isNotInCloud = !validCloudIds.contains(localM.id)
                val hasReplacementInCloud = (localUuid.length >= 4 && validCloudUuids.contains(localUuid)) ||
                    newPayload.members.values.any { com.example.data.IdentityUtils.getCanonicalPersonKey(it.name, it.id) == localCanonKey }

                if (isExplicitlyDeleted || (isNotInCloud && hasReplacementInCloud) || (isNotInCloud && (localM.id.contains("abc123") || localM.id.startsWith("mock_")))) {
                    repository.deleteMember(localM)
                    repository.clearBreadcrumbsForMember(localM.id)
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
        val payloadJson = payloadAdapter.toJson(payload)
        localMockCloudData[token] = payloadJson
        return try {
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
            val mockJson = localMockCloudData[token]
            if (mockJson != null && mockJson.isNotBlank()) {
                try {
                    payloadAdapter.fromJson(mockJson)
                } catch (ex: Exception) { null }
            } else null
        }
    }

    suspend fun removeMemberFromCloud(memberId: String, memberName: String) {
        val token = groupSyncToken.value
        if (token.isBlank()) return
        try {
            val payload = getGroupData(token) ?: return
            val updatedMembers = payload.members.toMutableMap()
            val myCloudId = "device_" + myDeviceName.value.lowercase().replace("\\s".toRegex(), "") + "_" + myDeviceUUID.value

            val targetUuid = if (memberId.startsWith("device_") && memberId.contains("_")) memberId.substringAfterLast("_") else ""
            val cleanTargetName = memberName.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father|Other Device)\\)", RegexOption.IGNORE_CASE), "").trim()

            val keysToRemove = updatedMembers.filter { entry ->
                if (entry.key == myCloudId || (myDeviceUUID.value.isNotBlank() && entry.key.endsWith("_" + myDeviceUUID.value))) return@filter false
                val isExactMatch = entry.key == memberId || entry.value.id == memberId || entry.key.endsWith("_$memberId")
                val isUuidMatch = targetUuid.length >= 4 && (entry.key.endsWith("_$targetUuid") || entry.value.id.endsWith("_$targetUuid"))
                val isOldDuplicateName = cleanTargetName.isNotBlank() && entry.value.name.lowercase().trim() == cleanTargetName && (entry.key.contains("abc123") || entry.key.startsWith("mock_"))

                isExactMatch || isUuidMatch || isOldDuplicateName
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

    fun syncMyProfileToCloud() {
        scope.launch {
            if (!isCloudSyncEnabled.value || groupSyncToken.value.isBlank()) return@launch
            try {
                performCloudSyncTick()
            } catch (_: Exception) {}
        }
    }

    fun renameDeviceInCircle(updated: FamilyMember) {
        scope.launch {
            val token = groupSyncToken.value
            if (token.isBlank()) return@launch
            try {
                val payload = getGroupData(token) ?: return@launch
                val updatedMembers = payload.members.toMutableMap()

                val updatedTargetId = updated.id
                val updatedTargetUuid = if (updatedTargetId.startsWith("device_") && updatedTargetId.contains("_")) {
                    updatedTargetId.substringAfterLast("_")
                } else ""

                // Find matching member entry in cloud payload
                val targetEntry = updatedMembers.entries.firstOrNull {
                    it.key == updatedTargetId || it.value.id == updatedTargetId ||
                    (updatedTargetUuid.length >= 4 && (it.key.endsWith("_$updatedTargetUuid") || it.value.id.endsWith("_$updatedTargetUuid")))
                }

                val oldKey = targetEntry?.key ?: updatedTargetId
                val oldMember = targetEntry?.value

                val newCloudMember = oldMember?.copy(
                    name = updated.name,
                    avatarColorHex = if (updated.avatarColorHex.isNotBlank()) updated.avatarColorHex else oldMember.avatarColorHex,
                    avatarEmoji = if (updated.avatarEmoji.isNotBlank()) updated.avatarEmoji else oldMember.avatarEmoji,
                    lastActive = System.currentTimeMillis()
                ) ?: CloudMember(
                    id = oldKey,
                    name = updated.name,
                    avatarColorHex = updated.avatarColorHex,
                    x = updated.x,
                    y = updated.y,
                    batteryPercentage = updated.batteryPercentage,
                    isCharging = updated.isCharging,
                    speedMph = updated.speedMph,
                    statusText = updated.statusText,
                    isComingHome = updated.isComingHome,
                    etaMinutes = updated.etaMinutes,
                    lastActive = System.currentTimeMillis(),
                    avatarEmoji = updated.avatarEmoji
                )

                val newKey = if (updatedTargetUuid.length >= 4) {
                    "device_" + updated.name.lowercase().replace("\\s".toRegex(), "") + "_" + updatedTargetUuid
                } else oldKey

                if (updatedTargetUuid.length >= 4) {
                    val toRemove = updatedMembers.filter { it.key.endsWith("_$updatedTargetUuid") || it.value.id.endsWith("_$updatedTargetUuid") }.keys
                    toRemove.forEach { updatedMembers.remove(it) }
                } else {
                    updatedMembers.remove(oldKey)
                }

                val finalMember = newCloudMember.copy(id = newKey)
                updatedMembers[newKey] = finalMember

                val updatedPayload = payload.copy(
                    lastUpdated = System.currentTimeMillis(),
                    members = updatedMembers
                )
                updateGroupData(token, updatedPayload)

                // Also notify the server REST API if available
                try {
                    val bodyJson = "{\"memberId\":\"$oldKey\",\"newName\":\"${updated.name}\",\"avatarColorHex\":\"${updated.avatarColorHex}\",\"avatarEmoji\":\"${updated.avatarEmoji}\"}"
                    val requestBody = bodyJson.toRequestBody("application/json".toMediaTypeOrNull())
                    apiService.renameMember(token, requestBody)
                } catch (_: Exception) {}

                uiEvents.emit("Renamed ${updated.name} across circle!")
            } catch (e: Exception) {}
        }
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
                cloudStatusText.value = "Creating Circle..."
                val nameToUse = groupName.ifBlank { "Family Circle" }
                
                // 1. Attempt REST Circle Creation on VPS
                var circleCode = ""
                var circleToken = ""
                var restSuccess = false

                try {
                    val createReq = CreateCircleRequest(
                        name = nameToUse,
                        creatorId = myDeviceUUID.value,
                        creatorName = myDeviceName.value,
                        avatarColorHex = myDeviceColor.value,
                        avatarEmoji = "👑",
                        homeLat = getHomeLat(),
                        homeLng = getHomeLng(),
                        isHomeCalibrated = isHomeCalibrated(),
                        workLat = getWorkLat(),
                        workLng = getWorkLng(),
                        isWorkCalibrated = isWorkCalibrated(),
                        homeRadiusMeters = getHomeRadius(),
                        workRadiusMeters = getWorkRadius()
                    )
                    val response = apiService.createCircle(createReq)
                    if (response.isSuccessful && response.body()?.circle != null) {
                        val circleData = response.body()!!.circle!!
                        circleCode = circleData.inviteCode
                        circleToken = circleData.circleId
                        restSuccess = true
                    }
                } catch (e: Exception) {
                    // Fallback to local 6-character code generation
                }

                // Fallback generation if server offline
                if (!restSuccess || circleCode.isBlank()) {
                    val chars = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
                    val p1 = (1..3).map { chars.random() }.joinToString("")
                    val p2 = (1..3).map { chars.random() }.joinToString("")
                    circleCode = "$p1-$p2"
                    val randomKey = UUID.randomUUID().toString().substring(0, 8)
                    circleToken = "${randomKey}_pin_group"
                }

                // ALWAYS register the PIN→token lookup key on the VPS so any device can join with the code.
                // This is the join mechanism — without this, joinGroupWithPin has nothing to look up.
                val pinMappingToken = "pin_${circleCode.replace("-", "")}"
                val mappingJson = "{\"groupSyncToken\":\"$circleToken\",\"creatorId\":\"${myDeviceUUID.value}\"}"
                try {
                    cloudService.updateGroupData(pinMappingToken, mappingJson.toRequestBody("application/json".toMediaTypeOrNull()))
                } catch (_: Exception) {}

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
                    pinCode = circleCode
                )
                updateGroupData(circleToken, initialPayload)

                val newMapping = GroupPinMapping(
                    pinCode = circleCode,
                    groupToken = circleToken,
                    groupName = nameToUse,
                    creatorId = myDeviceUUID.value,
                    createdTimestamp = System.currentTimeMillis(),
                    isOwner = true,
                    isActive = true
                )
                repository.deactivateAllGroups()
                repository.insertGroupPinMapping(newMapping)

                groupSyncToken.value = circleToken
                activeGroupPinCode.value = circleCode
                activeGroupCreatorId.value = myDeviceUUID.value
                isCloudSyncEnabled.value = true
                hasSuccessfullySyncedThisSession = false
                savePreferences()

                cloudStatusText.value = "Circle $circleCode Active"
                uiEvents.emit("Circle '$nameToUse' (Code: $circleCode) created successfully!")
            } catch (e: Exception) {
                uiEvents.emit("Failed to create circle: ${e.localizedMessage}")
            }
        }
    }

    fun joinGroupWithPin(pin: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                val cleanCode = pin.trim().replace("\\s".toRegex(), "").uppercase()
                cloudStatusText.value = "Connecting to Circle..."

                // 1. Try modern REST Join (works once new server is deployed to VPS)
                try {
                    val joinReq = JoinCircleRequest(
                        inviteCode = cleanCode,
                        memberId = "device_" + myDeviceName.value.lowercase().replace("\\s".toRegex(), "") + "_" + myDeviceUUID.value,
                        name = myDeviceName.value,
                        avatarColorHex = myDeviceColor.value,
                        avatarEmoji = "📱"
                    )
                    val response = apiService.joinCircle(joinReq)
                    if (response.isSuccessful && response.body()?.circle != null) {
                        val circle = response.body()!!.circle!!
                        if (circle.isHomeCalibrated) setHomeCalibrated(circle.homeLat, circle.homeLng)
                        if (circle.isWorkCalibrated && circle.workLat != 0.0 && circle.workLng != 0.0) {
                            setWorkCalibrated(circle.workLat, circle.workLng)
                        }
                        val newMapping = GroupPinMapping(
                            pinCode = circle.inviteCode.ifBlank { cleanCode },
                            groupToken = circle.circleId,
                            groupName = circle.name.ifBlank { "Family Circle" },
                            creatorId = circle.creatorId,
                            createdTimestamp = System.currentTimeMillis(),
                            isOwner = circle.creatorId == myDeviceUUID.value,
                            isActive = true
                        )
                        repository.deactivateAllGroups()
                        repository.insertGroupPinMapping(newMapping)
                        groupSyncToken.value = circle.circleId
                        activeGroupPinCode.value = circle.inviteCode.ifBlank { cleanCode }
                        activeGroupCreatorId.value = circle.creatorId
                        isCloudSyncEnabled.value = true
                        hasSuccessfullySyncedThisSession = false
                        savePreferences()
                        startCloudSyncLoop()
                        uiEvents.emit("Joined Circle '${circle.name}' (${circle.inviteCode})!")
                        onResult(true, "Joined circle!")
                        return@launch
                    }
                } catch (e: Exception) {
                    // New REST API not yet deployed — fall through to VPS PIN lookup
                }

                // 2. VPS PIN lookup: GET pin_{code} from the sync server to resolve the group token.
                // The creator's device writes this key when they create the circle (see createGroupWithPin).
                val pinMappingToken = "pin_${cleanCode.replace("-", "")}"
                val response = cloudService.getGroupData(pinMappingToken)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string() ?: ""
                    if (bodyString.isNotBlank() && bodyString != "null" && bodyString != "{}") {
                        var resolvedToken: String? = null
                        var creatorId: String = ""

                        try {
                            val pinAdapter = moshi.adapter(PinMappingResponse::class.java)
                            val pinResp = pinAdapter.fromJson(bodyString)
                            resolvedToken = pinResp?.groupSyncToken
                            creatorId = pinResp?.creatorId ?: ""
                        } catch (_: Exception) {}

                        // Fallback parsing via org.json in case of schema variance
                        if (resolvedToken.isNullOrBlank()) {
                            try {
                                val jsonObj = org.json.JSONObject(bodyString)
                                resolvedToken = jsonObj.optString("groupSyncToken", "")
                                creatorId = jsonObj.optString("creatorId", "")
                            } catch (_: Exception) {}
                        }

                        if (!resolvedToken.isNullOrBlank()) {
                            val groupPayload = getGroupData(resolvedToken)
                            if (groupPayload != null && groupPayload.isHomeCalibrated) setHomeCalibrated(groupPayload.homeLat, groupPayload.homeLng)
                            if (groupPayload != null && groupPayload.isWorkCalibrated && groupPayload.workLat != 0.0) setWorkCalibrated(groupPayload.workLat, groupPayload.workLng)
                            val newMapping = GroupPinMapping(
                                pinCode = cleanCode, groupToken = resolvedToken, groupName = "Family Circle",
                                creatorId = creatorId, createdTimestamp = System.currentTimeMillis(),
                                isOwner = creatorId == myDeviceUUID.value, isActive = true
                            )
                            repository.deactivateAllGroups()
                            repository.insertGroupPinMapping(newMapping)
                            groupSyncToken.value = resolvedToken
                            activeGroupPinCode.value = cleanCode
                            activeGroupCreatorId.value = creatorId
                            isCloudSyncEnabled.value = true
                            hasSuccessfullySyncedThisSession = false
                            savePreferences()
                            startCloudSyncLoop()
                            // Immediately broadcast location into the new group
                            try { performCloudSyncTick() } catch (_: Exception) {}
                            uiEvents.emit("✅ Joined Circle $cleanCode!")
                            onResult(true, "Joined circle!")
                            return@launch
                        }
                    }
                }

                uiEvents.emit("Could not find a Circle with code '$cleanCode'. Check the code and try again.")
                onResult(false, "Circle not found")
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
                        val deletedMembersPrefs = application.getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE)
                        deletedMembersPrefs.edit()
                            .putBoolean("deleted_$memberId", true)
                            .putBoolean("deleted_member_$memberId", true)
                            .apply()
                        repository.insertLog(ActivityLog(memberId = memberId, memberName = kickedMember?.name ?: memberId, actionText = "was permanently removed (kicked) from the circle by owner", iconName = "away"))
                        repository.getFamilyMembersOnce().firstOrNull { it.id == memberId }?.let { repository.deleteMember(it) }
                        repository.clearBreadcrumbsForMember(memberId)
                        uiEvents.emit("Successfully kicked ${kickedMember?.name ?: memberId}.")
                    }
                }
            } catch (e: Exception) {
                uiEvents.emit("Failed to kick member: ${e.localizedMessage}")
            }
        }
    }

    suspend fun cleanupDuplicatesNow(): Int {
        val token = groupSyncToken.value
        if (token.isBlank()) return 0
        return try {
            val payload = getGroupData(token) ?: return 0
            val updatedMembers = payload.members.toMutableMap()
            val beforeCount = updatedMembers.size
            val myCloudId = "device_" + myDeviceName.value.lowercase().replace("\\s".toRegex(), "") + "_" + myDeviceUUID.value
            val myCanonicalKey = com.example.data.IdentityUtils.getCanonicalPersonKey(myDeviceName.value, myDeviceUUID.value)

            // Deduplicate members by canonical identity
            val membersByCanonical = updatedMembers.values.groupBy { com.example.data.IdentityUtils.getCanonicalPersonKey(it.name, it.id) }
            for ((canonKey, list) in membersByCanonical) {
                if (list.size > 1) {
                    if (canonKey == myCanonicalKey) {
                        for (stale in list) {
                            if (stale.id != myCloudId) {
                                updatedMembers.remove(stale.id)
                            }
                        }
                    } else {
                        val newest = list.maxWithOrNull(
                            compareBy<CloudMember> { it.id.startsWith("device_") }.thenBy { it.lastActive }
                        )
                        for (stale in list) {
                            if (stale.id != newest?.id) {
                                updatedMembers.remove(stale.id)
                            }
                        }
                    }
                }
            }

            // Remove old mock/test IDs
            val mockKeys = updatedMembers.keys.filter { it.contains("abc123") || it.startsWith("mock_") }
            for (k in mockKeys) updatedMembers.remove(k)

            val removedCount = beforeCount - updatedMembers.size
            if (removedCount > 0) {
                val newPayload = payload.copy(lastUpdated = System.currentTimeMillis(), members = updatedMembers)
                updateGroupData(token, newPayload)

                // Clean from local DB
                val existingLocal = repository.getFamilyMembersOnce()
                for (localM in existingLocal) {
                    if (localM.id == "me") continue
                    if (localM.id == myCloudId || localM.id.endsWith("_" + myDeviceUUID.value)) continue
                    if (!updatedMembers.containsKey(localM.id)) {
                        repository.deleteMember(localM)
                        repository.clearBreadcrumbsForMember(localM.id)
                    }
                }
            }
            removedCount
        } catch (_: Exception) { 0 }
    }
}
