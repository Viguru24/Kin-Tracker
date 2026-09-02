package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryCosmic

@Composable
fun FloatingAlertToast(
    activeAlertMessage: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeAlertMessage != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val message = activeAlertMessage ?: ""
        val isDeparture = message.contains("Departure Warning") || message.contains("has left")
        val isArrival = message.contains("Arrival Notice") || message.contains("has arrived")

        val bgColor = when {
            isDeparture -> Color(0xFF3E1F00)
            isArrival -> Color(0xFF0D2E1C)
            else -> PrimaryCosmic
        }
        val borderColor = when {
            isDeparture -> Color(0xFFFF9100)
            isArrival -> Color(0xFF00E676)
            else -> Color.White.copy(alpha = 0.25f)
        }
        val iconEmoji = when {
            isDeparture -> "🚪"
            isArrival -> "📍"
            else -> "ℹ️"
        }

        Surface(
            color = bgColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, borderColor),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("ui_floating_alert")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = iconEmoji,
                    fontSize = 18.sp
                )
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
