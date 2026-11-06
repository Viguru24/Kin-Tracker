package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityLog
import com.example.ui.TimelineLogs
import com.example.ui.theme.*

@Composable
fun SystemTabContent(
    isLocationPaused: Boolean,
    onToggleLocationPaused: (Boolean) -> Unit,
    cloudStatusText: String,
    activityLogs: List<ActivityLog>,
    onClearLogs: () -> Unit,
    onOpenFeedback: () -> Unit,
    onSelectTrip: ((com.example.data.CloudCompletedTrip) -> Unit)? = null,
    serverUrl: String = "",
    onUpdateServerUrl: ((String) -> Unit)? = null
) {
    var showServerDialog by remember { mutableStateOf(false) }
    var currentServerInput by remember(serverUrl) { mutableStateOf(if (serverUrl.isBlank()) "https://api.cosmowhisper.com" else serverUrl) }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = {
                Text(
                    text = "Sovereign VPS Server",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your self-hosted Kin-Tracker server base URL (e.g. https://api.cosmowhisper.com or http://192.168.1.100:3000).",
                        fontSize = 12.sp,
                        color = SecondarySlate
                    )
                    OutlinedTextField(
                        value = currentServerInput,
                        onValueChange = { currentServerInput = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://api.cosmowhisper.com") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GlowingEmerald,
                            cursorColor = GlowingEmerald
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("server_url_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = currentServerInput.trim().trimEnd('/')
                        val finalUrl = if (cleaned.isBlank()) "https://api.cosmowhisper.com" else cleaned
                        onUpdateServerUrl?.invoke(finalUrl)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowingEmerald),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("server_url_save_btn")
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            currentServerInput = "https://api.cosmowhisper.com"
                            onUpdateServerUrl?.invoke("https://api.cosmowhisper.com")
                            showServerDialog = false
                        }
                    ) {
                        Text("Reset Default", color = SecondarySlate)
                    }
                    TextButton(onClick = { showServerDialog = false }) {
                        Text("Cancel", color = SecondarySlate)
                    }
                }
            },
            containerColor = CosmicSlateCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isLocationPaused) ActiveAmber.copy(alpha = 0.08f) else CosmicSlateCard
            ),
            border = BorderStroke(1.dp, if (isLocationPaused) ActiveAmber.copy(alpha = 0.5f) else SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(if (isLocationPaused) "⏸️" else "🛰️", fontSize = 22.sp)
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Background GPS Tracking",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (isLocationPaused) ActiveAmber.copy(alpha = 0.15f) else GlowingEmerald.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isLocationPaused) "PAUSED" else "ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isLocationPaused) ActiveAmber else GlowingEmerald,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (isLocationPaused) "0% battery drain (Home mode)" else "Broadcasting live to circle",
                                fontSize = 11.sp,
                                color = SecondarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }

                Switch(
                    checked = !isLocationPaused,
                    onCheckedChange = { isTracking -> onToggleLocationPaused(!isTracking) },
                    colors = SwitchDefaults.colors(checkedTrackColor = GlowingEmerald)
                )
            }
        }

        val activeDisplayUrl = if (serverUrl.isBlank()) "https://api.cosmowhisper.com" else serverUrl

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Text("🖥️", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text("Sovereign Sync Server", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(activeDisplayUrl, fontSize = 11.sp, color = SecondarySlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = { showServerDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp).testTag("configure_server_btn")
                    ) {
                        Text("Configure", fontSize = 11.sp, color = GlowingEmerald, maxLines = 1, softWrap = false)
                    }
                }

                Text(
                    text = "Status: $cloudStatusText",
                    fontSize = 11.sp,
                    color = SecondarySlate
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📋", fontSize = 15.sp)
                        Text(
                            text = "Notification & Movement Logs",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${activityLogs.size} events recorded • Viewable via 🔔 on map",
                        fontSize = 11.sp,
                        color = SecondarySlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (activityLogs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearLogs,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("clear_logs_btn")
                    ) {
                        Text("Clear", fontSize = 11.sp, color = ErrorRed, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        val completedTrips by com.example.data.TelemetryWebSocketManager.completedTrips.collectAsState()
        TimelineLogs(
            logs = activityLogs,
            onClearLogs = onClearLogs,
            completedTrips = completedTrips,
            onSelectTrip = onSelectTrip
        )

        Button(
            onClick = onOpenFeedback,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF2)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("💬 Send Feedback & Suggestions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Kin Tracker v${com.example.BuildConfig.VERSION_NAME} • Ra Smart Server Architecture",
                    color = SecondarySlate.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Zero-friction background coordination • Encrypted local DB",
                    color = SecondarySlate.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
        }
    }
}
