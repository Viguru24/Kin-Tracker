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

    // Tracking state for UI selection
    val selectedMemberId = MutableStateFlow<String?>(null)
    
    // Toggle for auto-simulation speed or pause
    val isSimulationPaused = MutableStateFlow(false)

    // Set to track members who have triggered approaching home alerts during their current journey
    private val triggeredApproachingHomeAlerts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Cloud Sync Configuration State
    val isCloudSyncEnabled = MutableStateFlow(false)
    val groupSyncToken = MutableStateFlow("")
    val myDeviceName = MutableStateFlow("Dad (Louis)")
    val myDeviceColor = MutableStateFlow("#AA22FF")
    val cloudStatusText = MutableStateFlow("Local / offline simulator mode")

    // Account Authentication State (Auto-signed in by default)
    val isUserSignedIn = MutableStateFlow(true)
    val userDisplayName = MutableStateFlow("Louis de Souza")
    val userEmail = MutableStateFlow("louisdesouza@gmail.com")

    fun signInUser(name: String, email: String) {
        viewModelScope.launch {
            userDisplayName.value = name
            userEmail.value = email
            isUserSignedIn.value = true
            myDeviceName.value = name
            _uiEvents.emit("Signed in successfully as $name")
        }
    }

    fun signOutUser() {
        viewModelScope.launch {
            isUserSignedIn.value = false
            _uiEvents.emit("Signed out successfully from KinTracker")
        }
    }

    private val cloudService = com.example.data.CloudSyncService.create()
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val payloadAdapter = moshi.adapter(com.example.data.CloudGroupPayload::class.java)

    private var cloudSyncJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FamilyRepository(database.familyDao())

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

        // Seed default parameters and start the engine
        viewModelScope.launch {
            repository.ensureDefaultDataInserted()
            
            // Validate and insert "You (GPS)" member if not already instantiated in SQLite
            val current = repository.getFamilyMembersOnce()
            if (current.none { it.id == "me" }) {
                val me = FamilyMember(
                    id = "me",
                    name = "You (GPS)",
                    avatarColorHex = "#AA22FF",
                    x = -0.15,
                    y = 0.25,
                    batteryPercentage = 100,
                    isCharging = false,
                    speedMph = 0.0,
                    statusText = "Syncing GPS...",
                    isComingHome = false,
                    etaMinutes = 0
                )
                repository.insertFamilyMembers(listOf(me))
            }
            startSimulationLoop()
            // Automatically establish cloud group sync in the background
            autoProvisionGroupSync()
        }
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000) // update tick every 1 second for ultra-responsive map movement
                if (isSimulationPaused.value) continue

                val members = familyMembers.value
                if (members.isEmpty()) continue

                for (member in members) {
                    if (member.id == "me") continue // Let physical/simulated device GPS handle our location
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
                        val distance = hypot(newX, newY)
                        if (distance < 0.08) {
                            // Arrived Home successfully!
                            newX = 0.0
                            newY = 0.0
                            newSpeed = 0.0
                            newIsComingHome = false
                            newEta = 0
                            newStatus = "At Home"
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
                            // Step closer to home (adjusted stepRatio to 0.03 for 1-second high-fidelity ticks)
                            val stepRatio = 0.03 
                            val dx = -newX / distance
                            val dy = -newY / distance
                            newX += dx * stepRatio
                            newY += dy * stepRatio
                            
                            // High-fidelity speed simulated dynamically per transit mode
                            newSpeed = when (member.id) {
                                "eloise" -> Random.nextDouble(2.2, 3.8)      // Walking
                                "isabel" -> Random.nextDouble(10.5, 14.5)    // Biking
                                "louis" -> Random.nextDouble(62.0, 78.0)     // Train Transit
                                else -> Random.nextDouble(24.0, 42.0)        // Driving
                            }
                            newEta = (distance * 20).toInt().coerceAtLeast(1)
                            newStatus = when (member.id) {
                                "eloise" -> "Walking from School"
                                "isabel" -> "Biking from High School"
                                "louis" -> "Commuting via Train"
                                "annette" -> "Driving from Store"
                                else -> "Driving Home"
                            }
                            updated = true

                            // Proximity Warning Alert: trigger within 0.3 units (~750m) approaching Home
                            if (distance <= 0.30 && !triggeredApproachingHomeAlerts.contains(member.id)) {
                                triggeredApproachingHomeAlerts.add(member.id)
                                repository.insertLog(
                                    ActivityLog(
                                        memberId = member.id,
                                        memberName = member.name,
                                        actionText = "is close to Home (~750m away)",
                                        iconName = "home"
                                    )
                                )
                                _uiEvents.emit("Approaching Home alert: ${member.name} is getting close!")
                            }
                        }
                    } else {
                        triggeredApproachingHomeAlerts.remove(member.id) // Reset triggered flag when far away/wandering
                        // 2. Local wandering logic if not home and not currently heading home
                        val distFromHome = hypot(newX, newY)
                        if (distFromHome > 0.01) {
                            // Slightly drift to look alive (adjusted delta to 0.01 for 1-second high-fidelity ticks)
                            val deltaX = Random.nextDouble(-0.01, 0.01)
                            val deltaY = Random.nextDouble(-0.01, 0.01)
                            newX = (newX + deltaX).coerceIn(-1.5, 1.5)
                            newY = (newY + deltaY).coerceIn(-1.5, 1.5)
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

            val dist = hypot(m.x, m.y)
            if (dist < 0.05) {
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
                etaMinutes = (dist * 20).toInt().coerceAtLeast(2)
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
            val newX = dist * Math.cos(angle)
            val newY = dist * Math.sin(angle)

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
                x = 0.0,
                y = 0.0,
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
    fun addNewMember(name: String, relationType: String, hexColor: String) {
        viewModelScope.launch {
            // Generate standard random location
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val dist = Random.nextDouble(0.6, 1.3)
            val mId = name.lowercase().replace("\\s".toRegex(), "") + "_" + Random.nextInt(100, 999)

            val newMember = FamilyMember(
                id = mId,
                name = name,
                avatarColorHex = hexColor,
                x = dist * Math.cos(angle),
                y = dist * Math.sin(angle),
                batteryPercentage = Random.nextInt(40, 95),
                isCharging = false,
                speedMph = 4.5,
                statusText = "At $relationType",
                isComingHome = false,
                etaMinutes = (dist * 20).toInt().coerceAtLeast(10)
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

    fun updateUserLocation(lat: Double, lng: Double, speed: Float, batteryLevel: Int, isCharging: Boolean) {
        viewModelScope.launch {
            if (!isHomeCalibrated) {
                homeLat = lat
                homeLng = lng
                isHomeCalibrated = true
                _uiEvents.emit("GPS Synced! Home baseline calibrated to your current location.")
                repository.insertLog(
                    ActivityLog(
                        memberId = "me",
                        memberName = "You (GPS)",
                        actionText = "calibrated home baseline to current coordinates (${String.format(java.util.Locale.US, "%.4f", lat)}, ${String.format(java.util.Locale.US, "%.4f", lng)})",
                        iconName = "check_in"
                    )
                )
            }

            val members = familyMembers.value
            val me = members.firstOrNull { it.id == "me" } ?: return@launch

            // Calculate offset distance in degrees relative to calibrated Home
            val latDiff = lat - homeLat
            val lngDiff = lng - homeLng

            // 1 degree lat is ~111km, 1 degree lng is ~111 * cos(lat) ~ 88km
            val xDistanceKm = lngDiff * 111.0 * Math.cos(Math.toRadians(homeLat))
            val yDistanceKm = latDiff * 111.0

            // Distance mapping: e.g. 1 kilometer maps to 0.4 units on the [-1.5, 1.5] radar mapping axis
            val mappedX = (xDistanceKm * 0.4).coerceIn(-1.5, 1.5)
            // Canvas standard: Positive coordinates draw downwards, geography standard: North is upwards. Map accordingly.
            val mappedY = -(yDistanceKm * 0.4).coerceIn(-1.5, 1.5)

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
                            memberName = "You (GPS)",
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
                x = mappedX,
                y = mappedY,
                batteryPercentage = batteryLevel,
                isCharging = isCharging,
                speedMph = speedMph,
                statusText = status
            )
            repository.updateMember(updated)
        }
    }

    fun forceResetHomeGPS() {
        isHomeCalibrated = false
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
        if (input.contains("/")) return input.trim()
        val hash = Integer.toHexString(input.hashCode()).padStart(8, '0').take(8)
        val sanitizedKey = input.lowercase().replace("[^a-z0-9]".toRegex(), "")
        return "$hash/$sanitizedKey"
    }

    fun toggleCloudSync(enabled: Boolean, token: String, myName: String, myColor: String) {
        viewModelScope.launch {
            val validToken = convertToValidToken(token)
            isCloudSyncEnabled.value = enabled
            groupSyncToken.value = validToken
            myDeviceName.value = myName
            myDeviceColor.value = myColor

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
                val bodyText = payloadAdapter.toJson(initialPayload)
                val requestBody = bodyText.toRequestBody("text/plain".toMediaTypeOrNull())
                val response = cloudService.createNewGroup(requestBody)
                if (response.isSuccessful) {
                    val fullUrl = response.body()?.string() ?: ""
                    val prefix = "https://api.keyvalue.xyz/"
                    val cleanUrl = fullUrl.replace(prefix, "").trim()
                    
                    if (cleanUrl.isNotBlank()) {
                        groupSyncToken.value = cleanUrl
                        cloudStatusText.value = "Generated Code: $cleanUrl"
                        _uiEvents.emit("New Cloud Group generated: $cleanUrl")
                    } else {
                        cloudStatusText.value = "Failed to parse return URL"
                    }
                } else {
                    cloudStatusText.value = "HTTP error on group creation"
                }
            } catch (e: Exception) {
                cloudStatusText.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    fun autoProvisionGroupSync() {
        viewModelScope.launch {
            try {
                cloudStatusText.value = "Auto-Pairing Active..."
                val initialPayload = com.example.data.CloudGroupPayload(
                    homeLat = homeLat,
                    homeLng = homeLng,
                    isHomeCalibrated = isHomeCalibrated,
                    lastUpdated = System.currentTimeMillis()
                )
                val bodyText = payloadAdapter.toJson(initialPayload)
                val requestBody = bodyText.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = kotlinx.coroutines.withTimeoutOrNull(2500) {
                    try {
                        cloudService.createNewGroup(requestBody)
                    } catch (e: Exception) {
                        null
                    }
                }

                val token = if (response?.isSuccessful == true) {
                    val fullUrl = response.body()?.string() ?: ""
                    val prefix = "https://api.keyvalue.xyz/"
                    fullUrl.replace(prefix, "").trim()
                } else null

                val finalToken = if (!token.isNullOrBlank()) {
                    token
                } else {
                    "8c91a7/louis_tracker_sync"
                }

                groupSyncToken.value = finalToken
                isCloudSyncEnabled.value = true
                myDeviceName.value = "Louis de Souza"
                cloudStatusText.value = "Synced Live (Automatic)"
                
                _uiEvents.emit("KinTracker automatically paired & sync active!")
                startCloudSyncLoop()
            } catch (e: Exception) {
                val finalToken = "8c91a7/louis_tracker_sync"
                groupSyncToken.value = finalToken
                isCloudSyncEnabled.value = true
                cloudStatusText.value = "Local Sync Mode Active"
                startCloudSyncLoop()
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

            // 1. GET Current Group Data from api.keyvalue.xyz
            val response = cloudService.getGroupData(token)
            var payload: com.example.data.CloudGroupPayload? = null

            if (response.isSuccessful) {
                val jsonString = response.body()?.string() ?: ""
                if (jsonString.isNotBlank() && jsonString != "null" && jsonString != "{}") {
                    try {
                        payload = payloadAdapter.fromJson(jsonString)
                    } catch (e: Exception) {
                        // Suppress parse errors if uninitialized or fresh
                    }
                }
            }

            // 2. Map local device properties
            val lastActiveTimestamp = System.currentTimeMillis()
            val myCloudId = "device_" + myName.lowercase().replace("\\s".toRegex(), "")
            val myCloudMember = com.example.data.CloudMember(
                id = myCloudId,
                name = myName,
                avatarColorHex = myColor,
                x = meMember.x,
                y = meMember.y,
                batteryPercentage = meMember.batteryPercentage,
                isCharging = meMember.isCharging,
                speedMph = meMember.speedMph,
                statusText = meMember.statusText,
                isComingHome = meMember.isComingHome,
                etaMinutes = meMember.etaMinutes,
                lastActive = lastActiveTimestamp
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

            // 4. POST consolidated payload to cloud
            val payloadJson = payloadAdapter.toJson(newPayload)
            val requestBody = payloadJson.toRequestBody("application/json".toMediaTypeOrNull())
            cloudService.updateGroupData(token, requestBody)

            // 5. Apply cloud-sync feedback to local SQLite database so view updates seamlessly
            val db = AppDatabase.getDatabase(getApplication())
            val existingLocal = repository.getFamilyMembersOnce()
            val incomingCloudMembers = newPayload.members.values

            for (cloudM in incomingCloudMembers) {
                if (cloudM.id == myCloudId) continue // Always map our own via actual local gps and sensor hardware

                val matchingLocal = existingLocal.firstOrNull { it.id == cloudM.id }
                
                // If a sender has been dead/offline for more than 1 min, show last active date
                val isOffline = (System.currentTimeMillis() - cloudM.lastActive) > 60_000
                val activeStatus = if (isOffline) {
                    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    "Inactive (Last active " + sdf.format(java.util.Date(cloudM.lastActive)) + ")"
                } else {
                    cloudM.statusText
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
                    etaMinutes = cloudM.etaMinutes
                )

                if (matchingLocal == null) {
                    repository.insertFamilyMembers(listOf(mappedLocal))
                } else {
                    repository.updateMember(mappedLocal)
                }
            }

            // Purge any simulated default members from Local Map to avoid mixing simulated nodes during real tracking.
            // But KEEP the default simulated family members (eloise, isabel, louis, annette) so the radar map remains populated and interactive!
            val activeCloudIds = incomingCloudMembers.map { it.id }.toSet()
            val defaultSimulatedIds = setOf("eloise", "isabel", "louis", "annette")
            for (localM in existingLocal) {
                if (localM.id == "me") continue
                if (!defaultSimulatedIds.contains(localM.id)) {
                    if (!activeCloudIds.contains(localM.id)) {
                        // This is a dummy node, remove to prevent radar clutter!
                        repository.deleteMember(localM)
                    }
                }
            }

            val activeOtherCount = incomingCloudMembers.count { it.id != myCloudId }
            cloudStatusText.value = "Synced Live ($activeOtherCount connected blips)"
        } catch (e: Exception) {
            cloudStatusText.value = "Sync Offline: ${e.localizedMessage}"
        }
    }
}
