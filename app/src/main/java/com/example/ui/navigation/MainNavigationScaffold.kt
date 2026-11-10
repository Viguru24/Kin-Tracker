package com.example.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()


    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isFamilyPopupOpen by remember { mutableStateOf(false) }
    var isShoppingListPopupOpen by remember { mutableStateOf(false) }
    var isFeedbackOpen by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showNotificationHistory by remember { mutableStateOf(false) }
    var isCircleDigestOpen by remember { mutableStateOf(false) }
    var showAddSafeZoneDialog by remember { mutableStateOf(false) }

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
    BackHandler(enabled = isSettingsOpen || isFamilyPopupOpen || showNotificationHistory || isCircleDigestOpen || isShoppingListPopupOpen || showAddSafeZoneDialog) {
        if (showNotificationHistory) {
            showNotificationHistory = false
        } else if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (isCircleDigestOpen) {
            isCircleDigestOpen = false
        } else if (isShoppingListPopupOpen) {
            isShoppingListPopupOpen = false
        } else if (showAddSafeZoneDialog) {
            showAddSafeZoneDialog = false
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

        // Google Maps Exact Location Helper
        val openGoogleMaps = { member: FamilyMember ->
            if (member.x == 0.0 && member.y == 0.0) {
                viewModel.triggerUIFeedback("No GPS coordinates available for ${member.name}")
            } else {
                viewModel.selectedMemberId.value = member.id
                val cleanName = member.name.replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE), "").trim()
                val gmmIntentUri = android.net.Uri.parse("geo:${member.y},${member.x}?q=${member.y},${member.x}(${android.net.Uri.encode(cleanName)})")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(mapIntent)
                } catch (_: Exception) {
                    val webUri = android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${member.y},${member.x}")
                    try {
                        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                    } catch (_: Exception) {
                        viewModel.triggerUIFeedback("Could not open Google Maps.")
                    }
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
            onOpenGoogleMaps = openGoogleMaps,
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
                    Text("🚨", fontSize = 16.sp, maxLines = 1, softWrap = false)
                    Text(
                        text = "Ringing $ringingNames loudly...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                        Text(
                            text = "🔕 Stop Ringing",
                            color = Color(0xFFD32F2F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 2. FLOATING OVERLAY QUICK ACTIONS BUBBLES (Bottom-Left)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
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
                                    isFamilyPopupOpen = false
                                    openGoogleMaps(meMember)
                                }
                            }

                            val otherMembers = members.filter { it.id != "me" }
                            otherMembers.forEach { member ->
                                SubFamilyBubble(
                                    member = member,
                                    labelText = getMemberLabel(member, isMe = false),
                                    isSelected = selectedMemberId == member.id
                                ) {
                                    isFamilyPopupOpen = false
                                    openGoogleMaps(member)
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
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
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
                            Text("⚙️", fontSize = 18.sp, maxLines = 1, softWrap = false)
                            Text(
                                text = "Circle Settings",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                color = RadarCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { viewModel.checkForAppUpdatesManually() }
                            ) {
                                Text(
                                    text = "v${com.example.BuildConfig.VERSION_NAME} • Check Update",
                                    color = RadarCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
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
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
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
                            onUpdateProfile = { name, color, emoji, phone, photoPath ->
                                viewModel.updateMyDeviceProfile(name, color, emoji, phone, photoPath)
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
                            onOpenGoogleMaps = openGoogleMaps,
                            onSelectTrip = { trip ->
                                com.example.data.TelemetryWebSocketManager.selectTrip(trip)
                                isSettingsOpen = false
                            },
                            onOpenCircleDigest = {
                                isSettingsOpen = false
                                isCircleDigestOpen = true
                            },
                            onOpenShoppingList = {
                                isSettingsOpen = false
                                isShoppingListPopupOpen = true
                            },
                            onOpenAddSafeZone = {
                                isSettingsOpen = false
                                showAddSafeZoneDialog = true
                            },
                            shoppingActiveCount = shoppingItems.count { !it.isChecked },
                            serverUrl = serverUrl,
                            onUpdateServerUrl = { newUrl -> viewModel.updateServerUrl(newUrl) },
                            onLoadTrips = { viewModel.loadCompletedTripsOnDemand() }
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

        if (isCircleDigestOpen) {
            com.example.ui.CircleDigestDashboard(
                isReset = isCircleDigestReset,
                onReset = { viewModel.resetCircleDigest() },
                onDismiss = { isCircleDigestOpen = false }
            )
        }

        if (showAddSafeZoneDialog) {
            var newZoneName by remember { mutableStateOf("") }
            var newZoneRadius by remember { mutableStateOf(80f) }
            var newZoneIcon by remember { mutableStateOf("home") }

            AlertDialog(
                onDismissRequest = { showAddSafeZoneDialog = false },
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️", fontSize = 20.sp)
                        Text(text = "Create Safe Zone", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Define a new geofenced alert boundary for your circle.", color = TextSecondary, fontSize = 12.sp)

                        OutlinedTextField(
                            value = newZoneName,
                            onValueChange = { newZoneName = it },
                            label = { Text("Zone Name (e.g. School, Gym, Park)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column {
                            Text(text = "Radius: ${newZoneRadius.toInt()} meters", color = TextPrimary, fontSize = 12.sp)
                            Slider(
                                value = newZoneRadius,
                                onValueChange = { newZoneRadius = it },
                                valueRange = 30f..250f,
                                colors = SliderDefaults.colors(
                                    thumbColor = RadarCyan,
                                    activeTrackColor = RadarCyan
                                )
                            )
                        }

                        Text(text = "Select Zone Icon Type:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("home" to "🏠", "school" to "🏫", "gym" to "💪", "work" to "💼", "shop" to "🛒").forEach { (type, emoji) ->
                                val isSelected = newZoneIcon == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) RadarCyan else Color(0xFFF0F2F6))
                                        .clickable { newZoneIcon = type }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val meLoc = members.firstOrNull { it.id == "me" }
                            val lat = meLoc?.y ?: homeLat
                            val lng = meLoc?.x ?: homeLng
                            viewModel.addSafeZone(
                                com.example.data.SafeZone(
                                    id = "zone_" + System.currentTimeMillis(),
                                    name = newZoneName.ifBlank { "Safe Zone" },
                                    latitude = lat,
                                    longitude = lng,
                                    radiusMeters = newZoneRadius.toDouble(),
                                    iconName = newZoneIcon
                                )
                            )
                            showAddSafeZoneDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan)
                    ) {
                        Text("Save Zone", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSafeZoneDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = CosmicSlateCard,
                shape = RoundedCornerShape(16.dp)
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
            .widthIn(max = 280.dp)
            .clickable(onClick = onClick)
            .background(if (isSelected) PrimaryCosmic else CosmicSlateCard, CircleShape)
            .border(1.5.dp, if (isSelected) RadarCyan else SlateBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color, CircleShape)
                .border(1.dp, androidx.compose.ui.graphics.Color.White, CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            if (member.photoPath.isNotBlank() && java.io.File(member.photoPath).exists()) {
                val bitmap = remember(member.photoPath) {
                    try { android.graphics.BitmapFactory.decodeFile(member.photoPath) } catch (_: Exception) { null }
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile Photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = member.avatarEmoji.ifBlank { member.name.take(1).uppercase() },
                        fontSize = 14.sp
                    )
                }
            } else {
                Text(
                    text = member.avatarEmoji.ifBlank { member.name.take(1).uppercase() },
                    fontSize = 14.sp
                )
            }
        }
        Text(
            text = labelText,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = "🗺️",
            fontSize = 12.sp
        )
    }
}
