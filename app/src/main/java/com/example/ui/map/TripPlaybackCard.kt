package com.example.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CloudCompletedTrip
import com.example.data.TelemetryWebSocketManager
import com.example.ui.theme.*

@Composable
fun TripPlaybackCard(
    selectedTrip: CloudCompletedTrip?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedTrip != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        selectedTrip?.let { trip ->
            val isTrain = trip.transitMode.equals("TRAIN", ignoreCase = true)
            val modeEmoji = when (trip.transitMode.uppercase()) {
                "TRAIN" -> "🚆"
                "DRIVING" -> "🚗"
                "CYCLING" -> "🚲"
                "WALKING" -> "👣"
                else -> "🚗"
            }
            // Strict No-Purple rule: use Emerald for trains, RadarCyan for driving/others
            val accentColor = if (isTrain) GlowingEmerald else RadarCyan

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                color = Color(0xF2161A24),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.8f)),
                shadowElevation = 10.dp
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = accentColor.copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(modeEmoji, fontSize = 16.sp)
                                }
                            }
                            Column {
                                Text(
                                    text = "${trip.memberName}'s Journey",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${trip.origin} → ${trip.destination ?: "Destination"}",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clickable { TelemetryWebSocketManager.selectTrip(null) }
                                .padding(4.dp),
                            color = Color(0xFF263238),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "✕",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Stat Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFF1E2638),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Duration", fontSize = 9.sp, color = Color(0xFF78909C))
                                Text("${trip.durationMinutes} min", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Surface(
                            color = Color(0xFF1E2638),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Distance", fontSize = 9.sp, color = Color(0xFF78909C))
                                Text("${String.format(java.util.Locale.US, "%.1f", trip.totalDistanceKm)} km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Surface(
                            color = Color(0xFF1E2638),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Top Speed", fontSize = 9.sp, color = Color(0xFF78909C))
                                Text("${trip.topSpeedMph.toInt()} mph", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }
                        }
                        if (trip.harshBrakingCount > 0) {
                            Surface(
                                color = Color(0xFF3E2723),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Harsh Brakes", fontSize = 9.sp, color = Color(0xFFFFAB91), maxLines = 1, softWrap = false)
                                    Text("${trip.harshBrakingCount}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252), maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
