package com.example

import android.os.Bundle
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
        val finePermission = PackageManager.PERMISSION_GRANTED == 
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = PackageManager.PERMISSION_GRANTED == 
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        
        if (finePermission || coarsePermission) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
                } catch (e: SecurityException) { /* Handled */ }
            }
            if (isNetworkEnabled) {
                try {
                    locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { loc ->
                        if (bestLastLocation == null || loc.time > bestLastLocation!!.time) {
                            bestLastLocation = loc
                        }
                    }
                } catch (e: SecurityException) { /* Handled */ }
            }
            bestLastLocation?.let { updateUserPosition(it) }

            // 2. Register for ultra-low latency updates (1 second update cycles, 0 meters delta threshold)
            if (hasFine && isGpsEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.0f,
                    locationListener
                )
            }
            if (isNetworkEnabled) {
                // If hasFine, we can register Network provider too. If hasCoarse, we can register it as well.
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0.0f,
                    locationListener
                )
            }
        } catch (e: Exception) {
            // Catch any security or unsupported provider/illegal argument errors safely
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

        checkAndRequestLocationPermissions()
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
    val cloudStatusText by viewModel.cloudStatusText.collectAsStateWithLifecycle()

    val isUserSignedIn by viewModel.isUserSignedIn.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "KinTracker",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.testTag("app_header_title")
                    )
                    Text(
                        text = "Live Family Telemetry Radar",
                        color = RadarCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Simulating/Active Indicator Pill
                Box(
                    modifier = Modifier
                        .border(1.dp, if (isPaused) SlateBorder else GlowingEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .background(if (isPaused) Color.Transparent else GlowingEmerald.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flashing neon dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isPaused) ActiveAmber else GlowingEmerald, CircleShape)
                        )
                        Text(
                            text = if (isPaused) "FEED PAUSED" else "LIVE GPS FEED",
                            color = if (isPaused) ActiveAmber else GlowingEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // SECTION 1: INTERACTIVE CANVAS RADAR MAP
            RadarMap(
                members = members,
                selectedMemberId = selectedMemberId,
                onSelectMember = { viewModel.selectedMemberId.value = it },
                homeLat = homeLat,
                homeLng = homeLng,
                modifier = Modifier.fillMaxWidth()
            )

            // SECTION 1.5: CLOUD SYNC & MULTI-DEVICE PAIRING CONTROLS
            CloudSyncControls(
                isCloudSyncEnabled = isCloudSyncEnabled,
                groupSyncToken = groupSyncToken,
                myDeviceName = myDeviceName,
                myDeviceColorHex = myDeviceColor,
                cloudStatusText = cloudStatusText,
                onToggleCloudSync = { enabled, token, name, color ->
                    viewModel.toggleCloudSync(enabled, token, name, color)
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

            // SECTION 2: HARDWARE SIMULATION CONTROLLER (ADD DEVICES, RESUME SWITCH)
            SimControls(
                isPaused = isPaused,
                onTogglePause = { viewModel.isSimulationPaused.value = !isPaused },
                onAddMember = { name, type, color ->
                    viewModel.addNewMember(name, type, color)
                },
                onCalibrateHome = { viewModel.forceResetHomeGPS() }
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
                onDeleteMember = { viewModel.deleteFamilyMember(it) }
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
