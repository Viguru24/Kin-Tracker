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
import androidx.compose.ui.draw.clip
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

    // Forms fields for authentication fields
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
            // MAIN PROFILE STATS & IDENTITY (Permanently signed in)
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
                                    .background(Color(android.graphics.Color.parseColor(selectedColorHex)), CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userDisplayName.firstOrNull()?.uppercaseChar()?.toString() ?: "L",
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

                        // Status of account link
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GlowingEmerald, CircleShape)
                            )
                            Text(
                                text = "LINKED PERMANENTLY",
                                color = GlowingEmerald,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    // Quick fallback click to log straight back in as Louis de Souza
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSignIn("Louis de Souza", "louisdesouza@gmail.com") },
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "OFFLINE PROFILE - TAP TO RESTORE LOUIS DE SOUZA MAIN ACCOUNT",
                            color = ActiveAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

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
                            text = "AUTOMATIC INTERCOM BACKGROUND SYNC",
                            color = if (isCloudSyncEnabled) GlowingEmerald else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isCloudSyncEnabled) "ONLINE • AUTO-SYNC ACTIVE" else "LOCAL ONLY MODE",
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
                        text = if (expandedSetup) "Hide details" else "Sharing details",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Short overview showing active sync details when collapsed (Tidy offline/online summary)
            if (!expandedSetup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Commuter Circle Sync Key active. Real-time background update active. Share key below if linking external devices.",
                            color = SecondarySlate,
                            fontSize = 10.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Stable sharing key: ${groupSyncToken.take(15)}...",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
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
                    }
                }
            }

            AnimatedVisibility(visible = expandedSetup) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Your sync token is derived automatically from your main profile email. All devices signed into the same account will pair automatically without manual pairing room setup configuration.",
                        color = SecondarySlate,
                        fontSize = 10.sp
                    )

                    // Display active sync key
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Active Commuter Circle Sync Key") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_token_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy Key",
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(tokenInput))
                                }
                            )
                        }
                    )

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

                    // Apply and Synchronize Background button
                    Button(
                        onClick = {
                            onToggleCloudSync(true, tokenInput, nameInput, selectedColorHex)
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

                    // Toggle to disable Cloud Intercom update cycles entirely
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Cloud Sync",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = isCloudSyncEnabled,
                            onCheckedChange = { checked ->
                                onToggleCloudSync(checked, tokenInput, nameInput, selectedColorHex)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GlowingEmerald,
                                uncheckedThumbColor = SecondarySlate,
                                uncheckedTrackColor = SlateBorder
                            )
                        )
                    }
                }
            }
        }
    }
}
