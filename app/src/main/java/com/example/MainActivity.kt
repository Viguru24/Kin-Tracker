package com.example

import android.os.Bundle
import android.os.Build
import com.example.ui.OnboardingScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import java.io.File
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.example.data.FamilyMember
import com.example.data.ShoppingItem
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.basicMarquee



class MainActivity : ComponentActivity() {

    companion object {
        val globalSpokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    }

    private val viewModel: FamilyViewModel by viewModels()
    private var locationManager: LocationManager? = null
    private var textToSpeech: TextToSpeech? = null


    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateUserPosition(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        }
    }

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationUpdates()
        }
    }

    private fun updateUserPosition(location: Location) {
        var batteryPct = 85
        var isCharging = false
        try {
            val batteryStatusIntent = registerReceiver(
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
        } catch (e: Exception) {
            // Safe fallback values
        }
                         
        viewModel.updateUserLocation(
            lat = location.latitude,
            lng = location.longitude,
            speed = location.speed,
            batteryLevel = batteryPct,
            isCharging = isCharging
        )
    }

    private fun checkAndRequestLocationPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        val finePermission = PackageManager.PERMISSION_GRANTED == 
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = PackageManager.PERMISSION_GRANTED == 
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        val micPermission = PackageManager.PERMISSION_GRANTED ==
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (!finePermission && !coarsePermission) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!micPermission) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPermission = PackageManager.PERMISSION_GRANTED ==
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (!notifPermission) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (finePermission || coarsePermission) {
            startLocationUpdates()
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startLocationUpdates() {
        try {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (!hasFine && !hasCoarse) {
                return
            }

            // Check and request background location permission on Android Q+ (10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasBackground = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasBackground) {
                    requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }

            checkBatteryOptimization()

            if (viewModel.isLocationPaused.value) {
                stopLocationTracking()
                return
            }

            val isGpsEnabled = try {
                if (hasFine) {
                    locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
            val isNetworkEnabled = try {
                if (hasFine || hasCoarse) {
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
            
            // 1. Fetch best historical last-known locations from both sources instantly during startup
            var bestLastLocation: Location? = null
            if (hasFine && isGpsEnabled) {
                try {
                    locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { loc ->
                        if (bestLastLocation == null || loc.time > bestLastLocation!!.time) {
                            bestLastLocation = loc
                        }
                    }
                } catch (e: Exception) { /* Handled safely */ }
            }
            if ((hasFine || hasCoarse) && isNetworkEnabled) {
                try {
                    locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { loc ->
                        if (bestLastLocation == null || loc.time > bestLastLocation!!.time) {
                            bestLastLocation = loc
                        }
                    }
                } catch (e: Exception) { /* Handled safely */ }
            }
            bestLastLocation?.let { updateUserPosition(it) }

            // 2. Register for ultra-low latency updates (1 second update cycles, 0 meters delta threshold)
            if (hasFine && isGpsEnabled) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0.0f,
                        locationListener,
                        android.os.Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    // Safe fallback
                }
            }
            if ((hasFine || hasCoarse) && isNetworkEnabled) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000L,
                        0.0f,
                        locationListener,
                        android.os.Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    // Safe fallback
                }
            }

            // 3. Start high-reliability Foreground Service for active background location tracking
            try {
                val serviceIntent = Intent(this, com.example.data.BackgroundLocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Safe fallback
            }
        } catch (e: Exception) {
            // Catch any security or unsupported provider/illegal argument errors safely
        }
    }

    private fun stopLocationTracking() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {}
        try {
            val serviceIntent = Intent(this, com.example.data.BackgroundLocationService::class.java).apply {
                action = "ACTION_STOP"
            }
            startService(serviceIntent)
        } catch (e: Exception) {}
    }

    private fun resumeLocationTracking() {
        if (!viewModel.isLocationPaused.value) {
            startLocationUpdates()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.isLocationPaused.value) {
            checkAndRequestLocationPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isLocationPaused.value) return
        try {
            val serviceIntent = Intent(this, com.example.data.BackgroundLocationService::class.java).apply {
                action = "ACTION_FOREGROUND"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isLocationPaused.value) return
        try {
            val serviceIntent = Intent(this, com.example.data.BackgroundLocationService::class.java).apply {
                action = "ACTION_BACKGROUND"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun onDestroy() {
        try {
            com.example.data.VoiceAnnouncementManager.stop()
        } catch (e: Exception) {}
        super.onDestroy()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun checkBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
    }

    private val shouldOpenNotificationHistoryFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_notification_history", false)) {
            shouldOpenNotificationHistoryFlow.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("open_notification_history", false) == true) {
            shouldOpenNotificationHistoryFlow.value = true
        }
        
        // Safely configure OSMDroid prior to UI rendering to prevent storage write permission crashes
        try {
            Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = packageName
            val cacheDir = File(filesDir, "osmdroid")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            Configuration.getInstance().osmdroidTileCache = cacheDir
            Configuration.getInstance().osmdroidBasePath = cacheDir
            // Keep viewed map tiles on the phone (persistent storage, large cache, 60-day expiry) so they are not re-downloaded
            Configuration.getInstance().tileFileSystemCacheMaxBytes = 2000L * 1024 * 1024
            Configuration.getInstance().tileFileSystemCacheTrimBytes = 1700L * 1024 * 1024
            Configuration.getInstance().expirationOverrideDuration = 60L * 24 * 60 * 60 * 1000
        } catch (e: Exception) {
            // Safe fallback
        }

        enableEdgeToEdge()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            com.example.data.VoiceAnnouncementManager.initTts(applicationContext)
        } catch (e: Exception) {}

        lifecycleScope.launch {
            com.example.data.HeartbeatManager.sendDailyHeartbeatIfDue(applicationContext)
        }

        lifecycleScope.launch {
            viewModel.isLocationPaused.collect { isPaused ->
                if (isPaused) {
                    stopLocationTracking()
                } else {
                    resumeLocationTracking()
                }
            }
        }

        setContent {
            MyApplicationTheme {
                val hasOnboarded by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = CosmicBlack,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    if (!hasOnboarded) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onComplete = { /* state update triggers recompose automatically */ }
                        )
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            textToSpeech = textToSpeech,
                            shouldOpenNotificationHistoryFlow = shouldOpenNotificationHistoryFlow,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: FamilyViewModel,
    textToSpeech: TextToSpeech?,
    shouldOpenNotificationHistoryFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false),
    modifier: Modifier = Modifier
) {
    val members by viewModel.familyMembers.collectAsStateWithLifecycle()
    val locationTrails by viewModel.locationTrails.collectAsStateWithLifecycle()
    val routeTimeFilter by viewModel.routeTimeFilter.collectAsStateWithLifecycle()
    val logs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val shoppingItems by viewModel.shoppingItems.collectAsStateWithLifecycle()
    val isPaused by viewModel.isSimulationPaused.collectAsStateWithLifecycle()

    val selectedMemberId by viewModel.selectedMemberId.collectAsStateWithLifecycle()
    val homeLat by viewModel.homeLatFlow.collectAsStateWithLifecycle()
    val homeLng by viewModel.homeLngFlow.collectAsStateWithLifecycle()
    val homeRadiusMeters by viewModel.homeRadiusFlow.collectAsStateWithLifecycle()
    val workLat by viewModel.workLatFlow.collectAsStateWithLifecycle()
    val workLng by viewModel.workLngFlow.collectAsStateWithLifecycle()
    val isWorkCalibrated by viewModel.isWorkCalibratedFlow.collectAsStateWithLifecycle()
    val workRadiusMeters by viewModel.workRadiusFlow.collectAsStateWithLifecycle()
    val isDepartureAlertsEnabled by viewModel.isDepartureAlertsEnabled.collectAsStateWithLifecycle()

    val safeZones by viewModel.safeZones.collectAsStateWithLifecycle()
    val memberWeatherDetailed by viewModel.memberWeatherDetailed.collectAsStateWithLifecycle()
    val isCircleDigestReset by viewModel.isCircleDigestReset.collectAsStateWithLifecycle()
    val isVoiceAnnouncementsEnabled by viewModel.isVoiceAnnouncementsEnabled.collectAsStateWithLifecycle()
    val notificationVolume by viewModel.notificationVolume.collectAsStateWithLifecycle()
    val proximityAlertDistanceMeters by viewModel.proximityAlertDistanceMeters.collectAsStateWithLifecycle()
    val activeRingingMembers by viewModel.activeRingingMembers.collectAsStateWithLifecycle()

    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val isLocationPaused by viewModel.isLocationPaused.collectAsStateWithLifecycle()
    val groupSyncToken by viewModel.groupSyncToken.collectAsStateWithLifecycle()
    val myDeviceName by viewModel.myDeviceName.collectAsStateWithLifecycle()
    val myDeviceColor by viewModel.myDeviceColor.collectAsStateWithLifecycle()
    val myDeviceEmoji by viewModel.myDeviceEmoji.collectAsStateWithLifecycle()
    val myDevicePhone by viewModel.myDevicePhone.collectAsStateWithLifecycle()
    val cloudStatusText by viewModel.cloudStatusText.collectAsStateWithLifecycle()
    val ghostModeExpiryTime by viewModel.ghostModeExpiryTime.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()

    val isUserSignedIn by viewModel.isUserSignedIn.collectAsStateWithLifecycle()
    val groupPinMappings by viewModel.groupPinMappings.collectAsStateWithLifecycle()
    val activeGroupPinCode by viewModel.activeGroupPinCode.collectAsStateWithLifecycle()
    val activeGroupCreatorId by viewModel.activeGroupCreatorId.collectAsStateWithLifecycle()
    val myDeviceUUID by viewModel.myDeviceUUID.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()


    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isFamilyPopupOpen by remember { mutableStateOf(false) }
    var isShoppingListPopupOpen by remember { mutableStateOf(false) }
    var isFeedbackOpen by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showNotificationHistory by remember { mutableStateOf(false) }

    val shouldOpenFromIntent by shouldOpenNotificationHistoryFlow.collectAsStateWithLifecycle()
    LaunchedEffect(shouldOpenFromIntent) {
        if (shouldOpenFromIntent) {
            showNotificationHistory = true
            if (shouldOpenNotificationHistoryFlow is kotlinx.coroutines.flow.MutableStateFlow) {
                shouldOpenNotificationHistoryFlow.value = false
            }
        }
    }

    val recentAlertCount = remember(logs) {
        val dayAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
        logs.count { it.timestamp >= dayAgo }
    }


    // Top toast alert notification channel overlay
    var activeAlertMessage by remember { mutableStateOf<String?>(null) }
    // Full-screen SOS overlay — shown when a SOS alert is triggered
    var activeSosOverlay by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            val isDepartureAlert = message.contains("Departure Warning") || message.contains("has left") || message.contains("left the house")
            val isArrivalAlert = message.contains("Arrival Notice") || message.contains("has arrived")
            val isProximityAlert = message.contains("Approaching Alert") || message.contains("getting close") || message.contains("close to your location") || message.contains("minutes away") || message.contains("close to Home") || message.contains("arrived Home")

            if (isDepartureAlert || isArrivalAlert || isProximityAlert) {
                com.example.data.VoiceAnnouncementManager.announceIfEnabled(context, message)
            }
            if (message.contains("SOS ALERT") || message.contains("SOS BEACON")) {
                // Show full-screen SOS overlay for 5 seconds
                activeSosOverlay = message
                coroutineScope.launch {
                    delay(5000)
                    activeSosOverlay = null
                }
            } else {
                // Show regular toast for 3.5 seconds (replaces current if any)
                activeAlertMessage = message
                coroutineScope.launch {
                    delay(3500)
                    if (activeAlertMessage == message) activeAlertMessage = null
                }
            }
        }
    }



    // Handle system back gesture / back button to naturally close open panels
    BackHandler(enabled = isSettingsOpen || isFamilyPopupOpen || showNotificationHistory) {
        if (showNotificationHistory) {
            showNotificationHistory = false
        } else if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (isFamilyPopupOpen) {
            isFamilyPopupOpen = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBlack)
    ) {
        // Android System Status Bar Protection Scrim (Ensures Clock, Battery, 5G & Notifications are 100% visible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color(0xE60A0D14))
                .align(Alignment.TopCenter)
                .zIndex(98f)
        )

        // WhatsApp Action Helper
        val openWhatsApp = { member: FamilyMember ->
            val phone = member.phoneNumber
            if (phone.isBlank()) {
                viewModel.triggerUIFeedback("No phone number set for ${member.name}. Set it in Settings.")
            } else {
                val cleanPhone = com.example.data.GeoUtils.sanitizePhoneNumber(phone).trimStart('+')
                val url = "https://wa.me/$cleanPhone"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    intent.setPackage("com.whatsapp")
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            intent.setPackage("com.whatsapp.w4b")
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            intent.setPackage(null)
                            context.startActivity(intent)
                        }
                    }
                } catch (e: Exception) {
                    viewModel.triggerUIFeedback("Could not open WhatsApp link.")
                }
            }
        }

        // 1. Full Screen Radar Map occupying the background
        RadarMap(
            members = members,
            selectedMemberId = selectedMemberId,
            onSelectMember = { viewModel.selectedMemberId.value = it },
            homeLat = homeLat,
            homeLng = homeLng,
            locationTrails = locationTrails,
            onTriggerSOS = { viewModel.triggerSOS() },
            onTriggerCheckIn = { viewModel.triggerCheckIn() },
            onSendReaction = { memberId, reaction -> viewModel.sendEmojiReaction(memberId, reaction) },
            onSettingsClick = { isSettingsOpen = true },
            onOpenNotificationHistory = { showNotificationHistory = true },
            recentAlertCount = recentAlertCount,
            onOpenWhatsApp = openWhatsApp,
            onUpdateMember = { viewModel.updateFamilyMember(it) },
            onDeleteMember = { viewModel.deleteFamilyMember(it) },
            onTriggerAlarm = { viewModel.toggleFindMyPhone(it) },
            onToggleMemberTracking = { viewModel.toggleMemberTracking(it) },
            activeRingingMembers = activeRingingMembers,
            activeGroupCreatorId = activeGroupCreatorId,
            myDeviceUUID = myDeviceUUID,
            onKickMember = { memberId -> viewModel.kickGroupMember(memberId) },
            homeRadiusMeters = homeRadiusMeters,
            workLat = workLat,
            workLng = workLng,
            isWorkCalibrated = isWorkCalibrated,
            workRadiusMeters = workRadiusMeters,
            safeZones = safeZones,
            onAddSafeZone = { viewModel.addSafeZone(it) },
            onDeleteSafeZone = { viewModel.removeSafeZone(it) },
            memberWeatherDetailed = memberWeatherDetailed,
            isCircleDigestReset = isCircleDigestReset,
            onResetCircleDigest = { viewModel.resetCircleDigest() },
            groupPinMappings = groupPinMappings,
            activeGroupPinCode = activeGroupPinCode,
            onSwitchCircle = { pin -> viewModel.selectActiveCircle(pin) },
            routeTimeFilter = routeTimeFilter,
            onSelectRouteTimeFilter = { filter -> viewModel.setRouteTimeFilter(filter) },
            shoppingItems = shoppingItems,
            onOpenShoppingList = { isShoppingListPopupOpen = true },
            onJoinGroupWithPin = { pin -> viewModel.joinGroupWithPin(pin) },
            onCreateGroupWithPin = { name -> viewModel.createGroupWithPin(name) },
            isLocationPaused = isLocationPaused,
            onToggleLocationPaused = { viewModel.toggleLocationPaused(it) },
            isSettingsOpen = isSettingsOpen,
            bottomPadding = 0.dp,
            modifier = Modifier.fillMaxSize()
        )

        // Top Floating HUD Banner when Find My Phone Alarm is active
        if (activeRingingMembers.isNotEmpty()) {
            val ringingNames = members.filter { activeRingingMembers.contains(it.id) || activeRingingMembers.contains(it.name) }
                .joinToString(", ") { it.name }
                .ifEmpty { "Phone" }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 54.dp, start = 16.dp, end = 16.dp)
                    .zIndex(99f),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF0E53935),
                border = BorderStroke(1.5.dp, Color(0xFFFF8A80)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🚨", fontSize = 16.sp)
                    Text(
                        text = "Ringing $ringingNames loudly...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            activeRingingMembers.forEach { memberId ->
                                viewModel.stopFindMyPhone(memberId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("🔕 Stop Ringing", color = Color(0xFFD32F2F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. FLOATING OVERLAY QUICK ACTIONS BUBBLES (Bottom-Left)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp)
                .zIndex(75f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 1. Family Members Quick Focus Bubble (Leftmost)
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = isFamilyPopupOpen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 340.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fun getMemberLabel(m: com.example.data.FamilyMember, isMe: Boolean): String {
                                val cleanName = m.name.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()
                                if (isMe) return "$cleanName (You)"
                                val lower = cleanName.lowercase()
                                return when {
                                    lower.contains("louis") || lower.contains("dad") -> "$cleanName (Dad)"
                                    lower.contains("annette") || lower.contains("wife") || lower.contains("mom") || lower.contains("mama") || lower.contains("mum") -> "$cleanName (Wife)"
                                    lower.contains("isabel") || lower.contains("eloise") || lower.contains("daughter") -> "$cleanName (Daughter)"
                                    else -> cleanName
                                }
                            }

                            val meMember = members.firstOrNull { it.id == "me" }
                            if (meMember != null) {
                                SubFamilyBubble(
                                    member = meMember,
                                    labelText = getMemberLabel(meMember, isMe = true),
                                    isSelected = selectedMemberId == meMember.id
                                ) {
                                    viewModel.selectedMemberId.value = meMember.id
                                    isFamilyPopupOpen = false
                                }
                            }

                            val otherMembers = members.filter { it.id != "me" }
                            otherMembers.forEach { member ->
                                SubFamilyBubble(
                                    member = member,
                                    labelText = getMemberLabel(member, isMe = false),
                                    isSelected = selectedMemberId == member.id
                                ) {
                                    viewModel.selectedMemberId.value = member.id
                                    isFamilyPopupOpen = false
                                }
                            }

                        }
                    }

                    FloatingActionButton(
                        onClick = { isFamilyPopupOpen = !isFamilyPopupOpen },
                        containerColor = CosmicSlateCard,
                        contentColor = RadarCyan,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp).border(1.dp, SlateBorder, CircleShape),
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Text("👤", fontSize = 18.sp)
                    }
                }
            }
        }

        // 4. INTERACTIVE SHOPPING LIST OVERLAY DIALOG
        if (isShoppingListPopupOpen) {
            AlertDialog(
                onDismissRequest = { isShoppingListPopupOpen = false },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { isShoppingListPopupOpen = false }) {
                        Text("Close", color = RadarCyan, fontWeight = FontWeight.Bold)
                    }
                },
                title = null,
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        val listState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(listState)
                        ) {
                            ShoppingListControls(
                                shoppingItems = shoppingItems,
                                familyMembers = members,
                                onAddItem = { name, memberId, memberName -> viewModel.addShoppingItem(name, memberId, memberName) },
                                onToggleItem = { item -> viewModel.toggleShoppingItem(item) },
                                onDeleteItem = { item -> viewModel.deleteShoppingItem(item) }
                            )
                        }
                    }
                },
                containerColor = CosmicSlateCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            )
        }

        // 5. OTA APP UPDATE AVAILABLE DIALOG
        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { if (updateDownloadProgress == null) viewModel.dismissUpdateDialog() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🚀", fontSize = 20.sp)
                        Text("Kin-Tracker Update v${update.versionName}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = update.releaseNotes,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        if (updateDownloadProgress != null) {
                            val progress = updateDownloadProgress!!
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = RadarCyan,
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )
                                Text(
                                    text = "Downloading... ${(progress * 100).toInt()}%",
                                    color = RadarCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.startOtaUpdateDownload(update) },
                        enabled = updateDownloadProgress == null,
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan)
                    ) {
                        Text(
                            text = if (updateDownloadProgress != null) "Downloading..." else "Update Now",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    if (updateDownloadProgress == null) {
                        TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                            Text("Later", color = TextSecondary)
                        }
                    }
                },
                containerColor = CosmicSlateCard,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(16.dp)
            )
        }

        // 3. SETTINGS & SIMULATION FULLSCREEN SLIDE OVERLAY SHEET
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(90f)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = CosmicBlack
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text("⚙️", fontSize = 18.sp)
                            Text(
                                text = "Circle Settings",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Surface(
                                color = RadarCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { viewModel.checkForAppUpdatesManually() }
                            ) {
                                Text(
                                    text = "v2.6 • Check Update",
                                    color = RadarCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { isSettingsOpen = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5D2EE6),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("close_settings_button")
                        ) {
                            Text(
                                text = "◀  Map",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    val settingsScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(settingsScrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SettingsHub(
                            myDeviceName = myDeviceName,
                            myDeviceColorHex = myDeviceColor,
                            myDeviceEmoji = myDeviceEmoji,
                            myDevicePhone = myDevicePhone,
                            onUpdateProfile = { name, color, emoji, phone ->
                                viewModel.toggleCloudSync(isCloudSyncEnabled, groupSyncToken, name, color, emoji, phone)
                            },
                            ghostModeExpiryTime = ghostModeExpiryTime,
                            onToggleGhostMode = { enabled -> viewModel.toggleGhostMode(enabled) },
                            activeGroupPinCode = activeGroupPinCode,
                            groupPinMappings = groupPinMappings,
                            members = members,
                            myDeviceUUID = myDeviceUUID,
                            activeGroupCreatorId = activeGroupCreatorId,
                            onJoinGroupWithPin = { pin -> viewModel.joinGroupWithPin(pin) },
                            onCreateGroupWithPin = { name -> viewModel.createGroupWithPin(name) },
                            onDeleteGroupPinFromHistory = { mapping -> viewModel.deleteGroupPinFromHistory(mapping) },
                            onKickMember = { memberId -> viewModel.kickGroupMember(memberId) },
                            onUpdateActiveGroupSettings = { newName, newPin -> viewModel.updateActiveGroupSettings(newName, newPin) },
                            onSelectActiveCircle = { pin -> viewModel.selectActiveCircle(pin) },
                            onOpenAddDevice = { showAddDeviceDialog = true },
                            homeLat = homeLat,
                            homeLng = homeLng,
                            homeRadiusMeters = homeRadiusMeters,
                            onUpdateHomeRadius = { viewModel.updateHomeRadius(it) },
                            onCalibrateHome = { viewModel.setHomeToCurrentLocation() },
                            workLat = workLat,
                            workLng = workLng,
                            isWorkCalibrated = isWorkCalibrated,
                            workRadiusMeters = workRadiusMeters,
                            onUpdateWorkRadius = { viewModel.updateWorkRadius(it) },
                            onCalibrateWork = { viewModel.setWorkToCurrentLocation() },
                            onClearWork = { viewModel.clearWorkLocation() },
                            isDepartureAlertsEnabled = isDepartureAlertsEnabled,
                            onToggleDepartureAlerts = { viewModel.toggleDepartureAlerts(it) },
                            isVoiceAnnouncementsEnabled = isVoiceAnnouncementsEnabled,
                            onToggleVoiceAnnouncements = { viewModel.toggleVoiceAnnouncements(it) },
                            notificationVolume = notificationVolume,
                            onUpdateNotificationVolume = { viewModel.updateNotificationVolume(it) },
                            onTestNotificationSound = { viewModel.playNotificationPreview() },
                            proximityAlertDistanceMeters = proximityAlertDistanceMeters,
                            onUpdateProximityAlertDistance = { viewModel.updateProximityAlertDistance(it) },
                            isLocationPaused = isLocationPaused,
                            onToggleLocationPaused = { viewModel.toggleLocationPaused(it) },
                            isCloudSyncEnabled = isCloudSyncEnabled,
                            groupSyncToken = groupSyncToken,
                            cloudStatusText = cloudStatusText,
                            onToggleCloudSync = { enabled ->
                                viewModel.toggleCloudSync(enabled, groupSyncToken, myDeviceName, myDeviceColor, myDeviceEmoji, myDevicePhone)
                            },
                            activityLogs = logs,
                            onClearLogs = { viewModel.clearLogHistory() },
                            onOpenFeedback = { isFeedbackOpen = true },
                            onCleanDuplicates = { viewModel.cleanDuplicates() },
                            onDeleteMember = { member -> viewModel.deleteFamilyMember(member.id) },
                            onToggleMemberTracking = { memberId -> viewModel.toggleMemberTracking(memberId) },
                            onUpdateMember = { member -> viewModel.updateFamilyMember(member) },
                            onSelectTrip = { trip ->
                                com.example.data.TelemetryWebSocketManager.selectTrip(trip)
                                isSettingsOpen = false
                            }
                        )
                    }
                }
            }
        }

        if (isFeedbackOpen) {
            com.example.ui.FeedbackDialog(
                onDismiss = { isFeedbackOpen = false },
                onFeedbackSubmitted = {
                    viewModel.triggerUIFeedback("Thank you for your feedback! 🚀")
                }
            )
        }

        if (showNotificationHistory) {
            com.example.ui.NotificationHistorySheet(
                logs = logs,
                onDismiss = { showNotificationHistory = false },
                onClearLogs = { viewModel.clearLogHistory() },
                onSelectMember = { memberId ->
                    viewModel.selectedMemberId.value = memberId
                    showNotificationHistory = false
                }
            )
        }

        if (showAddDeviceDialog) {
            val activeCircle = groupPinMappings.firstOrNull { it.pinCode == activeGroupPinCode }
            val activeCircleName = activeCircle?.groupName?.ifBlank { "Family Circle" } ?: "Family Circle"
            com.example.ui.AddDeviceDialog(
                activeGroupPinCode = activeGroupPinCode,
                activeGroupName = activeCircleName,
                members = members,
                onJoinGroupWithPin = { pin -> viewModel.joinGroupWithPin(pin) },
                onCreateGroupWithPin = { name -> viewModel.createGroupWithPin(name) },
                onDeleteMemberFromCircle = { member -> viewModel.deleteFamilyMember(member.id) },
                onDismiss = { showAddDeviceDialog = false }
            )
        }

        // Floating Dynamic Alerts Toast HUD Overlay (drawn last = always on top in Box)
        FloatingAlertToast(
            activeAlertMessage = activeAlertMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
                .zIndex(100f)
        )

        // Full-Screen SOS Emergency Overlay — covers everything, impossible to miss
        SosEmergencyOverlay(
            activeSosOverlay = activeSosOverlay,
            onDismiss = { activeSosOverlay = null },
            modifier = Modifier.fillMaxSize()
        )
    }
}



@Composable
fun SubFamilyBubble(
    member: com.example.data.FamilyMember,
    labelText: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val color = try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(member.avatarColorHex))
    } catch (e: Exception) {
        androidx.compose.ui.graphics.Color(0xFF00E5FF) // RadarCyan equivalent
    }

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (isSelected) PrimaryCosmic else CosmicSlateCard, CircleShape)
            .border(1.5.dp, if (isSelected) RadarCyan else SlateBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color, CircleShape)
                .border(1.dp, androidx.compose.ui.graphics.Color.White, CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = member.avatarEmoji.ifBlank { member.name.take(1).uppercase() },
                fontSize = 14.sp
            )
        }
        Text(
            text = labelText,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

