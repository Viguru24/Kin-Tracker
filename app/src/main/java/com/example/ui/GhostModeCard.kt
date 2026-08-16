package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Clean Ghost Mode card displaying privacy status, countdown timer, and 1-tap activation toggle.
 */
@Composable
fun GhostModeCard(
    ghostModeExpiryTime: Long,
    onToggleGhostMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGhostMode = System.currentTimeMillis() < ghostModeExpiryTime
    var timeLeftString by remember { mutableStateOf("") }

    LaunchedEffect(ghostModeExpiryTime) {
        while (System.currentTimeMillis() < ghostModeExpiryTime) {
            val diffMs = ghostModeExpiryTime - System.currentTimeMillis()
            val hours = diffMs / 3600000
            val minutes = (diffMs % 3600000) / 60000
            val seconds = (diffMs % 60000) / 1000
            timeLeftString = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000L)
        }
        timeLeftString = ""
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isGhostMode) ActiveAmber.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f),
                RoundedCornerShape(12.dp)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isGhostMode) ActiveAmber.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f)
                ),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(if (isGhostMode) "👻" else "👁️", fontSize = 20.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ghost Mode (Privacy)",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isGhostMode) {
                            Text(
                                text = "ACTIVE",
                                color = ActiveAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = if (isGhostMode && timeLeftString.isNotEmpty())
                            "Pauses live GPS broadcast • Remaining: $timeLeftString"
                        else "Freezes your location for 2 hours",
                        color = if (isGhostMode) ActiveAmber else SecondarySlate,
                        fontSize = 10.sp
                    )
                }
            }

            Switch(
                checked = isGhostMode,
                onCheckedChange = onToggleGhostMode,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ActiveAmber,
                    uncheckedThumbColor = SecondarySlate,
                    uncheckedTrackColor = SlateBorder
                )
            )
        }
    }
}
