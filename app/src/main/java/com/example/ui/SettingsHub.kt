package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityLog
import com.example.data.FamilyMember
import com.example.data.GroupPinMapping
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt

enum class SettingsTab(val title: String, val emoji: String) {
    CIRCLE("Circle", "👥"),
    PROFILE("Profile", "👤"),
    PLACES("Places", "🏠"),
    ALERTS("Alerts", "🔔"),
    SYSTEM("System", "⚙️")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHub(
    myDeviceName: String,
    myDeviceColorHex: String,
    myDeviceEmoji: String,
    myDevicePhone: String,
    onUpdateProfile: (name: String, color: String, emoji: String, phone: String, photoPath: String) -> Unit,
    ghostModeExpiryTime: Long,
    onToggleGhostMode: (Boolean) -> Unit,
    activeGroupPinCode: String,
    groupPinMappings: List<GroupPinMapping>,
    members: List<FamilyMember>,
    myDeviceUUID: String,
    activeGroupCreatorId: String,
    onJoinGroupWithPin: (String) -> Unit,
    onCreateGroupWithPin: (String) -> Unit,
    onDeleteGroupPinFromHistory: (GroupPinMapping) -> Unit,
    onKickMember: (String) -> Unit,
    onUpdateActiveGroupSettings: (String, String) -> Unit,
    onSelectActiveCircle: (String) -> Unit,
    onOpenAddDevice: () -> Unit,
    homeLat: Double,
    homeLng: Double,
    homeRadiusMeters: Double,
    onUpdateHomeRadius: (Double) -> Unit,
    onCalibrateHome: () -> Unit,
    workLat: Double,
    workLng: Double,
    isWorkCalibrated: Boolean,
    workRadiusMeters: Double,
    onUpdateWorkRadius: (Double) -> Unit,
    onCalibrateWork: () -> Unit,
    onClearWork: () -> Unit,
    isDepartureAlertsEnabled: Boolean,
    onToggleDepartureAlerts: (Boolean) -> Unit,
    isVoiceAnnouncementsEnabled: Boolean,
    onToggleVoiceAnnouncements: (Boolean) -> Unit,
    notificationVolume: Int = 80,
    onUpdateNotificationVolume: (Int) -> Unit = {},
    onTestNotificationSound: () -> Unit = {},
    proximityAlertDistanceMeters: Int,
    onUpdateProximityAlertDistance: (Int) -> Unit,
    isLocationPaused: Boolean,
    onToggleLocationPaused: (Boolean) -> Unit,
    isCloudSyncEnabled: Boolean,
    groupSyncToken: String,
    cloudStatusText: String,
    onToggleCloudSync: (Boolean) -> Unit,
    activityLogs: List<ActivityLog>,
    onClearLogs: () -> Unit,
    onOpenFeedback: () -> Unit,
    onCleanDuplicates: (() -> Unit)? = null,
    onDeleteMember: ((FamilyMember) -> Unit)? = null,
    onToggleMemberTracking: ((String) -> Unit)? = null,
    onUpdateMember: ((FamilyMember) -> Unit)? = null,
    onSelectTrip: ((com.example.data.CloudCompletedTrip) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.CIRCLE) }
    var memberToEdit by remember { mutableStateOf<FamilyMember?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── CATEGORY PILL SELECTOR ──────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = RadarCyan,
            edgePadding = 0.dp,
            divider = {},
            indicator = {}
        ) {
            SettingsTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                Surface(
                    onClick = { selectedTab = tab },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) RadarCyan else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) RadarCyan else SlateBorder),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(tab.emoji, fontSize = 13.sp)
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // ── TAB CONTENT WITH SMOOTH CROSSFADE ──────────────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings_tab_content"
        ) { tab ->
            when (tab) {
                SettingsTab.CIRCLE -> CircleTabContent(
                    pinCode = activeGroupPinCode,
                    members = members,
                    myDeviceUUID = myDeviceUUID,
                    onOpenAddDevice = onOpenAddDevice,
                    onJoinGroupWithPin = onJoinGroupWithPin,
                    onCleanDuplicates = onCleanDuplicates,
                    onDeleteMember = onDeleteMember,
                    isLocationPaused = isLocationPaused,
                    onToggleLocationPaused = onToggleLocationPaused,
                    onToggleMemberTracking = onToggleMemberTracking,
                    onEditMember = { memberToEdit = it }
                )
                SettingsTab.PROFILE -> ProfileTabContent(
                    name = myDeviceName,
                    colorHex = myDeviceColorHex,
                    emoji = myDeviceEmoji,
                    phone = myDevicePhone,
                    onUpdate = onUpdateProfile,
                    ghostModeExpiryTime = ghostModeExpiryTime,
                    onToggleGhostMode = onToggleGhostMode,
                    members = members,
                    myDeviceUUID = myDeviceUUID,
                    onUpdateMember = onUpdateMember,
                    onEditMember = { memberToEdit = it }
                )
                SettingsTab.PLACES -> PlacesTabContent(
                    homeLat = homeLat,
                    homeLng = homeLng,
                    homeRadiusMeters = homeRadiusMeters,
                    onUpdateHomeRadius = onUpdateHomeRadius,
                    onCalibrateHome = onCalibrateHome,
                    workLat = workLat,
                    workLng = workLng,
                    isWorkCalibrated = isWorkCalibrated,
                    workRadiusMeters = workRadiusMeters,
                    onUpdateWorkRadius = onUpdateWorkRadius,
                    onCalibrateWork = onCalibrateWork,
                    onClearWork = onClearWork
                )
                SettingsTab.ALERTS -> AlertsTabContent(
                    isDepartureAlertsEnabled = isDepartureAlertsEnabled,
                    onToggleDepartureAlerts = onToggleDepartureAlerts,
                    isVoiceAnnouncementsEnabled = isVoiceAnnouncementsEnabled,
                    onToggleVoiceAnnouncements = onToggleVoiceAnnouncements,
                    notificationVolume = notificationVolume,
                    onUpdateNotificationVolume = onUpdateNotificationVolume,
                    onTestNotificationSound = onTestNotificationSound,
                    proximityDistanceMeters = proximityAlertDistanceMeters,
                    onUpdateProximityDistance = onUpdateProximityAlertDistance,
                    members = members
                )
                SettingsTab.SYSTEM -> SystemTabContent(
                    isLocationPaused = isLocationPaused,
                    onToggleLocationPaused = onToggleLocationPaused,
                    cloudStatusText = cloudStatusText,
                    activityLogs = activityLogs,
                    onClearLogs = onClearLogs,
                    onOpenFeedback = onOpenFeedback,
                    onSelectTrip = onSelectTrip
                )
            }
        }
    }

    // Modal Profile & Photo Editor Dialog for any circle member (including daughter Isabel and dad)
    memberToEdit?.let { member ->
        MemberEditDialog(
            member = member,
            onDismiss = { memberToEdit = null },
            onSave = { updated ->
                onUpdateMember?.invoke(updated)
                val isMe = updated.id == "me" || updated.id == myDeviceUUID || updated.id.endsWith("_$myDeviceUUID")
                if (isMe) {
                    onUpdateProfile(updated.name, updated.avatarColorHex, updated.avatarEmoji, updated.phoneNumber, updated.photoPath)
                }
                memberToEdit = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1: CIRCLE & DEVICES
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CircleTabContent(
    pinCode: String,
    members: List<FamilyMember>,
    myDeviceUUID: String,
    onOpenAddDevice: () -> Unit,
    onJoinGroupWithPin: (String) -> Unit,
    onCleanDuplicates: (() -> Unit)? = null,
    onDeleteMember: ((FamilyMember) -> Unit)? = null,
    isLocationPaused: Boolean = false,
    onToggleLocationPaused: (Boolean) -> Unit = {},
    onToggleMemberTracking: ((String) -> Unit)? = null,
    onUpdateMember: ((FamilyMember) -> Unit)? = null,
    onEditMember: (FamilyMember) -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var showJoinSheet by remember { mutableStateOf(false) }
    var joinPinInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Active Circle Hero Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GlowingEmerald))
                            Text("Family Circle", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("Connected to Ra Server • Real-Time Sync", fontSize = 11.sp, color = SecondarySlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // PIN Code Badge with Copy
                    Surface(
                        onClick = { clipboardManager.setText(AnnotatedString(pinCode)) },
                        shape = RoundedCornerShape(10.dp),
                        color = RadarCyan.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("PIN $pinCode", fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = RadarCyan, maxLines = 1, softWrap = false)
                            Icon(Icons.Default.Share, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = RadarCyan)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenAddDevice,
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0D1117))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Pair New Phone",
                            color = Color(0xFF0D1117),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = { showJoinSheet = !showJoinSheet },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Switch Circle", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // ── Connected Family Members ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONNECTED FAMILY MEMBERS (${members.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SecondarySlate
            )
            if (onCleanDuplicates != null) {
                Surface(
                    onClick = onCleanDuplicates,
                    shape = RoundedCornerShape(8.dp),
                    color = RadarCyan.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.25f))
                ) {
                    Text(
                        "🧹 Clean Duplicates",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RadarCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        members.forEach { member ->
            val isMe = member.id == "me" || member.id.endsWith("_$myDeviceUUID")
            val isOnline = (System.currentTimeMillis() - member.lastActive) < 15 * 60 * 1000L || isMe
            val isMemberPaused = member.isLocationPaused || (isMe && isLocationPaused) || member.statusText.contains("Paused", ignoreCase = true)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditMember(member) },
                colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
                border = BorderStroke(1.dp, if (isMe) RadarCyan.copy(alpha = 0.5f) else SlateBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(member.avatarColorHex.ifBlank { "#0061A4" }))
                    } catch (e: Exception) { RadarCyan }

                    // Avatar with online/offline status dot
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(parsedColor.copy(alpha = 0.15f))
                                .border(1.5.dp, parsedColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (member.photoPath.isNotBlank() && java.io.File(member.photoPath).exists()) {
                                val bitmap = remember(member.photoPath) {
                                    try { android.graphics.BitmapFactory.decodeFile(member.photoPath) } catch (_: Exception) { null }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(member.avatarEmoji.ifBlank { "👤" }, fontSize = 18.sp)
                                }
                            } else {
                                Text(member.avatarEmoji.ifBlank { "👤" }, fontSize = 18.sp)
                            }
                        }

                        // Small status dot indicator
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) GlowingEmerald else ActiveAmber)
                                .border(1.5.dp, CosmicSlateCard, CircleShape)
                        )
                    }

                    // Member Name and Single-Line Location/Status
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = member.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isMe) {
                                Surface(color = RadarCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "THIS PHONE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RadarCyan,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (isMemberPaused) {
                                Surface(color = ActiveAmber.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "⏸️ PAUSED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ActiveAmber,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Combined single-line location & status (guaranteed never to wrap letter-by-letter)
                        val locationText = if (isMemberPaused) "Hidden from map" else member.statusText.ifBlank { "Live GPS" }
                        val statusText = if (isOnline) "Live now" else "Offline"
                        Text(
                            text = "$locationText • $statusText",
                            fontSize = 11.sp,
                            color = if (isMemberPaused) ActiveAmber else if (member.statusText.contains("Home", ignoreCase = true)) GlowingEmerald else SecondarySlate,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Right Side: Battery Pill + Edit + Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (member.batteryPercentage > 20) Color(0xFFF1F8E9) else Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, if (member.batteryPercentage > 20) Color(0xFF81C784) else Color(0xFFE57373))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(if (member.isCharging) "⚡" else "🔋", fontSize = 10.sp)
                                Text(
                                    "${member.batteryPercentage}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (member.batteryPercentage > 20) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }

                        if (isMemberPaused && onToggleMemberTracking != null) {
                            IconButton(
                                onClick = {
                                    if (isMe) onToggleLocationPaused(false) else onToggleMemberTracking(member.id)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Unpause",
                                    tint = GlowingEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onEditMember(member) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit ${member.name}",
                                tint = RadarCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (!isMe && onDeleteMember != null) {
                            IconButton(
                                onClick = { onDeleteMember(member) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove ${member.name}",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showJoinSheet) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Join Another Circle", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = joinPinInput,
                            onValueChange = { if (it.length <= 4) joinPinInput = it },
                            label = { Text("4-digit PIN", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                if (joinPinInput.length == 4) {
                                    onJoinGroupWithPin(joinPinInput)
                                    joinPinInput = ""
                                    showJoinSheet = false
                                }
                            },
                            enabled = joinPinInput.length == 4,
                            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Join Circle", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 2: MY PROFILE & DEVICE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ProfileTabContent(
    name: String,
    colorHex: String,
    emoji: String,
    phone: String,
    onUpdate: (name: String, color: String, emoji: String, phone: String, photoPath: String) -> Unit,
    ghostModeExpiryTime: Long,
    onToggleGhostMode: (Boolean) -> Unit,
    members: List<FamilyMember> = emptyList(),
    myDeviceUUID: String = "",
    onUpdateMember: ((FamilyMember) -> Unit)? = null,
    onEditMember: (FamilyMember) -> Unit = {}
) {
    val meMember = members.firstOrNull { it.id == "me" || it.id == myDeviceUUID || it.id.endsWith("_$myDeviceUUID") }
        ?: FamilyMember(
            id = "me",
            name = name.ifBlank { "Dad" },
            avatarColorHex = colorHex.ifBlank { "#0061A4" },
            x = 0.0,
            y = 0.0,
            batteryPercentage = 100,
            isCharging = false,
            speedMph = 0.0,
            statusText = "Online",
            isComingHome = false,
            etaMinutes = 0,
            avatarEmoji = emoji.ifBlank { "👨" },
            phoneNumber = phone
        )

    val activeMember = meMember
    var nameState by remember(activeMember.id, activeMember.name) { mutableStateOf(activeMember.name) }
    var phoneState by remember(activeMember.id, activeMember.phoneNumber) { mutableStateOf(activeMember.phoneNumber) }
    var selectedColor by remember(activeMember.id, activeMember.avatarColorHex, colorHex) {
        mutableStateOf(if (activeMember.avatarColorHex.isNotBlank()) activeMember.avatarColorHex else colorHex.ifBlank { "#0061A4" })
    }
    var selectedEmoji by remember(activeMember.id, activeMember.avatarEmoji) { mutableStateOf(activeMember.avatarEmoji) }
    var saveSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(colorHex, activeMember.avatarColorHex) {
        val c = if (activeMember.avatarColorHex.isNotBlank()) activeMember.avatarColorHex else colorHex
        if (c.isNotBlank()) selectedColor = c
    }
    LaunchedEffect(name, activeMember.name) {
        val n = if (activeMember.name.isNotBlank()) activeMember.name else name
        if (n.isNotBlank()) nameState = n
    }
    LaunchedEffect(phone, activeMember.phoneNumber) {
        val p = if (activeMember.phoneNumber.isNotBlank()) activeMember.phoneNumber else phone
        if (p.isNotBlank()) phoneState = p
    }
    LaunchedEffect(emoji, activeMember.avatarEmoji) {
        val e = if (activeMember.avatarEmoji.isNotBlank()) activeMember.avatarEmoji else emoji
        if (e.isNotBlank()) selectedEmoji = e
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            delay(2000L)
            saveSuccess = false
        }
    }

    val emojis = listOf("👨", "👩", "👧", "👦", "👵", "👴", "🐱", "🐶")
    val colors = listOf("#0061A4", "#AA22FF", "#EC407A", "#26A69A", "#42A5F5", "#FF9800", "#00FF87", "#E040FB")

    val isGhostMode = System.currentTimeMillis() < ghostModeExpiryTime
    var timeLeftString by remember { mutableStateOf("") }
    LaunchedEffect(ghostModeExpiryTime) {
        while (System.currentTimeMillis() < ghostModeExpiryTime) {
            val diffMs = ghostModeExpiryTime - System.currentTimeMillis()
            val hours = diffMs / 3600000
            val minutes = (diffMs % 3600000) / 60000
            val seconds = (diffMs % 60000) / 1000
            timeLeftString = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000L)
        }
        timeLeftString = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── Active Member Details & Photo Editing Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header with Avatar and "Change Photo" action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        val parsedBorderColor = try {
                            Color(android.graphics.Color.parseColor(selectedColor.ifBlank { "#0061A4" }))
                        } catch (_: Exception) { RadarCyan }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(parsedBorderColor.copy(alpha = 0.15f))
                                .border(2.dp, parsedBorderColor, CircleShape)
                                .clickable { onEditMember(activeMember) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeMember.photoPath.isNotBlank() && java.io.File(activeMember.photoPath).exists()) {
                                val bitmap = remember(activeMember.photoPath) {
                                    try { android.graphics.BitmapFactory.decodeFile(activeMember.photoPath) } catch (_: Exception) { null }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(selectedEmoji.ifBlank { "👤" }, fontSize = 26.sp)
                                }
                            } else {
                                Text(selectedEmoji.ifBlank { "👤" }, fontSize = 26.sp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "My Profile (This Phone)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Tap photo or button to upload & crop",
                                fontSize = 11.sp,
                                color = SecondarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Direct "Change Photo" button
                    Button(
                        onClick = { onEditMember(activeMember) },
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, RadarCyan),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = RadarCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Photo & Crop", color = RadarCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }

                Divider(color = SlateBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

                // High-contrast, strong black Display Name TextField
                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    label = {
                        Text(
                            text = "Display Name",
                            color = Color(0xFF000000),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF000000),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF000000),
                        unfocusedTextColor = Color(0xFF000000),
                        focusedLabelColor = Color(0xFF000000),
                        unfocusedLabelColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF000000),
                        unfocusedBorderColor = Color(0xFF64748B),
                        cursorColor = Color(0xFF000000),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // High-contrast, strong black Phone Number TextField
                OutlinedTextField(
                    value = phoneState,
                    onValueChange = { phoneState = it },
                    label = {
                        Text(
                            text = "Phone Number",
                            color = Color(0xFF000000),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF000000),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF000000),
                        unfocusedTextColor = Color(0xFF000000),
                        focusedLabelColor = Color(0xFF000000),
                        unfocusedLabelColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF000000),
                        unfocusedBorderColor = Color(0xFF64748B),
                        cursorColor = Color(0xFF000000),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Avatar Icon Grid
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Avatar Icon", fontSize = 12.sp, color = SecondarySlate)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        emojis.forEach { e ->
                            Surface(
                                onClick = { selectedEmoji = e },
                                shape = CircleShape,
                                color = if (selectedEmoji == e) RadarCyan.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (selectedEmoji == e) RadarCyan else Color.Transparent),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(e, fontSize = 18.sp) }
                            }
                        }
                    }
                }

                // Map Accent Color
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Map Accent Color", fontSize = 12.sp, color = SecondarySlate)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        colors.forEach { c ->
                            val colorVal = try { Color(android.graphics.Color.parseColor(c)) } catch (e: Exception) { RadarCyan }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .clickable { selectedColor = c }
                                    .border(2.dp, if (selectedColor == c) Color.Black else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == c) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Save Profile Button
                Button(
                    onClick = {
                        val updated = activeMember.copy(
                            name = nameState.trim(),
                            phoneNumber = phoneState.trim(),
                            avatarColorHex = selectedColor,
                            avatarEmoji = selectedEmoji
                        )
                        onUpdateMember?.invoke(updated)
                        onUpdate(nameState.trim(), selectedColor, selectedEmoji, phoneState.trim(), activeMember.photoPath)
                        saveSuccess = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (saveSuccess) GlowingEmerald else RadarCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(if (saveSuccess) Icons.Default.Check else Icons.Default.Done, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (saveSuccess) "Saved Successfully!" else "Save My Profile",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── Ghost Mode Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isGhostMode) ActiveAmber.copy(alpha = 0.08f) else CosmicSlateCard),
            border = BorderStroke(1.dp, if (isGhostMode) ActiveAmber.copy(alpha = 0.5f) else SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("👻", fontSize = 16.sp)
                            Text("Ghost Mode (Privacy)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            text = if (isGhostMode) "Active • $timeLeftString remaining" else "Temporarily hides your live location from circle",
                            fontSize = 11.sp,
                            color = if (isGhostMode) ActiveAmber else SecondarySlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = isGhostMode,
                        onCheckedChange = onToggleGhostMode,
                        colors = SwitchDefaults.colors(checkedTrackColor = ActiveAmber)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 3: PLACES & SAFE ZONES (AUTO-COORDINATED)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlacesTabContent(
    homeLat: Double,
    homeLng: Double,
    homeRadiusMeters: Double,
    onUpdateHomeRadius: (Double) -> Unit,
    onCalibrateHome: () -> Unit,
    workLat: Double,
    workLng: Double,
    isWorkCalibrated: Boolean,
    workRadiusMeters: Double,
    onUpdateWorkRadius: (Double) -> Unit,
    onCalibrateWork: () -> Unit,
    onClearWork: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Text("🏠", fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Home Base Zone",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(color = GlowingEmerald.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "AUTO-SYNCED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlowingEmerald,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(String.format(java.util.Locale.US, "GPS: %.4f, %.4f", homeLat, homeLng), fontSize = 11.sp, color = SecondarySlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = onCalibrateHome,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Recalibrate", fontSize = 11.sp, color = RadarCyan, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Detection Radius", fontSize = 12.sp, color = TextPrimary)
                    Text("${homeRadiusMeters.toInt()}m (Exit: ${homeRadiusMeters.toInt() + 45}m)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RadarCyan)
                }

                Slider(
                    value = homeRadiusMeters.toFloat(),
                    onValueChange = { onUpdateHomeRadius(it.toDouble()) },
                    valueRange = 40f..150f,
                    steps = 10,
                    colors = SliderDefaults.colors(thumbColor = RadarCyan, activeTrackColor = RadarCyan)
                )

                Text(
                    text = "✨ Coordinates are automatically coordinated with all family members via Ra. Phones automatically snap to Home to prevent indoor GPS jitter.",
                    fontSize = 11.sp,
                    color = SecondarySlate,
                    lineHeight = 15.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Text("💼", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text("Work / School Zone", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (isWorkCalibrated) String.format(java.util.Locale.US, "GPS: %.4f, %.4f", workLat, workLng) else "Optional • Not configured",
                                fontSize = 11.sp,
                                color = SecondarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isWorkCalibrated) {
                            OutlinedButton(
                                onClick = onClearWork,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) { Text("Clear", fontSize = 10.sp, color = ErrorRed, maxLines = 1, softWrap = false) }
                        }
                        Button(
                            onClick = onCalibrateWork,
                            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text(if (isWorkCalibrated) "Update" else "Set Work", fontSize = 10.sp, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 4: SMART ALERTS & AUDIO
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AlertsTabContent(
    isDepartureAlertsEnabled: Boolean,
    onToggleDepartureAlerts: (Boolean) -> Unit,
    isVoiceAnnouncementsEnabled: Boolean,
    onToggleVoiceAnnouncements: (Boolean) -> Unit,
    notificationVolume: Int,
    onUpdateNotificationVolume: (Int) -> Unit,
    onTestNotificationSound: () -> Unit,
    proximityDistanceMeters: Int,
    onUpdateProximityDistance: (Int) -> Unit,
    members: List<FamilyMember>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Automation & Notifications", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Departure Warnings", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Alerts when family members leave Home or Work", fontSize = 11.sp, color = SecondarySlate)
                    }
                    Switch(
                        checked = isDepartureAlertsEnabled,
                        onCheckedChange = onToggleDepartureAlerts,
                        colors = SwitchDefaults.colors(checkedTrackColor = RadarCyan)
                    )
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Voice Announcements (TTS)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Speaks aloud arrivals and departures automatically", fontSize = 11.sp, color = SecondarySlate)
                    }
                    Switch(
                        checked = isVoiceAnnouncementsEnabled,
                        onCheckedChange = onToggleVoiceAnnouncements,
                        colors = SwitchDefaults.colors(checkedTrackColor = RadarCyan)
                    )
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                // ── NOTIFICATION VOLUME CONTROL ─────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val volumeIcon = when {
                                notificationVolume == 0 -> "🔇"
                                notificationVolume <= 50 -> "🔉"
                                else -> "🔊"
                            }
                            IconButton(
                                onClick = {
                                    if (notificationVolume > 0) {
                                        onUpdateNotificationVolume(0)
                                    } else {
                                        onUpdateNotificationVolume(80)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(volumeIcon, fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    text = "Notification Alert Volume",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Arrivals, departures, chimes & voice volume",
                                    fontSize = 11.sp,
                                    color = SecondarySlate
                                )
                            }
                        }

                        // Badge showing current volume level
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                notificationVolume == 0 -> ActiveAmber.copy(alpha = 0.18f)
                                notificationVolume >= 90 -> GlowingEmerald.copy(alpha = 0.18f)
                                else -> RadarCyan.copy(alpha = 0.15f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    notificationVolume == 0 -> ActiveAmber.copy(alpha = 0.6f)
                                    notificationVolume >= 90 -> GlowingEmerald.copy(alpha = 0.6f)
                                    else -> RadarCyan.copy(alpha = 0.5f)
                                }
                            )
                        ) {
                            Text(
                                text = when (notificationVolume) {
                                    0 -> "MUTED"
                                    100 -> "MAX 100%"
                                    else -> "$notificationVolume%"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    notificationVolume == 0 -> ActiveAmber
                                    notificationVolume >= 90 -> GlowingEmerald
                                    else -> RadarCyan
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Volume slider
                    Slider(
                        value = notificationVolume.toFloat(),
                        onValueChange = { onUpdateNotificationVolume(it.roundToInt()) },
                        valueRange = 0f..100f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = if (notificationVolume == 0) ActiveAmber else RadarCyan,
                            activeTrackColor = if (notificationVolume == 0) ActiveAmber else RadarCyan,
                            inactiveTrackColor = SlateBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_volume_slider")
                    )

                    // Presets & Test Sound Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Presets: Mute, 50%, 80%, 100%
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                        ) {
                            listOf(0 to "Mute", 50 to "50%", 80 to "80%", 100 to "100%").forEach { (vol, label) ->
                                val isSelected = notificationVolume == vol
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) RadarCyan.copy(alpha = 0.25f) else CosmicSlateCard.copy(alpha = 0.6f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) RadarCyan else SlateBorder.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onUpdateNotificationVolume(vol) }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) RadarCyan else SecondarySlate,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Test Sound Action Button
                        Button(
                            onClick = onTestNotificationSound,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (notificationVolume == 0) SlateBorder else Color(0xFF2E3A52),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("test_notification_sound_button")
                        ) {
                            Text("🔔 Test", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Proximity Arrival Radar", fontSize = 12.sp, color = TextPrimary)
                    Text("${proximityDistanceMeters}m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RadarCyan)
                }
                Slider(
                    value = proximityDistanceMeters.toFloat(),
                    onValueChange = { onUpdateProximityDistance(it.toInt()) },
                    valueRange = 100f..1000f,
                    steps = 8,
                    colors = SliderDefaults.colors(thumbColor = RadarCyan, activeTrackColor = RadarCyan)
                )
            }
        }

        RoomAudioMonitorControls(members = members)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 5: SYSTEM & DIAGNOSTICS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SystemTabContent(
    isLocationPaused: Boolean,
    onToggleLocationPaused: (Boolean) -> Unit,
    cloudStatusText: String,
    activityLogs: List<ActivityLog>,
    onClearLogs: () -> Unit,
    onOpenFeedback: () -> Unit,
    onSelectTrip: ((com.example.data.CloudCompletedTrip) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isLocationPaused) ActiveAmber.copy(alpha = 0.08f) else CosmicSlateCard
            ),
            border = BorderStroke(1.dp, if (isLocationPaused) ActiveAmber.copy(alpha = 0.5f) else SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(if (isLocationPaused) "⏸️" else "🛰️", fontSize = 22.sp)
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Background GPS Tracking",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (isLocationPaused) ActiveAmber.copy(alpha = 0.15f) else GlowingEmerald.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isLocationPaused) "PAUSED" else "ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isLocationPaused) ActiveAmber else GlowingEmerald,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (isLocationPaused) "0% battery drain (Home mode)" else "Broadcasting live to circle",
                                fontSize = 11.sp,
                                color = SecondarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }

                Switch(
                    checked = !isLocationPaused,
                    onCheckedChange = { isTracking -> onToggleLocationPaused(!isTracking) },
                    colors = SwitchDefaults.colors(checkedTrackColor = GlowingEmerald)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Text("🖥️", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text("Ra Sync Server", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("api.cosmowhisper.com", fontSize = 11.sp, color = SecondarySlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Surface(color = GlowingEmerald.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "ONLINE • 200 OK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlowingEmerald,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Status: $cloudStatusText",
                    fontSize = 11.sp,
                    color = SecondarySlate
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📋", fontSize = 15.sp)
                        Text(
                            text = "Notification & Movement Logs",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${activityLogs.size} events recorded • Viewable via 🔔 on map",
                        fontSize = 11.sp,
                        color = SecondarySlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (activityLogs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearLogs,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("clear_logs_btn")
                    ) {
                        Text("Clear", fontSize = 11.sp, color = ErrorRed, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        val completedTrips by com.example.data.TelemetryWebSocketManager.completedTrips.collectAsState()
        TimelineLogs(
            logs = activityLogs,
            onClearLogs = onClearLogs,
            completedTrips = completedTrips,
            onSelectTrip = onSelectTrip
        )

        Button(
            onClick = onOpenFeedback,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF2)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("💬 Send Feedback & Suggestions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Kin Tracker v${com.example.BuildConfig.VERSION_NAME} • Ra Smart Server Architecture",
                    color = SecondarySlate.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Zero-friction background coordination • Encrypted local DB",
                    color = SecondarySlate.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
        }
    }
}
