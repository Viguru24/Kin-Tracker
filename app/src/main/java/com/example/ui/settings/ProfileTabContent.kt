package com.example.ui.settings

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ProfileTabContent(
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
    val colors = listOf("#0061A4", "#10B981", "#EC407A", "#26A69A", "#42A5F5", "#FF9800", "#00FF87", "#14B8A6")

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

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

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
