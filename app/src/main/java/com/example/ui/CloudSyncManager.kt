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
    private val savePreferences: () -> Unit
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
                locationSince = meMember.locationSince
            )

            // Sync and Merge Shopping items
            val localShoppingItems = repository.getShoppingItemsOnce()
            val deletionPrefs = application.getSharedPreferences("shopping_deletions", android.content.Context.MODE_PRIVATE)
            val incomingShoppingItems = payload?.shoppingItems ?: emptyList()

            val mergedShoppingMap = mutableMapOf<String, CloudShoppingItem>()

            // 1. Populate map with local items
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

            // 2. Merge with cloud items
            for (cloudItem in incomingShoppingItems) {
                val key = cloudItem.name.lowercase().trim()
                val localDeletionTime = deletionPrefs.getLong(key, 0L)

                if (localDeletionTime > cloudItem.timestamp) {
                    // We deleted it after it was updated in the cloud. Drop it.
                    mergedShoppingMap.remove(key)
                } else {
                    val localMatch = mergedShoppingMap[key]
                    if (localMatch != null) {
                        // Exists both locally and in cloud, LWW logic:
                        if (cloudItem.timestamp > localMatch.timestamp) {
                            mergedShoppingMap[key] = cloudItem
                        }
                    } else {
                        // Exists in cloud but not locally:
                        if (localDeletionTime == 0L || cloudItem.timestamp > localDeletionTime) {
                            mergedShoppingMap[key] = cloudItem
                        }
                    }
                }
            }

            // 3. Write updates back to local DB
            val finalShoppingList = mergedShoppingMap.values.toList()
            for (cloudItem in finalShoppingList) {
                val key = cloudItem.name.lowercase().trim()
                val localMatch = localShoppingItems.firstOrNull { it.name.lowercase().trim() == key }
                if (localMatch == null) {
                    // New item from cloud, insert locally
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
                    // Exists locally, update if checked status is different and cloud timestamp is newer
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

            // 4. Delete items from local DB that were deleted by other clients
            for (localItem in localShoppingItems) {
                val key = localItem.name.lowercase().trim()
                val inIncoming = incomingShoppingItems.any { it.name.lowercase().trim() == key }
                val inMerged = mergedShoppingMap.containsKey(key)
                if (inIncoming && !inMerged) {
                    repository.deleteShoppingItem(localItem)
                }
            }

            val newPayload = if (payload != null) {
                if (!isHomeCalibrated() && payload.isHomeCalibrated) {
                    setHomeCalibrated(payload.homeLat, payload.homeLng)
                    uiEvents.emit("Synced common Home coordinates from cloud group!")
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
                    lastUpdated = lastActiveTimestamp,
                    members = updatedMembers,
                    shoppingItems = finalShoppingList
                )
            } else {
                CloudGroupPayload(
                    homeLat = getHomeLat(),
                    homeLng = getHomeLng(),
                    isHomeCalibrated = isHomeCalibrated(),
                    lastUpdated = lastActiveTimestamp,
                    members = mapOf(myCloudId to myCloudMember),
                    shoppingItems = finalShoppingList
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
            val defaultSimulatedIds = setOf("eloise", "isabel", "louis", "annette")

            for (cloudM in incomingCloudMembers) {
                if (cloudM.id == myCloudId) continue
                val cleanCloudName = cloudM.name.lowercase().trim()
                if (cleanCloudName == cleanMyName || 
                    (cleanMyName.contains("louis") && cleanCloudName.contains("louis")) ||
                    (cleanMyName.contains("dad") && cleanCloudName.contains("dad"))) {
                    continue
                }

                val matchingLocal = existingLocal.firstOrNull { it.id == cloudM.id }

                val matchingByName = existingLocal.firstOrNull {
                    it.id != "me" && !it.id.startsWith("device_") &&
                    !defaultSimulatedIds.contains(it.id) &&
                    it.name.trim().equals(cloudM.name.trim(), ignoreCase = true)
                }

                // Preserve local photoPath and phoneNumber from the matchingByName record, or fall back to persistent local contacts directory
                var resolvedPhone = if (matchingLocal?.phoneNumber?.isNotBlank() == true) {
                    matchingLocal.phoneNumber
                } else if (matchingByName?.phoneNumber?.isNotBlank() == true) {
                    matchingByName.phoneNumber
                } else {
                    val prefs = application.getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)
                    prefs.getString("phone_${cloudM.name.lowercase().trim()}", "") ?: ""
                }

                var resolvedPhoto = if (matchingLocal?.photoPath?.isNotBlank() == true) {
                    matchingLocal.photoPath
                } else if (matchingByName?.photoPath?.isNotBlank() == true) {
                    matchingByName.photoPath
                } else {
                    val prefs = application.getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)
                    prefs.getString("photo_${cloudM.name.lowercase().trim()}", "") ?: ""
                }

                // Persistently cache any valid phone/photo to the local contacts directory so it's remembered forever
                if (resolvedPhone.isNotBlank() || resolvedPhoto.isNotBlank()) {
                    val prefs = application.getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        if (resolvedPhone.isNotBlank()) putString("phone_${cloudM.name.lowercase().trim()}", resolvedPhone)
                        if (resolvedPhoto.isNotBlank()) putString("photo_${cloudM.name.lowercase().trim()}", resolvedPhoto)
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

                // Compute locationSince: normalize remote duration to eliminate device clock skew / 1-hour timezone differences
                val remoteSince = cloudM.locationSince
                val movedDistanceKm = if (matchingLocal != null && matchingLocal.x != 0.0 && matchingLocal.y != 0.0 && cloudM.x != 0.0 && cloudM.y != 0.0) {
                    Math.hypot((matchingLocal.x - cloudM.x) * 111.0 * Math.cos(Math.toRadians(cloudM.y)), (matchingLocal.y - cloudM.y) * 111.0)
                } else 0.0

                val coordsMoved = matchingLocal == null || (movedDistanceKm > 0.12 && cloudM.speedMph > 1.5)

                val locationSince = when {
                    !coordsMoved && matchingLocal != null && matchingLocal.locationSince > 0L -> {
                        // Person hasn't moved — preserve steady continuous timer
                        matchingLocal.locationSince
                    }
                    remoteSince > 0L && cloudM.lastActive >= remoteSince -> {
                        // Normalize elapsed duration relative to local time to prevent clock/timezone jumps
                        val remoteElapsedMs = cloudM.lastActive - remoteSince
                        (System.currentTimeMillis() - remoteElapsedMs).coerceAtMost(System.currentTimeMillis())
                    }
                    remoteSince > 0L -> remoteSince
                    else -> System.currentTimeMillis()
                }

                val mappedLocal = FamilyMember(
                    id = cloudM.id, name = cloudM.name, avatarColorHex = cloudM.avatarColorHex,
                    x = cloudM.x, y = cloudM.y, batteryPercentage = cloudM.batteryPercentage,
                    isCharging = cloudM.isCharging, speedMph = cloudM.speedMph,
                    statusText = activeStatus, isComingHome = cloudM.isComingHome,
                    etaMinutes = cloudM.etaMinutes, avatarEmoji = cloudM.avatarEmoji,
                    phoneNumber = if (matchingLocal?.phoneNumber?.isNotBlank() == true) matchingLocal.phoneNumber else resolvedPhone,
                    photoPath = if (matchingLocal?.photoPath?.isNotBlank() == true) matchingLocal.photoPath else resolvedPhoto,
                    lastActive = cloudM.lastActive,
                    locationSince = locationSince
                )

                if (matchingLocal == null) repository.insertFamilyMembers(listOf(mappedLocal))
                else repository.updateMember(mappedLocal)
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

    fun generateNewGroupKey() {
        scope.launch {
            try {
                cloudStatusText.value = "Generating on Server..."
                val initialPayload = CloudGroupPayload(
                    homeLat = getHomeLat(),
                    homeLng = getHomeLng(),
                    isHomeCalibrated = isHomeCalibrated(),
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
                val pin = String.format("%04d", Random().nextInt(9000) + 1000)
                val randomKey = UUID.randomUUID().toString().substring(0, 8)
                val cleanUrl = "${randomKey}_pin_group"
                
                val pinMappingToken = "pin_$pin"
                val mappingJson = "{\"groupSyncToken\":\"$cleanUrl\",\"creatorId\":\"${myDeviceUUID.value}\"}"
                cloudService.updateGroupData(pinMappingToken, mappingJson.toRequestBody("application/json".toMediaTypeOrNull()))
                
                val initialPayload = CloudGroupPayload(
                    homeLat = getHomeLat(),
                    homeLng = getHomeLng(),
                    isHomeCalibrated = isHomeCalibrated(),
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
