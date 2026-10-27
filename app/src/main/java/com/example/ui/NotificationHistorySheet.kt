package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityLog
import com.example.data.formatTimeAgo
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium Modal Bottom Sheet displaying persistent Notification & Alert History
 * with time-based pruning (7-day lifespan), category filtering, and direct member navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistorySheet(
    logs: List<ActivityLog>,
    onDismiss: () -> Unit,
    onClearLogs: () -> Unit,
    onSelectMember: ((String) -> Unit)? = null
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "Departures" -> logs.filter { it.iconName == "away" || it.actionText.contains("left", ignoreCase = true) }
            "Arrivals" -> logs.filter { it.iconName == "home" || it.actionText.contains("arrived", ignoreCase = true) }
            "Transit" -> logs.filter { it.iconName == "train" || it.actionText.contains("train", ignoreCase = true) || it.actionText.contains("car", ignoreCase = true) }
            "Alerts" -> logs.filter { it.iconName == "critical" || it.iconName == "battery" || it.iconName == "proximity" || it.actionText.contains("warning", ignoreCase = true) || it.actionText.contains("sos", ignoreCase = true) }
            else -> logs
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a • MMM d", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF161A24),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp),
                color = Color(0xFF4A5568),
                shape = RoundedCornerShape(2.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 18.dp)
        ) {
            // ── TOP HEADER ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🔔",
                        fontSize = 20.sp
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Notification History",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = RadarCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${logs.size}",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "🛡️ Auto-pruned after 7 days • Family movement & alerts",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Clear All",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = Color(0xFFB0BEC5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── CATEGORY FILTER CHIPS ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf("All", "Departures", "Arrivals", "Transit", "Alerts")
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val filterCount = when (filter) {
                        "All" -> logs.size
                        "Departures" -> logs.count { it.iconName == "away" || it.actionText.contains("left", ignoreCase = true) }
                        "Arrivals" -> logs.count { it.iconName == "home" || it.actionText.contains("arrived", ignoreCase = true) }
                        "Transit" -> logs.count { it.iconName == "train" || it.actionText.contains("train", ignoreCase = true) || it.actionText.contains("car", ignoreCase = true) }
                        "Alerts" -> logs.count { it.iconName == "critical" || it.iconName == "battery" || it.iconName == "proximity" || it.actionText.contains("warning", ignoreCase = true) || it.actionText.contains("sos", ignoreCase = true) }
                        else -> 0
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedFilter = filter },
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1E2638),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF2B3342)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (filterCount > 0 && filter != "All") "$filter ($filterCount)" else filter,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── NOTIFICATION LOGS LIST ──
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔕", fontSize = 32.sp)
                        Text(
                            text = if (selectedFilter == "All") "No notifications recorded yet" else "No $selectedFilter notifications",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Family departures, train trips, and alerts appear here automatically and are kept for 7 days.",
                            color = Color(0xFF90A4AE),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val (iconBadge, badgeColor) = when (log.iconName) {
                            "critical" -> "🚨" to Color(0xFFFF1744)
                            "train" -> "🚆" to Color(0xFFBA68C8)
                            "away" -> "🚪" to Color(0xFFFF9100)
                            "home" -> "🏠" to Color(0xFF00E676)
                            "battery" -> "🔋" to Color(0xFFFFEA00)
                            "proximity" -> "📍" to Color(0xFF00E5FF)
                            else -> "🔔" to Color(0xFF42A5F5)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (log.memberId.isNotBlank() && log.memberId != "system") {
                                        onSelectMember?.invoke(log.memberId)
                                        onDismiss()
                                    }
                                },
                            color = Color(0xFF1E2638).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF2B3342))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = badgeColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = iconBadge, fontSize = 15.sp)
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.memberName.ifBlank { "Family Circle" },
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = formatTimeAgo(log.timestamp),
                                            color = Color(0xFF00E5FF),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Text(
                                        text = log.actionText,
                                        color = Color(0xFFECEFF1),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )

                                    Text(
                                        text = timeFormat.format(Date(log.timestamp)),
                                        color = Color(0xFF78909C),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Notifications?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will erase all recorded notification history from your device. Normal automated alerts will continue to record.", color = Color(0xFFB0BEC5)) },
            containerColor = Color(0xFF1E2638),
            confirmButton = {
                Button(
                    onClick = {
                        onClearLogs()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = Color(0xFFB0BEC5))
                }
            }
        )
    }
}
