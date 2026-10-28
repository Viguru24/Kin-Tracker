package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityLog
import com.example.data.CloudCompletedTrip
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineLogs(
    logs: List<ActivityLog> = emptyList(),
    onClearLogs: () -> Unit = {},
    completedTrips: List<CloudCompletedTrip> = emptyList(),
    onSelectTrip: ((CloudCompletedTrip) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🚗", fontSize = 16.sp)
                    Text(
                        text = "Recent Journeys & Drives",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (completedTrips.isNotEmpty()) {
                    Surface(
                        color = GlowingEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${completedTrips.size} trips",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlowingEmerald,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (completedTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recorded trips yet. Family journeys and driving safety metrics will appear here automatically!",
                        color = SecondarySlate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    completedTrips.take(6).forEach { trip ->
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
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Route & Timestamp Header (guaranteed never to wrap or push time off-screen)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Text(text = modeEmoji, fontSize = 14.sp)
                                        Text(
                                            text = "${trip.origin} ➔ $dest",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = timeFormat.format(Date(trip.endTime ?: trip.startTime)),
                                        color = SecondarySlate,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Stat badges: FlowRow guarantees pills wrap to next row if screen is narrow, never squashing
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    BadgePill(text = "${trip.durationMinutes} mins", icon = "⏱️")
                                    BadgePill(text = "${String.format(Locale.US, "%.1f", trip.totalDistanceKm)} km", icon = "📏")
                                    BadgePill(text = "Top ${trip.topSpeedMph.toInt()} mph", icon = "⚡")

                                    if (trip.harshBrakingCount > 0) {
                                        BadgePill(
                                            text = "Harsh Brakes: ${trip.harshBrakingCount}",
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

@Composable
private fun BadgePill(
    text: String,
    icon: String,
    color: Color = RadarCyan
) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = icon, fontSize = 9.sp)
            Text(
                text = text,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
