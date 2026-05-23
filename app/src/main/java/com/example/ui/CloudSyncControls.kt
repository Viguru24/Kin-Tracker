package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    cloudStatusText: String,
    onToggleCloudSync: (enabled: Boolean, token: String, myName: String, myColor: String) -> Unit,
    onGenerateGroupKey: () -> Unit,
    isUserSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String,
    onSignIn: (name: String, email: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tokenInput by remember { mutableStateOf(groupSyncToken) }
    var nameInput by remember { mutableStateOf(myDeviceName) }
    var selectedColorHex by remember { mutableStateOf(myDeviceColorHex) }
    var expandedSetup by remember { mutableStateOf(false) }

    // local inputs for sign-in fields
    var authNameInput by remember { mutableStateOf("Louis de Souza") }
    var authEmailInput by remember { mutableStateOf("louisdesouza@gmail.com") }

    // Synchronize inputs with ViewModel state
    LaunchedEffect(groupSyncToken) {
        if (groupSyncToken.isNotBlank() && tokenInput != groupSyncToken) {
            tokenInput = groupSyncToken
        }
    }

    val clipboardManager = LocalClipboardManager.current

    val colorsList = listOf(
        "#AA22FF", // High contrast neon purple (default)
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
        border = BorderStroke(1.dp, if (isCloudSyncEnabled) GlowingEmerald.copy(alpha = 0.5f) else SlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // SECTION: USER ACCOUNT AUTHENTICATION PANEL
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Circular User Avatar Profile Blip
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(android.graphics.Color.parseColor(myDeviceColorHex)), CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userDisplayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Column {
                                Text(
                                    text = userDisplayName,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userEmail,
                                    color = SecondarySlate,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Transparent high-contrast modern Sign Out button
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("sign_out_button")
                        ) {
                            Text(
                                text = "Sign Out",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ErrorRed
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(ActiveAmber, CircleShape)
                            )
                            Text(
                                text = "OFFLINE PROFILE - ACCOUNT SIGN IN",
                                color = ActiveAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Form for Sign-In
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedTextField(
                                    value = authNameInput,
                                    onValueChange = { authNameInput = it },
                                    label = { Text("Display Name", fontSize = 9.sp, color = SecondarySlate) },
                                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder,
                                        cursorColor = RadarCyan
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                )

                                OutlinedTextField(
                                    value = authEmailInput,
                                    onValueChange = { authEmailInput = it },
                                    label = { Text("Account Email", fontSize = 9.sp, color = SecondarySlate) },
                                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder,
                                        cursorColor = RadarCyan
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                )
                            }

                            // Dynamic Sign In Trigger Button
                            Button(
                                onClick = {
                                    if (authNameInput.isNotBlank() && authEmailInput.isNotBlank()) {
                                        onSignIn(authNameInput, authEmailInput)
                                        nameInput = authNameInput
                                    }
                                },
                                enabled = authNameInput.isNotBlank() && authEmailInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(80.dp)
                                    .width(76.dp)
                                    .testTag("sign_in_button")
                            ) {
                                Text(
                                    text = "Sign In",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Header Row: Cloud Status and Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isCloudSyncEnabled) GlowingEmerald else SecondarySlate,
                                CircleShape
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    )
                    Column {
                        Text(
                            text = "FAMILY INTERCOM & CLOUD SYNC",
                            color = if (isCloudSyncEnabled) GlowingEmerald else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = cloudStatusText,
                            color = if (isCloudSyncEnabled) RadarCyan else SecondarySlate,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Expand settings button
                Button(
                    onClick = { expandedSetup = !expandedSetup },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (expandedSetup) "Close" else "Setup Sync",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Short overview showing active sync details when collapsed
            if (!expandedSetup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Identity: $myDeviceName",
                            color = SecondarySlate,
                            fontSize = 11.sp
                        )
                        if (groupSyncToken.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Group Code: $groupSyncToken",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy code",
                                    tint = RadarCyan,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(groupSyncToken))
                                        }
                                )
                            }
                        } else {
                            Text(
                                text = "No active sync room code.",
                                color = SecondarySlate,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Quick Switch Toggle
                    Switch(
                        checked = isCloudSyncEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (tokenInput.isBlank()) {
                                    expandedSetup = true
                                } else {
                                    onToggleCloudSync(true, tokenInput, nameInput, selectedColorHex)
                                }
                            } else {
                                onToggleCloudSync(false, tokenInput, nameInput, selectedColorHex)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlowingEmerald,
                            uncheckedThumbColor = SecondarySlate,
                            uncheckedTrackColor = SlateBorder
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expandedSetup) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Connect multiple phones together by entering the SAME Group Sync Code on all of them! Choose different names so everyone gets their own blip on the radar.",
                        color = SecondarySlate,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    // Form Field 1: Your custom name on the tracker map (like WIFE, Daughter name)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your Phone Name (e.g. wife, daughter, dad)", fontSize = 10.sp, color = SecondarySlate) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    // Form Field 2: Group Code Room String
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Group Sync Code", fontSize = 10.sp, color = SecondarySlate) },
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
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
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        )

                        // Button to Generate a brand new unique key
                        Button(
                            onClick = onGenerateGroupKey,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("Generate", fontSize = 10.sp, color = RadarCyan)
                        }
                    }

                    // Form Field 3: Color picker for your map blip
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Your Personal Radar Color:", color = SecondarySlate, fontSize = 10.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorsList.forEach { hex ->
                                val colorValue = try {
                                    Color(android.graphics.Color.parseColor(hex))
                                } catch (e: Exception) {
                                    Color(0xFF26A69A)
                                }
                                val isSelected = hex == selectedColorHex
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(colorValue, CircleShape)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) TextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }
                    }

                    // Action Activation Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Join/Connect Button
                        Button(
                            onClick = {
                                if (tokenInput.isNotBlank() && nameInput.isNotBlank()) {
                                    onToggleCloudSync(true, tokenInput, nameInput, selectedColorHex)
                                    expandedSetup = false
                                }
                            },
                            enabled = tokenInput.isNotBlank() && nameInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCloudSyncEnabled) ActiveAmber else GlowingEmerald
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text(
                                text = if (isCloudSyncEnabled) "Update Cloud Settings" else "Enable Live Cloud Sync",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (isCloudSyncEnabled) {
                            // Turn Off Sync Button
                            Button(
                                onClick = {
                                    onToggleCloudSync(false, tokenInput, nameInput, selectedColorHex)
                                    expandedSetup = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Stop Sync", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
