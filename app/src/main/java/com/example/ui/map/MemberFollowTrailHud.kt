package com.example.ui.map

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.data.TransitMode
import com.example.data.classifyTransitMode
import com.example.data.formatTransitBadge
import com.example.ui.theme.*

@Composable
fun MemberFollowTrailHud(
    selectedMember: FamilyMember?,
    isLocationPaused: Boolean,
    isFollowingSelectedMember: Boolean,
    onFollowMember: () -> Unit,
    onDismissMemberSelection: () -> Unit,
    isRouteTrailEnabled: Boolean,
    onToggleRouteTrail: () -> Unit,
    routeTimeFilter: String,
    onSelectRouteTimeFilter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMemberLocationPaused = selectedMember?.let {
        it.isLocationPaused || (it.id == "me" && isLocationPaused) || it.statusText.contains("Paused", ignoreCase = true)
    } ?: false

    AnimatedVisibility(
        visible = selectedMember != null && !isMemberLocationPaused,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        if (selectedMember != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live Follow Pill
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onFollowMember() },
                            color = Color(0xF012121A),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.2.dp, if (isFollowingSelectedMember) RadarCyan else Color(0xFFFFB300)),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isFollowingSelectedMember) RadarCyan else Color(0xFFFFB300), CircleShape)
                                )
                                val transitMode = classifyTransitMode(selectedMember.speedMph, selectedMember.statusText, selectedMember.id)
                                val transitBadge = formatTransitBadge(selectedMember.speedMph, selectedMember.statusText, selectedMember.locationSince, selectedMember.id)
                                if (transitMode != TransitMode.STATIONARY) {
                                    AnimatedTransitIcon(mode = transitMode, size = 12.dp)
                                }
                                val detailsText = if (transitBadge.isNotBlank()) {
                                    if (transitMode != TransitMode.STATIONARY) " • ${transitBadge.drop(2).trim()}" else " • $transitBadge"
                                } else ""
                                Text(
                                    text = if (isFollowingSelectedMember) "🎯 Following ${selectedMember.name}$detailsText" else "🎯 Re-center on ${selectedMember.name}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable { onDismissMemberSelection() },
                                    color = Color(0x33FFFFFF),
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("✕", color = Color(0xFFECEFF1), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }

                    // Route Trail Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onToggleRouteTrail() },
                        color = if (isRouteTrailEnabled) PrimaryCosmic else Color(0xF01E1E28),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isRouteTrailEnabled) RadarCyan else SlateBorder),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("🛤️", fontSize = 12.sp, maxLines = 1, softWrap = false)
                            Text(
                                text = if (isRouteTrailEnabled) "Trail: ON" else "Trail: OFF",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Time window filter chips: Today, 7 Days, 30 Days (when trail is active)
                if (isRouteTrailEnabled) {
                    Surface(
                        color = Color(0xF0161622),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Pair("today", "Today"),
                                Pair("7days", "7 Days"),
                                Pair("30days", "30 Days")
                            ).forEach { (filterKey, label) ->
                                val isSelectedFilter = routeTimeFilter == filterKey
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectRouteTimeFilter(filterKey) },
                                    color = if (isSelectedFilter) RadarCyan.copy(alpha = 0.25f) else Color.Transparent,
                                    border = if (isSelectedFilter) BorderStroke(1.dp, RadarCyan) else null,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelectedFilter) RadarCyan else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelectedFilter) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
