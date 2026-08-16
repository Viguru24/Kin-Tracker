package com.example

import android.os.Bundle
import android.os.Build
import com.example.ui.OnboardingScreen
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

    override fun onResume() {
        super.onResume()
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
            textToSpeech?.stop()
            textToSpeech?.shutdown()
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

        try {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.getDefault()
                }
            }
        } catch (e: Exception) {}


        setContent {
            MyApplicationTheme {
                val hasOnboarded by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = CosmicBlack
                ) { innerPadding ->
                    if (!hasOnboarded) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onComplete = { /* state update triggers recompose automatically */ }
                        )
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            textToSpeech = textToSpeech,
                            modifier = Modifier.padding(innerPadding)
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
    modifier: Modifier = Modifier
) {
    val members by viewModel.familyMembers.collectAsStateWithLifecycle()
    val locationTrails by viewModel.locationTrails.collectAsStateWithLifecycle()
    val logs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val shoppingItems by viewModel.shoppingItems.collectAsStateWithLifecycle()
    val isPaused by viewModel.isSimulationPaused.collectAsStateWithLifecycle()

    val selectedMemberId by viewModel.selectedMemberId.collectAsStateWithLifecycle()
    val homeLat by viewModel.homeLatFlow.collectAsStateWithLifecycle()
    val homeLng by viewModel.homeLngFlow.collectAsStateWithLifecycle()
    val safeZones by viewModel.safeZones.collectAsStateWithLifecycle()
    val memberWeatherDetailed by viewModel.memberWeatherDetailed.collectAsStateWithLifecycle()
    val isCircleDigestReset by viewModel.isCircleDigestReset.collectAsStateWithLifecycle()
    val isVoiceAnnouncementsEnabled by viewModel.isVoiceAnnouncementsEnabled.collectAsStateWithLifecycle()
    val proximityAlertDistanceMeters by viewModel.proximityAlertDistanceMeters.collectAsStateWithLifecycle()

    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val groupSyncToken by viewModel.groupSyncToken.collectAsStateWithLifecycle()
    val myDeviceName by viewModel.myDeviceName.collectAsStateWithLifecycle()
    val myDeviceColor by viewModel.myDeviceColor.collectAsStateWithLifecycle()
    val myDeviceEmoji by viewModel.myDeviceEmoji.collectAsStateWithLifecycle()
    val myDevicePhone by viewModel.myDevicePhone.collectAsStateWithLifecycle()
    val cloudStatusText by viewModel.cloudStatusText.collectAsStateWithLifecycle()
    val ghostModeExpiryTime by viewModel.ghostModeExpiryTime.collectAsStateWithLifecycle()

    val isUserSignedIn by viewModel.isUserSignedIn.collectAsStateWithLifecycle()
    val groupPinMappings by viewModel.groupPinMappings.collectAsStateWithLifecycle()
    val activeGroupPinCode by viewModel.activeGroupPinCode.collectAsStateWithLifecycle()
    val activeGroupCreatorId by viewModel.activeGroupCreatorId.collectAsStateWithLifecycle()
    val myDeviceUUID by viewModel.myDeviceUUID.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()


    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isFamilyListExpanded by remember { mutableStateOf(false) }
    var isFamilyPopupOpen by remember { mutableStateOf(false) }
    var isShoppingListPopupOpen by remember { mutableStateOf(false) }


    // Top toast alert notification channel overlay
    var activeAlertMessage by remember { mutableStateOf<String?>(null) }
    // Full-screen SOS overlay — shown when a SOS alert is triggered
    var activeSosOverlay by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            val isProximityAlert = message.contains("Approaching Alert") || message.contains("getting close") || message.contains("close to your location") || message.contains("minutes away") || message.contains("close to Home")
            if (isProximityAlert && isVoiceAnnouncementsEnabled) {
                try {
                    val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                    toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 350)
                } catch (e: Exception) {}
                try {
                    textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
                } catch (e: Exception) {}
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

    // Auto-deactivate location history trail & member selection after 20 seconds
    LaunchedEffect(selectedMemberId) {
        if (selectedMemberId != null) {
            delay(20000)
            if (viewModel.selectedMemberId.value == selectedMemberId) {
                viewModel.selectedMemberId.value = null
            }
        }
    }

    val sheetHeight by animateDpAsState(
        targetValue = if (isFamilyListExpanded) 520.dp else 105.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    // Handle system back gesture / back button to naturally close open panels
    BackHandler(enabled = isSettingsOpen || isFamilyListExpanded || isFamilyPopupOpen) {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (isFamilyPopupOpen) {
            isFamilyPopupOpen = false
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
            onOpenWhatsApp = openWhatsApp,
            onUpdateMember = { viewModel.updateFamilyMember(it) },
            onDeleteMember = { viewModel.deleteFamilyMember(it) },
            onTriggerAlarm = { viewModel.triggerFindMyPhone(it) },
            activeGroupCreatorId = activeGroupCreatorId,
            myDeviceUUID = myDeviceUUID,
            onKickMember = { memberId -> viewModel.kickGroupMember(memberId) },
            safeZones = safeZones,
            onAddSafeZone = { viewModel.addSafeZone(it) },
            onDeleteSafeZone = { viewModel.removeSafeZone(it) },
            memberWeatherDetailed = memberWeatherDetailed,
            isCircleDigestReset = isCircleDigestReset,
            onResetCircleDigest = { viewModel.resetCircleDigest() },
            groupPinMappings = groupPinMappings,
            activeGroupPinCode = activeGroupPinCode,
            onSwitchCircle = { pin -> viewModel.selectActiveCircle(pin) },
            bottomPadding = 0.dp,
            modifier = Modifier.fillMaxSize()
        )

        // 2. TOP SHOPPING LIST MARQUEE TICKER
        if (shoppingItems.isNotEmpty()) {
            val activeItemsText = remember(shoppingItems) {
                shoppingItems.filter { !it.isChecked }.joinToString("   •   ") { it.name }
            }
            if (activeItemsText.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 90.dp) // Float below system notifications / SOS
                        .fillMaxWidth(0.9f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(BorderStroke(1.dp, RadarCyan.copy(alpha = 0.5f)), RoundedCornerShape(18.dp))
                        .clickable { isShoppingListPopupOpen = true }
                        .zIndex(70f),
                    color = CosmicSlateCard,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🛒", fontSize = 14.sp)
                        Text(
                            text = "Need: $activeItemsText",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }
        }

        // 3. FLOATING OVERLAY QUICK ACTIONS BUBBLES (Bottom-Left)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 80.dp)
                .zIndex(75f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Notepad / Shopping List Bubble
                FloatingActionButton(
                    onClick = { isShoppingListPopupOpen = !isShoppingListPopupOpen },
                    containerColor = CosmicSlateCard,
                    contentColor = RadarCyan,
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp).border(1.dp, SlateBorder, CircleShape),
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Text("📝", fontSize = 20.sp)
                }

                // Face / Family quick-focus bubble triggers
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
                        modifier = Modifier.size(50.dp).border(1.dp, SlateBorder, CircleShape),
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Text("👤", fontSize = 20.sp)
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
                        CloudSyncControls(
                            isCloudSyncEnabled = isCloudSyncEnabled,
                            groupSyncToken = groupSyncToken,
                            myDeviceName = myDeviceName,
                            myDeviceColorHex = myDeviceColor,
                            myDeviceEmoji = myDeviceEmoji,
                            myDevicePhone = myDevicePhone,
                            cloudStatusText = cloudStatusText,
                            onToggleCloudSync = { enabled, token, name, color, emoji, phone ->
                                viewModel.toggleCloudSync(enabled, token, name, color, emoji, phone)
                            },
                            onGenerateGroupKey = { viewModel.generateNewGroupKey() },
                            isUserSignedIn = isUserSignedIn,
                            userDisplayName = userDisplayName,
                            userEmail = userEmail,
                            onSignIn = { name, email -> viewModel.signInUser(name, email) },
                            onSignOut = { viewModel.signOutUser() },
                            ghostModeExpiryTime = ghostModeExpiryTime,
                            onToggleGhostMode = { enabled -> viewModel.toggleGhostMode(enabled) },
                            groupPinMappings = groupPinMappings,
                            activeGroupPinCode = activeGroupPinCode,
                            onCreateGroupWithPin = { name -> viewModel.createGroupWithPin(name) },
                            onJoinGroupWithPin = { pin -> viewModel.joinGroupWithPin(pin) },
                            onDeleteGroupPinFromHistory = { mapping -> viewModel.deleteGroupPinFromHistory(mapping) },
                            members = members,
                            activeGroupCreatorId = activeGroupCreatorId,
                            myDeviceUUID = myDeviceUUID,
                            onKickMember = { memberId -> viewModel.kickGroupMember(memberId) },
                            onUpdateActiveGroupSettings = { newName, newPin -> viewModel.updateActiveGroupSettings(newName, newPin) },
                            onSelectActiveCircle = { pin -> viewModel.selectActiveCircle(pin) }
                        )

                        SettingsControls(
                            onCalibrateHome = { viewModel.setHomeToCurrentLocation() },
                            homeLat = homeLat,
                            homeLng = homeLng,
                            isVoiceAnnouncementsEnabled = isVoiceAnnouncementsEnabled,
                            onToggleVoiceAnnouncements = { viewModel.toggleVoiceAnnouncements(it) },
                            proximityAlertDistanceMeters = proximityAlertDistanceMeters,
                            onUpdateProximityAlertDistance = { viewModel.updateProximityAlertDistance(it) }
                        )

                        ShoppingListControls(
                            shoppingItems = shoppingItems,
                            familyMembers = members,
                            onAddItem = { name, memberId, memberName -> viewModel.addShoppingItem(name, memberId, memberName) },
                            onToggleItem = { item -> viewModel.toggleShoppingItem(item) },
                            onDeleteItem = { item -> viewModel.deleteShoppingItem(item) }
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
                                    text = "Pulse Tracker Telemetry Version v1.5 - Secure Client-Side SQL Database",
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
        FloatingAlertToast(
            activeAlertMessage = activeAlertMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
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

