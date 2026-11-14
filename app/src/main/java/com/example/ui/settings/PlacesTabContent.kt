package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun PlacesTabContent(
    homeLat: Double,
    homeLng: Double,
    homeRadiusMeters: Double,
    onUpdateHomeRadius: (Double) -> Unit,
    onCalibrateHome: () -> Unit,
    workLat: Double,
    workLng: Double,
    isWorkCalibrated: Boolean,
    workRadiusMeters: Double,
    onUpdateWorkRadius: (Double) -> Unit,
    onCalibrateWork: () -> Unit,
    onClearWork: () -> Unit,
    safeZones: List<com.example.data.SafeZone> = emptyList(),
    onAddPlace: (() -> Unit)? = null,
    onEditPlace: ((com.example.data.SafeZone) -> Unit)? = null,
    onDeletePlace: ((com.example.data.SafeZone) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Text("🏠", fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Home Base Zone",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(color = GlowingEmerald.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "AUTO-SYNCED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlowingEmerald,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(String.format(java.util.Locale.US, "GPS: %.4f, %.4f", homeLat, homeLng), fontSize = 11.sp, color = SecondarySlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = onCalibrateHome,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Recalibrate", fontSize = 11.sp, color = RadarCyan, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Detection Radius", fontSize = 12.sp, color = TextPrimary)
                    Text("${homeRadiusMeters.toInt()}m (Exit: ${homeRadiusMeters.toInt() + 45}m)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RadarCyan)
                }

                Slider(
                    value = homeRadiusMeters.toFloat(),
                    onValueChange = { onUpdateHomeRadius(it.toDouble()) },
                    valueRange = 40f..150f,
                    steps = 10,
                    colors = SliderDefaults.colors(thumbColor = RadarCyan, activeTrackColor = RadarCyan)
                )

                Text(
                    text = "✨ Coordinates are automatically coordinated with all family members via Ra. Phones automatically snap to Home to prevent indoor GPS jitter.",
                    fontSize = 11.sp,
                    color = SecondarySlate,
                    lineHeight = 15.sp
                )
            }
        }

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
                        Text("💼", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text("Work / School Zone", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (isWorkCalibrated) String.format(java.util.Locale.US, "GPS: %.4f, %.4f", workLat, workLng) else "Optional • Not configured",
                                fontSize = 11.sp,
                                color = SecondarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isWorkCalibrated) {
                            OutlinedButton(
                                onClick = onClearWork,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) { Text("Clear", fontSize = 10.sp, color = ErrorRed, maxLines = 1, softWrap = false) }
                        }
                        Button(
                            onClick = onCalibrateWork,
                            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text(if (isWorkCalibrated) "Update" else "Set Work", fontSize = 10.sp, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }

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
                    Text("Family Places & Workplaces", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    if (onAddPlace != null) {
                        Button(
                            onClick = onAddPlace,
                            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("+ Add Place", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                    }
                }
                if (safeZones.isEmpty()) {
                    Text("No places yet. Places are shared with everyone in your circle.", fontSize = 11.sp, color = SecondarySlate)
                }
                safeZones.forEach { zone ->
                    val emoji = when (zone.iconName) { "school" -> "🏫"; "work" -> "💼"; "gym" -> "💪"; "shop" -> "🛒"; else -> "🏠" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(emoji, fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(zone.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(String.format(java.util.Locale.US, "%.4f, %.4f • %dm", zone.latitude, zone.longitude, zone.radiusMeters.toInt()), fontSize = 11.sp, color = SecondarySlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (onEditPlace != null) {
                            TextButton(onClick = { onEditPlace(zone) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Edit", fontSize = 11.sp, color = RadarCyan) }
                        }
                        if (onDeletePlace != null) {
                            TextButton(onClick = { onDeletePlace(zone) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Delete", fontSize = 11.sp, color = ErrorRed) }
                        }
                    }
                }
            }
        }
    }
}