package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncControls(
    isCloudSyncEnabled: Boolean,
    groupSyncToken: String,
    myDeviceName: String,
    myDeviceColorHex: String,
    myDeviceEmoji: String,
    myDevicePhone: String = "",
    cloudStatusText: String,
    onToggleCloudSync: (enabled: Boolean, token: String, myName: String, myColor: String, myEmoji: String, myPhone: String) -> Unit,
    onGenerateGroupKey: () -> Unit,
    isUserSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String,
    onSignIn: (name: String, email: String) -> Unit,
    onSignOut: () -> Unit,
    ghostModeExpiryTime: Long,
    onToggleGhostMode: (Boolean) -> Unit,
    groupPinMappings: List<com.example.data.GroupPinMapping> = emptyList(),
    activeGroupPinCode: String = "",
    onCreateGroupWithPin: (String) -> Unit = {},
    onJoinGroupWithPin: (String) -> Unit = {},
    onDeleteGroupPinFromHistory: (com.example.data.GroupPinMapping) -> Unit = {},
    members: List<com.example.data.FamilyMember> = emptyList(),
    activeGroupCreatorId: String = "",
    myDeviceUUID: String = "",
    onKickMember: (String) -> Unit = {},
    onUpdateActiveGroupSettings: (String, String) -> Unit = { _, _ -> },
    onSelectActiveCircle: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tokenInput by remember { mutableStateOf(groupSyncToken) }
    var nameInput by remember { mutableStateOf(myDeviceName) }
    var phoneInput by remember { mutableStateOf(myDevicePhone) }
    var selectedColorHex by remember { mutableStateOf(myDeviceColorHex) }
    var selectedEmoji by remember(myDeviceEmoji) { mutableStateOf(myDeviceEmoji) }
    var expandedSetup by remember { mutableStateOf(false) }

    val isGhostMode = System.currentTimeMillis() < ghostModeExpiryTime
    var timeLeftString by remember { mutableStateOf("") }
    LaunchedEffect(ghostModeExpiryTime) {
        while (System.currentTimeMillis() < ghostModeExpiryTime) {
            val diffMs = ghostModeExpiryTime - System.currentTimeMillis()
            val hours = diffMs / 3600000
            val minutes = (diffMs % 3600000) / 60000
            val seconds = (diffMs % 60000) / 1000
            timeLeftString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            kotlinx.coroutines.delay(1000L)
        }
        timeLeftString = ""
    }

    var authNameInput by remember { mutableStateOf("") }
    var authEmailInput by remember { mutableStateOf("") }

    // Forms for PIN circle addition/generation
    var pinToJoinInput by remember { mutableStateOf("") }
    var groupToCreateNameInput by remember { mutableStateOf("") }
    var activeTabCreateGroup by remember { mutableStateOf(false) } // False = Join, True = Create

    LaunchedEffect(groupSyncToken) {
        if (groupSyncToken.isNotBlank() && tokenInput != groupSyncToken) {
            tokenInput = groupSyncToken
        }
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val colorsList = listOf(
        "#AA22FF", // High contrast neon purple
        "#EC407A", // Magenta Pink
        "#26A69A", // Teal
        "#42A5F5", // Cyan Blue
        "#FF9800", // Gold/Orange
        "#00FF87"  // Neon green
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_sync_controls_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(
            1.dp,
            if (isGhostMode) ActiveAmber.copy(alpha = 0.6f) else GlowingEmerald.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // MAIN PROFILE STATS & IDENTITY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                if (isUserSignedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(selectedColorHex))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = selectedEmoji, fontSize = 18.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userDisplayName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = userEmail,
                                    color = SecondarySlate,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GlowingEmerald, CircleShape)
                            )
                            Text(
                                text = "LINKED",
                                color = GlowingEmerald,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSignIn(authNameInput, authEmailInput) },
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "OFFLINE - TAP TO SET UP YOUR ACCOUNT",
                            color = ActiveAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Header Row: Cloud Status
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isGhostMode) ActiveAmber else GlowingEmerald,
                                CircleShape
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MAP SHARING WITH FAMILY",
                            color = if (isGhostMode) ActiveAmber else GlowingEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (isGhostMode) "SHARING GHOSTED" else "SHARING ACTIVE",
                            color = if (isGhostMode) ActiveAmber else RadarCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Button(
                    onClick = { expandedSetup = !expandedSetup },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (expandedSetup) "Hide" else "Setup",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Short overview showing active sync details when collapsed
            if (!expandedSetup) {
                var collapsedSetupViewMode by remember { mutableIntStateOf(0) } // 0 = Buttons, 1 = Create form, 2 = Join form
                var localGroupNameInput by remember { mutableStateOf("") }

                if (activeGroupPinCode.isBlank()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "You are currently not connected to any group. Create a private group to get a 4-digit PIN, or enter a PIN to join instantly.",
                            color = SecondarySlate,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        if (collapsedSetupViewMode == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        collapsedSetupViewMode = 1
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GlowingEmerald),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("👑 Create New Group", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        collapsedSetupViewMode = 2
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("👥 Join Group with PIN", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (collapsedSetupViewMode == 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = localGroupNameInput,
                                    onValueChange = { localGroupNameInput = it },
                                    label = { Text("Group Name") },
                                    placeholder = { Text("e.g. My Family") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = GlowingEmerald,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        val finalName = localGroupNameInput.trim().ifBlank { "Family Circle" }
                                        onCreateGroupWithPin(finalName)
                                        localGroupNameInput = ""
                                        collapsedSetupViewMode = 0
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GlowingEmerald),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Create 👑", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { collapsedSetupViewMode = 0 }
                                ) {
                                    Text("Cancel", color = SecondarySlate, fontSize = 11.sp)
                                }
                            }
                        } else if (collapsedSetupViewMode == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinToJoinInput,
                                    onValueChange = { input ->
                                        if (input.length <= 4 && input.all { it.isDigit() }) {
                                            pinToJoinInput = input
                                        }
                                    },
                                    label = { Text("Enter 4-Digit PIN") },
                                    placeholder = { Text("e.g. 1234") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        if (pinToJoinInput.length == 4) {
                                            onJoinGroupWithPin(pinToJoinInput)
                                            pinToJoinInput = ""
                                            collapsedSetupViewMode = 0
                                        }
                                    },
                                    enabled = pinToJoinInput.length == 4,
                                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Join 👥", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { collapsedSetupViewMode = 0 }
                                ) {
                                    Text("Cancel", color = SecondarySlate, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "You are active in a permanent sharing group circle. Share this PIN with family members to let them join your map.",
                            color = SecondarySlate,
                            fontSize = 10.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ACTIVE GROUP PIN",
                                    color = SecondarySlate,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = activeGroupPinCode,
                                    color = RadarCyan,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            clipboardManager.setText(AnnotatedString(activeGroupPinCode))
                                            val inviteText = "Hey! I've set up Pulse Tracker so we can see each other on a live map. Download the app, tap \"Join Group with PIN\" and enter this 4-digit PIN:\n\n$activeGroupPinCode"
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://api.whatsapp.com/send?text=" + android.net.Uri.encode(inviteText))
                                            )
                                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            clipboardManager.setText(AnnotatedString(activeGroupPinCode))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("💬 Share PIN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(activeGroupPinCode))
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(SlateBorder, RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Copy code",
                                        tint = RadarCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Ghost Mode card
            GhostModeCard(
                ghostModeExpiryTime = ghostModeExpiryTime,
                onToggleGhostMode = onToggleGhostMode
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = expandedSetup) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // NEW TAB SWITCHER FOR JOIN OR CREATE WITH PIN
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!activeTabCreateGroup) PrimaryCosmic else Color.Transparent)
                                .clickable { activeTabCreateGroup = false }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Join with PIN 👥",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTabCreateGroup) PrimaryCosmic else Color.Transparent)
                                .clickable { activeTabCreateGroup = true }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Group with PIN 👑",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!activeTabCreateGroup) {
                        // JOIN CIRCLE VIEW
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Enter a 4-digit PIN generated by a family creator to link to their map permanently.",
                                color = SecondarySlate,
                                fontSize = 10.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinToJoinInput,
                                    onValueChange = { if (it.length <= 4) pinToJoinInput = it },
                                    label = { Text("4-digit PIN") },
                                    placeholder = { Text("e.g. 5729") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (pinToJoinInput.length == 4) {
                                            onJoinGroupWithPin(pinToJoinInput)
                                            pinToJoinInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Join 👥", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // CREATE CIRCLE VIEW
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Instantiate a new private circle! A unique 4-digit PIN is generated instantly, linking other devices seamlessly.",
                                color = SecondarySlate,
                                fontSize = 10.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = groupToCreateNameInput,
                                    onValueChange = { groupToCreateNameInput = it },
                                    label = { Text("Circle / Group Name") },
                                    placeholder = { Text("e.g. My Family") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (groupToCreateNameInput.isNotBlank()) {
                                            onCreateGroupWithPin(groupToCreateNameInput)
                                            groupToCreateNameInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GlowingEmerald),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Create 👑", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // PIN HISTORY DATABASE VIEWER
                    if (groupPinMappings.isNotEmpty()) {
                        Text(
                            text = "My PIN Group Registry Database",
                            fontSize = 11.sp,
                            color = SecondarySlate,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth()
                            ) {
                                groupPinMappings.forEach { mapping ->
                                    val isActive = groupSyncToken == mapping.groupToken
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isActive) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                                            .clickable {
                                                onSelectActiveCircle(mapping.pinCode)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(if (mapping.isOwner) "👑" else "👥", fontSize = 14.sp)
                                            Column {
                                                Text(
                                                    text = mapping.groupName,
                                                    color = TextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "PIN: ${mapping.pinCode}",
                                                    color = RadarCyan,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(GlowingEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text("ACTIVE", color = GlowingEmerald, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            IconButton(
                                                onClick = { onDeleteGroupPinFromHistory(mapping) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ACTIVE GROUP CUSTOMIZATION & MEMBER MANAGEMENT (Louis' requested features)
                    if (activeGroupPinCode.isNotBlank()) {
                        val activeMapping = groupPinMappings.firstOrNull { it.groupToken == groupSyncToken }
                        val isAmIOwner = activeMapping?.isOwner == true || activeGroupCreatorId == myDeviceUUID

                        Text(
                            text = "Active Circle Configuration",
                            fontSize = 11.sp,
                            color = SecondarySlate,
                            fontWeight = FontWeight.Bold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var editGroupName by remember(activeMapping?.groupName) { mutableStateOf(activeMapping?.groupName ?: "Family Circle") }
                                var editPinCode by remember(activeGroupPinCode) { mutableStateOf(activeGroupPinCode) }

                                OutlinedTextField(
                                    value = editGroupName,
                                    onValueChange = { editGroupName = it },
                                    label = { Text("Circle / Group Name") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = editPinCode,
                                    onValueChange = { input ->
                                        if (input.length <= 4 && input.all { it.isDigit() }) {
                                            editPinCode = input
                                        }
                                    },
                                    label = { Text("4-Digit Access PIN") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        if (editPinCode.length == 4) {
                                            onUpdateActiveGroupSettings(editGroupName.trim(), editPinCode)
                                        }
                                    },
                                    enabled = editPinCode.length == 4 && (editGroupName != activeMapping?.groupName || editPinCode != activeGroupPinCode),
                                    colors = ButtonDefaults.buttonColors(containerColor = GlowingEmerald),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text("Apply & Update Group Settings", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // CIRCLE GROUP MEMBERS LIST & KICK FUNCTIONALITY
                        val activeMembers = members.filter { it.id != "me" && it.id != myDeviceUUID }
                        Text(
                            text = "Circle Members (${activeMembers.size + 1} devices connected)",
                            fontSize = 11.sp,
                            color = SecondarySlate,
                            fontWeight = FontWeight.Bold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                // 1. Render myself at the top of the list
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(selectedColorHex))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(selectedEmoji, fontSize = 12.sp)
                                        }
                                        Column {
                                            Text(
                                                text = "$nameInput (You)",
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isAmIOwner) "Group Owner • Active" else "Member • Active",
                                                color = GlowingEmerald,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // 2. Render other group members dynamically
                                activeMembers.forEach { member ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(android.graphics.Color.parseColor(member.avatarColorHex))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(member.avatarEmoji.ifBlank { "👤" }, fontSize = 12.sp)
                                            }
                                            Column {
                                                Text(
                                                    text = member.name,
                                                    color = TextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${member.batteryPercentage}% • Speed: ${member.speedMph} mph",
                                                    color = SecondarySlate,
                                                    fontSize = 8.sp
                                                )
                                            }
                                        }

                                        if (isAmIOwner) {
                                            Button(
                                                onClick = { onKickMember(member.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(24.dp)
                                            ) {
                                                Text("🥾 Kick", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    // Device Display Name on Map
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("My Device Name on Map List") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_device_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder
                        ),
                        singleLine = true
                    )

                    // My Phone Number (WhatsApp)
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("My Phone Number (WhatsApp)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_device_phone_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder
                        ),
                        singleLine = true
                    )

                    // Color picker palette
                    Text(
                        text = "My Personal Map Pin Color Accent",
                        fontSize = 11.sp,
                        color = SecondarySlate,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorsList.forEach { col ->
                            val isSel = selectedColorHex == col
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(col)))
                                    .border(
                                        width = if (isSel) 2.5.dp else 0.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = col }
                            )
                        }
                    }

                    // Profile Picture Emoji selector grid
                    Text(text = "My Personal Profile Picture Emoji", fontSize = 11.sp, color = SecondarySlate, fontWeight = FontWeight.Bold)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val userEmojisList = listOf("👨", "👨‍💻", "👩", "👩‍💻", "👦", "👧", "👶", "👵", "👴", "🐱", "🐶", "🚗", "🚲", "🏡", "🦊", "🦸")
                        val chunks = userEmojisList.chunked(8)
                        chunks.forEach { rowEmojis ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowEmojis.forEach { emo ->
                                    val isSel = selectedEmoji == emo
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) PrimaryCosmic else Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = if (isSel) 1.5.dp else 1.dp,
                                                color = if (isSel) RadarCyan else SlateBorder,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedEmoji = emo },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emo, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Apply and Synchronize Background button
                    Button(
                        onClick = {
                            onToggleCloudSync(true, tokenInput, nameInput, selectedColorHex, selectedEmoji, phoneInput)
                            expandedSetup = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("apply_sync_custom_btn")
                    ) {
                        Text(
                            text = "Save Device Setup Sync Settings",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    }
            }
        }
    }
}
