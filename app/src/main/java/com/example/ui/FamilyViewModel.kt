package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.hypot
import kotlin.random.Random

class FamilyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FamilyRepository
    val familyMembers: StateFlow<List<FamilyMember>>
    val activityLogs: StateFlow<List<ActivityLog>>
    val groupPinMappings: StateFlow<List<GroupPinMapping>>
    val safeZones: StateFlow<List<SafeZone>>
    val shoppingItems: StateFlow<List<ShoppingItem>>


    val activeGroupCreatorId = MutableStateFlow("")
    val activeGroupPinCode = MutableStateFlow("")

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    val selectedMemberId = MutableStateFlow<String?>(null)
    val isSimulationPaused = MutableStateFlow(false)
    private val _locationTrails = MutableStateFlow<Map<String, List<Pair<Double, Double>>>>(emptyMap())
    val locationTrails: StateFlow<Map<String, List<Pair<Double, Double>>>> = _locationTrails

    val isCloudSyncEnabled = MutableStateFlow(true)
    val groupSyncToken = MutableStateFlow("")
    val ghostModeExpiryTime = MutableStateFlow(0L)
    val myDeviceName = MutableStateFlow("Dad")
    val myDeviceColor = MutableStateFlow("#AA22FF")
    val myDeviceEmoji = MutableStateFlow("👨")
    val myDevicePhone = MutableStateFlow("+447802436159")
    val myDevicePhotoPath = MutableStateFlow("")
    val myDeviceUUID = MutableStateFlow("")
    val cloudStatusText = MutableStateFlow("Local / offline tracking mode")
    val isSimulationModeEnabled = MutableStateFlow(false)
    val isWifeCloudSimulationEnabled = MutableStateFlow(false)
    val hasCompletedOnboarding = MutableStateFlow(false)
    val isCircleDigestReset = MutableStateFlow(false)
    val isVoiceAnnouncementsEnabled = MutableStateFlow(false)
    val proximityAlertDistanceMeters = MutableStateFlow(400)



    val isMySosAlertActive = MutableStateFlow(false)
    val myActiveReaction = MutableStateFlow<String?>(null)
    var myReactionExpirationTime = 0L
    val isMyCheckInTriggered = MutableStateFlow(false)
    var myCheckInExpirationTime = 0L

    val isUserSignedIn = MutableStateFlow(true)
    val userDisplayName = MutableStateFlow("")
    val userEmail = MutableStateFlow("")

    val homeLatFlow = MutableStateFlow(AppConfig.DEFAULT_HOME_LAT)
    val homeLngFlow = MutableStateFlow(AppConfig.DEFAULT_HOME_LNG)
    var homeLat: Double get() = homeLatFlow.value; set(value) { homeLatFlow.value = value }
    var homeLng: Double get() = homeLngFlow.value; set(value) { homeLngFlow.value = value }
    var isHomeCalibrated = true

    private val simulationEngine: SimulationEngine
    private val cloudSyncManager: CloudSyncManager
    private val proximityEngine: ProximityEngine

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FamilyRepository(database.familyDao())
        proximityEngine = ProximityEngine(repository, _uiEvents)
        
        familyMembers = repository.familyMembers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        activityLogs = repository.activityLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        groupPinMappings = repository.groupPinMappings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        safeZones = repository.safeZones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        shoppingItems = repository.shoppingItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


        cloudSyncManager = CloudSyncManager(
            repository, viewModelScope, application, _uiEvents, isCloudSyncEnabled, groupSyncToken, cloudStatusText,
            familyMembers, myDeviceName, myDeviceColor, myDeviceUUID, ghostModeExpiryTime, activeGroupCreatorId, activeGroupPinCode,
            { homeLat }, { homeLng }, { isHomeCalibrated }, { lat, lng -> homeLat = lat; homeLng = lng; isHomeCalibrated = true; savePreferences() },
            isSimulationModeEnabled, { getMyActiveStatusText(it) }, { savePreferences() }
        )

        simulationEngine = SimulationEngine(
            repository,
            viewModelScope,
            _uiEvents,
            isSimulationPaused,
            isSimulationModeEnabled,
            isWifeCloudSimulationEnabled,
            groupSyncToken,
            cloudSyncManager,
            familyMembers,
            { homeLat },
            { homeLng },
            { savePreferences() }
        )

        loadPreferences()
        setupLocationTrails()
        setupCircleProximityMonitoring()
        setupSafeZoneGeofences()
        setupWeatherTracking()
        initializeData()
    }

    private fun setupLocationTrails() {
        viewModelScope.launch {
            familyMembers.collect { membersList ->
                if (membersList.isEmpty()) return@collect
                val currentTrails = _locationTrails.value.toMutableMap()
                var updated = false
                membersList.forEach { m ->
                    if (m.x == 0.0 && m.y == 0.0) return@forEach
                    val coords = currentTrails[m.id] ?: emptyList()
                    val newPoint = Pair(m.y, m.x)
                    if (coords.isEmpty()) {
                        val isAwayFromHome = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(m.y - homeLat, m.x - homeLng) * 111.0 > 0.06)
                        if (isAwayFromHome) {
                            val homePoint = Pair(homeLat, homeLng)
                            currentTrails[m.id] = listOf(homePoint, newPoint)
                            updated = true
                            viewModelScope.launch {
                                val roadSegments = fetchRoadRoute(homePoint, newPoint)
                                if (roadSegments.size >= 2) {
                                    val activeTrails = _locationTrails.value.toMutableMap()
                                    activeTrails[m.id] = roadSegments.takeLast(300)
                                    _locationTrails.value = activeTrails
                                }
                            }
                        } else {
                            currentTrails[m.id] = listOf(newPoint)
                            updated = true
                        }
                    } else if (coords.last() != newPoint) {
                        val oldPoint = coords.last()
                        // Immediately draw a direct line for fast visual feedback, then refine with OSRM
                        currentTrails[m.id] = (coords + newPoint).takeLast(300)
                        updated = true
                        
                        viewModelScope.launch {
                            val roadSegments = fetchRoadRoute(oldPoint, newPoint)
                            val activeTrails = _locationTrails.value.toMutableMap()
                            val currentTrail = activeTrails[m.id] ?: emptyList()
                            if (currentTrail.isNotEmpty() && currentTrail.last() == newPoint) {
                                // Swap out the straight line and insert detailed road points
                                val baseTrail = currentTrail.dropLast(1)
                                val cleanSegments = if (roadSegments.size >= 2 && roadSegments.first() == oldPoint) {
                                    roadSegments.drop(1)
                                } else {
                                    roadSegments
                                }
                                activeTrails[m.id] = (baseTrail + cleanSegments).takeLast(300)
                                _locationTrails.value = activeTrails
                            }
                        }
                    }
                }
                if (updated) _locationTrails.value = currentTrails
            }
        }
    }

    private fun setupCircleProximityMonitoring() {
        viewModelScope.launch {
            familyMembers.collect { membersList ->
                proximityEngine.evaluateProximity(
                    membersList = membersList,
                    homeLat = homeLat,
                    homeLng = homeLng,
                    proximityThresholdMeters = proximityAlertDistanceMeters.value
                )
            }
        }
    }

    private suspend fun fetchRoadRoute(from: Pair<Double, Double>, to: Pair<Double, Double>): List<Pair<Double, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                // If they are extremely close (less than 15 meters), return raw straight line
                val dist = hypot(from.second - to.second, from.first - to.first) * 111000.0
                if (dist < 15.0) return@withContext listOf(from, to)

                val url = java.net.URL("https://router.project-osrm.org/route/v1/driving/${from.second},${from.first};${to.second},${to.first}?overview=full&geometries=geojson")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"
                
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val coordinatesList = mutableListOf<Pair<Double, Double>>()
                    val matcher = java.util.regex.Pattern.compile("\\[\\s*(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)\\s*\\]").matcher(text)
                    while (matcher.find()) {
                        val lng = matcher.group(1)?.toDouble() ?: 0.0
                        val lat = matcher.group(2)?.toDouble() ?: 0.0
                        coordinatesList.add(Pair(lat, lng))
                    }
                    if (coordinatesList.isNotEmpty()) {
                        return@withContext coordinatesList
                    }
                }
                listOf(from, to)
            } catch (e: Exception) {
                listOf(from, to)
            }
        }
    }

    private val lastMemberZoneStatus = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lastZoneAlertTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun setupSafeZoneGeofences() {
        viewModelScope.launch {
            combine(repository.safeZones, familyMembers) { zones, members ->
                Pair(zones, members)
            }.collect { (zones, members) ->
                if (zones.isEmpty() || members.isEmpty()) return@collect
                members.forEach { member ->
                    if (member.x == 0.0 && member.y == 0.0) return@forEach
                    zones.forEach { zone ->
                        val xDist = (member.x - zone.longitude) * 111.0 * Math.cos(Math.toRadians(zone.latitude))
                        val yDist = (member.y - zone.latitude) * 111.0
                        val distMeters = Math.hypot(xDist, yDist) * 1000.0
                        
                        val key = "${member.id}_${zone.id}"
                        val lastStatus = lastMemberZoneStatus[key]
                        
                        // Apply a 20-meter hysteresis buffer to prevent boundary jitter
                        val isInside = if (lastStatus == "inside") {
                            distMeters <= (zone.radiusMeters + 20.0)
                        } else {
                            distMeters <= zone.radiusMeters
                        }
                        
                        if (isInside && lastStatus != "inside") {
                            lastMemberZoneStatus[key] = "inside"
                            if (lastStatus != null) {
                                val now = System.currentTimeMillis()
                                val lastAlert = lastZoneAlertTime[key] ?: 0L
                                if (now - lastAlert > 300_000L) {
                                    lastZoneAlertTime[key] = now
                                    repository.insertLog(
                                        ActivityLog(
                                            memberId = member.id,
                                            memberName = member.name,
                                            actionText = "arrived at ${zone.name}",
                                            iconName = "check_in"
                                        )
                                    )
                                    _uiEvents.emit("${member.name} has arrived at ${zone.name}!")
                                }
                            }
                        } else if (!isInside && lastStatus == "inside") {
                            lastMemberZoneStatus[key] = "outside"
                            val now = System.currentTimeMillis()
                            val lastAlert = lastZoneAlertTime[key] ?: 0L
                            if (now - lastAlert > 300_000L) {
                                lastZoneAlertTime[key] = now
                                repository.insertLog(
                                    ActivityLog(
                                        memberId = member.id,
                                        memberName = member.name,
                                        actionText = "left ${zone.name}",
                                        iconName = "away"
                                    )
                                    )
                                _uiEvents.emit("${member.name} has left ${zone.name}!")
                            }
                        } else if (lastStatus == null) {
                            lastMemberZoneStatus[key] = if (isInside) "inside" else "outside"
                        }
                    }
                }
            }
        }
    }

    private fun initializeData() {
        viewModelScope.launch {
            repository.ensureDefaultDataInserted(homeLat, homeLng)

            val storedPin = activeGroupPinCode.value
            val storedToken = groupSyncToken.value
            val storedCreator = activeGroupCreatorId.value

            if (storedPin.isNotBlank()) {
                if (repository.getGroupPinMappingByPin(storedPin) == null) {
                    repository.insertGroupPinMapping(
                        GroupPinMapping(
                            pinCode = storedPin,
                            groupToken = storedToken,
                            groupName = if (storedPin == "4666") "Family Circle" else "Joined Circle",
                            creatorId = storedCreator,
                            createdTimestamp = System.currentTimeMillis(),
                            isOwner = storedCreator == myDeviceUUID.value,
                            isActive = true
                        )
                    )
                } else {
                    repository.activateGroup(storedPin)
                }
            }

            // Pre-populate PIN 4666 circle registry so it shows up in local UI circle selection list
            if (repository.getGroupPinMappingByPin("4666") == null) {
                repository.insertGroupPinMapping(
                    GroupPinMapping(
                        pinCode = "4666",
                        groupToken = "81e5632c_pin_group",
                        groupName = "Family Circle",
                        creatorId = "336a12",
                        createdTimestamp = System.currentTimeMillis(),
                        isOwner = false,
                        isActive = (storedPin == "4666" || storedPin.isBlank())
                    )
                )
            }

            // Resiliently query active circle from SQLite Room database on startup
            repository.getActiveGroupPinMappingOnce()?.let { activeGroup ->
                groupSyncToken.value = activeGroup.groupToken
                activeGroupPinCode.value = activeGroup.pinCode
                activeGroupCreatorId.value = activeGroup.creatorId
                isCloudSyncEnabled.value = true
                savePreferences()
            }

            val prefs = getApplication<Application>().getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
            val savedLocationSince = prefs.getLong("my_location_since", System.currentTimeMillis()).let {
                if (it == 0L) System.currentTimeMillis() else it
            }
            prefs.edit().putLong("my_location_since", savedLocationSince).apply()

            val current = repository.getFamilyMembersOnce()
            val eloiseMember = current.firstOrNull { it.id == "eloise" || (it.name.contains("Eloise", ignoreCase = true) && !it.id.startsWith("device_")) }
            if (eloiseMember != null && (eloiseMember.statusText.contains("Dance Class") || (eloiseMember.x != homeLng && eloiseMember.y != homeLat))) {
                repository.updateMember(
                    eloiseMember.copy(
                        x = homeLng,
                        y = homeLat,
                        speedMph = 0.0,
                        statusText = "At Home",
                        etaMinutes = 0
                    )
                )
            }

            if (current.none { it.id == "me" }) {
                repository.insertFamilyMembers(listOf(FamilyMember("me", myDeviceName.value, myDeviceColor.value, homeLng, homeLat, 100, false, 0.0, "Syncing GPS...", false, 0, myDeviceEmoji.value, myDevicePhone.value, myDevicePhotoPath.value, locationSince = savedLocationSince)))
            } else {
                current.first { it.id == "me" }.let {
                    val resolvedSince = if (it.locationSince > 0L) it.locationSince else savedLocationSince
                    repository.updateMember(it.copy(name = myDeviceName.value, avatarColorHex = myDeviceColor.value, avatarEmoji = myDeviceEmoji.value, phoneNumber = myDevicePhone.value, photoPath = myDevicePhotoPath.value, locationSince = resolvedSince))
                }
            }
            
            simulationEngine.start()
            if (isCloudSyncEnabled.value) {
                if (groupSyncToken.value.isBlank()) autoProvisionGroupSync()
                else {
                    cloudSyncManager.startCloudSyncLoop()
                    if (isWifeCloudSimulationEnabled.value) startWifeCloudSimulationLoop()
                }
            }
        }
    }

    fun triggerUIFeedback(message: String) { viewModelScope.launch { _uiEvents.emit(message) } }
    fun toggleVoiceAnnouncements(enabled: Boolean) {
        isVoiceAnnouncementsEnabled.value = enabled
        savePreferences()
    }

    fun addShoppingItem(name: String, memberId: String, memberName: String) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            val item = ShoppingItem(name = name, addedByMemberId = memberId, addedByMemberName = memberName)
            repository.insertShoppingItem(item)
            repository.insertLog(ActivityLog(
                memberId = memberId,
                memberName = memberName,
                actionText = "added '$name' to the shopping list",
                iconName = "check_in"
            ))
        }
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            val updated = item.copy(isChecked = !item.isChecked, timestamp = System.currentTimeMillis())
            repository.updateShoppingItem(updated)
            val action = if (updated.isChecked) {
                "marked '${item.name}' as purchased"
            } else {
                "marked '${item.name}' as active"
            }
            repository.insertLog(ActivityLog(
                memberId = "system",
                memberName = "Shopping List",
                actionText = action,
                iconName = "check_in"
            ))
        }
    }

    fun deleteShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("shopping_deletions", android.content.Context.MODE_PRIVATE)
                prefs.edit().putLong(item.name.lowercase().trim(), System.currentTimeMillis()).apply()
            } catch (e: Exception) {}
            repository.deleteShoppingItem(item)
            repository.insertLog(ActivityLog(
                memberId = "system",
                memberName = "Shopping List",
                actionText = "removed '${item.name}' from the shopping list",
                iconName = "away"
            ))
        }
    }



    private fun getMyActiveStatusText(baseStatus: String): String {
        if (isMySosAlertActive.value) return "🚨 EMERGENCY SOS ACTIVE! distress beacon triggered!"
        myActiveReaction.value?.let { if (System.currentTimeMillis() < myReactionExpirationTime) return "💬 Reaction: $it" }
        if (isMyCheckInTriggered.value && System.currentTimeMillis() < myCheckInExpirationTime) return "📍 Checked in safely at Home base!"
        return baseStatus
    }

    private fun savePreferences() {
         val prefs = getApplication<Application>().getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
         prefs.edit().apply {
             putBoolean("isUserSignedIn", isUserSignedIn.value); putString("userDisplayName", userDisplayName.value); putString("userEmail", userEmail.value)
             putString("myDeviceName", myDeviceName.value); putString("myDeviceColor", myDeviceColor.value); putString("myDeviceEmoji", myDeviceEmoji.value)
             putString("myDeviceUUID", myDeviceUUID.value); putString("groupSyncToken", groupSyncToken.value); putBoolean("isCloudSyncEnabled", isCloudSyncEnabled.value)
             putLong("ghostModeExpiryTime", ghostModeExpiryTime.value); putBoolean("isSimulationModeEnabled", isSimulationModeEnabled.value)
             putBoolean("isWifeCloudSimulationEnabled", isWifeCloudSimulationEnabled.value); putBoolean("hasCompletedOnboarding", hasCompletedOnboarding.value)
             putBoolean("isCircleDigestReset", isCircleDigestReset.value)
             putBoolean("isVoiceAnnouncementsEnabled", isVoiceAnnouncementsEnabled.value)
             putFloat("homeLat", homeLat.toFloat()); putFloat("homeLng", homeLng.toFloat()); putBoolean("isHomeCalibrated", isHomeCalibrated)
             putString("myDevicePhone", myDevicePhone.value); putString("myDevicePhotoPath", myDevicePhotoPath.value)
             putString("activeGroupPinCode", activeGroupPinCode.value); putString("activeGroupCreatorId", activeGroupCreatorId.value)
             apply()
         }
     }

     private fun loadPreferences() {
         val prefs = getApplication<Application>().getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
         isUserSignedIn.value = prefs.getBoolean("isUserSignedIn", true)
         userDisplayName.value = prefs.getString("userDisplayName", "") ?: ""; userEmail.value = prefs.getString("userEmail", "") ?: ""
         myDeviceName.value = prefs.getString("myDeviceName", "Dad") ?: "Dad"; myDeviceColor.value = prefs.getString("myDeviceColor", "#AA22FF") ?: "#AA22FF"
         myDeviceEmoji.value = prefs.getString("myDeviceEmoji", "👨") ?: "👨"; myDevicePhone.value = prefs.getString("myDevicePhone", "+447802436159") ?: "+447802436159"
         myDevicePhotoPath.value = prefs.getString("myDevicePhotoPath", "") ?: ""
         
         activeGroupPinCode.value = prefs.getString("activeGroupPinCode", "4666") ?: "4666"
         activeGroupCreatorId.value = prefs.getString("activeGroupCreatorId", "336a12") ?: "336a12"
         
         var dUuid = prefs.getString("myDeviceUUID", "") ?: ""
         if (dUuid.isBlank()) { dUuid = java.util.UUID.randomUUID().toString().substring(0, 6); prefs.edit().putString("myDeviceUUID", dUuid).apply() }
         myDeviceUUID.value = dUuid

         val savedToken = prefs.getString("groupSyncToken", "81e5632c_pin_group") ?: "81e5632c_pin_group"
         groupSyncToken.value = cloudSyncManager.convertToValidToken(savedToken)

         isCloudSyncEnabled.value = prefs.getBoolean("isCloudSyncEnabled", true); ghostModeExpiryTime.value = prefs.getLong("ghostModeExpiryTime", 0L)
         isSimulationModeEnabled.value = prefs.getBoolean("isSimulationModeEnabled", false); isWifeCloudSimulationEnabled.value = prefs.getBoolean("isWifeCloudSimulationEnabled", false)
         hasCompletedOnboarding.value = prefs.getBoolean("hasCompletedOnboarding", false) || groupSyncToken.value.isNotBlank()
         isCircleDigestReset.value = prefs.getBoolean("isCircleDigestReset", false)
         isVoiceAnnouncementsEnabled.value = prefs.getBoolean("isVoiceAnnouncementsEnabled", false)
         proximityAlertDistanceMeters.value = prefs.getInt("proximityAlertDistanceMeters", 400)
         homeLat = prefs.getFloat("homeLat", 51.332308f).toDouble(); homeLng = prefs.getFloat("homeLng", -0.117188f).toDouble(); isHomeCalibrated = prefs.getBoolean("isHomeCalibrated", true)
     }

    fun updateProximityAlertDistance(meters: Int) {
        proximityAlertDistanceMeters.value = meters
        savePreferences()
        viewModelScope.launch {
            val label = if (meters >= 1000) "${String.format(java.util.Locale.US, "%.1f", meters / 1000.0)}km" else "${meters}m"
            _uiEvents.emit("Proximity warning distance set to $label")
        }
    }

    fun toggleSimulationMode(enabled: Boolean) {
        viewModelScope.launch {
            isSimulationModeEnabled.value = enabled
            savePreferences()
            if (enabled) {
                repository.ensureDefaultDataInserted(homeLat, homeLng)
                _uiEvents.emit("GPS Simulation Mode activated.")
            } else {
                _uiEvents.emit("Live GPS & Real Tracking active.")
            }
        }
    }

    fun signInUser(name: String, email: String) {
        viewModelScope.launch {
            userDisplayName.value = name; userEmail.value = email; isUserSignedIn.value = true; myDeviceName.value = name
            repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }?.let { repository.updateMember(it.copy(name = name)) }
            savePreferences(); _uiEvents.emit("Signed in successfully as $name")
        }
    }

    fun signOutUser() { viewModelScope.launch { isUserSignedIn.value = false; savePreferences(); _uiEvents.emit("Signed out successfully from Pulse Tracker") } }
    fun completeOnboarding() { hasCompletedOnboarding.value = true; savePreferences() }

    fun orderHeadingHome(memberId: String) {
        viewModelScope.launch {
            val m = familyMembers.value.firstOrNull { it.id == memberId } ?: return@launch
            if (m.isComingHome) return@launch
            val distKm = hypot(m.x - homeLng, m.y - homeLat) * 111.0
            if (distKm < 0.05) { _uiEvents.emit("${m.name} is already at Home!"); return@launch }
            val startSpeed = when (m.id) { "eloise" -> 3.0; "isabel" -> 12.0; "louis" -> 70.0; else -> 35.0 }
            val startStatus = when (m.id) { "eloise" -> "Walking from School"; "isabel" -> "Biking from High School"; "louis" -> "Commuting via Train"; else -> "On the way home" }
            repository.updateMember(m.copy(isComingHome = true, speedMph = startSpeed, statusText = startStatus, etaMinutes = (distKm * 1.5).toInt().coerceAtLeast(2)))
            repository.insertLog(ActivityLog(memberId = m.id, memberName = m.name, actionText = "started heading back Home", iconName = "home"))
            _uiEvents.emit("Notified ${m.name} to come Home.")
        }
    }

    fun sendAway(memberId: String, destName: String) {
        viewModelScope.launch {
            val m = familyMembers.value.firstOrNull { it.id == memberId } ?: return@launch
            val angle = Random.nextDouble(0.0, 2 * Math.PI); val dist = Random.nextDouble(0.7, 1.4)
            val newX = homeLng + (dist * Math.cos(angle) * 0.01); val newY = homeLat + (dist * Math.sin(angle) * 0.01)
            val speed = when (m.id) { "eloise" -> 3.2; "isabel" -> 11.5; "louis" -> 68.0; else -> 32.0 }
            repository.updateMember(m.copy(x = newX, y = newY, isComingHome = false, speedMph = speed, statusText = destName, etaMinutes = (dist * 20).toInt().coerceAtLeast(10)))
            repository.insertLog(ActivityLog(memberId = m.id, memberName = m.name, actionText = "went to $destName", iconName = "away"))
            _uiEvents.emit("${m.name} sent to $destName")
        }
    }

    fun instantCheckInAtHome(memberId: String) {
        viewModelScope.launch {
            val m = familyMembers.value.firstOrNull { it.id == memberId } ?: return@launch
            repository.updateMember(m.copy(x = homeLng, y = homeLat, isComingHome = false, speedMph = 0.0, statusText = "At Home", etaMinutes = 0))
            repository.insertLog(ActivityLog(memberId = m.id, memberName = m.name, actionText = "checked in: arrived Home instantly", iconName = "check_in"))
            _uiEvents.emit("${m.name} is now at Home!")
        }
    }

    fun pingMember(memberId: String) {
        viewModelScope.launch {
            val m = familyMembers.value.firstOrNull { it.id == memberId } ?: return@launch
            repository.insertLog(ActivityLog(memberId = m.id, memberName = m.name, actionText = "received check-in request ping", iconName = "check_in"))
            _uiEvents.emit("Sent Status Request Ping to ${m.name}")
        }
    }

    fun addNewMember(name: String, relationType: String, hexColor: String, avatarEmoji: String) {
        viewModelScope.launch {
            val angle = Random.nextDouble(0.0, 2 * Math.PI); val dist = Random.nextDouble(0.6, 1.3)
            val mId = name.lowercase().replace("\\s".toRegex(), "") + "_" + Random.nextInt(100, 999)
            val prefs = getApplication<Application>().getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)
            val cleanName = name.lowercase().trim()
            val savedPhone = prefs.getString("phone_$cleanName", "") ?: ""
            val savedPhoto = prefs.getString("photo_$cleanName", "") ?: ""
            val newMember = FamilyMember(mId, name, hexColor, homeLng + (dist * Math.cos(angle) * 0.01), homeLat + (dist * Math.sin(angle) * 0.01), Random.nextInt(40, 95), false, 4.5, "At $relationType", false, (dist * 20).toInt().coerceAtLeast(10), avatarEmoji, savedPhone, savedPhoto)
            repository.insertFamilyMembers(listOf(newMember))
            repository.insertLog(ActivityLog(memberId = mId, memberName = name, actionText = "added to track list ($relationType)", iconName = "check_in"))
            _uiEvents.emit("$name joined the radar tracking circle!")
        }
    }

    fun updateFamilyMember(updated: FamilyMember) {
        viewModelScope.launch {
            repository.updateMember(updated)
            if (updated.name.isNotBlank()) {
                val prefs = getApplication<Application>().getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)
                val cleanName = updated.name.lowercase().trim()
                prefs.edit().apply {
                    if (updated.phoneNumber.isNotBlank()) putString("phone_$cleanName", updated.phoneNumber)
                    if (updated.photoPath.isNotBlank()) putString("photo_$cleanName", updated.photoPath)
                    apply()
                }
            }
            if (updated.id == "me") {
                myDeviceName.value = updated.name; myDeviceColor.value = updated.avatarColorHex; myDeviceEmoji.value = updated.avatarEmoji
                myDevicePhone.value = updated.phoneNumber; myDevicePhotoPath.value = updated.photoPath; savePreferences()
            }
            repository.insertLog(ActivityLog(memberId = updated.id, memberName = updated.name, actionText = "updated tracker details", iconName = "check_in"))
            _uiEvents.emit("${updated.name}'s tracker details updated!")
        }
    }

    fun deleteFamilyMember(memberId: String) {
        viewModelScope.launch {
            val target = familyMembers.value.firstOrNull { it.id == memberId } ?: return@launch
            repository.deleteMember(target)
            repository.insertLog(ActivityLog(memberId = "system", memberName = "System", actionText = "removed tracker of ${target.name}", iconName = "away"))
            if (selectedMemberId.value == memberId) selectedMemberId.value = null
            _uiEvents.emit("${target.name} removed from radar circle.")
        }
    }

    fun clearLogHistory() { viewModelScope.launch { repository.clearLogs(); _uiEvents.emit("Activity log cleared successfully") } }

    fun triggerSOS() {
        viewModelScope.launch {
            isMySosAlertActive.value = !isMySosAlertActive.value
            val action = if (isMySosAlertActive.value) "🚨 Triggered EMERGENCY SOS ALERT distress beacon!" else "🟢 Emergency SOS distress beacon cleared"
            repository.insertLog(ActivityLog(memberId = "me", memberName = myDeviceName.value, actionText = action, iconName = if (isMySosAlertActive.value) "critical" else "home"))
            _uiEvents.emit(if (isMySosAlertActive.value) "🚨 SOS BEACON SENT! Distress alert active on family channels." else "🟢 SOS distress beacon cleared.")
        }
    }

    fun triggerCheckIn() {
        viewModelScope.launch {
            isMyCheckInTriggered.value = true; myCheckInExpirationTime = System.currentTimeMillis() + 15000
            repository.insertLog(ActivityLog(memberId = "me", memberName = myDeviceName.value, actionText = "📍 checked in safely and shared live coordinates", iconName = "check_in"))
            _uiEvents.emit("📍 Shared safe check-in status with family circle.")
        }
    }

    fun toggleGhostMode(enabled: Boolean) {
        viewModelScope.launch {
            ghostModeExpiryTime.value = if (enabled) System.currentTimeMillis() + 8 * 60 * 60 * 1000L else 0L
            savePreferences()
            val action = if (enabled) "entered Ghost Mode (location paused for 8 hours)" else "exited Ghost Mode (resumed live location sharing)"
            repository.insertLog(ActivityLog(memberId = "me", memberName = myDeviceName.value, actionText = action, iconName = if (enabled) "check_in" else "home"))
            _uiEvents.emit(if (enabled) "Ghost Mode activated. Location sharing paused for 8 hours." else "Ghost Mode deactivated. Resumed live tracking.")
        }
    }

    fun triggerFindMyPhone(memberId: String) = viewModelScope.launch { cloudSyncManager.getGroupData(groupSyncToken.value)?.let { payload ->
        val updatedMembers = payload.members.toMutableMap()
        updatedMembers[memberId]?.let { target ->
            updatedMembers[memberId] = target.copy(statusText = "🚨 ALARM")
            cloudSyncManager.updateGroupData(groupSyncToken.value, payload.copy(lastUpdated = System.currentTimeMillis(), members = updatedMembers))
            _uiEvents.emit("Sent loud alarm command to ${target.name}'s phone.")
        }
    } }

    fun sendEmojiReaction(memberId: String, emoji: String) {
        viewModelScope.launch {
            val m = familyMembers.value.firstOrNull { it.id == memberId } ?: return@launch
            myActiveReaction.value = "$emoji (to ${m.name})"; myReactionExpirationTime = System.currentTimeMillis() + 12000
            repository.insertLog(ActivityLog(memberId = "me", memberName = myDeviceName.value, actionText = "sent reaction '$emoji' to ${m.name}", iconName = "check_in"))
            _uiEvents.emit("Sent reaction $emoji to ${m.name}!")
        }
    }

    fun updateUserLocation(lat: Double, lng: Double, speed: Float, batteryLevel: Int, isCharging: Boolean) {
        viewModelScope.launch {
            val me = familyMembers.value.firstOrNull { it.id == "me" } ?: return@launch
            val xDistKm = (lng - homeLng) * 111.0 * Math.cos(Math.toRadians(homeLat))
            val yDistKm = (lat - homeLat) * 111.0
            val distTotalKm = Math.hypot(xDistKm, yDistKm)
            val isAtHome = distTotalKm < 0.035 // 35 meter tight home geofence

            val targetX = if (isAtHome) homeLng else lng
            val targetY = if (isAtHome) homeLat else lat

            val prefs = getApplication<Application>().getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
            val savedLocationSince = prefs.getLong("my_location_since", 0L)
            val anchorLat = prefs.getFloat("anchor_lat", 0f).toDouble()
            val anchorLng = prefs.getFloat("anchor_lng", 0f).toDouble()

            val distFromAnchorKm = if (anchorLat != 0.0 && anchorLng != 0.0) {
                Math.hypot((targetX - anchorLng) * 111.0 * Math.cos(Math.toRadians(targetY)), (targetY - anchorLat) * 111.0)
            } else 0.0

            val currentSpeedMph = Math.round((speed * 2.23694f) * 10.0) / 10.0
            val hasDeparted = distFromAnchorKm > 0.15 && currentSpeedMph > 2.5

            val resolvedLocationSince = if (savedLocationSince > 0L && !hasDeparted) {
                savedLocationSince
            } else if (me.locationSince > 0L && !hasDeparted) {
                prefs.edit().putLong("my_location_since", me.locationSince).apply()
                me.locationSince
            } else {
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putLong("my_location_since", now)
                    .putFloat("anchor_lat", targetY.toFloat())
                    .putFloat("anchor_lng", targetX.toFloat())
                    .apply()
                now
            }

            repository.updateMember(
                me.copy(
                    x = targetX,
                    y = targetY,
                    batteryPercentage = batteryLevel,
                    isCharging = isCharging,
                    speedMph = if (isAtHome) 0.0 else Math.round((speed * 2.23694f) * 10.0) / 10.0,
                    statusText = if (isAtHome) "At Home (Live GPS)" else "Live GPS tracking (${String.format(java.util.Locale.US, "%.2f", distTotalKm)} km away)",
                    lastActive = System.currentTimeMillis(),
                    locationSince = resolvedLocationSince
                )
            )
        }
    }

    fun setHomeToCurrentLocation() { viewModelScope.launch {
        val me = familyMembers.value.firstOrNull { it.id == "me" }
        if (me == null || (me.y == 0.0 && me.x == 0.0)) { _uiEvents.emit("Waiting for GPS signal — please try again in a moment."); return@launch }
        
        // 1. Lock baseline instantly to provide a responsive, instantaneous user experience
        val initialLat = me.y
        val initialLng = me.x
        homeLat = initialLat
        homeLng = initialLng
        isHomeCalibrated = true
        savePreferences()
        _uiEvents.emit("🏠 Home locked instantly to current GPS baseline! Refining accuracy in background...")
        
        // 2. Spin off background collector loop to refine averaging coordinates dynamically without blocking
        viewModelScope.launch(Dispatchers.Default) {
            val samples = mutableListOf(Pair(initialLat, initialLng))
            repeat(7) {
                delay(1000)
                familyMembers.value.firstOrNull { it.id == "me" }?.let { 
                    if (it.y != 0.0 && it.x != 0.0) {
                        samples.add(Pair(it.y, it.x))
                        
                        // Refine running average baseline in real-time
                        homeLat = samples.map { s -> s.first }.average()
                        homeLng = samples.map { s -> s.second }.average()
                        savePreferences()
                    }
                }
            }
            
            // Log final refined telemetry
            val maxDevM = samples.maxOf { (lat, lng) -> hypot(lat - homeLat, lng - homeLng) * 111000.0 }.toInt()
            val accStr = if (maxDevM < 5) "±${maxDevM}m (excellent)" else if (maxDevM < 15) "±${maxDevM}m (good)" else "±${maxDevM}m"
            
            withContext(Dispatchers.Main) {
                _uiEvents.emit("✨ Telemetry Calibrated: Averaged ${samples.size} GPS readings — refined precision: $accStr")
                repository.insertLog(ActivityLog(
                    memberId = "me",
                    memberName = me.name,
                    actionText = "locked Home to refined averaged GPS fix (${String.format(java.util.Locale.US, "%.6f", homeLat)}, ${String.format(java.util.Locale.US, "%.6f", homeLng)}) — $accStr",
                    iconName = "home"
                ))
            }
        }
    } }

    fun saveCustomHome(lat: Double, lng: Double) { viewModelScope.launch {
        homeLat = lat; homeLng = lng; isHomeCalibrated = true; savePreferences(); _uiEvents.emit("Manual Home saved successfully!")
        repository.insertLog(ActivityLog(memberId = "me", memberName = familyMembers.value.firstOrNull { it.id == "me" }?.name ?: myDeviceName.value, actionText = "updated manual Home landmarks to (${String.format(java.util.Locale.US, "%.5f", lat)}, ${String.format(java.util.Locale.US, "%.5f", lng)})", iconName = "home"))
    } }

    fun triggerManualGpsMockPreset(presetIndex: Int) { viewModelScope.launch {
        if (!isHomeCalibrated) { homeLat = 37.7749; homeLng = -122.4194; isHomeCalibrated = true }
        val scale = 111000.0; val cosLat = Math.cos(Math.toRadians(homeLat))
        var tLat = homeLat; var tLng = homeLng; var spd = 0f; var st = "At Home"
        when (presetIndex) {
            1 -> { tLat = homeLat + (300.0 / scale); spd = 1.4f; st = "Walking (300m North)" }
            2 -> { tLng = homeLng + (1200.0 / (scale * cosLat)); spd = 11.2f; st = "Driving (1.2 km East)" }
            3 -> { tLat = homeLat - (2500.0 / scale); tLng = homeLng - (2500.0 / (scale * cosLat)); spd = 20.1f; st = "Away at Commute (3.5 km Southwest)" }
        }
        updateUserLocation(tLat, tLng, spd, 92, false); _uiEvents.emit("Mock GPS Preset applied: $st")
    } }

    fun toggleCloudSync(e: Boolean, t: String, n: String, c: String, em: String, p: String) {
        myDeviceName.value = n; myDeviceColor.value = c; myDeviceEmoji.value = em; myDevicePhone.value = p
        cloudSyncManager.toggleCloudSync(e, t, myDeviceName, myDeviceColor, myDeviceEmoji, myDevicePhone)
    }

    fun generateNewGroupKey() = cloudSyncManager.generateNewGroupKey()
    fun createGroupWithPin(name: String) = cloudSyncManager.createGroupWithPin(name)
    fun joinGroupWithPin(pin: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) = cloudSyncManager.joinGroupWithPin(pin, onResult)
    fun selectActiveCircle(pin: String) = viewModelScope.launch {
        repository.activateGroup(pin)
        repository.getGroupPinMappingByPin(pin)?.let { mapping ->
            activeGroupPinCode.value = mapping.pinCode
            activeGroupCreatorId.value = mapping.creatorId
            groupSyncToken.value = mapping.groupToken
            isCloudSyncEnabled.value = true
            cloudSyncManager.toggleCloudSync(true, mapping.groupToken, myDeviceName, myDeviceColor, myDeviceEmoji, myDevicePhone)
            savePreferences()
        }
    }
    fun updateActiveGroupSettings(n: String, p: String) = cloudSyncManager.updateActiveGroupSettings(n, p)
    fun kickGroupMember(id: String) = cloudSyncManager.kickGroupMember(id)
    fun deleteGroupPinFromHistory(m: GroupPinMapping) = viewModelScope.launch { repository.deleteGroupPinMapping(m); _uiEvents.emit("PIN ${m.pinCode} removed from history.") }
    fun autoProvisionGroupSync() { viewModelScope.launch {
        cloudStatusText.value = "Auto-Pairing Active..."
        val token = if (groupSyncToken.value.isNotBlank()) cloudSyncManager.convertToValidToken(groupSyncToken.value)
                    else cloudSyncManager.convertToValidToken(userEmail.value).also { cloudSyncManager.updateGroupData(it, CloudGroupPayload(homeLat, homeLng, isHomeCalibrated, System.currentTimeMillis())) }
        groupSyncToken.value = token; isCloudSyncEnabled.value = true
        if (myDeviceName.value in setOf("You", "You (GPS)")) myDeviceName.value = "My Device"
        repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }?.let { repository.updateMember(it.copy(name = myDeviceName.value, avatarColorHex = myDeviceColor.value)) }
        savePreferences(); cloudStatusText.value = "Live Map Connected"; _uiEvents.emit("Map sharing connected successfully!"); cloudSyncManager.startCloudSyncLoop()
    } }

    fun toggleWifeCloudSimulation(enabled: Boolean) {
        simulationEngine.toggleWifeCloudSimulation(enabled)
    }

    private fun startWifeCloudSimulationLoop() {
        simulationEngine.startWifeCloudSimulationLoop()
    }

    data class WeatherInfo(
        val emoji: String,
        val temp: Double,
        val windSpeed: Double,
        val description: String
    )

    val memberWeatherDetailed = MutableStateFlow<Map<String, WeatherInfo>>(emptyMap())
    private val memberDetailedWeatherCache = java.util.concurrent.ConcurrentHashMap<String, WeatherInfo>()

    val memberWeather = MutableStateFlow<Map<String, String>>(emptyMap())
    private val memberWeatherCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lastWeatherCheckCoords = java.util.concurrent.ConcurrentHashMap<String, Pair<Double, Double>>()

    private fun setupWeatherTracking() {
        viewModelScope.launch {
            familyMembers.collect { membersList ->
                if (membersList.isEmpty()) return@collect
                membersList.forEach { m ->
                    if (m.x == 0.0 && m.y == 0.0) return@forEach
                    val last = lastWeatherCheckCoords[m.id]
                    if (last == null || hypot(m.x - last.second, m.y - last.first) > 0.003) {
                        lastWeatherCheckCoords[m.id] = Pair(m.y, m.x)
                        fetchWeatherForCoordinates(m.id, m.y, m.x)
                    }
                }
            }
        }
    }

    fun fetchWeatherForCoordinates(memberId: String, lat: Double, lng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current_weather=true")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    
                    val tempMatcher = java.util.regex.Pattern.compile("\"temperature\"\\s*:\\s*(-?\\d+\\.?\\d*)").matcher(text)
                    val windMatcher = java.util.regex.Pattern.compile("\"windspeed\"\\s*:\\s*(-?\\d+\\.?\\d*)").matcher(text)
                    val codeMatcher = java.util.regex.Pattern.compile("\"weathercode\"\\s*:\\s*(\\d+)").matcher(text)
                    
                    var temp = 15.0
                    var wind = 5.0
                    var code = 0
                    
                    if (tempMatcher.find()) temp = tempMatcher.group(1)!!.toDouble()
                    if (windMatcher.find()) wind = windMatcher.group(1)!!.toDouble()
                    if (codeMatcher.find()) code = codeMatcher.group(1)!!.toInt()
                    
                    val (emoji, desc) = when (code) {
                        0 -> Pair("☀️", "Clear Sky")
                        1, 2, 3 -> Pair("🌤️", "Partly Cloudy")
                        45, 48 -> Pair("🌫️", "Foggy")
                        51, 53, 55, 56, 57 -> Pair("🌦️", "Drizzle")
                        61, 63, 65, 66, 67 -> Pair("🌧️", "Rainy")
                        71, 73, 75, 77 -> Pair("🌨️", "Snowy")
                        80, 81, 82 -> Pair("🌧️", "Showers")
                        95, 96, 99 -> Pair("⛈️", "Thunderstorm")
                        else -> Pair("🌤️", "Partly Cloudy")
                    }
                    
                    memberWeatherCache[memberId] = emoji
                    memberWeather.value = memberWeatherCache.toMap()
                    
                    val weatherInfo = WeatherInfo(emoji, temp, wind, desc)
                    memberDetailedWeatherCache[memberId] = weatherInfo
                    memberWeatherDetailed.value = memberDetailedWeatherCache.toMap()
                }
            } catch (e: Exception) {
                // Ignore background errors
            }
        }
    }

    fun addSafeZone(zone: SafeZone) {
        viewModelScope.launch {
            repository.insertSafeZone(zone)
            repository.insertLog(ActivityLog(memberId = "system", memberName = "System", actionText = "created safe zone: ${zone.name}", iconName = "check_in"))
            _uiEvents.emit("Safe Zone ${zone.name} created successfully.")
        }
    }

    fun removeSafeZone(zone: SafeZone) {
        viewModelScope.launch {
            repository.deleteSafeZone(zone)
            repository.insertLog(ActivityLog(memberId = "system", memberName = "System", actionText = "deleted safe zone: ${zone.name}", iconName = "away"))
            _uiEvents.emit("Safe Zone ${zone.name} deleted.")
        }
    }

    fun resetCircleDigest() {
        viewModelScope.launch {
            isCircleDigestReset.value = true
            savePreferences()
            repository.insertLog(ActivityLog(memberId = "system", memberName = "System", actionText = "cleared circle diagnostics digest charts", iconName = "away"))
            _uiEvents.emit("Weekly Travel Circle Digest reset successfully.")
        }
    }
}
