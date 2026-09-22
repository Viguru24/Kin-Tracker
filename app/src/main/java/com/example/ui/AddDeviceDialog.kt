package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.AppConfig
import com.example.data.FamilyMember

@Composable
fun AddDeviceDialog(
    activeGroupPinCode: String,
    activeGroupName: String = "Family Circle",
    members: List<FamilyMember> = emptyList(),
    onJoinGroupWithPin: (String) -> Unit = {},
    onCreateGroupWithPin: (String) -> Unit = {},
    onDeleteMemberFromCircle: (FamilyMember) -> Unit = {},
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableStateOf(initialTab) } // 0 = Join Circle, 1 = Invite Device, 2 = Delete from Circle
    var pinToJoinInput by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }
    var showCreateCircleView by remember { mutableStateOf(false) }
    var newCircleNameInput by remember { mutableStateOf("") }

    val pinCode = activeGroupPinCode.ifBlank { AppConfig.DEFAULT_CIRCLE_INVITE_CODE }
    val inviteMessage = "Hey! Join our family circle on Kin-Tracker so we can stay connected on the live map.\n\n" +
            "1. Download and open Kin-Tracker\n" +
            "2. Tap 'Join a Circle'\n" +
            "3. Enter Circle Code: $pinCode"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        containerColor = Color(0xFF131520),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .border(BorderStroke(1.5.dp, RadarCyan.copy(alpha = 0.5f)), RoundedCornerShape(24.dp)),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header row
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RadarCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📱", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "Family Circles & Members",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activeGroupName.ifBlank { "Family Circle" }} • Code: $pinCode",
                                color = RadarCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SecondarySlate
                        )
                    }
                }

                // 3-Tab Switcher: Join Circle vs Invite Device vs Delete from Circle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E2232),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Tab 0: Join Circle
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 0) PrimaryCosmic else Color.Transparent)
                                .clickable {
                                    selectedTab = 0
                                    showCreateCircleView = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "👥 Join",
                                color = if (selectedTab == 0) Color.White else SecondarySlate,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        // Tab 1: Invite Device
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 1) PrimaryCosmic else Color.Transparent)
                                .clickable {
                                    selectedTab = 1
                                    showCreateCircleView = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "➕ Invite",
                                color = if (selectedTab == 1) Color.White else SecondarySlate,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        // Tab 2: Delete from Circle
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 2) Color(0xFFD32F2F) else Color.Transparent)
                                .clickable {
                                    selectedTab = 2
                                    showCreateCircleView = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🗑️ Delete",
                                color = if (selectedTab == 2) Color.White else SecondarySlate,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // TAB 0 CONTENT: JOIN EXISTING CIRCLE
                if (selectedTab == 0 && !showCreateCircleView) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF181C2A),
                            border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "CONNECT THIS DEVICE TO AN EXISTING CIRCLE",
                                    color = RadarCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Enter the 6-character invite code (or 4-digit PIN) generated by your family circle to connect instantly.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Invite Code Input Field (Life360 alphanumeric code)
                        OutlinedTextField(
                            value = pinToJoinInput,
                            onValueChange = { input ->
                                val clean = input.filter { it.isLetterOrDigit() || it == '-' }.uppercase()
                                if (clean.length <= 10) {
                                    pinToJoinInput = clean
                                }
                            },
                            label = { Text("Circle Invite Code") },
                            placeholder = { Text("e.g. K9F-2Q8 or KT-4666") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder,
                                focusedLabelColor = RadarCyan,
                                unfocusedLabelColor = SecondarySlate
                            ),
                            singleLine = true
                        )

                        // Join Button
                        val isValidCode = pinToJoinInput.trim().length >= 4
                        Button(
                            onClick = {
                                if (isValidCode) {
                                    onJoinGroupWithPin(pinToJoinInput.trim())
                                    onDismiss()
                                }
                            },
                            enabled = isValidCode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadarCyan,
                                disabledContainerColor = RadarCyan.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text(
                                text = "Join Circle 👥",
                                color = if (isValidCode) Color.Black else SecondarySlate,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Helpful tip
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.04f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("💡", fontSize = 12.sp)
                                Text(
                                    text = "Where do I find the PIN? Look at the top bar on your phone or family member's Kin-Tracker map.",
                                    color = SecondarySlate,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // Create circle link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Want to create a brand new circle? ",
                                color = SecondarySlate,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Create Circle",
                                color = RadarCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showCreateCircleView = true }
                            )
                        }
                    }
                }

                // TAB 0 SUB-VIEW: CREATE NEW CIRCLE
                if (selectedTab == 0 && showCreateCircleView) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CREATE A BRAND NEW CIRCLE",
                            color = GlowingEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "This creates a new isolated group and generates a new 4-digit PIN.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        OutlinedTextField(
                            value = newCircleNameInput,
                            onValueChange = { newCircleNameInput = it },
                            label = { Text("Circle Name") },
                            placeholder = { Text("e.g. Smith Family") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GlowingEmerald,
                                unfocusedBorderColor = SlateBorder,
                                focusedLabelColor = GlowingEmerald,
                                unfocusedLabelColor = SecondarySlate
                            ),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCreateCircleView = false },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Back", color = SecondarySlate, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    val name = newCircleNameInput.ifBlank { "Family Circle" }
                                    onCreateGroupWithPin(name)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GlowingEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Create 👑", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // TAB 1 CONTENT: INVITE TO THIS CIRCLE
                if (selectedTab == 1) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Big PIN display card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1B2030),
                            border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "THIS CIRCLE'S INVITE CODE",
                                    color = SecondarySlate,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = pinCode,
                                    color = RadarCyan,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp
                                )
                                Text(
                                    text = "Share this invite code to connect family members and devices to this circle",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 3 Step-by-step instructions
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "HOW TO CONNECT THE OTHER DEVICE:",
                                color = SecondarySlate,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            // Step 1
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryCosmic),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("1", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Open Kin-Tracker on the other device.", color = TextPrimary, fontSize = 11.sp)
                            }

                            // Step 2
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryCosmic),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("2", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Tap 'Join a Circle' in the menu or setup screen.", color = TextPrimary, fontSize = 11.sp)
                            }

                            // Step 3
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(GlowingEmerald),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("3", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Enter Code $pinCode to link live instantly!", color = GlowingEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Share Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // WhatsApp / Message Share Button
                            Button(
                                onClick = {
                                    try {
                                        clipboardManager.setText(AnnotatedString(inviteMessage))
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(inviteMessage))
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        clipboardManager.setText(AnnotatedString(inviteMessage))
                                        isCopied = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("💬", fontSize = 13.sp)
                                    Text("Share via WhatsApp", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Copy Code Button
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(inviteMessage))
                                    isCopied = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isCopied) GlowingEmerald else Color(0xFF202638)),
                                border = BorderStroke(1.dp, if (isCopied) GlowingEmerald else SlateBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = if (isCopied) Color.Black else RadarCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isCopied) "Copied! ✓" else "Copy PIN",
                                        color = if (isCopied) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 2 CONTENT: DELETE FROM CIRCLE
                if (selectedTab == 2) {
                    val otherMembers = members.filter { it.id != "me" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF2A1518),
                            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "REMOVE DEVICES FROM THIS CIRCLE",
                                    color = Color(0xFFFF5252),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Permanently delete duplicate, stale, or old devices from your live circle radar.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        if (otherMembers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No other devices in this circle to remove.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                otherMembers.forEach { member ->
                                    var showConfirmDelete by remember(member.id) { mutableStateOf(false) }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF181C2A),
                                        border = BorderStroke(1.dp, SlateBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
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
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                try {
                                                                    Color(android.graphics.Color.parseColor(member.avatarColorHex))
                                                                } catch (_: Exception) {
                                                                    PrimaryCosmic
                                                                }
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(member.avatarEmoji.ifEmpty { "👤" }, fontSize = 16.sp)
                                                    }
                                                    Column {
                                                        Text(
                                                            text = member.name,
                                                            color = TextPrimary,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${if (member.isCharging) "⚡" else "🔋"} ${member.batteryPercentage}% • ${member.statusText}",
                                                            color = if (member.batteryPercentage <= 20) Color(0xFFE53935) else TextSecondary,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }

                                            if (!showConfirmDelete) {
                                                Button(
                                                    onClick = { showConfirmDelete = true },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF371B1E)
                                                    ),
                                                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("🗑️", fontSize = 12.sp)
                                                        Text(
                                                            text = "Delete from Circle",
                                                            color = Color(0xFFFF5252),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { showConfirmDelete = false },
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, SlateBorder),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Cancel", color = SecondarySlate, fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            onDeleteMemberFromCircle(member)
                                                            showConfirmDelete = false
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1.2f)
                                                    ) {
                                                        Text("Confirm Delete", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        }
    )
}
