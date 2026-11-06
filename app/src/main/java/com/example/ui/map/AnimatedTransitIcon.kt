package com.example.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TransitMode

@Composable
fun AnimatedTransitIcon(
    mode: TransitMode,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    val emoji = when (mode) {
        TransitMode.WALKING -> "👣"
        TransitMode.CYCLING -> "🚲"
        TransitMode.DRIVING -> "🚗"
        TransitMode.TRAIN -> "🚆"
        TransitMode.STATIONARY -> "📍"
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.9f).sp)
    }
}
