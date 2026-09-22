package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
    onUpdateProfile: (name: String, color: String, emoji: String, phone: String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.CIRCLE) }

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
                            color = if (isSelected) Color.White else TextPrimary
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
                    onJoinGroupWithPin = onJoinGroupWithPin
                )
                SettingsTab.PROFILE -> ProfileTabContent(
                    name = myDeviceName,
                    colorHex = myDeviceColorHex,
                    emoji = myDeviceEmoji,
                    phone = myDevicePhone,
                    onUpdate = onUpdateProfile,
                    ghostModeExpiryTime = ghostModeExpiryTime,
                    onToggleGhostMode = onToggleGhostMode
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
                    onOpenFeedback = onOpenFeedback
                )
            }
        }
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
    onJoinGroupWithPin: (String) -> Unit
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GlowingEmerald))
                            Text("Family Circle", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Text("Connected to Ra Server • Real-Time Sync", fontSize = 11.sp, color = SecondarySlate)
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
                            Text("PIN $pinCode", fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = RadarCyan)
                            Icon(Icons.Default.Share, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = RadarCyan)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenAddDevice,
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ Pair New Phone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showJoinSheet = !showJoinSheet },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Switch Circle", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ── Connected Family Members ──
        Text(
            text = "CONNECTED FAMILY MEMBERS (${members.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SecondarySlate,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        members.forEach { member ->
            val isMe = member.id == "me" || member.id.endsWith("_$myDeviceUUID")
            val isOnline = (System.currentTimeMillis() - member.lastActive) < 15 * 60 * 1000L || isMe

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
                border = BorderStroke(1.dp, if (isMe) RadarCyan.copy(alpha = 0.4f) else SlateBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val parsedColor = try {
                            Color(android.graphics.Color.parseColor(member.avatarColorHex.ifBlank { "#0061A4" }))
                        } catch (e: Exception) { RadarCyan }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(parsedColor.copy(alpha = 0.15f))
                                .border(2.dp, parsedColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(member.avatarEmoji.ifBlank { "👤" }, fontSize = 20.sp)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(member.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                if (isMe) {
                                    Surface(color = RadarCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text("THIS PHONE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RadarCyan, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = member.statusText.ifBlank { "Live GPS" },
                                    fontSize = 11.sp,
                                    color = if (member.statusText.contains("Home", ignoreCase = true)) GlowingEmerald else SecondarySlate
                                )
                                Text("•", fontSize = 10.sp, color = SecondarySlate)
                                Text(
                                    text = if (isOnline) "Live now" else "Offline",
                                    fontSize = 11.sp,
                                    color = if (isOnline) GlowingEmerald else ActiveAmber
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (member.batteryPercentage > 20) Color(0xFFF1F8E9) else Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, if (member.batteryPercentage > 20) Color(0xFF81C784) else Color(0xFFE57373))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(if (member.isCharging) "⚡" else "🔋", fontSize = 11.sp)
                            Text("${member.batteryPercentage}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (member.batteryPercentage > 20) Color(0xFF2E7D32) else Color(0xFFC62828))
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
    onUpdate: (name: String, color: String, emoji: String, phone: String) -> Unit,
    ghostModeExpiryTime: Long,
    onToggleGhostMode: (Boolean) -> Unit
) {
    var nameState by remember(name) { mutableStateOf(name) }
    var phoneState by remember(phone) { mutableStateOf(phone) }
    var selectedColor by remember(colorHex) { mutableStateOf(colorHex) }
    var selectedEmoji by remember(emoji) { mutableStateOf(emoji) }

    val emojis = listOf("👨", "👩", "👧", "👦", "👵", "👴", "🐱", "🐶")
    val colors = listOf("#0061A4", "#AA22FF", "#EC407A", "#2E7D32", "#FF9800", "#00B4D8")

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Device Identity", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                OutlinedTextField(
                    value = nameState,
                    onValueChange = {
                        nameState = it
                        onUpdate(nameState, selectedColor, selectedEmoji, phoneState)
                    },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phoneState,
                    onValueChange = {
                        phoneState = it
                        onUpdate(nameState, selectedColor, selectedEmoji, phoneState)
                    },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Avatar Icon", fontSize = 12.sp, color = SecondarySlate)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        emojis.forEach { e ->
                            Surface(
                                onClick = {
                                    selectedEmoji = e
                                    onUpdate(nameState, selectedColor, selectedEmoji, phoneState)
                                },
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
                                    .clickable {
                                        selectedColor = c
                                        onUpdate(nameState, selectedColor, selectedEmoji, phoneState)
                                    }
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
            }
        }

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
                            Text("Ghost Mode (Privacy)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Text(
                            text = if (isGhostMode) "Active • $timeLeftString remaining" else "Temporarily hides your live location from circle",
                            fontSize = 11.sp,
                            color = if (isGhostMode) ActiveAmber else SecondarySlate
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Home Base Zone", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Surface(color = GlowingEmerald.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                    Text("AUTO-SYNCED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlowingEmerald, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            Text(String.format(java.util.Locale.US, "GPS: %.4f, %.4f", homeLat, homeLng), fontSize = 11.sp, color = SecondarySlate)
                        }
                    }

                    OutlinedButton(
                        onClick = onCalibrateHome,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Recalibrate", fontSize = 11.sp, color = RadarCyan, fontWeight = FontWeight.Bold)
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
                        Column {
                            Text("Work / School Zone", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                text = if (isWorkCalibrated) String.format(java.util.Locale.US, "GPS: %.4f, %.4f", workLat, workLng) else "Optional • Not configured",
                                fontSize = 11.sp,
                                color = SecondarySlate
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
                            ) { Text("Clear", fontSize = 10.sp, color = ErrorRed) }
                        }
                        Button(
                            onClick = onCalibrateWork,
                            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text(if (isWorkCalibrated) "Update" else "Set Work", fontSize = 10.sp) }
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
    onOpenFeedback: () -> Unit
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Background GPS Tracking", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                if (isLocationPaused) "PAUSED" else "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isLocationPaused) ActiveAmber else GlowingEmerald
                            )
                        }
                        Text(
                            text = if (isLocationPaused) "0% battery drain (Home mode)" else "Broadcasting live GPS to family circle",
                            fontSize = 11.sp,
                            color = SecondarySlate
                        )
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🖥️", fontSize = 18.sp)
                        Column {
                            Text("Ra Sync Server", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("api.cosmowhisper.com", fontSize = 11.sp, color = SecondarySlate)
                        }
                    }

                    Surface(color = GlowingEmerald.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text("ONLINE • 200 OK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowingEmerald, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
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
                Column {
                    Text("Activity History Logs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${activityLogs.size} recorded events", fontSize = 11.sp, color = SecondarySlate)
                }

                OutlinedButton(
                    onClick = onClearLogs,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Clear", fontSize = 11.sp, color = ErrorRed)
                }
            }
        }

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
                    text = "Kin Tracker v2.5 • Ra Smart Server Architecture",
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
