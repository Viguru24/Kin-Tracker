package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsControls(
    onCalibrateHome: () -> Unit = {},
    homeLat: Double = 0.0,
    homeLng: Double = 0.0,
    homeRadiusMeters: Double = 65.0,
    onUpdateHomeRadius: (Double) -> Unit = {},
    onCalibrateWork: () -> Unit = {},
    onClearWork: () -> Unit = {},
    workLat: Double = 0.0,
    workLng: Double = 0.0,
    isWorkCalibrated: Boolean = false,
    workRadiusMeters: Double = 75.0,
    onUpdateWorkRadius: (Double) -> Unit = {},
    isDepartureAlertsEnabled: Boolean = true,
    onToggleDepartureAlerts: (Boolean) -> Unit = {},
    isVoiceAnnouncementsEnabled: Boolean = false,
    onToggleVoiceAnnouncements: (Boolean) -> Unit = {},
    proximityAlertDistanceMeters: Int = 400,
    onUpdateProximityAlertDistance: (Int) -> Unit = {},
    isLocationPaused: Boolean = false,
    onToggleLocationPaused: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_controls_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Device Location Tracking Master Toggle (Battery Saver for Tablets) ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isLocationPaused) ActiveAmber.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isLocationPaused) ActiveAmber.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Text(if (isLocationPaused) "⏸️" else "🛰️", fontSize = 22.sp)
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Device Location Tracking",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isLocationPaused) "PAUSED" else "ACTIVE",
                                    color = if (isLocationPaused) ActiveAmber else Color(0xFF00E676),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (isLocationPaused)
                                    "GPS & background services stopped • 0% battery drain (Home mode)"
                                else
                                    "Broadcasting live GPS coordinates to family circle",
                                color = if (isLocationPaused) ActiveAmber else SecondarySlate,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Switch(
                        checked = !isLocationPaused,
                        onCheckedChange = { isTracking -> onToggleLocationPaused(!isTracking) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E676),
                            uncheckedThumbColor = ActiveAmber,
                            uncheckedTrackColor = SlateBorder
                        ),
                        modifier = Modifier.testTag("location_tracking_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // ── Building Departure Warnings Master Toggle ──────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("🚪", fontSize = 18.sp)
                    Column {
                        Text(
                            text = "Building Departure Warnings",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Alerts & voice when family members leave Home, Work, or Safe Zones",
                            color = SecondarySlate,
                            fontSize = 9.sp
                        )
                    }
                }
                Switch(
                    checked = isDepartureAlertsEnabled,
                    onCheckedChange = onToggleDepartureAlerts,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF9100),
                        uncheckedThumbColor = SecondarySlate,
                        uncheckedTrackColor = SlateBorder
                    ),
                    modifier = Modifier.testTag("departure_warnings_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // ── Home Base Location ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = RadarCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Home Base Area",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val coordsText = if (homeLat != 0.0 && homeLng != 0.0) {
                                String.format(java.util.Locale.US, "GPS: %.4f, %.4f", homeLat, homeLng)
                            } else "Not calibrated"
                            Text(
                                text = coordsText,
                                color = SecondarySlate,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Button(
                        onClick = onCalibrateHome,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp).testTag("set_home_gps_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Text("Set to Current GPS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Home Radius Slider with Anti-False-Alarm buffer label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Home Zone Radius",
                        color = SecondarySlate,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${homeRadiusMeters.toInt()}m (Exit: ${homeRadiusMeters.toInt() + 45}m)",
                        color = RadarCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = homeRadiusMeters.toFloat(),
                    onValueChange = { onUpdateHomeRadius(it.toDouble()) },
                    valueRange = 40f..150f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = RadarCyan,
                        activeTrackColor = PrimaryCosmic,
                        inactiveTrackColor = SlateBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // ── Work Area Location ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                        Text("💼", fontSize = 16.sp)
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Work Area",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isWorkCalibrated) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                            .border(0.5.dp, Color(0xFF00E676), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("CONFIGURED", color = Color(0xFF00E676), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            val workCoordsText = if (isWorkCalibrated && workLat != 0.0 && workLng != 0.0) {
                                String.format(java.util.Locale.US, "GPS: %.4f, %.4f", workLat, workLng)
                            } else "Not stipulated yet"
                            Text(
                                text = workCoordsText,
                                color = SecondarySlate,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isWorkCalibrated) {
                            TextButton(
                                onClick = onClearWork,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Clear", color = SecondarySlate, fontSize = 10.sp)
                            }
                        }
                        Button(
                            onClick = onCalibrateWork,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D5AFE)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp).testTag("set_work_gps_btn")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                Text(if (isWorkCalibrated) "Update GPS" else "Set Work GPS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isWorkCalibrated) {
                    // Work Radius Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Work Zone Radius",
                            color = SecondarySlate,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${workRadiusMeters.toInt()}m (Exit: ${workRadiusMeters.toInt() + 45}m)",
                            color = Color(0xFF8C9EFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = workRadiusMeters.toFloat(),
                        onValueChange = { onUpdateWorkRadius(it.toDouble()) },
                        valueRange = 40f..200f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8C9EFF),
                            activeTrackColor = Color(0xFF3D5AFE),
                            inactiveTrackColor = SlateBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // ── Voice Announcements ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("🔊", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "Proximity Sound & Voice Alerts",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Chime & speech when someone gets close (OFF by default)",
                            color = SecondarySlate,
                            fontSize = 9.sp
                        )
                    }
                }
                Switch(
                    checked = isVoiceAnnouncementsEnabled,
                    onCheckedChange = onToggleVoiceAnnouncements,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RadarCyan,
                        uncheckedThumbColor = SecondarySlate,
                        uncheckedTrackColor = SlateBorder
                    ),
                    modifier = Modifier.testTag("voice_announcements_switch")
                )
            }

            // ── Proximity Alert Distance Threshold ───────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                        Text("🎯", fontSize = 16.sp)
                        Column {
                            Text(
                                text = "Proximity Alert Distance",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Warn when circle members get within:",
                                color = SecondarySlate,
                                fontSize = 9.sp
                            )
                        }
                    }
                    val distLabel = if (proximityAlertDistanceMeters >= 1000) {
                        "${String.format(java.util.Locale.US, "%.1f", proximityAlertDistanceMeters / 1000.0)} km"
                    } else {
                        "$proximityAlertDistanceMeters m"
                    }
                    Text(
                        text = distLabel,
                        color = RadarCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = proximityAlertDistanceMeters.toFloat(),
                    onValueChange = { onUpdateProximityAlertDistance(it.toInt()) },
                    valueRange = 100f..2000f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = RadarCyan,
                        activeTrackColor = PrimaryCosmic,
                        inactiveTrackColor = SlateBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick preset buttons (Row 1 & Row 2 for perfect fit on all phone screen sizes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(200 to "200m", 400 to "400m", 750 to "750m", 1000 to "1km", 2000 to "2km").forEach { (meters, label) ->
                        val isSelected = Math.abs(proximityAlertDistanceMeters - meters) < 50
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdateProximityAlertDistance(meters) },
                            label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryCosmic,
                                selectedLabelColor = Color.White,
                                containerColor = CosmicBlack,
                                labelColor = SecondarySlate
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = SlateBorder,
                                selectedBorderColor = RadarCyan
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))


            // ── Background Tracking Durability ────────────────────────────────
            var hasBackgroundLocation by remember { mutableStateOf(true) }
            var isBatteryOptimizationIgnored by remember { mutableStateOf(true) }
            val context = LocalContext.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            fun refreshDiagnostics() {
                hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                isBatteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                } else {
                    true
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        refreshDiagnostics()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🛡️ Background Tracking Durability",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ensure the app can map location even when phone is locked or sleeping",
                        color = SecondarySlate,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Background Location Permission Status Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, SlateBorder.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Location Permission",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasBackgroundLocation) "Allow all the time (Enabled)" else "Limited to 'While Using App'",
                            color = if (hasBackgroundLocation) GlowingEmerald else ActiveAmber,
                            fontSize = 8.sp
                        )
                    }

                    if (!hasBackgroundLocation) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Safe fallback
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Allow All the Time", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("✅ Granted", color = GlowingEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Battery Optimization Status Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, SlateBorder.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Optimization Status",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isBatteryOptimizationIgnored) "Unrestricted (Service runs permanently)" else "Optimized (OS can kill tracking)",
                            color = if (isBatteryOptimizationIgnored) GlowingEmerald else ActiveAmber,
                            fontSize = 8.sp
                        )
                    }

                    if (!isBatteryOptimizationIgnored) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        // Safe fallback
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Exempt App", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("✅ Exempted", color = GlowingEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Inline Dynamic utility styling class (kept for build compat)
object DynamicColors {
    fun emeraldSecureGreen() = Color(0xFF00C853)
}
