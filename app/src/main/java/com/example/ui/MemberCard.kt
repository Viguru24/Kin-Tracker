package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.data.formatTimeAgo
import com.example.data.formatDuration
import com.example.data.formatExactTime
import com.example.data.formatTransitBadge
import com.example.data.classifyTransitMode
import com.example.data.TransitMode
import com.example.data.RailwayTransitDetector
import com.example.data.RoomAudioStreamManager
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemberCard(
    member: FamilyMember,
    isSelected: Boolean,
    homeLat: Double,
    homeLng: Double,
    onSelectMember: (String?) -> Unit,
    onCommuteHome: (String) -> Unit,
    onSendAway: (String, String) -> Unit,
    onInstantCheckIn: (String) -> Unit,
    onPing: (String) -> Unit,
    onEditMember: (FamilyMember) -> Unit,
    onDeleteMember: (FamilyMember) -> Unit,
    onTriggerAlarm: (String) -> Unit,
    isRinging: Boolean = false,
    onToggleTracking: (String) -> Unit = {},
    onOpenWhatsApp: (FamilyMember) -> Unit,
    onTriggerSOS: () -> Unit,
    onSendReaction: (String, String) -> Unit
) {
    val avatarColor = try {
        Color(android.graphics.Color.parseColor(member.avatarColorHex))
    } catch (e: Exception) {
        RadarCyan
    }
    var showContextMenu by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isListening by RoomAudioStreamManager.isListening.collectAsState()
    val activeListeningId by RoomAudioStreamManager.activeListeningMemberId.collectAsState()
    val decibels by RoomAudioStreamManager.currentDecibels.collectAsState()
    val isListeningToThisMember = isListening && activeListeningId == member.id

    if (showBatteryDialog) {
        BatteryStatusDialog(
            member = member,
            onDismiss = { showBatteryDialog = false }
        )
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onSelectMember(if (isSelected) null else member.id) },
                    onLongClick = { showContextMenu = true }
                )
                .testTag("member_card_${member.id}"),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) SlateBorder else CosmicSlateCard
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isSelected) RadarCyan else SlateBorder.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Header info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        // Avatar circle with floating relative time badge above it
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Live relative time badge: how long ago was the last update
                            val isOfflineMember = member.id != "me" &&
                                member.lastActive > 0L &&
                                (System.currentTimeMillis() - member.lastActive) > 60_000L
                            val timeLabel = if (member.id == "me") "now"
                                            else if (member.lastActive > 0L) formatTimeAgo(member.lastActive)
                                            else "now"
                            val isStale = isOfflineMember

                            // Activity / Movement badge or Location duration (👣, 🚗, 🚲, 🚆, 📍)
                            val activityLabel = formatTransitBadge(member.speedMph, member.statusText, member.locationSince)

                            Text(
                                text = timeLabel,
                                color = if (isStale) Color(0xFFE53935) else RadarCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            if (activityLabel.isNotEmpty()) {
                                Text(
                                    text = activityLabel,
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(avatarColor, CircleShape)
                                    .clickable { showBatteryDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                if (member.photoPath.isNotEmpty() && File(member.photoPath).exists()) {
                                    val bitmap = remember(member.photoPath) {
                                        android.graphics.BitmapFactory.decodeFile(member.photoPath)
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Profile Photo",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else member.name.first().uppercase(),
                                            color = Color.White,
                                            fontSize = if (member.avatarEmoji.isNotBlank()) 18.sp else 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else member.name.first().uppercase(),
                                        color = Color.White,
                                        fontSize = if (member.avatarEmoji.isNotBlank()) 18.sp else 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Name & Status
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = member.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = member.statusText,
                            color = if (member.isComingHome) RadarCyan else SecondarySlate,
                            fontSize = 12.sp,
                            fontWeight = if (member.isComingHome) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (member.id != "me") {
                            Surface(
                                onClick = {
                                    if (isListeningToThisMember) {
                                        RoomAudioStreamManager.stopListening()
                                    } else {
                                        RoomAudioStreamManager.startListeningToMember(context, member.id, member.name)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isListeningToThisMember) Color(0xFFE53935) else Color(0xFF202534),
                                border = BorderStroke(1.dp, if (isListeningToThisMember) Color(0xFFFF5252) else SlateBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(if (isListeningToThisMember) "⏹️" else "🎧", fontSize = 10.sp)
                                    Text(
                                        text = if (isListeningToThisMember) "Stop" else "Audio",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // High-fidelity physical battery gauge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            if (member.isCharging) {
                                Text("⚡", fontSize = 11.sp, color = Color(0xFF00FF87))
                            }
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(13.dp)
                                    .border(1.dp, if (member.batteryPercentage <= 20) Color(0xFFE53935) else Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                    .padding(1.5.dp)
                            ) {
                                val barColor = when {
                                    member.isCharging -> Color(0xFF00FF87)
                                    member.batteryPercentage <= 20 -> Color(0xFFE53935)
                                    member.batteryPercentage <= 50 -> Color(0xFFFFB300)
                                    else -> Color(0xFF00FF87)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((member.batteryPercentage / 100f).coerceIn(0f, 1f))
                                        .background(barColor, RoundedCornerShape(1.dp))
                                )
                            }
                            Text(
                                text = "${member.batteryPercentage}%",
                                color = if (member.isCharging) Color(0xFF00FF87) else if (member.batteryPercentage <= 20) Color(0xFFE53935) else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (isListeningToThisMember) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F121C), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(if (decibels > 65f) ActiveAmber else GlowingEmerald, CircleShape))
                            Text(
                                text = "Live Audio (${member.name.substringBefore(" ")})",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${decibels.toInt()} dB",
                            color = RadarCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sub stats row
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Coordinates",
                            tint = SecondarySlate,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Lat: ${member.y.toString().take(7)}, Lng: ${member.x.toString().take(8)}",
                            color = SecondarySlate,
                            fontSize = 10.sp
                        )
                    }

                    val transitMode = classifyTransitMode(member.speedMph, member.statusText, member.id)
                    val transportInfo = getTransportInfo(member.speedMph, member.statusText, member.id)
                    val manualOverride = RailwayTransitDetector.getManualOverride(member.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(transportInfo.color.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .clickable {
                                val nextMode = when (manualOverride) {
                                    null -> if (transitMode == TransitMode.TRAIN) TransitMode.DRIVING else TransitMode.TRAIN
                                    TransitMode.TRAIN -> TransitMode.DRIVING
                                    TransitMode.DRIVING -> null
                                    else -> null
                                }
                                RailwayTransitDetector.setManualOverride(member.id, nextMode)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        AnimatedTransitIcon(mode = transitMode, size = 13.dp)
                        Text(
                            text = if (member.speedMph >= 0.6) "${String.format(java.util.Locale.US, "%.1f", member.speedMph)} mph (${transportInfo.label}${if (manualOverride != null) " • set" else ""})" else "Stationary",
                            color = transportInfo.color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }


                    val dist = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                    val isAtHome = dist <= 0.12 || member.statusText.contains("At Home", ignoreCase = true) || member.statusText.contains("at Home")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "ETA Home",
                            tint = if (member.isComingHome) RadarCyan else GlowingEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = when {
                                member.isComingHome -> "ETA: ${member.etaMinutes}m"
                                isAtHome -> "At Home"
                                else -> "Away"
                            },
                            color = when {
                                member.isComingHome -> RadarCyan
                                isAtHome -> GlowingEmerald
                                else -> SecondarySlate
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Collapsible action items
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DividerGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Live Tracking Actions (Simulator):",
                            color = SecondarySlate,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val dist = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                            val isAtHome = dist <= 0.12 || member.statusText.contains("At Home", ignoreCase = true) || member.statusText.contains("at Home")

                            Button(
                                onClick = { onPing(member.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Ping",
                                    tint = ActiveAmber,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ping circle", fontSize = 10.sp, color = TextPrimary)
                            }

                            if (!member.isComingHome && !isAtHome) {
                                Button(
                                    onClick = { onCommuteHome(member.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Commute Home",
                                        tint = RadarCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Set Route: Home", fontSize = 10.sp, color = TextPrimary)
                                }
                            }

                            Button(
                                onClick = {
                                    val dest = listOf("School", "Office", "Soccer Practice", "Gym", "Coffee Shop").random()
                                    onSendAway(member.id, dest)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Outside",
                                    tint = GlowingMagenta,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Outside", fontSize = 10.sp, color = TextPrimary)
                            }

                            if (!isAtHome) {
                                Button(
                                    onClick = { onInstantCheckIn(member.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Check-in Home",
                                        tint = GlowingEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Check-In Home", fontSize = 10.sp, color = TextPrimary)
                                }
                            }

                            Button(
                                onClick = { onEditMember(member) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp).testTag("edit_member_btn_${member.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Tracker",
                                    tint = RadarCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit info", fontSize = 10.sp, color = TextPrimary)
                            }

                            if (member.id != "me") {
                                Button(
                                    onClick = { onDeleteMember(member) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp).testTag("delete_member_btn_${member.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Tracker",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                     Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove", fontSize = 10.sp, color = TextPrimary)
                                }
                            }
                        }

                        // Transit Mode Quick Selector (Auto / Train / Car)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Transit:", fontSize = 10.sp, color = SecondarySlate, fontWeight = FontWeight.SemiBold)
                            val currentOverride = RailwayTransitDetector.getManualOverride(member.id)

                            FilterChip(
                                selected = currentOverride == null,
                                onClick = { RailwayTransitDetector.setManualOverride(member.id, null) },
                                label = { Text("🔄 Auto", fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryCosmic,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.height(26.dp)
                            )
                            FilterChip(
                                selected = currentOverride == TransitMode.TRAIN,
                                onClick = {
                                    RailwayTransitDetector.setManualOverride(
                                        member.id,
                                        if (currentOverride == TransitMode.TRAIN) null else TransitMode.TRAIN
                                    )
                                },
                                label = { Text("🚆 On Train", fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFAB47BC),
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.height(26.dp)
                            )
                            FilterChip(
                                selected = currentOverride == TransitMode.DRIVING,
                                onClick = {
                                    RailwayTransitDetector.setManualOverride(
                                        member.id,
                                        if (currentOverride == TransitMode.DRIVING) null else TransitMode.DRIVING
                                    )
                                },
                                label = { Text("🚗 In Car", fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ActiveAmber,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                }
            }
        }


        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .background(CosmicSlateCard)
                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(8.dp))
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = RadarCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Edit ${member.name}", color = TextPrimary, fontSize = 13.sp)
                    }
                },
                onClick = {
                    showContextMenu = false
                    onEditMember(member)
                }
            )
            HorizontalDivider(color = DividerGray)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💬", fontSize = 16.sp)
                        Text("WhatsApp ${member.name}", color = Color(0xFF25D366), fontSize = 13.sp)
                    }
                },
                onClick = {
                    showContextMenu = false
                    onOpenWhatsApp(member)
                }
            )
            HorizontalDivider(color = DividerGray)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🚨", fontSize = 16.sp)
                        Text("SOS / Trigger Distress Alert", color = Color(0xFFE53935), fontSize = 13.sp)
                    }
                },
                onClick = {
                    showContextMenu = false
                    onTriggerSOS()
                }
            )
            HorizontalDivider(color = DividerGray)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("👻", fontSize = 16.sp)
                        Text("Say Boo!", color = TextPrimary, fontSize = 13.sp)
                    }
                },
                onClick = {
                    showContextMenu = false
                    onSendReaction(member.id, "Boo 👻")
                }
            )
            HorizontalDivider(color = DividerGray)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("❤️", fontSize = 16.sp)
                        Text("Say Love ya!", color = TextPrimary, fontSize = 13.sp)
                    }
                },
                onClick = {
                    showContextMenu = false
                    onSendReaction(member.id, "Love ya ❤️")
                }
            )
            HorizontalDivider(color = DividerGray)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🐢", fontSize = 16.sp)
                        Text("Say Slow down!", color = TextPrimary, fontSize = 13.sp)
                    }
                },
                onClick = {
                    showContextMenu = false
                    onSendReaction(member.id, "Slow down 🐢")
                }
            )
            val isMemberPaused = member.isLocationPaused || member.statusText.contains("Paused", ignoreCase = true)
            HorizontalDivider(color = DividerGray)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (isMemberPaused) "👁️" else "⏸️", fontSize = 14.sp)
                        Text(
                            text = if (isMemberPaused) "Un-pause & Show on Screen" else "Pause & Remove from Screen",
                            color = if (isMemberPaused) Color(0xFF00FF87) else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                onClick = {
                    showContextMenu = false
                    onToggleTracking(member.id)
                }
            )

            if (member.id != "me") {
                val isRingingNow = isRinging || member.statusText == "🚨 ALARM"
                HorizontalDivider(color = DividerGray)
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Trigger Alarm",
                                tint = if (isRingingNow) ErrorRed else ActiveAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isRingingNow) "🔕 Stop Ringing Phone" else "Find Their Phone (🚨 Alarm)",
                                color = if (isRingingNow) ErrorRed else ActiveAmber,
                                fontSize = 13.sp,
                                fontWeight = if (isRingingNow) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    onClick = {
                        showContextMenu = false
                        onTriggerAlarm(member.id)
                    }
                )
            }
            if (member.id != "me") {
                HorizontalDivider(color = DividerGray)
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = ErrorRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Delete ${member.name}", color = ErrorRed, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        showContextMenu = false
                        onDeleteMember(member)
                    }
                )
            }
        }
    }
}

data class TransportDisplayInfo(
    val label: String,
    val iconEmoji: String,
    val iconVector: ImageVector,
    val color: Color
)

@Composable
fun getTransportInfo(speedMph: Double, statusText: String, memberId: String = ""): TransportDisplayInfo {
    val mode = classifyTransitMode(speedMph, statusText, memberId)
    return when (mode) {
        TransitMode.WALKING -> TransportDisplayInfo("Walking", "👣", Icons.AutoMirrored.Filled.DirectionsWalk, GlowingEmerald)
        TransitMode.CYCLING -> TransportDisplayInfo("Cycling", "🚲", Icons.AutoMirrored.Filled.DirectionsBike, RadarCyan)
        TransitMode.DRIVING -> TransportDisplayInfo("Driving", "🚗", Icons.Filled.DirectionsCar, ActiveAmber)
        TransitMode.TRAIN -> TransportDisplayInfo("Train", "🚆", Icons.Filled.DirectionsTransit, Color(0xFFAB47BC))
        TransitMode.STATIONARY -> TransportDisplayInfo("Stationary", "📍", Icons.Filled.Person, SecondarySlate)
    }
}


@Composable
fun AnimatedTransitIcon(
    mode: TransitMode,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "transit_anim")

    when (mode) {
        TransitMode.WALKING -> {
            val rotation by infiniteTransition.animateFloat(
                initialValue = -12f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "walk_rot"
            )
            val translateY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -3.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(225, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "walk_bounce"
            )
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        rotationZ = rotation
                        translationY = translateY
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("👣", fontSize = (size.value * 0.9f).sp)
            }
        }
        TransitMode.CYCLING -> {
            val rotation by infiniteTransition.animateFloat(
                initialValue = -14f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(380, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bike_rot"
            )
            val translateY by infiniteTransition.animateFloat(
                initialValue = -1.5f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(190, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bike_bounce"
            )
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        rotationZ = rotation
                        translationY = translateY
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🚲", fontSize = (size.value * 0.9f).sp)
            }
        }
        TransitMode.DRIVING -> {
            val translateX by infiniteTransition.animateFloat(
                initialValue = -2.5f,
                targetValue = 2.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(280, easing = FastOutLinearInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "car_glide"
            )
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(140, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "car_pulse"
            )
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        translationX = translateX
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🚗", fontSize = (size.value * 0.9f).sp)
            }
        }
        TransitMode.TRAIN -> {
            val translateX by infiniteTransition.animateFloat(
                initialValue = -3.5f,
                targetValue = 3.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "train_glide"
            )
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        translationX = translateX
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🚆", fontSize = (size.value * 0.9f).sp)
            }
        }
        TransitMode.STATIONARY -> {
            Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
                Text("📍", fontSize = (size.value * 0.9f).sp)
            }
        }
    }
}
