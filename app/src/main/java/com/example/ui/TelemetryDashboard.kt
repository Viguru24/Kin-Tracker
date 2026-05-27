package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.data.FamilyMember
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelemetryDashboard(
    members: List<FamilyMember>,
    selectedMemberId: String?,
    onSelectMember: (String?) -> Unit,
    onCommuteHome: (String) -> Unit,
    onSendAway: (String, String) -> Unit,
    onInstantCheckIn: (String) -> Unit,
    onPing: (String) -> Unit,
    onUpdateMember: (FamilyMember) -> Unit,
    onDeleteMember: (String) -> Unit,
    modifier: Modifier = Modifier,
    homeLat: Double = 51.332308,
    homeLng: Double = -0.117188
) {
    var memberToEdit by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToDelete by remember { mutableStateOf<FamilyMember?>(null) }

    val colorsList = listOf(
        "#EC407A", // Magenta Pink
        "#26A69A", // Teal
        "#42A5F5", // Cyan Blue
        "#FF9800", // Gold/Orange
        "#FFEA00", // Yellow-Glow
        "#E040FB", // Hot violet
        "#00FF87"  // Neon green
    )

    // Edit Dialog Composable
    memberToEdit?.let { member ->
        val context = LocalContext.current
        var editName by remember(member.id) { mutableStateOf(member.name) }
        var editStatus by remember(member.id) { mutableStateOf(member.statusText) }
        var editBattery by remember(member.id) { mutableStateOf(member.batteryPercentage.toFloat()) }
        var editCharging by remember(member.id) { mutableStateOf(member.isCharging) }
        var editSpeed by remember(member.id) { mutableStateOf(member.speedMph.toString()) }
        var editEta by remember(member.id) { mutableStateOf(member.etaMinutes.toString()) }
        var editColorHex by remember(member.id) { mutableStateOf(member.avatarColorHex) }
        var editEmoji by remember(member.id) { mutableStateOf(member.avatarEmoji) }
        var editPhone by remember(member.id) { mutableStateOf(member.phoneNumber) }
        var editPhotoPath by remember(member.id) { mutableStateOf(member.photoPath) }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                val localFile = saveUriToInternalStorage(context, uri)
                if (localFile != null) {
                    editPhotoPath = localFile.absolutePath
                }
            }
        }

        AlertDialog(
            onDismissRequest = { memberToEdit = null },
            title = {
                Text(
                    text = "Edit Tracker Settings",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Name
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name", fontSize = 11.sp, color = SecondarySlate) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder,
                            cursorColor = RadarCyan,
                            focusedLabelColor = RadarCyan,
                            unfocusedLabelColor = SecondarySlate
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(62.dp).testTag("edit_name_input")
                    )

                    // Profile Photo selection
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current photo/avatar preview
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(editColorHex)).copy(alpha = 0.2f))
                                .border(1.5.dp, Color(android.graphics.Color.parseColor(editColorHex)), CircleShape)
                                .clickable { launcher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (editPhotoPath.isNotEmpty() && java.io.File(editPhotoPath).exists()) {
                                val bitmap = remember(editPhotoPath) {
                                    android.graphics.BitmapFactory.decodeFile(editPhotoPath)
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(text = editEmoji, fontSize = 20.sp)
                                }
                            } else {
                                Text(text = editEmoji, fontSize = 20.sp)
                            }
                        }
                        
                        Button(
                            onClick = { launcher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Choose Photo", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        if (editPhotoPath.isNotEmpty()) {
                            TextButton(
                                onClick = { editPhotoPath = "" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Remove", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Phone Number
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number (WhatsApp)", fontSize = 11.sp, color = SecondarySlate) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder,
                            cursorColor = RadarCyan,
                            focusedLabelColor = RadarCyan,
                            unfocusedLabelColor = SecondarySlate
                        ),
                        singleLine = true,
                        placeholder = { Text("+447803171262", color = SecondarySlate.copy(alpha = 0.5f), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().height(62.dp).testTag("edit_phone_input")
                    )

                    // Status
                    OutlinedTextField(
                        value = editStatus,
                        onValueChange = { editStatus = it },
                        label = { Text("Current Status Text", fontSize = 11.sp, color = SecondarySlate) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder,
                            cursorColor = RadarCyan,
                            focusedLabelColor = RadarCyan,
                            unfocusedLabelColor = SecondarySlate
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(62.dp).testTag("edit_status_input")
                    )

                    // Battery Slider / Checkbox Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Battery Level: ${editBattery.toInt()}%",
                                color = SecondarySlate,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Charging", color = SecondarySlate, fontSize = 11.sp)
                                Checkbox(
                                    checked = editCharging,
                                    onCheckedChange = { editCharging = it },
                                    colors = CheckboxDefaults.colors(checkedColor = RadarCyan),
                                    modifier = Modifier.testTag("edit_charging_cb")
                                )
                            }
                        }
                        Slider(
                            value = editBattery,
                            onValueChange = { editBattery = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = RadarCyan,
                                activeTrackColor = RadarCyan,
                                inactiveTrackColor = SlateBorder
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("edit_battery_slider")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Speed
                        OutlinedTextField(
                            value = editSpeed,
                            onValueChange = { editSpeed = it },
                            label = { Text("Speed (mph)", fontSize = 11.sp, color = SecondarySlate) },
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder,
                                cursorColor = RadarCyan,
                                focusedLabelColor = RadarCyan
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(62.dp).testTag("edit_speed_input")
                        )

                        // ETA
                        OutlinedTextField(
                            value = editEta,
                            onValueChange = { editEta = it },
                            label = { Text("ETA (mins)", fontSize = 11.sp, color = SecondarySlate) },
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder,
                                cursorColor = RadarCyan,
                                focusedLabelColor = RadarCyan
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(62.dp).testTag("edit_eta_input")
                        )
                    }

                    // Color Picker Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Radar Color Tag:", color = SecondarySlate, fontSize = 11.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            colorsList.forEach { rgbHex ->
                                val rgbColor = try {
                                    Color(android.graphics.Color.parseColor(rgbHex))
                                } catch (e: Exception) {
                                    Color(0xFF26A69A)
                                }
                                val isChosen = rgbHex == editColorHex
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(rgbColor, CircleShape)
                                        .border(
                                            width = if (isChosen) 2.dp else 0.dp,
                                            color = if (isChosen) TextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { editColorHex = rgbHex }
                                )
                            }
                        }
                    }

                    // Profile Picture Emoji Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Profile Picture Icon:", color = SecondarySlate, fontSize = 11.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            val editEmojisList = listOf("👨", "👩", "👦", "👧", "👶", "👵", "👴", "🐱", "🐶", "🚗", "🚲", "🏡", "🦊", "🐼", "🦸", "🚀")
                            val chunks = editEmojisList.chunked(8)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                chunks.forEach { chunk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        chunk.forEach { emo ->
                                            val isSelected = editEmoji == emo
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(if (isSelected) PrimaryCosmic else Color.White.copy(alpha = 0.05f), CircleShape)
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) RadarCyan else SlateBorder,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { editEmoji = emo },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = emo, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            val parsedSpeed = editSpeed.toDoubleOrNull() ?: member.speedMph
                            val parsedEta = editEta.toIntOrNull() ?: member.etaMinutes
                            val updated = member.copy(
                                name = editName,
                                statusText = editStatus,
                                batteryPercentage = editBattery.toInt(),
                                isCharging = editCharging,
                                speedMph = parsedSpeed,
                                etaMinutes = parsedEta,
                                avatarColorHex = editColorHex,
                                avatarEmoji = editEmoji,
                                phoneNumber = editPhone,
                                photoPath = editPhotoPath
                            )
                            onUpdateMember(updated)
                            memberToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                    shape = RoundedCornerShape(8.dp),
                    enabled = editName.isNotBlank(),
                    modifier = Modifier.testTag("save_edit_btn")
                ) {
                    Text("Save Changes", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { memberToEdit = null },
                    modifier = Modifier.testTag("cancel_edit_btn")
                ) {
                    Text("Cancel", color = SecondarySlate, fontSize = 12.sp)
                }
            },
            containerColor = CosmicSlateCard,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    // Delete Confirmation Dialog
    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = {
                Text(
                    text = "Remove Tracker Signal?",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently detach tracking and remove `${member.name}` from the active family radar?\nThis will disconnect cellular diagnostic relays.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMember(member.id)
                        memberToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text("Remove Tracker", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { memberToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_btn")
                ) {
                    Text("Cancel", color = SecondarySlate, fontSize = 12.sp)
                }
            },
            containerColor = CosmicSlateCard,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Family Members (${members.size})",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            
            if (selectedMemberId != null) {
                TextButton(
                    onClick = { onSelectMember(null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = RadarCyan),
                    modifier = Modifier.testTag("clear_selection_btn")
                ) {
                    Text("Clear Selection", fontSize = 11.sp)
                }
            }
        }

        if (members.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RadarCyan)
            }
        } else {
            members.forEach { member ->
                val isSelected = member.id == selectedMemberId
                val avatarColor = try {
                    Color(android.graphics.Color.parseColor(member.avatarColorHex))
                } catch (e: Exception) {
                    Color(0xFF26A69A) // Emerald safety fallback
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectMember(if (isSelected) null else member.id) }
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
                            // Avatar circle
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(avatarColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (member.photoPath.isNotEmpty() && java.io.File(member.photoPath).exists()) {
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

                            // High-fidelity physical battery gauge (capsule-styled)
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

                        // Sub stats row
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Map coordinates or action indicators
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

                            // Speed or Status
                            val transportInfo = getTransportInfo(member.speedMph, member.statusText)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(transportInfo.third.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = transportInfo.second,
                                    contentDescription = transportInfo.first,
                                    tint = transportInfo.third,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (member.speedMph > 0.0) "${member.speedMph} mph (${transportInfo.first})" else "Stationary",
                                    color = transportInfo.third,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Dynamic ETA home indicator
                            val dist = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                            val isAtHome = dist < 0.05 || member.statusText.contains("At Home")
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

                        // Collapsible action items for selected members
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

                                 FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                 ) {
                                    val dist = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                                    val isAtHome = dist < 0.05 || member.statusText.contains("At Home")

                                    // 1. Send Ping Alert Button
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

                                    // 2. Call Home / Commute Home Button
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

                                    // 3. Send Away Button
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

                                    // 4. Instant Check-In Button
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

                                    // 5. Edit Button
                                    Button(
                                        onClick = { memberToEdit = member },
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

                                    // 6. Delete Button (Hide for self unit "me" for system stability)
                                    if (member.id != "me") {
                                        Button(
                                            onClick = { memberToDelete = member },
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
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper utility to deduce the active transport mode based on speed and status indicators
@Composable
fun getTransportInfo(speedMph: Double, statusText: String): Triple<String, androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val statusLower = statusText.lowercase()
    return when {
        speedMph <= 0.15 -> {
            Triple("Stationary", Icons.Filled.Person, SecondarySlate)
        }
        statusLower.contains("train") || statusLower.contains("transit") || statusLower.contains("rail") || speedMph > 45.0 -> {
            Triple("Train", Icons.Filled.DirectionsTransit, Color(0xFFAB47BC)) // Purple transit color
        }
        statusLower.contains("bike") || statusLower.contains("bicycle") || statusLower.contains("cycle") || (speedMph > 4.5 && speedMph <= 15.0) -> {
            Triple("Biking", Icons.AutoMirrored.Filled.DirectionsBike, RadarCyan) // Theme cyan-blue biking color
        }
        statusLower.contains("walk") || statusLower.contains("foot") || statusLower.contains("hiking") || statusLower.contains("run") || (speedMph > 0.15 && speedMph <= 4.5) -> {
            Triple("Walking", Icons.AutoMirrored.Filled.DirectionsWalk, GlowingEmerald) // Emerald green walking color
        }
        else -> {
            Triple("Driving", Icons.Filled.DirectionsCar, ActiveAmber) // Amber gold driving color
        }
    }
}

private fun saveUriToInternalStorage(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "profile_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
