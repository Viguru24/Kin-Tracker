package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
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
    modifier: Modifier = Modifier
) {
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
                                Text(
                                    text = member.name.first().uppercase(),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
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

                            // Battery Gauge and isCharging
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (member.isCharging) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Charging",
                                        tint = GlowingEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${member.batteryPercentage}%",
                                        color = GlowingEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    val isLow = member.batteryPercentage <= 20
                                    Icon(
                                        imageVector = if (isLow) Icons.Default.Warning else Icons.Default.Person,
                                        contentDescription = "Battery Status",
                                        tint = if (isLow) ErrorRed else SecondarySlate,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${member.batteryPercentage}%",
                                        color = if (isLow) ErrorRed else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isLow) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
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
                                    text = "Lat: ${(37.7749 + member.y * 0.015).toString().take(7)}, Lng: ${(-122.4194 + member.x * 0.015).toString().take(8)}",
                                    color = SecondarySlate,
                                    fontSize = 10.sp
                                )
                            }

                            // Speed or Status
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Speed",
                                    tint = SecondarySlate,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (member.speedMph > 0.0) "${member.speedMph} mph" else "Stationary",
                                    color = if (member.speedMph > 0) TextPrimary else SecondarySlate,
                                    fontSize = 10.sp,
                                    fontWeight = if (member.speedMph > 0) FontWeight.Medium else FontWeight.Normal
                                )
                            }

                            // Dynamic ETA home indicator
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
                                        member.x == 0.0 && member.y == 0.0 -> "At Home"
                                        else -> "Away"
                                    },
                                    color = when {
                                        member.isComingHome -> RadarCyan
                                        member.x == 0.0 && member.y == 0.0 -> GlowingEmerald
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
                                Divider(color = DividerGray)
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
                                    if (!member.isComingHome && (member.x != 0.0 || member.y != 0.0)) {
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
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send Outside",
                                            tint = GlowingMagenta,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Send Outside", fontSize = 10.sp, color = TextPrimary)
                                    }

                                    // 4. Instant Check-In Button
                                    if (member.x != 0.0 || member.y != 0.0) {
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
