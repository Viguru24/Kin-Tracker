package com.example.ui

import android.app.Application
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActivityLog
import com.example.data.AppDatabase
import com.example.data.FamilyMember
import com.example.data.FamilyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

class FamilyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FamilyRepository
    val familyMembers: StateFlow<List<FamilyMember>>
    val activityLogs: StateFlow<List<ActivityLog>>

    private var simulationJob: Job? = null

    // Real-time Event Broadcaster (for visual toast-like feedback when check-in or alerts are triggered)
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    fun triggerUIFeedback(message: String) {
        viewModelScope.launch {
            _uiEvents.emit(message)
        }
    }

    // Tracking state for UI selection
    val selectedMemberId = MutableStateFlow<String?>(null)
    
    // Toggle for auto-simulation speed or pause
    val isSimulationPaused = MutableStateFlow(false)

    // Real-time location trails (past 30 coordinates per member for visual breadcrumb trails on the custom OSM map)
    private val _locationTrails = MutableStateFlow<Map<String, List<Pair<Double, Double>>>>(emptyMap())
    val locationTrails: StateFlow<Map<String, List<Pair<Double, Double>>>> = _locationTrails

    // Set to track members who have triggered approaching home alerts during their current journey
    private val triggeredApproachingHomeAlerts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Cloud Sync Configuration State
    val isCloudSyncEnabled = MutableStateFlow(true)
    val groupSyncToken = MutableStateFlow("")
    val myDeviceName = MutableStateFlow("My Device")
    val myDeviceColor = MutableStateFlow("#AA22FF")
    val myDeviceEmoji = MutableStateFlow("👨") // Added profile picture emoji preference!
    val myDeviceUUID = MutableStateFlow("")
    val cloudStatusText = MutableStateFlow("Local / offline tracking mode")
    val isSimulationModeEnabled = MutableStateFlow(false)
    val isWifeCloudSimulationEnabled = MutableStateFlow(false)
    val hasCompletedOnboarding = MutableStateFlow(false)
    private var simulatedWifeJob: kotlinx.coroutines.Job? = null
    private var simulatedWifeAngle = 0.0
    private val localMockCloudData = java.util.concurrent.ConcurrentHashMap<String, String>()

    // Active states for "me" to broadcast to the cloud
    val isMySosAlertActive = MutableStateFlow(false)
    val myActiveReaction = MutableStateFlow<String?>(null)
    var myReactionExpirationTime = 0L
    val isMyCheckInTriggered = MutableStateFlow(false)
    var myCheckInExpirationTime = 0L
    
    // Tracking map for deduplicating incoming reactions/check-ins from other cloud members
    private val lastProcessedReaction = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lastProcessedCheckIn = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun getMyActiveStatusText(baseStatus: String): String {
        if (isMySosAlertActive.value) {
            return "🚨 EMERGENCY SOS ACTIVE! distress beacon triggered!"
        }
        val reaction = myActiveReaction.value
        if (reaction != null && System.currentTimeMillis() < myReactionExpirationTime) {
            return "💬 Reaction: $reaction"
        }
        if (isMyCheckInTriggered.value && System.currentTimeMillis() < myCheckInExpirationTime) {
            return "📍 Checked in safely at Home base!"
        }
        return baseStatus
    }

    // Account Authentication State (Auto-signed in by default)
    val isUserSignedIn = MutableStateFlow(true)
    val userDisplayName = MutableStateFlow("")
    val userEmail = MutableStateFlow("")

    private fun savePreferences() {
         val prefs = getApplication<Application>().getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
         prefs.edit().apply {
             putBoolean("isUserSignedIn", isUserSignedIn.value)
             putString("userDisplayName", userDisplayName.value)
             putString("userEmail", userEmail.value)
             putString("myDeviceName", myDeviceName.value)
             putString("myDeviceColor", myDeviceColor.value)
             putString("myDeviceEmoji", myDeviceEmoji.value)
             putString("myDeviceUUID", myDeviceUUID.value)
             putString("groupSyncToken", groupSyncToken.value)
             putBoolean("isCloudSyncEnabled", isCloudSyncEnabled.value)
             putBoolean("isSimulationModeEnabled", isSimulationModeEnabled.value)
             putBoolean("isWifeCloudSimulationEnabled", isWifeCloudSimulationEnabled.value)
             putBoolean("hasCompletedOnboarding", hasCompletedOnboarding.value)
             putFloat("homeLat", homeLat.toFloat())
             putFloat("homeLng", homeLng.toFloat())
             putBoolean("isHomeCalibrated", isHomeCalibrated)
             apply()
         }
     }

     private fun loadPreferences() {
         val prefs = getApplication<Application>().getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
         isUserSignedIn.value = prefs.getBoolean("isUserSignedIn", true)
         userDisplayName.value = prefs.getString("userDisplayName", "") ?: ""
         userEmail.value = prefs.getString("userEmail", "") ?: ""
         myDeviceName.value = prefs.getString("myDeviceName", "My Device") ?: "My Device"
         myDeviceColor.value = prefs.getString("myDeviceColor", "#AA22FF") ?: "#AA22FF"
         myDeviceEmoji.value = prefs.getString("myDeviceEmoji", "👨") ?: "👨"
         
         var dUuid = prefs.getString("myDeviceUUID", "") ?: ""
         if (dUuid.isBlank()) {
             dUuid = java.util.UUID.randomUUID().toString().substring(0, 6)
             prefs.edit().putString("myDeviceUUID", dUuid).apply()
         }
         myDeviceUUID.value = dUuid

         val savedToken = prefs.getString("groupSyncToken", "") ?: ""
         if (savedToken.isBlank()) {
             val derived = convertToValidToken(userEmail.value)
             groupSyncToken.value = derived
             prefs.edit().putString("groupSyncToken", derived).apply()
         } else {
             groupSyncToken.value = convertToValidToken(savedToken)
         }
         isCloudSyncEnabled.value = prefs.getBoolean("isCloudSyncEnabled", true)
         isSimulationModeEnabled.value = prefs.getBoolean("isSimulationModeEnabled", false)
         isWifeCloudSimulationEnabled.value = prefs.getBoolean("isWifeCloudSimulationEnabled", false)
         // If a real sync token was already saved, treat as onboarded (skip wizard for existing users)
         val alreadyOnboarded = prefs.getBoolean("hasCompletedOnboarding", false)
         val hasToken = prefs.getString("groupSyncToken", "")?.isNotBlank() == true
         hasCompletedOnboarding.value = alreadyOnboarded || hasToken
         homeLat = prefs.getFloat("homeLat", 51.332308f).toDouble()
         homeLng = prefs.getFloat("homeLng", -0.117188f).toDouble()
         isHomeCalibrated = prefs.getBoolean("isHomeCalibrated", true)
     }

    fun toggleSimulationMode(enabled: Boolean) {
        viewModelScope.launch {
            isSimulationModeEnabled.value = enabled
            savePreferences()

            if (enabled) {
                repository.ensureDefaultDataInserted(homeLat, homeLng)
                _uiEvents.emit("Demo mock members activated.")
            } else {
                // Production mode: Purge all default simulated mock members from the SQLite database
                val current = repository.getFamilyMembersOnce()
                val defaultSimulatedIds = setOf("eloise", "isabel", "louis", "annette")
                for (member in current) {
                    if (defaultSimulatedIds.contains(member.id)) {
                        repository.deleteMember(member)
                    }
                }
                _uiEvents.emit("Demo assets deactivated. Live production mode active!")
            }
        }
    }

    fun signInUser(name: String, email: String) {
        viewModelScope.launch {
            userDisplayName.value = name
            userEmail.value = email
            isUserSignedIn.value = true
            myDeviceName.value = name
            val current = repository.getFamilyMembersOnce()
            val me = current.firstOrNull { it.id == "me" }
            if (me != null) {
                repository.updateMember(me.copy(name = name))
            }
            savePreferences()
            _uiEvents.emit("Signed in successfully as $name")
        }
    }

    fun signOutUser() {
        viewModelScope.launch {
            isUserSignedIn.value = false
            savePreferences()
            _uiEvents.emit("Signed out successfully from KinTracker")
        }
    }

    fun completeOnboarding() {
        hasCompletedOnboarding.value = true
        savePreferences()
    }

    private val cloudService = com.example.data.CloudSyncService.create()
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val payloadAdapter = moshi.adapter(com.example.data.CloudGroupPayload::class.java)

    private var cloudSyncJob: Job? = null

    // GPS CALIBRATION ENGINE & COORDINATES TRANSLATOR
    val homeLatFlow = kotlinx.coroutines.flow.MutableStateFlow(51.332308)
    val homeLngFlow = kotlinx.coroutines.flow.MutableStateFlow(-0.117188)

    var homeLat: Double
        get() = homeLatFlow.value
        set(value) { homeLatFlow.value = value }

    var homeLng: Double
        get() = homeLngFlow.value
        set(value) { homeLngFlow.value = value }

    var isHomeCalibrated = true

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FamilyRepository(database.familyDao())

        // Load persisted preferences first
        loadPreferences()

        // Cache flow states for high-frequency consumption
        familyMembers = repository.familyMembers
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        activityLogs = repository.activityLogs
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Keep track of locations updates real-time to render visual pathways (last 30 points)
        viewModelScope.launch {
            // Seed visual trails dynamically using current home coordinates to showcase active routes immediately on start!
            val seedTrails = mutableMapOf<String, List<Pair<Double, Double>>>()
            seedTrails["isabel"] = listOf(
                Pair(homeLat, homeLng),
                Pair(homeLat + 0.0006, homeLng + 0.0008),
                Pair(homeLat + 0.0012, homeLng + 0.0016),
                Pair(homeLat + 0.0018, homeLng + 0.0024),
                Pair(homeLat + 0.0024, homeLng + 0.0032),
                Pair(homeLat + 0.003, homeLng + 0.004)
            )
            seedTrails["annette"] = listOf(
                Pair(homeLat, homeLng),
                Pair(homeLat + 0.001, homeLng - 0.0006),
                Pair(homeLat + 0.002, homeLng - 0.0012),
                Pair(homeLat + 0.003, homeLng - 0.0018),
                Pair(homeLat + 0.004, homeLng - 0.0024),
                Pair(homeLat + 0.005, homeLng - 0.003)
            )
            seedTrails["eloise"] = listOf(
                Pair(homeLat, homeLng),
                Pair(homeLat - 0.001, homeLng - 0.001),
                Pair(homeLat - 0.002, homeLng - 0.002),
                Pair(homeLat - 0.003, homeLng - 0.003),
                Pair(homeLat - 0.004, homeLng - 0.005)
            )
            _locationTrails.value = seedTrails

            familyMembers.collect { membersList ->
                if (membersList.isEmpty()) return@collect
                val currentTrails = _locationTrails.value.toMutableMap()
                var updated = false
                membersList.forEach { m ->
                    // Exclude standard reset coordinates if they are uninitialized values
                    if (m.x == 0.0 && m.y == 0.0) return@forEach
                    
                    val coords = currentTrails[m.id] ?: emptyList()
                    val newPoint = Pair(m.y, m.x) // Pair(Lat, Lng) matches (y, x)
                    if (coords.isEmpty() || coords.last() != newPoint) {
                        currentTrails[m.id] = (coords + newPoint).takeLast(30)
                        updated = true
                    }
                }
                if (updated) {
                    _locationTrails.value = currentTrails
                }
            }
        }

        // Seed default parameters and start the engine
        viewModelScope.launch {
            if (isSimulationModeEnabled.value) {
                repository.ensureDefaultDataInserted(homeLat, homeLng)
            } else {
                // Production mode: Purge all default simulated mock members from the SQLite database
                val current = repository.getFamilyMembersOnce()
                val defaultSimulatedIds = setOf("eloise", "isabel", "louis", "annette")
                for (member in current) {
                    if (defaultSimulatedIds.contains(member.id)) {
                        repository.deleteMember(member)
                    }
                }
            }
            
            // Validate and insert the localized self member if not already instantiated in SQLite
            val current = repository.getFamilyMembersOnce()
            val existingMe = current.firstOrNull { it.id == "me" }
            if (existingMe == null) {
                val me = FamilyMember(
                    id = "me",
                    name = myDeviceName.value,
                    avatarColorHex = myDeviceColor.value,
                    x = homeLng,
                    y = homeLat,
                    batteryPercentage = 100,
                    isCharging = false,
                    speedMph = 0.0,
                    statusText = "Syncing GPS...",
                    isComingHome = false,
                    etaMinutes = 0,
                    avatarEmoji = myDeviceEmoji.value
                )
                repository.insertFamilyMembers(listOf(me))
            } else {
                // Sync SQLite name and color values with the persisted SharedPreferences options
                repository.updateMember(existingMe.copy(
                    name = myDeviceName.value,
                    avatarColorHex = myDeviceColor.value,
                    avatarEmoji = myDeviceEmoji.value
                ))
            }
            
            startSimulationLoop()
            // Automatically establish cloud group sync in the background
            if (isCloudSyncEnabled.value) {
                if (groupSyncToken.value.isBlank()) {
                    autoProvisionGroupSync()
                } else {
                    startCloudSyncLoop()
                    if (isWifeCloudSimulationEnabled.value) {
                        startWifeCloudSimulationLoop()
                    }
                }
            }
        }
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000) // update tick every 1 second for ultra-responsive map movement
                if (isSimulationPaused.value || !isSimulationModeEnabled.value) continue

                val members = familyMembers.value
                if (members.isEmpty()) continue

                for (member in members) {
                    if (member.id == "me") continue // Let physical/simulated device GPS handle our location
                    if (member.id.startsWith("device_")) continue // Never simulate real cloud devices
                    var updated = false
                    var newX = member.x
                    var newY = member.y
                    var newBattery = member.batteryPercentage
                    var newCharging = member.isCharging
                    var newSpeed = member.speedMph
                    var newStatus = member.statusText
                    var newIsComingHome = member.isComingHome
                    var newEta = member.etaMinutes

                    // 1. Simulate coming home physics (linear interpolation with snapping)
                    if (newIsComingHome) {
                        val latDiff = newY - homeLat
                        val lngDiff = newX - homeLng
                        val distanceInDegrees = hypot(lngDiff, latDiff)
                        
                        if (distanceInDegrees < 0.0005) { // within ~50 meters
                            // Arrived Home successfully!
                            newX = homeLng
                            newY = homeLat
                            newSpeed = 0.0
                            newIsComingHome = false
                            newEta = 0
                            newStatus = "At Home (Live GPS)"
                            updated = true

                            // log arrival
                            repository.insertLog(
                                ActivityLog(
                                    memberId = member.id,
                                    memberName = member.name,
                                    actionText = "arrived Home safely",
                                    iconName = "home"
                                )
                            )
                            _uiEvents.emit("${member.name} has arrived Home!")
                            triggeredApproachingHomeAlerts.remove(member.id) // Clear triggered flag on arrival
                        } else {
                            // Step closer to home (moving about 10-15 meters per tick)
                            val stepRatio = 0.0001
                            val dx = -lngDiff / distanceInDegrees
                            val dy = -latDiff / distanceInDegrees
                            newX += dx * stepRatio
                            newY += dy * stepRatio
                            
                            // High-fidelity speed simulated dynamically per transit mode
                            newSpeed = when (member.id) {
                                "eloise" -> Random.nextDouble(2.2, 3.8)      // Walking
                                "isabel" -> Random.nextDouble(10.5, 14.5)    // Biking
                                "louis" -> Random.nextDouble(62.0, 78.0)     // Train Transit
                                else -> Random.nextDouble(24.0, 42.0)        // Driving
                            }
                            // Roughly calculate distance in km
                            val distanceKm = distanceInDegrees * 111.0
                            newEta = (distanceKm * 1.5).toInt().coerceAtLeast(1)
                            newStatus = when (member.id) {
                                "eloise" -> "Walking from School"
                                "isabel" -> "Biking from High School"
                                "louis" -> "Commuting via Train"
                                "annette" -> "Driving from Store"
                                else -> "Driving Home"
                            }
                            updated = true

                            // Proximity Warning Alert: trigger within 400m
                            if (distanceKm <= 0.40 && !triggeredApproachingHomeAlerts.contains(member.id)) {
                                triggeredApproachingHomeAlerts.add(member.id)
                                repository.insertLog(
                                    ActivityLog(
                                        memberId = member.id,
                                        memberName = member.name,
                                        actionText = "is close to Home (~400m away)",
                                        iconName = "home"
                                    )
                                )
                                _uiEvents.emit("Approaching Home alert: ${member.name} is getting close!")
                            }
                        }
                    } else {
                        triggeredApproachingHomeAlerts.remove(member.id) // Reset triggered flag when far away/wandering
                        // 2. Local wandering logic if not home and not currently heading home
                        val latDiff = newY - homeLat
                        val lngDiff = newX - homeLng
                        val distanceInDegrees = hypot(lngDiff, latDiff)
                        val distanceKm = distanceInDegrees * 111.0
                        
                        if (distanceKm > 0.05) { // more than 50m away from home
                            // Slightly drift to look alive in degrees
                            val deltaX = Random.nextDouble(-0.0001, 0.0001)
                            val deltaY = Random.nextDouble(-0.0001, 0.0001)
                            newX = (newX + deltaX).coerceIn(homeLng - 0.08, homeLng + 0.08)
                            newY = (newY + deltaY).coerceIn(homeLat - 0.08, homeLat + 0.08)
                            newSpeed = (member.speedMph + Random.nextDouble(-0.8, 0.8)).coerceIn(1.0, 8.0)
                            updated = true
                        } else {
                            // Safe at home
                            if (newSpeed > 0.0) {
                                newSpeed = 0.0
                                updated = true
                            }
                        }
                    }

                    // 3. Simulated smart battery engine
                    if (newCharging) {
                        newBattery += 2
                        if (newBattery >= 100) {
                            newBattery = 100
                            newCharging = false
                            repository.insertLog(
                                ActivityLog(
                                    memberId = member.id,
                                    memberName = member.name,
                                    actionText = "fully charged (100%)",
                                    iconName = "battery"
                                )
                            )
                        }
                        updated = true
                    } else {
                        // Slowly deplete battery (adjusted threshold to 0.125 for 1-second high-frequency ticks)
                        if (Random.nextDouble() < 0.125) { 
                            newBattery -= 1
                            if (newBattery <= 15 && member.batteryPercentage > 15) {
                                // Raise a critical battery alert!
                                repository.insertLog(
                                    ActivityLog(
                                        memberId = member.id,
                                        memberName = member.name,
                                        actionText = "battery low alert (${newBattery}%)",
                                        iconName = "critical"
                                    )
                                )
                                _uiEvents.emit("Low battery warning for ${member.name}!")
                            }
                            if (newBattery <= 5) {
                                newBattery = 5
                                // Plug in out of emergency
                                newCharging = true
                            }
                            updated = true
                        }
                    }

                    // Write changes back to persistent Room storage
                    if (updated) {
                        repository.updateMember(
                            member.copy(
                                x = newX,
                                y = newY,
                                batteryPercentage = newBattery,
                                isCharging = newCharging,
                                speedMph = Math.round(newSpeed * 10.0) / 10.0,
                                statusText = newStatus,
                                isComingHome = newIsComingHome,
                                etaMinutes = newEta
                            )
                        )
                    }
                }
            }
        }
    }

    // Interactive Action #1: Order Heading Home (Initiate GPS Journey)
    fun orderHeadingHome(memberId: String) {
        viewModelScope.launch {
            val members = familyMembers.value
            val m = members.firstOrNull { it.id == memberId } ?: return@launch
            if (m.isComingHome) return@launch

            val distanceInDegrees = hypot(m.x - homeLng, m.y - homeLat)
            val distKm = distanceInDegrees * 111.0
            if (distKm < 0.05) {
                _uiEvents.emit("${m.name} is already at Home!")
                return@launch
            }

            val startSpeed = when (m.id) {
                "eloise" -> 3.0      // Walking
                "isabel" -> 12.0     // Biking
                "louis" -> 70.0      // Train
                else -> 35.0         // Driving
            }
            val startStatusText = when (m.id) {
                "eloise" -> "Walking from School"
                "isabel" -> "Biking from High School"
                "louis" -> "Commuting via Train"
                else -> "On the way home"
            }
            val updated = m.copy(
                isComingHome = true,
                speedMph = startSpeed,
                statusText = startStatusText,
                etaMinutes = (distKm * 1.5).toInt().coerceAtLeast(2)
            )
            repository.updateMember(updated)
            repository.insertLog(
                ActivityLog(
                    memberId = m.id,
                    memberName = m.name,
                    actionText = "started heading back Home",
                    iconName = "home"
                )
            )
            _uiEvents.emit("Notified ${m.name} to come Home.")
        }
    }

    // Interactive Action #2: Send out on location (Away simulation)
    fun sendAway(memberId: String, destName: String) {
        viewModelScope.launch {
            val members = familyMembers.value
            val m = members.firstOrNull { it.id == memberId } ?: return@launch

            // Teleport outside home radius
            // Distribute at random quadrants
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val dist = Random.nextDouble(0.7, 1.4)
            val newX = homeLng + (dist * Math.cos(angle) * 0.01)
            val newY = homeLat + (dist * Math.sin(angle) * 0.01)

            val transitSpeed = when (m.id) {
                "eloise" -> 3.2      // Walking
                "isabel" -> 11.5     // Biking
                "louis" -> 68.0      // Train
                else -> 32.0         // Driving
            }
            val updated = m.copy(
                x = newX,
                y = newY,
                isComingHome = false,
                speedMph = transitSpeed,
                statusText = destName,
                etaMinutes = (dist * 20).toInt().coerceAtLeast(10)
            )
            repository.updateMember(updated)
            repository.insertLog(
                ActivityLog(
                    memberId = m.id,
                    memberName = m.name,
                    actionText = "went to $destName",
                    iconName = "away"
                )
            )
            _uiEvents.emit("${m.name} sent to $destName")
        }
    }

    // Interactive Action #3: Instantly teleport home (Simulate Check-in arrival)
    fun instantCheckInAtHome(memberId: String) {
        viewModelScope.launch {
            val members = familyMembers.value
            val m = members.firstOrNull { it.id == memberId } ?: return@launch

            val updated = m.copy(
                x = homeLng,
                y = homeLat,
                isComingHome = false,
                speedMph = 0.0,
                statusText = "At Home",
                etaMinutes = 0
            )
            repository.updateMember(updated)
            repository.insertLog(
                ActivityLog(
                    memberId = m.id,
                    memberName = m.name,
                    actionText = "checked in: arrived Home instantly",
                    iconName = "check_in"
                )
            )
            _uiEvents.emit("${m.name} is now at Home!")
        }
    }

    // Interactive Action #4: Ping / Check status (Shake Alert & notify log)
    fun pingMember(memberId: String) {
        viewModelScope.launch {
            val members = familyMembers.value
            val m = members.firstOrNull { it.id == memberId } ?: return@launch

            repository.insertLog(
                ActivityLog(
                    memberId = m.id,
                    memberName = m.name,
                    actionText = "received check-in request ping",
                    iconName = "check_in"
                )
            )
            _uiEvents.emit("Sent Status Request Ping to ${m.name}")
        }
    }

    // Interactive Action #5: Dynamic Member Addition
    fun addNewMember(name: String, relationType: String, hexColor: String, avatarEmoji: String) {
        viewModelScope.launch {
            // Generate standard random location
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val dist = Random.nextDouble(0.6, 1.3)
            val mId = name.lowercase().replace("\\s".toRegex(), "") + "_" + Random.nextInt(100, 999)

            val newMember = FamilyMember(
                id = mId,
                name = name,
                avatarColorHex = hexColor,
                x = homeLng + (dist * Math.cos(angle) * 0.01),
                y = homeLat + (dist * Math.sin(angle) * 0.01),
                batteryPercentage = Random.nextInt(40, 95),
                isCharging = false,
                speedMph = 4.5,
                statusText = "At $relationType",
                isComingHome = false,
                etaMinutes = (dist * 20).toInt().coerceAtLeast(10),
                avatarEmoji = avatarEmoji
            )

            val current = familyMembers.value.toMutableList()
            current.add(newMember)
            
            // Insert in DB
            repository.insertFamilyMembers(listOf(newMember))
            
            repository.insertLog(
                ActivityLog(
                    memberId = mId,
                    memberName = name,
                    actionText = "added to track list ($relationType)",
                    iconName = "check_in"
                )
            )
            _uiEvents.emit("$name joined the radar tracking circle!")
        }
    }

    // Interactive Action #5B: Dynamic Member Editing
    fun updateFamilyMember(updated: FamilyMember) {
        viewModelScope.launch {
            repository.updateMember(updated)
            repository.insertLog(
                ActivityLog(
                    memberId = updated.id,
                    memberName = updated.name,
                    actionText = "updated tracker details",
                    iconName = "check_in"
                )
            )
            _uiEvents.emit("${updated.name}'s tracker details updated!")
        }
    }

    // Interactive Action #5C: Dynamic Member Deletion
    fun deleteFamilyMember(memberId: String) {
        viewModelScope.launch {
            val members = familyMembers.value
            val target = members.firstOrNull { it.id == memberId } ?: return@launch
            repository.deleteMember(target)
            repository.insertLog(
                ActivityLog(
                    memberId = "system",
                    memberName = "System",
                    actionText = "removed tracker of ${target.name}",
                    iconName = "away"
                )
            )
            if (selectedMemberId.value == memberId) {
                selectedMemberId.value = null
            }
            _uiEvents.emit("${target.name} removed from radar circle.")
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiEvents.emit("Activity log cleared successfully")
        }
    }

    fun triggerSOS() {
        viewModelScope.launch {
            val name = myDeviceName.value
            isMySosAlertActive.value = !isMySosAlertActive.value // Toggle SOS alert state
            if (isMySosAlertActive.value) {
                repository.insertLog(
                    ActivityLog(
                        memberId = "me",
                        memberName = name,
                        actionText = "🚨 Triggered EMERGENCY SOS ALERT distress beacon!",
                        iconName = "critical"
                    )
                )
                _uiEvents.emit("🚨 SOS BEACON SENT! Distress alert active on family channels.")
            } else {
                repository.insertLog(
                    ActivityLog(
                        memberId = "me",
                        memberName = name,
                        actionText = "🟢 Emergency SOS distress beacon cleared",
                        iconName = "home"
                    )
                )
                _uiEvents.emit("🟢 SOS distress beacon cleared.")
            }
        }
    }

    fun triggerCheckIn() {
        viewModelScope.launch {
            val name = myDeviceName.value
            isMyCheckInTriggered.value = true
            myCheckInExpirationTime = System.currentTimeMillis() + 15000 // Expose for 15 seconds
            
            repository.insertLog(
                ActivityLog(
                    memberId = "me",
                    memberName = name,
                    actionText = "📍 checked in safely and shared live coordinates",
                    iconName = "check_in"
                )
            )
            _uiEvents.emit("📍 Shared safe check-in status with family circle.")
        }
    }

    fun sendEmojiReaction(memberId: String, emoji: String) {
        viewModelScope.launch {
            val members = familyMembers.value
            val m = members.firstOrNull { it.id == memberId } ?: return@launch
            val name = myDeviceName.value
            
            myActiveReaction.value = "$emoji (to ${m.name})"
            myReactionExpirationTime = System.currentTimeMillis() + 12000 // Expose for 12 seconds
            
            repository.insertLog(
                ActivityLog(
                    memberId = "me",
                    memberName = name,
                    actionText = "sent reaction '$emoji' to ${m.name}",
                    iconName = "check_in"
                )
            )
            _uiEvents.emit("Sent reaction $emoji to ${m.name}!")
        }
    }

    fun updateUserLocation(lat: Double, lng: Double, speed: Float, batteryLevel: Int, isCharging: Boolean) {
        viewModelScope.launch {
            val members = familyMembers.value
            val me = members.firstOrNull { it.id == "me" } ?: return@launch

            // Calculate offset distance in degrees relative to calibrated Home
            val latDiff = lat - homeLat
            val lngDiff = lng - homeLng

            // 1 degree lat is ~111km, 1 degree lng is ~111 * cos(lat) ~ 88km
            val xDistanceKm = lngDiff * 111.0 * Math.cos(Math.toRadians(homeLat))
            val yDistanceKm = latDiff * 111.0

            // Speed in MPH
            val speedMph = Math.round((speed * 2.23694f) * 10.0) / 10.0
            val distanceTotalKm = Math.hypot(xDistanceKm, yDistanceKm)
            val isAtHome = distanceTotalKm < 0.05 // 50m radius safe zone at Home

            if (isAtHome) {
                triggeredApproachingHomeAlerts.remove("me")
            } else if (distanceTotalKm <= 0.40) { // dentro de 400 metros de casa
                if (!triggeredApproachingHomeAlerts.contains("me") && distanceTotalKm > 0.08) {
                    triggeredApproachingHomeAlerts.add("me")
                    repository.insertLog(
                        ActivityLog(
                            memberId = "me",
                            memberName = me.name,
                            actionText = "is close to Home (~${String.format(java.util.Locale.US, "%.0f", distanceTotalKm * 1000)}m away)",
                            iconName = "home"
                        )
                    )
                    _uiEvents.emit("Approaching Home alert: You are getting close!")
                }
            } else {
                triggeredApproachingHomeAlerts.remove("me")
            }

            val status = if (isAtHome) {
                "At Home (Live GPS)"
            } else {
                "Live GPS tracking (${String.format(java.util.Locale.US, "%.2f", distanceTotalKm)} km away)"
            }

            val updated = me.copy(
                x = lng,
                y = lat,
                batteryPercentage = batteryLevel,
                isCharging = isCharging,
                speedMph = speedMph,
                statusText = status
            )
            repository.updateMember(updated)
        }
    }

    fun forceResetHomeGPS() {
        setHomeToCurrentLocation()
    }

    fun setHomeToCurrentLocation() {
        viewModelScope.launch {
            val me = familyMembers.value.firstOrNull { it.id == "me" }
            if (me == null || (me.y == 0.0 && me.x == 0.0)) {
                _uiEvents.emit("Waiting for GPS signal — please try again in a moment.")
                return@launch
            }

            // Use the current position as a starting baseline immediately
            // Then collect up to 8 readings via the live location stream over ~8 seconds
            // and average them for a much more accurate home fix.
            _uiEvents.emit("📍 Locking on… collecting GPS readings for accuracy…")

            val samples = mutableListOf<Pair<Double, Double>>()
            // Seed with current known position
            samples.add(Pair(me.y, me.x))

            // Collect additional samples from the live updateUserLocation flow
            // We wait up to 8 seconds, sampling at ~1s intervals via the GPS listener
            repeat(7) {
                delay(1100)
                val latest = familyMembers.value.firstOrNull { it.id == "me" }
                if (latest != null && latest.y != 0.0 && latest.x != 0.0) {
                    samples.add(Pair(latest.y, latest.x))
                }
            }

            if (samples.isEmpty()) {
                _uiEvents.emit("Could not get GPS fix — please try again outdoors.")
                return@launch
            }

            // Average all collected samples to reduce GPS scatter
            val avgLat = samples.map { it.first }.average()
            val avgLng = samples.map { it.second }.average()

            // Estimate accuracy: max deviation from the averaged centre in metres
            val maxDevM = samples.maxOf { (lat, lng) ->
                kotlin.math.hypot(lat - avgLat, lng - avgLng) * 111000.0
            }.toInt()

            homeLat = avgLat
            homeLng = avgLng
            isHomeCalibrated = true
            savePreferences()

            val accuracyStr = if (maxDevM < 5) "±${maxDevM}m (excellent)" else if (maxDevM < 15) "±${maxDevM}m (good)" else "±${maxDevM}m"
            _uiEvents.emit("🏠 Home locked! Averaged ${samples.size} GPS readings — accuracy $accuracyStr")
            repository.insertLog(
                ActivityLog(
                    memberId = "me",
                    memberName = me.name,
                    actionText = "locked Home to averaged GPS fix (${String.format(java.util.Locale.US, "%.6f", homeLat)}, ${String.format(java.util.Locale.US, "%.6f", homeLng)}) — $accuracyStr",
                    iconName = "home"
                )
            )
        }
    }

    fun saveCustomHome(lat: Double, lng: Double) {
        viewModelScope.launch {
            homeLat = lat
            homeLng = lng
            isHomeCalibrated = true
            savePreferences()
            _uiEvents.emit("Manual Home saved successfully!")
            val me = familyMembers.value.firstOrNull { it.id == "me" }
            repository.insertLog(
                ActivityLog(
                    memberId = "me",
                    memberName = me?.name ?: myDeviceName.value,
                    actionText = "updated manual Home landmarks to (${String.format(java.util.Locale.US, "%.5f", lat)}, ${String.format(java.util.Locale.US, "%.5f", lng)})",
                    iconName = "home"
                )
            )
        }
    }

    // MANUAL GPS MOCK CONTROLLER FOR REPLICATING COORDINATES IN EMULATORS
    fun triggerManualGpsMockPreset(presetIndex: Int) {
        viewModelScope.launch {
            if (!isHomeCalibrated) {
                homeLat = 37.7749
                homeLng = -122.4194
                isHomeCalibrated = true
            }
            
            val scale = 111000.0 // approx meters per degree lat
            val cosLat = Math.cos(Math.toRadians(homeLat))
            
            var targetLat = homeLat
            var targetLng = homeLng
            var speed = 0f
            var status = "At Home"
            
            when (presetIndex) {
                0 -> { // Safe At Home
                    targetLat = homeLat
                    targetLng = homeLng
                    speed = 0f
                    status = "At Home"
                }
                1 -> { // 300 meters North
                    targetLat = homeLat + (300.0 / scale)
                    targetLng = homeLng
                    speed = 1.4f // ~3 mph
                    status = "Walking (300m North)"
                }
                2 -> { // 1.2 kilometers East
                    targetLat = homeLat
                    targetLng = homeLng + (1200.0 / (scale * cosLat))
                    speed = 11.2f // ~25 mph
                    status = "Driving (1.2 km East)"
                }
                3 -> { // 3.5 kilometers Southwest
                    targetLat = homeLat - (2500.0 / scale)
                    targetLng = homeLng - (2500.0 / (scale * cosLat))
                    speed = 20.1f // ~45 mph
                    status = "Away at Commute (3.5 km Southwest)"
                }
            }
            
            updateUserLocation(targetLat, targetLng, speed, 92, false)
            _uiEvents.emit("Mock GPS Preset applied: $status")
        }
    }

    // CLOUD SYNC LOGIC ENGINE
    fun convertToValidToken(input: String): String {
        val cleaned = input.replace("/", "_").trim()
        if (cleaned.contains("_")) return cleaned
        val hash = Integer.toHexString(input.hashCode()).padStart(8, '0').take(8)
        val sanitizedKey = input.lowercase().replace("[^a-z0-9]".toRegex(), "")
        return "${hash}_${sanitizedKey}"
    }

    fun toggleCloudSync(enabled: Boolean, token: String, myName: String, myColor: String, myEmoji: String) {
        viewModelScope.launch {
            val validToken = convertToValidToken(token)
            isCloudSyncEnabled.value = enabled
            groupSyncToken.value = validToken
            myDeviceName.value = myName
            myDeviceColor.value = myColor
            myDeviceEmoji.value = myEmoji

            val current = repository.getFamilyMembersOnce()
            val me = current.firstOrNull { it.id == "me" }
            if (me != null) {
                repository.updateMember(me.copy(name = myName, avatarColorHex = myColor, avatarEmoji = myEmoji))
            }
            savePreferences()

            if (enabled) {
                // Keep simulation active so family members continue moving on the radar map!
                isSimulationPaused.value = false
                cloudStatusText.value = "Configuring Cloud..."
                _uiEvents.emit("Sync Activated! Hooking up to $validToken...")
                startCloudSyncLoop()
            } else {
                cloudStatusText.value = "Local / offline simulator mode"
                isSimulationPaused.value = false
                cloudSyncJob?.cancel()
                _uiEvents.emit("Cloud Sync Deactivated.")
            }
        }
    }

    fun generateNewGroupKey() {
        viewModelScope.launch {
            try {
                cloudStatusText.value = "Generating on Server..."
                val initialPayload = com.example.data.CloudGroupPayload(
                    homeLat = homeLat,
                    homeLng = homeLng,
                    isHomeCalibrated = isHomeCalibrated,
                    lastUpdated = System.currentTimeMillis()
                )
                val randomKey = java.util.UUID.randomUUID().toString().substring(0, 6)
                val cleanUrl = "${randomKey}_louis_synced"

                val bodyText = payloadAdapter.toJson(initialPayload)
                val requestBody = bodyText.toRequestBody("application/json".toMediaTypeOrNull())
                
                val response = cloudService.updateGroupData(cleanUrl, requestBody)
                if (response.isSuccessful) {
                    groupSyncToken.value = cleanUrl
                    savePreferences()
                    cloudStatusText.value = "Generated Code: $cleanUrl"
                    _uiEvents.emit("New Cloud Group generated: $cleanUrl")
                } else {
                    val localToken = "${randomKey}_louis_synced"
                    groupSyncToken.value = localToken
                    savePreferences()
                    cloudStatusText.value = "Generated Code: $localToken"
                    _uiEvents.emit("KinTracker paired using local fallback channel!")
                }
            } catch (e: Exception) {
                val localToken = "${java.util.UUID.randomUUID().toString().substring(0, 6)}_louis_synced"
                groupSyncToken.value = localToken
                savePreferences()
                cloudStatusText.value = "Synced Live (Active Offline Mode)"
                _uiEvents.emit("KinTracker paired using local fallback channel!")
            }
        }
    }

    fun autoProvisionGroupSync() {
        viewModelScope.launch {
            try {
                cloudStatusText.value = "Auto-Pairing Active..."
                
                val existingToken = groupSyncToken.value
                val tokenToUse = if (existingToken.isNotBlank()) {
                    convertToValidToken(existingToken)
                } else {
                    val fallbackToken = convertToValidToken(userEmail.value)
                    val initialPayload = com.example.data.CloudGroupPayload(
                        homeLat = homeLat,
                        homeLng = homeLng,
                        isHomeCalibrated = isHomeCalibrated,
                        lastUpdated = System.currentTimeMillis()
                    )
                    val bodyText = payloadAdapter.toJson(initialPayload)
                    val requestBody = bodyText.toRequestBody("application/json".toMediaTypeOrNull())

                    val response = kotlinx.coroutines.withTimeoutOrNull(2500) {
                        try {
                            cloudService.updateGroupData(fallbackToken, requestBody)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    fallbackToken
                }

                groupSyncToken.value = tokenToUse
                isCloudSyncEnabled.value = true
                
                if (myDeviceName.value == "You" || myDeviceName.value == "You (GPS)") {
                    myDeviceName.value = "My Device"
                }
                
                val current = repository.getFamilyMembersOnce()
                val me = current.firstOrNull { it.id == "me" }
                if (me != null) {
                    repository.updateMember(me.copy(name = myDeviceName.value, avatarColorHex = myDeviceColor.value))
                }
                savePreferences()
                cloudStatusText.value = "Live Map Connected"
                
                _uiEvents.emit("Map sharing connected successfully!")
                startCloudSyncLoop()
            } catch (e: Exception) {
                if (groupSyncToken.value.isBlank()) {
                    groupSyncToken.value = java.util.UUID.randomUUID().toString().substring(0, 12) + "_kt_sync"
                }
                isCloudSyncEnabled.value = true
                cloudStatusText.value = "Local Sync Mode Active"
                savePreferences()
                startCloudSyncLoop()
            }
        }
    }

    fun toggleWifeCloudSimulation(enabled: Boolean) {
        viewModelScope.launch {
            isWifeCloudSimulationEnabled.value = enabled
            savePreferences()
            if (enabled) {
                _uiEvents.emit("Partner simulation active!")
                startWifeCloudSimulationLoop()
            } else {
                simulatedWifeJob?.cancel()
                _uiEvents.emit("Partner simulation deactivated.")
                
                // Clean up simulated wife device from cloud payload
                val token = groupSyncToken.value
                if (token.isNotBlank()) {
                    try {
                        val response = cloudService.getGroupData(token)
                        if (response.isSuccessful) {
                            val jsonString = response.body()?.string() ?: ""
                            if (jsonString.isNotBlank() && jsonString != "null" && jsonString != "{}") {
                                val payload = payloadAdapter.fromJson(jsonString)
                                if (payload != null) {
                                    val updatedMembers = payload.members.toMutableMap()
                                    updatedMembers.remove("device_annette_mock_simulated")
                                    val updatedPayload = payload.copy(
                                        lastUpdated = System.currentTimeMillis(),
                                        members = updatedMembers
                                    )
                                    val payloadJson = payloadAdapter.toJson(updatedPayload)
                                    val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
                                    cloudService.updateGroupData(token, requestBody)
                                    
                                    localMockCloudData[token] = payloadJson
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Suppress network errors
                    }
                }
            }
        }
    }

    private fun startWifeCloudSimulationLoop() {
        simulatedWifeJob?.cancel()
        if (!isWifeCloudSimulationEnabled.value || !isCloudSyncEnabled.value) return
        
        simulatedWifeJob = viewModelScope.launch {
            while (isActive) {
                val token = groupSyncToken.value
                if (token.isNotBlank()) {
                    try {
                        simulatedWifeAngle += 0.05
                        val scale = 111000.0
                        val cosLat = Math.cos(Math.toRadians(homeLat))
                        
                        // Circle around home coordinates
                        val simulatedLat = homeLat + (1200.0 * Math.sin(simulatedWifeAngle) / scale)
                        val simulatedLng = homeLng + (1200.0 * Math.cos(simulatedWifeAngle) / (scale * cosLat))
                        
                        var payload: com.example.data.CloudGroupPayload? = null
                        try {
                            val response = cloudService.getGroupData(token)
                            if (response.isSuccessful) {
                                val jsonString = response.body()?.string() ?: ""
                                if (jsonString.isNotBlank() && jsonString != "null" && jsonString != "{}") {
                                    payload = payloadAdapter.fromJson(jsonString)
                                }
                            }
                        } catch (e: Exception) {
                            val mockJson = localMockCloudData[token]
                            if (mockJson != null && mockJson.isNotBlank()) {
                                payload = payloadAdapter.fromJson(mockJson)
                            }
                        }
                        
                        val p = payload ?: com.example.data.CloudGroupPayload(
                            homeLat = homeLat,
                            homeLng = homeLng,
                            isHomeCalibrated = isHomeCalibrated,
                            lastUpdated = System.currentTimeMillis(),
                            members = emptyMap()
                        )
                        
                        val wifeCloudId = "device_annette_mock_simulated"
                        val battery = (85 - (simulatedWifeAngle * 2).toInt() % 30).coerceIn(10, 100)
                        val isCharging = battery < 70 && (System.currentTimeMillis() / 60000) % 2 == 0L
                        
                        val wifeCloudMember = com.example.data.CloudMember(
                            id = wifeCloudId,
                            name = "Partner (Simulated Phone)",
                            avatarColorHex = "#EC407A", // Pink
                            x = simulatedLng,
                            y = simulatedLat,
                            batteryPercentage = battery,
                            isCharging = isCharging,
                            speedMph = 24.5,
                            statusText = "Driving back home",
                            isComingHome = true,
                            etaMinutes = 7,
                            lastActive = System.currentTimeMillis(),
                            avatarEmoji = "👩"
                        )
                        
                        val updatedMembers = p.members.toMutableMap()
                        updatedMembers[wifeCloudId] = wifeCloudMember
                        
                        val updatedPayload = p.copy(
                            lastUpdated = System.currentTimeMillis(),
                            members = updatedMembers
                        )
                        
                        val payloadJson = payloadAdapter.toJson(updatedPayload)
                        try {
                            val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
                            cloudService.updateGroupData(token, requestBody)
                        } catch (e: Exception) {
                            // Offline fallback
                        }
                        localMockCloudData[token] = payloadJson
                    } catch (e: java.util.ConcurrentModificationException) {
                        // ignore
                    } catch (e: Exception) {
                        // ignore general network
                    }
                }
                delay(8000) // update every 8 seconds
            }
        }
    }

    private fun startCloudSyncLoop() {
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch {
            while (isActive) {
                if (isCloudSyncEnabled.value && groupSyncToken.value.isNotBlank()) {
                    performCloudSyncTick()
                }
                delay(5000) // Sync frequency of 5 seconds
            }
        }
    }

    private suspend fun performCloudSyncTick() {
        val token = groupSyncToken.value
        val myName = myDeviceName.value
        val myColor = myDeviceColor.value
        val meMember = familyMembers.value.firstOrNull { it.id == "me" } ?: return

        try {
            cloudStatusText.value = "Syncing with Cloud..."

            // 1. GET Current Group Data from cloud or memory-based fallback
            var payload: com.example.data.CloudGroupPayload? = null
            var networkSuccess = false

            try {
                val response = cloudService.getGroupData(token)
                if (response.isSuccessful) {
                    val jsonString = response.body()?.string() ?: ""
                    if (jsonString.isNotBlank() && jsonString != "null" && jsonString != "{}") {
                        try {
                            payload = payloadAdapter.fromJson(jsonString)
                        } catch (e: Exception) {
                            // Suppress json parsing error on corrupted payload
                        }
                    }
                    networkSuccess = true
                } else if (response.code() == 404) {
                    payload = null
                    networkSuccess = true
                }
            } catch (e: Exception) {
                // Network down or certificate/host translation failure. Fetch from in-memory backup cache
                val mockJson = localMockCloudData[token]
                if (mockJson != null && mockJson.isNotBlank()) {
                    try {
                        payload = payloadAdapter.fromJson(mockJson)
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
            }

            // 2. Map local device properties
            val lastActiveTimestamp = System.currentTimeMillis()
            val myCloudId = "device_" + myName.lowercase().replace("\\s".toRegex(), "") + "_" + myDeviceUUID.value
            val myCloudMember = com.example.data.CloudMember(
                id = myCloudId,
                name = myName,
                avatarColorHex = myColor,
                x = meMember.x,
                y = meMember.y,
                batteryPercentage = meMember.batteryPercentage,
                isCharging = meMember.isCharging,
                speedMph = meMember.speedMph,
                statusText = getMyActiveStatusText(meMember.statusText),
                isComingHome = meMember.isComingHome,
                etaMinutes = meMember.etaMinutes,
                lastActive = lastActiveTimestamp,
                avatarEmoji = meMember.avatarEmoji
            )

            // 3. Compile updated consolidated payload
            val newPayload = if (payload != null) {
                // If cloud has calibrated Home baseline and we don't, absorb matching coordination center!
                if (!isHomeCalibrated && payload.isHomeCalibrated) {
                    homeLat = payload.homeLat
                    homeLng = payload.homeLng
                    isHomeCalibrated = true
                    _uiEvents.emit("Synced common Home coordinates from cloud group!")
                }

                val updatedMembers = payload.members.toMutableMap()
                updatedMembers[myCloudId] = myCloudMember

                payload.copy(
                    homeLat = if (payload.isHomeCalibrated) payload.homeLat else homeLat,
                    homeLng = if (payload.isHomeCalibrated) payload.homeLng else homeLng,
                    isHomeCalibrated = payload.isHomeCalibrated || isHomeCalibrated,
                    lastUpdated = lastActiveTimestamp,
                    members = updatedMembers
                )
            } else {
                com.example.data.CloudGroupPayload(
                    homeLat = homeLat,
                    homeLng = homeLng,
                    isHomeCalibrated = isHomeCalibrated,
                    lastUpdated = lastActiveTimestamp,
                    members = mapOf(myCloudId to myCloudMember)
                )
            }

            // 4. PUT consolidated payload to cloud or memory-based backup cache
            val payloadJson = payloadAdapter.toJson(newPayload)
            var putSuccessful = false

            try {
                val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
                val putResponse = cloudService.updateGroupData(token, requestBody)
                if (putResponse.isSuccessful) {
                    putSuccessful = true
                }
            } catch (e: Exception) {
                // Network write failed
            }

            // Always update our local memory mock cache with newest data as well so offline simulation works flawlessly
            localMockCloudData[token] = payloadJson

            // 5. Apply cloud-sync feedback to local SQLite database so view updates seamlessly
            val db = AppDatabase.getDatabase(getApplication())
            val existingLocal = repository.getFamilyMembersOnce()
            val incomingCloudMembers = newPayload.members.values
            val defaultSimulatedIds = setOf("eloise", "isabel", "louis", "annette")

            for (cloudM in incomingCloudMembers) {
                if (cloudM.id == myCloudId) continue // Always map our own via actual local gps and sensor hardware

                // Automatically clean up duplicate custom placeholder nodes if her real live device joins!
                val matchingByName = existingLocal.firstOrNull {
                    it.id != "me" &&
                    !it.id.startsWith("device_") &&
                    !defaultSimulatedIds.contains(it.id) &&
                    it.name.trim().equals(cloudM.name.trim(), ignoreCase = true)
                }
                if (matchingByName != null) {
                    repository.deleteMember(matchingByName)
                }

                val matchingLocal = existingLocal.firstOrNull { it.id == cloudM.id }
                
                // If a sender has been dead/offline for more than 1 min, show last active date
                val isOffline = (System.currentTimeMillis() - cloudM.lastActive) > 60_000
                val activeStatus = if (isOffline) {
                    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    "Inactive (Last active " + sdf.format(java.util.Date(cloudM.lastActive)) + ")"
                } else {
                    cloudM.statusText
                }

                // Process reactions, SOS, and check-ins from other members in real-time!
                if (!isOffline && matchingLocal != null) {
                    val previousStatus = matchingLocal.statusText
                    val currentStatus = cloudM.statusText
                    if (previousStatus != currentStatus) {
                        if (currentStatus.contains("🚨 EMERGENCY SOS ACTIVE")) {
                            // Insert an urgent SOS activity log locally
                            repository.insertLog(
                                ActivityLog(
                                    memberId = cloudM.id,
                                    memberName = cloudM.name,
                                    actionText = "🚨 Triggered EMERGENCY SOS ALERT distress beacon!",
                                    iconName = "critical"
                                )
                            )
                            _uiEvents.emit("🚨 SOS ALERT: ${cloudM.name} triggered SOS panic button!")
                        } else if (currentStatus.startsWith("💬 Reaction: ")) {
                            val reactionText = currentStatus.substringAfter("Reaction: ")
                            // Only trigger overlay/notification if it's a new unique reaction string
                            if (lastProcessedReaction[cloudM.id] != currentStatus) {
                                lastProcessedReaction[cloudM.id] = currentStatus
                                // Insert a reaction activity log locally
                                repository.insertLog(
                                    ActivityLog(
                                        memberId = cloudM.id,
                                        memberName = cloudM.name,
                                        actionText = "sent reaction: $reactionText",
                                        iconName = "check_in"
                                    )
                                )
                                _uiEvents.emit("${cloudM.name} sent reaction: $reactionText")
                            }
                        } else if (currentStatus.contains("📍 Checked in safely")) {
                            if (lastProcessedCheckIn[cloudM.id] != currentStatus) {
                                lastProcessedCheckIn[cloudM.id] = currentStatus
                                // Insert a check-in activity log locally
                                repository.insertLog(
                                    ActivityLog(
                                        memberId = cloudM.id,
                                        memberName = cloudM.name,
                                        actionText = "📍 checked in safely at Home base",
                                        iconName = "check_in"
                                    )
                                )
                                _uiEvents.emit("📍 ${cloudM.name} checked in safely at Home!")
                            }
                        }
                    }
                }

                val mappedLocal = FamilyMember(
                    id = cloudM.id,
                    name = cloudM.name,
                    avatarColorHex = cloudM.avatarColorHex,
                    x = cloudM.x,
                    y = cloudM.y,
                    batteryPercentage = cloudM.batteryPercentage,
                    isCharging = cloudM.isCharging,
                    speedMph = cloudM.speedMph,
                    statusText = activeStatus,
                    isComingHome = cloudM.isComingHome,
                    etaMinutes = cloudM.etaMinutes,
                    avatarEmoji = cloudM.avatarEmoji
                )

                if (matchingLocal == null) {
                    repository.insertFamilyMembers(listOf(mappedLocal))
                } else {
                    repository.updateMember(mappedLocal)
                }
            }

            // Purge any simulated default members from Local Map to avoid mixing simulated nodes during real tracking.
            val activeCloudIds = incomingCloudMembers.map { it.id }.toSet()
            for (localM in existingLocal) {
                if (localM.id == "me") continue
                if (!isSimulationModeEnabled.value && defaultSimulatedIds.contains(localM.id)) {
                    repository.deleteMember(localM)
                    continue
                }
                // Only clean up former device trackers that are no longer part of this cluster, never custom manually added members
                if (localM.id.startsWith("device_")) {
                    if (!activeCloudIds.contains(localM.id)) {
                        // This is a retired cloud device, remove to prevent radar clutter!
                        repository.deleteMember(localM)
                    }
                }
            }

            val activeOtherCount = incomingCloudMembers.count { it.id != myCloudId }
            if (networkSuccess && putSuccessful) {
                cloudStatusText.value = "Synced Live ($activeOtherCount connected blips)"
            } else {
                cloudStatusText.value = "Synced Live (Active Offline Mode, $activeOtherCount blips)"
            }
        } catch (e: Exception) {
            val isNetworkIssue = e is java.net.UnknownHostException || 
                                 e is java.net.ConnectException || 
                                 e is java.net.SocketTimeoutException || 
                                 e is java.io.IOException ||
                                 e.message?.contains("Unable to resolve host", ignoreCase = true) == true
            if (isNetworkIssue) {
                cloudStatusText.value = "Synced Live (Active Offline Mode)"
            } else {
                cloudStatusText.value = "Sync Offline: ${e.localizedMessage}"
            }
        }
    }
}
