package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityLog
import com.example.data.CloudCompletedTrip
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TimelineLogs(
    logs: List<ActivityLog>,
    onClearLogs: () -> Unit,
    completedTrips: List<CloudCompletedTrip> = emptyList(),
    onSelectTrip: ((CloudCompletedTrip) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var selectedTab by remember { mutableIntStateOf(if (completedTrips.isNotEmpty()) 1 else 0) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header with Tab Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = {
                            Text(
                                "Live Activity",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RadarCyan.copy(alpha = 0.2f),
                            selectedLabelColor = RadarCyan,
                            containerColor = Color.Transparent,
                            labelColor = SecondarySlate
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selectedTab == 0) RadarCyan.copy(alpha = 0.6f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = {
                            Text(
                                if (completedTrips.isNotEmpty()) "Trips (${completedTrips.size})" else "Trip History",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GlowingEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = GlowingEmerald,
                            containerColor = Color.Transparent,
                            labelColor = SecondarySlate
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selectedTab == 1) GlowingEmerald.copy(alpha = 0.6f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (selectedTab == 0 && logs.isNotEmpty()) {
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("clear_logs_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear logs",
                            tint = ErrorRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // TAB 0: LIVE ACTIVITY LOGS
            if (selectedTab == 0) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent movements logged. Safe zones & transit active!",
                            color = SecondarySlate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        logs.take(6).forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val (icon, iconColor) = when (log.iconName) {
                                    "home" -> Pair(Icons.Default.Home, GlowingEmerald)
                                    "away" -> Pair(Icons.Default.LocationOn, PrimaryCosmic)
                                    "battery" -> Pair(Icons.Default.Refresh, ActiveAmber)
                                    "critical" -> Pair(Icons.Default.Warning, ErrorRed)
                                    else -> Pair(Icons.Default.Check, RadarCyan)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = log.iconName,
                                        tint = iconColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.memberName,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = timeFormat.format(Date(log.timestamp)),
                                            color = SecondarySlate,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Text(
                                        text = log.actionText,
                                        color = SecondarySlate,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // TAB 1: LIFE360-STYLE TRIP HISTORY
            else {
                if (completedTrips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No completed trips yet. Family journeys will record automatically!",
                            color = SecondarySlate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        completedTrips.take(5).forEach { trip ->
                            val modeEmoji = when (trip.transitMode.uppercase()) {
                                "TRAIN" -> "🚆"
                                "DRIVING" -> "🚗"
                                "CYCLING" -> "🚲"
                                "WALKING" -> "👣"
                                else -> "🚗"
                            }
                            val dest = trip.destination ?: "Destination"

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTrip?.invoke(trip) },
                                colors = CardDefaults.cardColors(containerColor = CosmicBlack.copy(alpha = 0.6f)),
                                border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = modeEmoji, fontSize = 14.sp)
                                            Text(
                                                text = "${trip.origin} ➔ $dest",
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = timeFormat.format(Date(trip.endTime ?: trip.startTime)),
                                            color = SecondarySlate,
                                            fontSize = 9.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BadgePill(text = "${trip.durationMinutes} mins", icon = "⏱️")
                                        BadgePill(text = "${String.format(Locale.US, "%.1f", trip.totalDistanceKm)} km", icon = "📏")
                                        BadgePill(text = "Top ${trip.topSpeedMph.toInt()} mph", icon = "⚡")

                                        if (trip.harshBrakingCount > 0) {
                                            BadgePill(
                                                text = "Harsh Braking: ${trip.harshBrakingCount}",
                                                icon = "⚠️",
                                                color = ActiveAmber
                                            )
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

@Composable
private fun BadgePill(
    text: String,
    icon: String,
    color: Color = RadarCyan
) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = icon, fontSize = 9.sp)
            Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
