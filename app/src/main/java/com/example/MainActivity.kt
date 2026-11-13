package com.example

import com.example.ui.navigation.*
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
        if (!com.example.data.GeoUtils.acceptLocationUpdate(location)) return
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
            val lastKnownGps = if (hasFine && isGpsEnabled) {
                try {
                    locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } catch (e: Exception) { null }
            } else null
            val lastKnownNet = if ((hasFine || hasCoarse) && isNetworkEnabled) {
                try {
                    locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (e: Exception) { null }
            } else null
            val bestLastLocation = com.example.data.GeoUtils.pickBetterLocation(lastKnownGps, lastKnownNet)
            bestLastLocation?.let { updateUserPosition(it) }

            // 2. Register for ultra-low latency GPS updates while throttling fallback NETWORK fixes
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
                    val netInterval = if (hasFine && isGpsEnabled) 15000L else 1000L
                    val netDistance = if (hasFine && isGpsEnabled) 25.0f else 0.0f
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        netInterval,
                        netDistance,
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
