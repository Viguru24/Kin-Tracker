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
    val routeTimeFilter = MutableStateFlow("today") // "today", "7days", "30days"
    private val _locationTrails = MutableStateFlow<Map<String, List<Pair<Double, Double>>>>(emptyMap())
    val locationTrails: StateFlow<Map<String, List<Pair<Double, Double>>>> = _locationTrails

    val isCloudSyncEnabled = MutableStateFlow(true)
    val groupSyncToken = MutableStateFlow("")
    val activeRingingMembers = MutableStateFlow<Set<String>>(emptySet())
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
    val homeRadiusFlow = MutableStateFlow(AppConfig.DEFAULT_HOME_RADIUS_METERS)
    var homeLat: Double get() = homeLatFlow.value; set(value) { homeLatFlow.value = value }
    var homeLng: Double get() = homeLngFlow.value; set(value) { homeLngFlow.value = value }
    var homeRadiusMeters: Double get() = homeRadiusFlow.value; set(value) { homeRadiusFlow.value = value }
    var isHomeCalibrated = true

    val workLatFlow = MutableStateFlow(0.0)
    val workLngFlow = MutableStateFlow(0.0)
    val isWorkCalibratedFlow = MutableStateFlow(false)
    val workRadiusFlow = MutableStateFlow(AppConfig.DEFAULT_WORK_RADIUS_METERS)
    var workLat: Double get() = workLatFlow.value; set(value) { workLatFlow.value = value }
    var workLng: Double get() = workLngFlow.value; set(value) { workLngFlow.value = value }
    var isWorkCalibrated: Boolean get() = isWorkCalibratedFlow.value; set(value) { isWorkCalibratedFlow.value = value }
    var workRadiusMeters: Double get() = workRadiusFlow.value; set(value) { workRadiusFlow.value = value }

    val isDepartureAlertsEnabled = MutableStateFlow(true)

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
            isSimulationModeEnabled, { getMyActiveStatusText(it) }, { savePreferences() },
            { workLat }, { workLng }, { isWorkCalibrated }, { lat, lng -> workLat = lat; workLng = lng; isWorkCalibrated = true; savePreferences() },
            { homeRadiusMeters }, { workRadiusMeters }
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

    fun setRouteTimeFilter(filter: String) {
        routeTimeFilter.value = filter
        viewModelScope.launch {
            refreshLocationTrails()
        }
    }

    private fun getCutoffTimestamp(filter: String): Long {
        val now = System.currentTimeMillis()
        return when (filter) {
            "today" -> {
                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            }
            "7days" -> now - (7L * 24 * 60 * 60 * 1000)
            "30days" -> now - (30L * 24 * 60 * 60 * 1000)
            else -> now - (24L * 60 * 60 * 1000)
        }
    }

    private val roadRouteCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<Double, Double>>>()

    private suspend fun refreshLocationTrails() {
        val membersList = familyMembers.value
        if (membersList.isEmpty()) return
        val cutoff = getCutoffTimestamp(routeTimeFilter.value)
        val loadedTrails = mutableMapOf<String, List<Pair<Double, Double>>>()

        membersList.forEach { m ->
            val cleanKey = m.name.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()
            val dbBreadcrumbs = (repository.getBreadcrumbsForMemberSinceOnce(m.id, cutoff) + repository.getBreadcrumbsForMemberSinceOnce(cleanKey, cutoff))
                .distinctBy { "${it.latitude}_${it.longitude}" }
                .sortedBy { it.timestamp }

            val points = if (m.x != 0.0 && m.y != 0.0) {
                val currentPoint = Pair(m.y, m.x)
                val isAwayFromHome = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(m.y - homeLat, m.x - homeLng) * 111.0 > 0.06)

                if (dbBreadcrumbs.size >= 3) {
                    dbBreadcrumbs.map { Pair(it.latitude, it.longitude) } + currentPoint
                } else if (isAwayFromHome) {
                    val cacheKey = "${m.id}_${homeLat}_${homeLng}_${m.y}_${m.x}"
                    roadRouteCache[cacheKey] ?: run {
                        val route = fetchRoadRoute(Pair(homeLat, homeLng), currentPoint)
                        roadRouteCache[cacheKey] = route
                        route
                    }
                } else {
                    listOf(currentPoint)
                }
            } else {
                dbBreadcrumbs.map { Pair(it.latitude, it.longitude) }
            }

            if (points.isNotEmpty()) {
                loadedTrails[m.id] = points.takeLast(400)
                loadedTrails[cleanKey] = points.takeLast(400)
            }
        }
        _locationTrails.value = loadedTrails
    }

    private fun setupLocationTrails() {
        viewModelScope.launch {
            // Prune breadcrumbs older than 30 days automatically
            repository.pruneOldBreadcrumbs(30)
            refreshLocationTrails()
        }

        viewModelScope.launch {
            combine(familyMembers, routeTimeFilter) { membersList, filter ->
                Pair(membersList, filter)
            }.collect { (membersList, filter) ->
                if (membersList.isEmpty()) return@collect
                val cutoff = getCutoffTimestamp(filter)
                val activeTrails = _locationTrails.value.toMutableMap()
                var hasUpdates = false

                membersList.forEach { m ->
                    if (m.x == 0.0 && m.y == 0.0) return@forEach
                    // Persist throttled breadcrumb to DB
                    repository.recordBreadcrumbThrottled(m.id, m.y, m.x, m.speedMph)
                    
                    val cleanKey = m.name.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()
                    val existingCoords = activeTrails[m.id] ?: emptyList()
                    val newPoint = Pair(m.y, m.x)

                    if (existingCoords.isEmpty() || existingCoords.size < 2) {
                        val isAwayFromHome = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(m.y - homeLat, m.x - homeLng) * 111.0 > 0.06)
                        val trail = if (isAwayFromHome) {
                            val cacheKey = "${m.id}_${homeLat}_${homeLng}_${m.y}_${m.x}"
                            roadRouteCache[cacheKey] ?: run {
                                val route = fetchRoadRoute(Pair(homeLat, homeLng), newPoint)
                                roadRouteCache[cacheKey] = route
                                route
                            }
                        } else {
                            listOf(newPoint)
                        }
                        activeTrails[m.id] = trail.takeLast(400)
                        activeTrails[cleanKey] = trail.takeLast(400)
                        hasUpdates = true
                    } else if (existingCoords.last() != newPoint) {
                        val updated = (existingCoords + newPoint).takeLast(400)
                        activeTrails[m.id] = updated
                        activeTrails[cleanKey] = updated
                        hasUpdates = true
                    }
                }
                if (hasUpdates) {
                    _locationTrails.value = activeTrails
                }
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
                    proximityThresholdMeters = proximityAlertDistanceMeters.value,
                    myDeviceUUID = myDeviceUUID.value,
                    myDeviceName = myDeviceName.value
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

                // Try walking route first, fallback to driving route
                val urls = listOf(
                    "https://router.project-osrm.org/route/v1/walking/${from.second},${from.first};${to.second},${to.first}?overview=full&geometries=geojson",
                    "https://router.project-osrm.org/route/v1/driving/${from.second},${from.first};${to.second},${to.first}?overview=full&geometries=geojson"
                )

                for (urlStr in urls) {
                    try {
                        val url = java.net.URL(urlStr)
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
                            if (coordinatesList.size >= 2) {
                                return@withContext coordinatesList
                            }
                        }
                    } catch (_: Exception) {}
                }
                listOf(from, to)
            } catch (e: Exception) {
                listOf(from, to)
            }
        }
    }

    private data class MonitoredPlace(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val iconName: String,
        val isHome: Boolean = false,
        val isWork: Boolean = false
    )

    private val lastMemberZoneStatus = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val outsideConfirmCount = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val insideConfirmCount = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val lastZoneAlertTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun setupSafeZoneGeofences() {
        viewModelScope.launch {
            combine(
                repository.safeZones,
                familyMembers,
                homeLatFlow,
                homeLngFlow,
                homeRadiusFlow,
                workLatFlow,
                workLngFlow,
                isWorkCalibratedFlow,
                workRadiusFlow
            ) { args: Array<Any> ->
                @Suppress("UNCHECKED_CAST")
                val customZones = args[0] as List<SafeZone>
                @Suppress("UNCHECKED_CAST")
                val members = args[1] as List<FamilyMember>
                val hLat = args[2] as Double
                val hLng = args[3] as Double
                val hRadius = args[4] as Double
                val wLat = args[5] as Double
                val wLng = args[6] as Double
                val isWCal = args[7] as Boolean
                val wRadius = args[8] as Double

                val allPlaces = mutableListOf<MonitoredPlace>()
                if (hLat != 0.0 && hLng != 0.0) {
                    allPlaces.add(MonitoredPlace("place_home", "Home", hLat, hLng, hRadius, "home", isHome = true))
                }
                if (isWCal && wLat != 0.0 && wLng != 0.0) {
                    allPlaces.add(MonitoredPlace("place_work", "Work", wLat, wLng, wRadius, "work", isWork = true))
                }
                customZones.forEach { zone ->
                    if (zone.iconName.lowercase() != "home" && !zone.name.lowercase().contains("home")) {
                        allPlaces.add(MonitoredPlace(zone.id, zone.name, zone.latitude, zone.longitude, zone.radiusMeters, zone.iconName))
                    }
                }
                Pair(allPlaces, members)
            }.collect { (places, members) ->
                if (places.isEmpty() || members.isEmpty()) return@collect
                val now = System.currentTimeMillis()

                members.forEach { member ->
                    if (member.x == 0.0 && member.y == 0.0) return@forEach

                    // Never notify or announce the device owner ("me") about their own arrival/departure
                    val isSelf = member.id == "me" ||
                            member.id == myDeviceUUID.value ||
                            member.name.equals(myDeviceName.value, ignoreCase = true) ||
                            member.name.contains("(You)", ignoreCase = true)
                    if (isSelf) return@forEach

                    places.forEach { place ->
                        val xDist = (member.x - place.longitude) * 111.0 * Math.cos(Math.toRadians(place.latitude))
                        val yDist = (member.y - place.latitude) * 111.0
                        val distMeters = Math.hypot(xDist, yDist) * 1000.0

                        val cleanMemberName = member.name.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "").trim()
                        val key = "${cleanMemberName.lowercase()}_${place.id}"
                        val lastStatus = lastMemberZoneStatus[key]

                        val effectiveRadius = if (place.isHome) maxOf(place.radiusMeters, 160.0) else place.radiusMeters
                        val exitHysteresis = if (place.isHome) 70.0 else AppConfig.EXIT_HYSTERESIS_METERS

                        // 1. INSIDE BOUNDARY CHECK: Within defined place radius (160m for Home)
                        if (distMeters <= effectiveRadius) {
                            outsideConfirmCount[key] = 0
                            val inCount = (insideConfirmCount[key] ?: 0) + 1
                            insideConfirmCount[key] = inCount

                            if (lastStatus == null) {
                                // Initial startup state: member was already inside this zone — no false arrival alert
                                lastMemberZoneStatus[key] = "inside"
                                insideConfirmCount[key] = AppConfig.ARRIVAL_CONFIRMATION_CHECKS
                            } else if (lastStatus == "outside") {
                                // Confirm arrival only after consecutive verified stable readings inside boundary
                                if (inCount >= AppConfig.ARRIVAL_CONFIRMATION_CHECKS) {
                                    lastMemberZoneStatus[key] = "inside"
                                    insideConfirmCount[key] = 0
                                    val lastAlert = lastZoneAlertTime["arr_$key"] ?: 0L
                                    if (now - lastAlert > AppConfig.GEOFENCE_COOLDOWN_MS) {
                                        lastZoneAlertTime["arr_$key"] = now
                                        val placeDisplayName = if (place.isHome) "Home" else place.name
                                        repository.insertLog(
                                            ActivityLog(
                                                memberId = member.id,
                                                memberName = member.name,
                                                actionText = "arrived at $placeDisplayName",
                                                iconName = "check_in"
                                            )
                                        )
                                        _uiEvents.emit("📍 Arrival Notice: $cleanMemberName has arrived at $placeDisplayName!")
                                    }
                                }
                            }
                        }
                        // 2. OUTSIDE / DEPARTURE BOUNDARY CHECK: Beyond radius + buffer (230m for Home)
                        else if (distMeters > (effectiveRadius + exitHysteresis)) {
                            insideConfirmCount[key] = 0
                            if (lastStatus == "inside") {
                                val count = (outsideConfirmCount[key] ?: 0) + 1
                                outsideConfirmCount[key] = count
                                
                                // Intentional departure confirmed ONLY after consecutive verified checks outside 230m buffer
                                val isConfirmedDeparture = count >= AppConfig.DEPARTURE_CONFIRMATION_CHECKS && (member.speedMph >= AppConfig.MIN_EXIT_SPEED_MPH || distMeters > (effectiveRadius + 100.0))
                                if (isConfirmedDeparture) {
                                    lastMemberZoneStatus[key] = "outside"
                                    outsideConfirmCount[key] = 0
                                    
                                    val lastAlert = lastZoneAlertTime["dep_$key"] ?: 0L
                                    if (now - lastAlert > AppConfig.GEOFENCE_COOLDOWN_MS) {
                                        lastZoneAlertTime["dep_$key"] = now
                                        val placeDisplayName = if (place.isHome) "the house" else place.name
                                        val warningMsg = "🚪 Departure Warning: $cleanMemberName has left $placeDisplayName!"
                                        
                                        repository.insertLog(
                                            ActivityLog(
                                                memberId = member.id,
                                                memberName = member.name,
                                                actionText = "left ${place.name} (departed building)",
                                                iconName = "away"
                                            )
                                        )
                                        if (isDepartureAlertsEnabled.value) {
                                            _uiEvents.emit(warningMsg)
                                        }
                                    }
                                }
                            } else if (lastStatus == null) {
                                // Initial startup state: member was already outside
                                lastMemberZoneStatus[key] = "outside"
                                outsideConfirmCount[key] = 0
                            }
                        } else {
                            // In hysteresis buffer zone: retain current state, reset confirmation counts
                            insideConfirmCount[key] = 0
                            outsideConfirmCount[key] = 0
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
            val savedLocationSince = prefs.getLong("my_location_since", 0L)
            if (savedLocationSince == 0L) {
                prefs.edit().putLong("my_location_since", System.currentTimeMillis()).apply()
            }

            val current = repository.getFamilyMembersOnce()
            val contactsPrefs = getApplication<Application>().getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE)
            val deletedMembersPrefs = getApplication<Application>().getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE)
            
            // Guarantee Eloise tombstone is permanently locked in SharedPreferences
            deletedMembersPrefs.edit()
                .putBoolean("deleted_eloise", true)
                .putBoolean("deleted_member_eloise", true)
                .apply()

            // Populate / restore contacts and photos for active members in local database
            for (m in current) {
                val cleanKey = m.name.lowercase()
                    .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "")
                    .trim()

                // If user deleted this member, or if it is Eloise/demo ghost, purge immediately from SQLite!
                if (m.id == "eloise" || cleanKey.contains("eloise") || m.name.contains("eloise", ignoreCase = true) ||
                    deletedMembersPrefs.getBoolean("deleted_${m.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_$cleanKey", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_${m.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_$cleanKey", false)) {
                    repository.deleteMember(m)
                    repository.clearBreadcrumbsForMember(m.id)
                    continue
                }

                val filesDir = getApplication<Application>().filesDir
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

                val resolvedPhoto = when {
                    m.photoPath.isNotBlank() -> m.photoPath
                    contactsPrefs.getString("photo_$cleanKey", "")?.isNotBlank() == true -> contactsPrefs.getString("photo_$cleanKey", "")!!
                    fallbackPhoto.isNotBlank() -> fallbackPhoto
                    else -> ""
                }

                val resolvedPhoneNum = when {
                    m.phoneNumber.isNotBlank() -> m.phoneNumber
                    contactsPrefs.getString("phone_$cleanKey", "")?.isNotBlank() == true -> contactsPrefs.getString("phone_$cleanKey", "")!!
                    fallbackPhone.isNotBlank() -> fallbackPhone
                    else -> ""
                }

                // If Isabel is at Home or was set with inaccurate test offsets, calibrate position
                val isIsabel = m.id == "isabel" || (cleanKey.contains("isabel") && !m.id.startsWith("device_"))
                
                var targetX = m.x
                var targetY = m.y
                var targetStatus = m.statusText

                if (isIsabel && (m.statusText.contains("Dance Class") || m.statusText.contains("At School") || (m.x == 0.0 && m.y == 0.0) || (Math.hypot(m.x - homeLng, m.y - homeLat) * 111.0 > 100.0))) {
                    targetX = homeLng
                    targetY = homeLat
                    targetStatus = "At Home"
                }

                if (resolvedPhoto != m.photoPath || resolvedPhoneNum != m.phoneNumber || targetX != m.x || targetY != m.y) {
                    repository.updateMember(
                        m.copy(
                            photoPath = resolvedPhoto,
                            phoneNumber = resolvedPhoneNum,
                            x = targetX,
                            y = targetY,
                            statusText = targetStatus
                        )
                    )
                }
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
            val cleanKey = name.lowercase().trim()
            val cleanKeyNormalized = cleanKey.replace("[^a-z0-9]".toRegex(), "")
            try {
                val prefs = getApplication<Application>().getSharedPreferences("shopping_deletions", android.content.Context.MODE_PRIVATE)
                prefs.edit().remove(cleanKey).remove(cleanKeyNormalized).apply()
            } catch (e: Exception) {}

            val item = ShoppingItem(name = name.trim(), addedByMemberId = memberId, addedByMemberName = memberName, timestamp = System.currentTimeMillis())
            repository.insertShoppingItem(item)
            cloudSyncManager.unmarkShoppingItemDeletedInCloud(name.trim())
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
            val cleanKey = item.name.lowercase().trim()
            val cleanKeyNormalized = cleanKey.replace("[^a-z0-9]".toRegex(), "")
            try {
                val prefs = getApplication<Application>().getSharedPreferences("shopping_deletions", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong(cleanKey, System.currentTimeMillis())
                    .putLong(cleanKeyNormalized, System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) {}

            // Delete all matching duplicate items from local SQLite DB
            val allItems = repository.getShoppingItemsOnce()
            for (i in allItems) {
                val iNorm = i.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
                if (i.id == item.id || iNorm == cleanKeyNormalized || i.name.equals(item.name, ignoreCase = true)) {
                    repository.deleteShoppingItem(i)
                }
            }

            // Also remove from cloud group payload
            cloudSyncManager.removeShoppingItemFromCloud(item.name)

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
              putBoolean("isDepartureAlertsEnabled", isDepartureAlertsEnabled.value)
              putFloat("homeLat", homeLat.toFloat()); putFloat("homeLng", homeLng.toFloat()); putBoolean("isHomeCalibrated", isHomeCalibrated)
              putFloat("homeRadiusMeters", homeRadiusMeters.toFloat())
              putFloat("workLat", workLat.toFloat()); putFloat("workLng", workLng.toFloat()); putBoolean("isWorkCalibrated", isWorkCalibrated)
              putFloat("workRadiusMeters", workRadiusMeters.toFloat())
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
          isDepartureAlertsEnabled.value = prefs.getBoolean("isDepartureAlertsEnabled", true)
          proximityAlertDistanceMeters.value = prefs.getInt("proximityAlertDistanceMeters", 400)
          homeLat = prefs.getFloat("homeLat", 51.332308f).toDouble(); homeLng = prefs.getFloat("homeLng", -0.117188f).toDouble(); isHomeCalibrated = prefs.getBoolean("isHomeCalibrated", true)
          homeRadiusMeters = prefs.getFloat("homeRadiusMeters", AppConfig.DEFAULT_HOME_RADIUS_METERS.toFloat()).toDouble()
          workLat = prefs.getFloat("workLat", 0.0f).toDouble(); workLng = prefs.getFloat("workLng", 0.0f).toDouble(); isWorkCalibrated = prefs.getBoolean("isWorkCalibrated", false)
          workRadiusMeters = prefs.getFloat("workRadiusMeters", AppConfig.DEFAULT_WORK_RADIUS_METERS.toFloat()).toDouble()
      }

    fun setWorkToCurrentLocation() {
        viewModelScope.launch {
            val me = familyMembers.value.firstOrNull { it.id == "me" }
            if (me != null && me.x != 0.0 && me.y != 0.0) {
                workLat = me.y
                workLng = me.x
                isWorkCalibrated = true
                savePreferences()
                _uiEvents.emit("💼 Work Area set to current GPS: ${String.format(java.util.Locale.US, "%.4f, %.4f", workLat, workLng)}")
            } else {
                _uiEvents.emit("⚠️ GPS lock required to calibrate Work location.")
            }
        }
    }

    fun setWorkLocation(lat: Double, lng: Double, radius: Double = AppConfig.DEFAULT_WORK_RADIUS_METERS) {
        workLat = lat
        workLng = lng
        workRadiusMeters = radius
        isWorkCalibrated = true
        savePreferences()
        viewModelScope.launch {
            _uiEvents.emit("💼 Work Area updated.")
        }
    }

    fun clearWorkLocation() {
        workLat = 0.0
        workLng = 0.0
        isWorkCalibrated = false
        savePreferences()
        viewModelScope.launch {
            _uiEvents.emit("Work Area cleared.")
        }
    }

    fun updateHomeRadius(radiusMeters: Double) {
        homeRadiusMeters = radiusMeters
        savePreferences()
    }

    fun updateWorkRadius(radiusMeters: Double) {
        workRadiusMeters = radiusMeters
        savePreferences()
    }

    fun toggleDepartureAlerts(enabled: Boolean) {
        isDepartureAlertsEnabled.value = enabled
        savePreferences()
        viewModelScope.launch {
            val status = if (enabled) "ENABLED" else "DISABLED"
            _uiEvents.emit("Building Departure Warnings $status")
        }
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
            val allCurrent = repository.getFamilyMembersOnce()
            val target = allCurrent.firstOrNull { it.id == memberId }
                ?: allCurrent.firstOrNull { it.name.contains(memberId, ignoreCase = true) }
            val cleanId = memberId.lowercase().trim()
            val targetName = target?.name ?: memberId

            // 1. Permanently record deletion in SharedPreferences so it can NEVER be resurrected
            val prefs = getApplication<Application>().getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE)
            val cleanName = targetName.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "").trim()
            prefs.edit()
                .putBoolean("deleted_$cleanId", true)
                .putBoolean("deleted_$cleanName", true)
                .putBoolean("deleted_member_$cleanId", true)
                .putBoolean("deleted_member_$cleanName", true)
                .putLong("deleted_time_$cleanId", System.currentTimeMillis())
                .apply()

            // 2. Delete all matching records from local database (by ID, and by clean name)
            for (m in allCurrent) {
                val mClean = m.name.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "").trim()
                if (m.id == memberId || m.id == cleanId || mClean == cleanName || (cleanName.isNotEmpty() && mClean.contains(cleanName))) {
                    repository.deleteMember(m)
                }
            }

            // 3. Remove from cloud group payload
            cloudSyncManager.removeMemberFromCloud(memberId, targetName)

            // 4. Remove location breadcrumbs for this member
            repository.clearBreadcrumbsForMember(memberId)
            if (target != null && target.id != memberId) repository.clearBreadcrumbsForMember(target.id)

            repository.insertLog(ActivityLog(memberId = "system", memberName = "System", actionText = "removed tracker of $targetName", iconName = "away"))
            if (selectedMemberId.value == memberId || selectedMemberId.value == target?.id) selectedMemberId.value = null
            _uiEvents.emit("$targetName removed from radar circle.")
        }
    }

    fun purgeDeletedCacheAndRefresh() {
        viewModelScope.launch {
            val deletedMembersPrefs = getApplication<Application>().getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE)
            val allCurrent = repository.getFamilyMembersOnce()
            for (m in allCurrent) {
                val cleanKey = m.name.lowercase().replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE), "").trim()
                if (deletedMembersPrefs.getBoolean("deleted_${m.id}", false) || 
                    deletedMembersPrefs.getBoolean("deleted_$cleanKey", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_${m.id}", false) ||
                    deletedMembersPrefs.getBoolean("deleted_member_$cleanKey", false) ||
                    cleanKey.contains("eloise")) {
                    repository.deleteMember(m)
                }
            }
            _uiEvents.emit("Radar cache cleaned & refreshed!")
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

    fun triggerFindMyPhone(memberId: String) = viewModelScope.launch {
        val token = groupSyncToken.value
        cloudSyncManager.getGroupData(token)?.let { payload ->
            val updatedMembers = payload.members.toMutableMap()
            val targetEntry = updatedMembers.entries.firstOrNull {
                it.key == memberId || it.value.id == memberId || it.value.name.equals(memberId, ignoreCase = true)
            }
            if (targetEntry != null) {
                val targetKey = targetEntry.key
                val target = targetEntry.value
                updatedMembers[targetKey] = target.copy(statusText = "🚨 ALARM")
                cloudSyncManager.updateGroupData(token, payload.copy(lastUpdated = System.currentTimeMillis(), members = updatedMembers))
                activeRingingMembers.value = activeRingingMembers.value + memberId + target.id + target.name
                _uiEvents.emit("🚨 Ringing ${target.name}'s phone loudly...")

                // Automatically timeout/reset after 14 seconds
                launch {
                    kotlinx.coroutines.delay(14000L)
                    if (activeRingingMembers.value.contains(memberId) || activeRingingMembers.value.contains(target.id)) {
                        stopFindMyPhone(memberId, isAutoTimeout = true)
                    }
                }
            } else {
                // If local member match
                val localTarget = familyMembers.value.firstOrNull { it.id == memberId || it.name.equals(memberId, ignoreCase = true) }
                if (localTarget != null) {
                    activeRingingMembers.value = activeRingingMembers.value + memberId + localTarget.id + localTarget.name
                    _uiEvents.emit("🚨 Ringing ${localTarget.name}'s phone loudly...")
                    launch {
                        kotlinx.coroutines.delay(14000L)
                        activeRingingMembers.value = activeRingingMembers.value - memberId - localTarget.id - localTarget.name
                    }
                }
            }
        }
    }

    fun stopFindMyPhone(memberId: String, isAutoTimeout: Boolean = false) = viewModelScope.launch {
        val token = groupSyncToken.value
        activeRingingMembers.value = activeRingingMembers.value - memberId
        cloudSyncManager.getGroupData(token)?.let { payload ->
            val updatedMembers = payload.members.toMutableMap()
            val targetEntry = updatedMembers.entries.firstOrNull {
                it.key == memberId || it.value.id == memberId || it.value.name.equals(memberId, ignoreCase = true)
            }
            if (targetEntry != null) {
                val targetKey = targetEntry.key
                val target = targetEntry.value
                activeRingingMembers.value = activeRingingMembers.value - target.id - target.name
                if (target.statusText == "🚨 ALARM") {
                    updatedMembers[targetKey] = target.copy(statusText = "Stationary")
                    cloudSyncManager.updateGroupData(token, payload.copy(lastUpdated = System.currentTimeMillis(), members = updatedMembers))
                }
                if (!isAutoTimeout) {
                    _uiEvents.emit("🔕 Stopped ringing ${target.name}'s phone.")
                }
            }
        }
    }

    fun toggleFindMyPhone(memberId: String) {
        val isCurrentlyRinging = activeRingingMembers.value.any { it == memberId || memberId.contains(it) }
        if (isCurrentlyRinging) {
            stopFindMyPhone(memberId)
        } else {
            triggerFindMyPhone(memberId)
        }
    }

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
            val isAtHome = distTotalKm <= 0.12 // 120 meters realistic residential geofence

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
            val isMoving = if (isAtHome) false else (currentSpeedMph >= 1.2 || distFromAnchorKm > 0.15)
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
                            .putFloat("anchor_lat", homeLat.toFloat())
                            .putFloat("anchor_lng", homeLng.toFloat())
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
        val description: String,
        val minTempNight: Double = 0.0,
        val maxTempDay: Double = 0.0
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
                val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current_weather=true&daily=temperature_2m_max,temperature_2m_min&timezone=auto")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    
                    val tempMatcher = java.util.regex.Pattern.compile("\"temperature\"\\s*:\\s*(-?\\d+\\.?\\d*)").matcher(text)
                    val windMatcher = java.util.regex.Pattern.compile("\"windspeed\"\\s*:\\s*(-?\\d+\\.?\\d*)").matcher(text)
                    val codeMatcher = java.util.regex.Pattern.compile("\"weathercode\"\\s*:\\s*(\\d+)").matcher(text)
                    val minTempMatcher = java.util.regex.Pattern.compile("\"temperature_2m_min\"\\s*:\\s*\\[\\s*(-?\\d+\\.?\\d*)").matcher(text)
                    val maxTempMatcher = java.util.regex.Pattern.compile("\"temperature_2m_max\"\\s*:\\s*\\[\\s*(-?\\d+\\.?\\d*)").matcher(text)

                    var temp = 15.0
                    var wind = 5.0
                    var code = 0
                    var minTempNight = 0.0
                    var maxTempDay = 0.0
                    
                    if (tempMatcher.find()) temp = tempMatcher.group(1)!!.toDouble()
                    if (windMatcher.find()) wind = windMatcher.group(1)!!.toDouble()
                    if (codeMatcher.find()) code = codeMatcher.group(1)!!.toInt()
                    if (minTempMatcher.find()) minTempNight = minTempMatcher.group(1)!!.toDouble()
                    if (maxTempMatcher.find()) maxTempDay = maxTempMatcher.group(1)!!.toDouble()

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
                    
                    val weatherInfo = WeatherInfo(emoji, temp, wind, desc, minTempNight, maxTempDay)
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
