package com.example.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.*

@Composable
fun MapControlsOverlay(
    isCameraFollowingMe: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onWhereAmINow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 24.dp)
            .zIndex(75f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // ZOOM IN (+) FAB
        Surface(
            modifier = Modifier
                .size(46.dp)
                .clickable { onZoomIn() },
            color = CosmicSlateCard,
            shape = CircleShape,
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = RadarCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ZOOM OUT (-) FAB
        Surface(
            modifier = Modifier
                .size(46.dp)
                .clickable { onZoomOut() },
            color = CosmicSlateCard,
            shape = CircleShape,
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = RadarCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // WHERE AM I NOW (FIND ME ON MAP) FAB
        Surface(
            modifier = Modifier
                .size(46.dp)
                .clickable { onWhereAmINow() },
            color = if (isCameraFollowingMe) Color(0xFF1B5E20) else CosmicSlateCard,
            shape = CircleShape,
            border = BorderStroke(1.dp, if (isCameraFollowingMe) Color(0xFF00FF87) else SlateBorder),
            shadowElevation = 6.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Where am I now",
                    tint = if (isCameraFollowingMe) Color.White else RadarCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
