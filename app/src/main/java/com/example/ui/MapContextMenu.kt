package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.theme.*

/**
 * Modal dialog displaying quick actions, messaging, alarms, and settings for a selected family member.
 */
@Composable
fun MapContextMenu(
    member: FamilyMember,
    myDeviceUUID: String,
    activeGroupCreatorId: String,
    isRouteTrailEnabled: Boolean = false,
    onToggleRouteTrail: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenWhatsApp: (FamilyMember) -> Unit,
    onTriggerSOS: () -> Unit,
    onSendReaction: (memberId: String, reaction: String) -> Unit,
    onTriggerAlarm: (String) -> Unit,
    onKickMember: (String) -> Unit,
    onEditMember: (FamilyMember) -> Unit,
    onDeleteMember: (FamilyMember) -> Unit
) {
    val isOwner = myDeviceUUID.isNotBlank() && activeGroupCreatorId.isNotBlank() && myDeviceUUID == activeGroupCreatorId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
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
                        Text(text = member.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = member.statusText, color = TextSecondary, fontSize = 11.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(if (member.isCharging) "⚡" else "🔋", fontSize = 11.sp)
                    Text(
                        text = "${member.batteryPercentage}%",
                        color = if (member.isCharging) Color(0xFF00FF87) else if (member.batteryPercentage <= 20) Color(0xFFE53935) else TextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Choose action for ${member.name}:", color = TextSecondary, fontSize = 13.sp)

                // Route Trail Tracking Toggle (Off by default)
                Button(
                    onClick = {
                        onDismiss()
                        onToggleRouteTrail()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRouteTrailEnabled) PrimaryCosmic else SlateBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛤️", fontSize = 16.sp)
                        Text(
                            text = if (isRouteTrailEnabled) "Hide Route Trail on Map" else "Show Route Trail on Map",
                            color = if (isRouteTrailEnabled) Color.White else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // WhatsApp Action
                Button(
                    onClick = {
                        onDismiss()
                        onOpenWhatsApp(member)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💬", fontSize = 16.sp)
                        Text("WhatsApp Message", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // SOS Distress Trigger
                Button(
                    onClick = {
                        onDismiss()
                        onTriggerSOS()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚨", fontSize = 16.sp)
                        Text("SOS / Trigger Distress Alert", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Reactions Block
                Text(text = "Send Quick Reaction Status:", color = SecondarySlate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("👻 Say Boo", "❤️ Love ya", "🐢 Slow down").forEach { reactionText ->
                        Button(
                            onClick = {
                                onDismiss()
                                val cleanReaction = when (reactionText) {
                                    "👻 Say Boo" -> "Boo 👻"
                                    "❤️ Love ya" -> "Love ya ❤️"
                                    else -> "Slow down 🐢"
                                }
                                onSendReaction(member.id, cleanReaction)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(text = reactionText, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Find Their Phone (Alarm)
                if (member.id != "me") {
                    Button(
                        onClick = {
                            onDismiss()
                            onTriggerAlarm(member.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveAmber),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = "Alarm", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Text("Find Their Phone (🚨 Alarm)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Kick / Remove Member (Owner only)
                if (isOwner && member.id != "me" && member.id.startsWith("device_")) {
                    Button(
                        onClick = {
                            onDismiss()
                            onKickMember(member.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🥾", fontSize = 16.sp)
                            Text("Kick / Remove Member permanently", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Edit / Delete Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onEditMember(member)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = RadarCyan, modifier = Modifier.size(14.dp))
                            Text("Edit Info", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (member.id != "me") {
                        Button(
                            onClick = {
                                onDismiss()
                                onDeleteMember(member)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(14.dp))
                                Text("Delete", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondarySlate)
            }
        },
        containerColor = CosmicSlateCard,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    )
}
