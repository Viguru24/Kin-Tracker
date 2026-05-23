package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.data.ActivityLog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineLogs(
    logs: List<ActivityLog>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Activity Feed",
                        tint = RadarCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Live Activity Log",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (logs.isNotEmpty()) {
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

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent movements logged. Try moving members inside the circle!",
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
                            // Dynamic bullet indicator
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

                            // Text Detail
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
    }
}
