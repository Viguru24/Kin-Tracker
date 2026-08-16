package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryStatusDialog(
    member: FamilyMember,
    onDismiss: () -> Unit
) {
    val avatarColor = remember(member.avatarColorHex) {
        try {
            Color(android.graphics.Color.parseColor(member.avatarColorHex))
        } catch (e: Exception) {
            Color(0xFF26A69A)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(avatarColor, CircleShape),
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else member.name.first().uppercase(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = member.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                
                // Standard battery representation (Gauge and Percentage)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Battery shape gauge
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(24.dp)
                            .border(1.5.dp, if (member.batteryPercentage <= 20) Color(0xFFE53935) else Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(2.dp)
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
                                .background(barColor, RoundedCornerShape(1.5.dp))
                        )
                    }

                    Text(
                        text = "${member.batteryPercentage}%",
                        color = if (member.isCharging) Color(0xFF00FF87) else if (member.batteryPercentage <= 20) Color(0xFFE53935) else TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Charging status info
                if (member.isCharging) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0xFF00FF87).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("⚡", fontSize = 14.sp, color = Color(0xFF00FF87))
                        Text(
                            text = "Charging",
                            color = Color(0xFF00FF87),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Discharging (automatically synced)",
                        color = SecondarySlate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CosmicSlateCard,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    )
}
