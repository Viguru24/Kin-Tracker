package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.RoomAudioMonitorControls
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun AlertsTabContent(
    isDepartureAlertsEnabled: Boolean,
    onToggleDepartureAlerts: (Boolean) -> Unit,
    isVoiceAnnouncementsEnabled: Boolean,
    onToggleVoiceAnnouncements: (Boolean) -> Unit,
    notificationVolume: Int,
    onUpdateNotificationVolume: (Int) -> Unit,
    onTestNotificationSound: () -> Unit,
    proximityDistanceMeters: Int,
    onUpdateProximityDistance: (Int) -> Unit,
    members: List<FamilyMember>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Automation & Notifications", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Departure Warnings", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Alerts when family members leave Home or Work", fontSize = 11.sp, color = SecondarySlate)
                    }
                    Switch(
                        checked = isDepartureAlertsEnabled,
                        onToggleDepartureAlerts,
                        colors = SwitchDefaults.colors(checkedTrackColor = RadarCyan)
                    )
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Voice Announcements (TTS)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Speaks aloud arrivals and departures automatically", fontSize = 11.sp, color = SecondarySlate)
                    }
                    Switch(
                        checked = isVoiceAnnouncementsEnabled,
                        onToggleVoiceAnnouncements,
                        colors = SwitchDefaults.colors(checkedTrackColor = RadarCyan)
                    )
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                // ── NOTIFICATION VOLUME CONTROL ─────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            val volumeIcon = when {
                                notificationVolume == 0 -> "🔇"
                                notificationVolume <= 50 -> "🔉"
                                else -> "🔊"
                            }
                            IconButton(
                                onClick = {
                                    if (notificationVolume > 0) {
                                        onUpdateNotificationVolume(0)
                                    } else {
                                        onUpdateNotificationVolume(80)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(volumeIcon, fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    text = "Notification Alert Volume",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Arrivals, departures, chimes & voice volume",
                                    fontSize = 11.sp,
                                    color = SecondarySlate
                                )
                            }
                        }

                        // Badge showing current volume level
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                notificationVolume == 0 -> ActiveAmber.copy(alpha = 0.18f)
                                notificationVolume >= 90 -> GlowingEmerald.copy(alpha = 0.18f)
                                else -> RadarCyan.copy(alpha = 0.15f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    notificationVolume == 0 -> ActiveAmber.copy(alpha = 0.6f)
                                    notificationVolume >= 90 -> GlowingEmerald.copy(alpha = 0.6f)
                                    else -> RadarCyan.copy(alpha = 0.5f)
                                }
                            )
                        ) {
                            Text(
                                text = when (notificationVolume) {
                                    0 -> "MUTED"
                                    100 -> "MAX 100%"
                                    else -> "$notificationVolume%"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    notificationVolume == 0 -> ActiveAmber
                                    notificationVolume >= 90 -> GlowingEmerald
                                    else -> RadarCyan
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Volume slider
                    Slider(
                        value = notificationVolume.toFloat(),
                        onValueChange = { onUpdateNotificationVolume(it.roundToInt()) },
                        valueRange = 0f..100f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = if (notificationVolume == 0) ActiveAmber else RadarCyan,
                            activeTrackColor = if (notificationVolume == 0) ActiveAmber else RadarCyan,
                            inactiveTrackColor = SlateBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_volume_slider")
                    )

                    // Presets & Test Sound Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Presets: Mute, 50%, 80%, 100%
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                        ) {
                            listOf(0 to "Mute", 50 to "50%", 80 to "80%", 100 to "100%").forEach { (vol, label) ->
                                val isSelected = notificationVolume == vol
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) RadarCyan.copy(alpha = 0.25f) else CosmicSlateCard.copy(alpha = 0.6f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) RadarCyan else SlateBorder.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onUpdateNotificationVolume(vol) }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) RadarCyan else SecondarySlate,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Test Sound Action Button
                        Button(
                            onClick = onTestNotificationSound,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (notificationVolume == 0) SlateBorder else Color(0xFF2E3A52),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("test_notification_sound_button")
                        ) {
                            Text("🔔 Test", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Proximity Arrival Radar", fontSize = 12.sp, color = TextPrimary)
                    Text("${proximityDistanceMeters}m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RadarCyan)
                }
                Slider(
                    value = proximityDistanceMeters.toFloat(),
                    onValueChange = { onUpdateProximityDistance(it.toInt()) },
                    valueRange = 100f..1000f,
                    steps = 8,
                    colors = SliderDefaults.colors(thumbColor = RadarCyan, activeTrackColor = RadarCyan)
                )
            }
        }

        RoomAudioMonitorControls(members = members)
    }
}
