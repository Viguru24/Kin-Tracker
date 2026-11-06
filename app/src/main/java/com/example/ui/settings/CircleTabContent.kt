package com.example.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.theme.*

@Composable
fun CircleTabContent(
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
    onOpenGoogleMaps: ((FamilyMember) -> Unit)? = null,
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
                    .clickable {
                        if (onOpenGoogleMaps != null && (member.x != 0.0 || member.y != 0.0)) {
                            onOpenGoogleMaps(member)
                        } else {
                            onEditMember(member)
                        }
                    },
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

                        if (onOpenGoogleMaps != null && (member.x != 0.0 || member.y != 0.0)) {
                            IconButton(
                                onClick = { onOpenGoogleMaps(member) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Text("🗺️", fontSize = 14.sp)
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
