package com.example

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.flow.collectLatest
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
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
            } catch (e: Exception) {
                false
            }
            val isNetworkEnabled = try {
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
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
            if (isNetworkEnabled) {
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
            if (isNetworkEnabled) {
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

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Top toast alert notification channel overlay
    var activeAlertMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { message ->
            activeAlertMessage = message
            delay(3500) // Keep visible for 3.5 seconds
            activeAlertMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBlack)
    ) {
        // Core Layout Scroll Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Compact Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "KinTracker",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("app_header_title")
                    )
                    Text(
                        text = "• Live Radar",
                        color = RadarCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Tiny active feed indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(if (isPaused) ActiveAmber else GlowingEmerald, CircleShape)
                    )
                    Text(
                        text = if (isPaused) "MAP PAUSED" else "LIVE MAP ACTIVE",
                        color = if (isPaused) ActiveAmber else GlowingEmerald,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // SECTION 1: INTERACTIVE CANVAS RADAR MAP
            RadarMap(
                members = members,
                selectedMemberId = selectedMemberId,
                onSelectMember = { viewModel.selectedMemberId.value = it },
                homeLat = homeLat,
                homeLng = homeLng,
                onTriggerSOS = { viewModel.triggerSOS() },
                onTriggerCheckIn = { viewModel.triggerCheckIn() },
                onSendReaction = { memberId, reaction -> viewModel.sendEmojiReaction(memberId, reaction) },
                onSettingsClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(1000)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // SECTION 1.5: CLOUD SYNC & MULTI-DEVICE PAIRING CONTROLS
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
                onGenerateGroupKey = {
                    viewModel.generateNewGroupKey()
                },
                isUserSignedIn = isUserSignedIn,
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                onSignIn = { name, email -> viewModel.signInUser(name, email) },
                onSignOut = { viewModel.signOutUser() }
            )

            // SECTION 2: HARDWARE SIMULATION CONTROLLER (ADD DEVICES, RESUME SWITCH, SET HOME)
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
                onToggleSimulationMode = { viewModel.toggleSimulationMode(it) }
            )

            // SECTION 3: EXPANDABLE TELEMETRY DASHBOARD
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
                homeLng = homeLng
            )

            // SECTION 4: GEOFENCED TIMELINE AUDIT HISTORY (ROOM DB LOGS)
            TimelineLogs(
                logs = logs,
                onClearLogs = { viewModel.clearLogHistory() }
            )

            // Core Footer Vitals Disclaimer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp),
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

        // Floating Dynamic Alerts Toast HUD Overlay
        AnimatedVisibility(
            visible = activeAlertMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .zIndex(100f)
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
    }
}

// Annotation to provide correct UI drawing in layered viewports
private fun Modifier.zIndex(value: Float): Modifier = this.then(
    object : Modifier.Element {
        override fun toString() = "zIndex($value)"
    }
)
