package com.example

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

class MainActivity : ComponentActivity() {

    private val viewModel: FamilyViewModel by viewModels()
    private var locationManager: LocationManager? = null

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
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val finePermission = PackageManager.PERMISSION_GRANTED == 
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = PackageManager.PERMISSION_GRANTED == 
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        
        if (finePermission || coarsePermission) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startLocationUpdates() {
        try {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (!hasFine && !hasCoarse) {
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

    override fun onStart() {
        super.onStart()
        checkAndRequestLocationPermissions()
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
        super.onDestroy()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Safely configure OSMDroid prior to UI rendering to prevent storage write permission crashes
        try {
            Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = packageName
            val cacheDir = File(cacheDir, "osmdroid")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            Configuration.getInstance().osmdroidTileCache = cacheDir
            Configuration.getInstance().osmdroidBasePath = cacheDir
        } catch (e: Exception) {
            // Safe fallback
        }

        enableEdgeToEdge()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = CosmicBlack
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: FamilyViewModel,
    modifier: Modifier = Modifier
) {
    val members by viewModel.familyMembers.collectAsStateWithLifecycle()
    val locationTrails by viewModel.locationTrails.collectAsStateWithLifecycle()
    val logs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val isPaused by viewModel.isSimulationPaused.collectAsStateWithLifecycle()
    val selectedMemberId by viewModel.selectedMemberId.collectAsStateWithLifecycle()
    val homeLat by viewModel.homeLatFlow.collectAsStateWithLifecycle()
    val homeLng by viewModel.homeLngFlow.collectAsStateWithLifecycle()

    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val groupSyncToken by viewModel.groupSyncToken.collectAsStateWithLifecycle()
    val myDeviceName by viewModel.myDeviceName.collectAsStateWithLifecycle()
    val myDeviceColor by viewModel.myDeviceColor.collectAsStateWithLifecycle()
    val myDeviceEmoji by viewModel.myDeviceEmoji.collectAsStateWithLifecycle()
    val cloudStatusText by viewModel.cloudStatusText.collectAsStateWithLifecycle()

    val isUserSignedIn by viewModel.isUserSignedIn.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val isSimulationEnabled by viewModel.isSimulationModeEnabled.collectAsStateWithLifecycle()
    val isWifeCloudSimulationEnabled by viewModel.isWifeCloudSimulationEnabled.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isFamilyListExpanded by remember { mutableStateOf(false) }

    // Top toast alert notification channel overlay
    var activeAlertMessage by remember { mutableStateOf<String?>(null) }
    // Full-screen SOS overlay — shown when a SOS alert is triggered
    var activeSosOverlay by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
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

    val sheetHeight by animateDpAsState(
        targetValue = if (isFamilyListExpanded) 520.dp else 105.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    // Handle system back gesture / back button to naturally close open panels
    BackHandler(enabled = isSettingsOpen || isFamilyListExpanded) {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (isFamilyListExpanded) {
            isFamilyListExpanded = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBlack)
    ) {
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
            onOpenWhatsApp = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://api.whatsapp.com/send")
                        `package` = "com.whatsapp"
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com"))
                        context.startActivity(webIntent)
                    } catch (ex: Exception) {
                        viewModel.triggerUIFeedback("Could not open WhatsApp link.")
                    }
                }
            },
            bottomPadding = sheetHeight,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Expandable Family List Sits on top of the map floating elegantly at the bottom
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .padding(horizontal = 12.dp)
                .zIndex(10f),
            color = CosmicBlack.copy(alpha = 0.95f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, SlateBorder),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Drag handle bar which expands and collapses
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFamilyListExpanded = !isFamilyListExpanded }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isFamilyListExpanded) "COLLAPSE FAMILY LIST" else "SWIPE UP FOR FAMILY LIST (${members.size})",
                                color = RadarCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("expand_family_handle")
                            )
                            Text(
                                text = if (isFamilyListExpanded) "▼" else "▲",
                                color = RadarCyan,
                                fontSize = 8.sp
                            )
                        }
                    }
                }

                if (isFamilyListExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    ) {
                        TelemetryDashboard(
                            members = members,
                            selectedMemberId = selectedMemberId,
                            onSelectMember = { viewModel.selectedMemberId.value = it },
                            onCommuteHome = { viewModel.orderHeadingHome(it) },
                            onSendAway = { id, dest -> viewModel.sendAway(id, dest) },
                            onInstantCheckIn = { viewModel.instantCheckInAtHome(it) },
                            onPing = { viewModel.pingMember(it) },
                            onUpdateMember = { viewModel.updateFamilyMember(it) },
                            onDeleteMember = { viewModel.deleteFamilyMember(it) },
                            homeLat = homeLat,
                            homeLng = homeLng,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFamilyListExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            members.take(4).forEach { member ->
                                val avatarCol = try {
                                    Color(android.graphics.Color.parseColor(member.avatarColorHex))
                                } catch (e: Exception) {
                                    Color(0xFF26A69A)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(avatarCol.copy(alpha = 0.2f))
                                        .border(1.dp, avatarCol, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = member.avatarEmoji, fontSize = 15.sp)
                                }
                            }
                            if (members.size > 4) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+${members.size - 4}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "My Family Circle",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val homeCount = members.count { it.statusText.contains("Home", ignoreCase = true) }
                                Text(
                                    text = "$homeCount at Home • ${members.size - homeCount} Commuting",
                                    color = SecondarySlate,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚙️", fontSize = 20.sp)
                            Text(
                                text = "KinTracker Circle Settings",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { isSettingsOpen = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5D2EE6),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("close_settings_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🗺️", fontSize = 12.sp)
                                Text("Map", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    val settingsScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(settingsScrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CloudSyncControls(
                            isCloudSyncEnabled = isCloudSyncEnabled,
                            groupSyncToken = groupSyncToken,
                            myDeviceName = myDeviceName,
                            myDeviceColorHex = myDeviceColor,
                            myDeviceEmoji = myDeviceEmoji,
                            cloudStatusText = cloudStatusText,
                            onToggleCloudSync = { enabled, token, name, color, emoji ->
                                viewModel.toggleCloudSync(enabled, token, name, color, emoji)
                            },
                            onGenerateGroupKey = { viewModel.generateNewGroupKey() },
                            isUserSignedIn = isUserSignedIn,
                            userDisplayName = userDisplayName,
                            userEmail = userEmail,
                            onSignIn = { name, email -> viewModel.signInUser(name, email) },
                            onSignOut = { viewModel.signOutUser() },
                            isWifeCloudSimulationEnabled = isWifeCloudSimulationEnabled,
                            onToggleWifeCloudSimulation = { viewModel.toggleWifeCloudSimulation(it) }
                        )

                        SimControls(
                            isPaused = isPaused,
                            onTogglePause = { viewModel.isSimulationPaused.value = !isPaused },
                            onAddMember = { name, type, color, emoji ->
                                viewModel.addNewMember(name, type, color, emoji)
                            },
                            onCalibrateHome = { viewModel.setHomeToCurrentLocation() },
                            onSaveCustomHome = { lat, lng -> viewModel.saveCustomHome(lat, lng) },
                            homeLat = homeLat,
                            homeLng = homeLng,
                            isSimulationEnabled = isSimulationEnabled,
                            onToggleSimulationMode = { viewModel.toggleSimulationMode(it) },
                            onMockGpsPreset = { idx -> viewModel.triggerManualGpsMockPreset(idx) }
                        )

                        TimelineLogs(
                            logs = logs,
                            onClearLogs = { viewModel.clearLogHistory() }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "KinTracker Telemetry Version v1.0.4 - Secure Client-Side SQL Database",
                                    color = SecondarySlate.copy(alpha = 0.5f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Encrypted Family Location Service. No central server location tracking.",
                                    color = SecondarySlate.copy(alpha = 0.4f),
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Dynamic Alerts Toast HUD Overlay (drawn last = always on top in Box)
        AnimatedVisibility(
            visible = activeAlertMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            val message = activeAlertMessage ?: ""
            Surface(
                color = PrimaryCosmic,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .testTag("ui_floating_alert")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Alert Notify",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Full-Screen SOS Emergency Overlay — covers everything, impossible to miss
        AnimatedVisibility(
            visible = activeSosOverlay != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            val sosMsg = activeSosOverlay ?: ""
            val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sos_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFCC0000).copy(alpha = alpha))
                    .clickable { activeSosOverlay = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "🚨",
                        fontSize = 72.sp
                    )
                    Text(
                        text = "EMERGENCY SOS",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = sosMsg.removePrefix("🚨 "),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap anywhere to dismiss",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
