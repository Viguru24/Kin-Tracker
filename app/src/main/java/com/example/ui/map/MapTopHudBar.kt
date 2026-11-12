package com.example.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GroupPinMapping
import com.example.ui.theme.RadarCyan

@Composable
fun MapTopHudBar(
    recentAlertCount: Int,
    onOpenNotificationHistory: () -> Unit,
    activeGroupPinCode: String,
    groupPinMappings: List<GroupPinMapping>,
    memberCount: Int,
    isCircleSwitcherOpen: Boolean,
    onToggleCircleSwitcher: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── 1. LEFT: Notification History Bell Button ──
        Surface(
            modifier = Modifier
                .size(38.dp)
                .clickable { onOpenNotificationHistory() }
                .testTag("notification_bell_button"),
            color = Color(0xF2161A24),
            shape = CircleShape,
            border = BorderStroke(1.2.dp, if (recentAlertCount > 0) Color(0xFF00E5FF) else Color(0xFF384358)),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("🔔", fontSize = 16.sp)
                if (recentAlertCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(8.dp)
                            .background(Color(0xFF00E5FF), CircleShape)
                    )
                }
            }
        }

        // ── 2. CENTRE: CIRCLE SELECTOR PILL (Wide, roomy, never squashed) ──
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val activeMapping = groupPinMappings.firstOrNull { it.pinCode == activeGroupPinCode }
                ?: groupPinMappings.firstOrNull { it.isActive }
                ?: groupPinMappings.firstOrNull()
            val resolvedPin = activeGroupPinCode.ifBlank { activeMapping?.pinCode.orEmpty() }
            val activeCircleName = when {
                activeMapping != null && activeMapping.groupName.isNotBlank() -> activeMapping.groupName
                resolvedPin == "4666" -> "Family Circle"
                resolvedPin.isNotBlank() -> "Circle $resolvedPin"
                else -> "No Circle"
            }

            Surface(
                modifier = Modifier
                    .height(38.dp)
                    .clickable { onToggleCircleSwitcher() }
                    .testTag("circle_switcher_pill"),
                color = Color(0xF2121215),
                shape = RoundedCornerShape(19.dp),
                border = BorderStroke(1.2.dp, if (isCircleSwitcherOpen) RadarCyan else Color(0xFF27272A)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "👥", fontSize = 13.sp, maxLines = 1, softWrap = false)
                    Text(
                        text = activeCircleName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (resolvedPin.isNotBlank()) {
                        Text(
                            text = "#$resolvedPin",
                            color = Color(0xFFA1A1AA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0x3300E5FF)
                    ) {
                        Text(
                            text = "$memberCount",
                            color = RadarCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (isCircleSwitcherOpen) "▲" else "▼",
                        color = RadarCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // ── 3. RIGHT: Dedicated Settings Gear Button (Replaces Tools) ──
        Surface(
            modifier = Modifier
                .size(38.dp)
                .clickable { onSettingsClick() }
                .testTag("settings_button"),
            color = Color(0xF2161A24),
            shape = CircleShape,
            border = BorderStroke(1.2.dp, Color(0xFF384358)),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("⚙️", fontSize = 16.sp)
            }
        }
    }
}
