package com.example.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsControls(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onCalibrateHome: () -> Unit,
    onSaveCustomHome: (Double, Double) -> Unit,
    homeLat: Double,
    homeLng: Double,
    modifier: Modifier = Modifier
) {
    var expandedHome by remember { mutableStateOf(false) }
    var inputLat by remember { mutableStateOf(homeLat.toString()) }
    var inputLng by remember { mutableStateOf(homeLng.toString()) }

    LaunchedEffect(homeLat, homeLng) {
        inputLat = homeLat.toString()
        inputLng = homeLng.toString()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_controls_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Live Status ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isPaused) ActiveAmber else GlowingEmerald,
                                CircleShape
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                    Column {
                        Text(
                            text = if (isPaused) "GPS UPDATES PAUSED" else "LIVE TRACKING ACTIVE",
                            color = if (isPaused) ActiveAmber else GlowingEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPaused) "Your location is not being shared" else "Your live location is being shared with your family circle",
                            color = SecondarySlate,
                            fontSize = 9.sp
                        )
                    }
                }

                Button(
                    onClick = onTogglePause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) GlowingEmerald else SlateBorder
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp).testTag("settings_pause_btn")
                ) {
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // ── Home Location ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedHome = !expandedHome }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = RadarCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Home Location",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set your permanent home anchor on the map",
                            color = SecondarySlate,
                            fontSize = 9.sp
                        )
                    }
                }
                Text(
                    text = if (expandedHome) "Done" else "Change",
                    color = RadarCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = expandedHome) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Current saved coords display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Saved Latitude", fontSize = 9.sp, color = SecondarySlate)
                            Text(
                                String.format(java.util.Locale.US, "%.6f", homeLat),
                                fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Saved Longitude", fontSize = 9.sp, color = SecondarySlate)
                            Text(
                                String.format(java.util.Locale.US, "%.6f", homeLng),
                                fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Use current GPS location as home
                    Button(
                        onClick = {
                            onCalibrateHome()
                            expandedHome = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("set_home_gps_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = Color.Black)
                            Text(
                                "Use my current GPS location as Home",
                                color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.3f))

                    Text(
                        "Or type exact coordinates manually:",
                        color = SecondarySlate, fontSize = 9.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputLat,
                            onValueChange = { inputLat = it },
                            label = { Text("Latitude", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder
                            )
                        )
                        OutlinedTextField(
                            value = inputLng,
                            onValueChange = { inputLng = it },
                            label = { Text("Longitude", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val lat = inputLat.toDoubleOrNull()
                            val lng = inputLng.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                onSaveCustomHome(lat, lng)
                                expandedHome = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("save_custom_home_btn")
                    ) {
                        Text("Save These Coordinates as Home",
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
