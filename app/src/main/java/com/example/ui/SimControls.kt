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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimControls(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onAddMember: (String, String, String, String) -> Unit, // Added support for custom profile pic emoji!
    onCalibrateHome: () -> Unit,
    onSaveCustomHome: (Double, Double) -> Unit,
    homeLat: Double,
    homeLng: Double,
    isSimulationEnabled: Boolean,
    onToggleSimulationMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedRegister by remember { mutableStateOf(false) }
    var expandedHome by remember { mutableStateOf(false) }

    // Forms fields
    var newName by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("Husband") }
    val relations = listOf("Husband", "Wife", "Son", "Daughter", "Grandma", "Grandpa", "Pet Tracker", "Vehicle")

    var selectedColorHex by remember { mutableStateOf("#9C27B0") } // Purple default
    var selectedEmoji by remember { mutableStateOf("👨") } // Selected profile pic emoji!
    val emojisList = listOf("👨", "👩", "👦", "👧", "👶", "👵", "👴", "🐶", "🐱", "🚗", "🚲", "🏡", "🦊", "🐼", "🦸", "🚀")
    val colorsList = listOf(
        "#EC407A", // Magenta Pink
        "#26A69A", // Teal
        "#42A5F5", // Cyan Blue
        "#FF9800", // Gold/Orange
        "#FFEA00", // Yellow-Glow
        "#E040FB", // Hot violet
        "#00FF87"  // Neon green
    )

    // Permanent home field inputs synchronized with current values
    var inputLat by remember { mutableStateOf(homeLat.toString()) }
    var inputLng by remember { mutableStateOf(homeLng.toString()) }

    LaunchedEffect(homeLat, homeLng) {
        inputLat = homeLat.toString()
        inputLng = homeLng.toString()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sim_controls_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Heartbeat + Live Status Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heartbeat live signal representation
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
                            text = if (isPaused) "MAP UPDATES (PAUSED)" else "LIVE MAP ACTIVE",
                            color = if (isPaused) ActiveAmber else GlowingEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPaused) "Updates paused temporarily" else "Sharing live locations on the family map",
                            color = SecondarySlate,
                            fontSize = 9.sp
                        )
                    }
                }

                // Row containing Pause and Debug switches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pausing switch (looks custom and stylish)
                    Button(
                        onClick = onTogglePause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) DynamicColors.emeraldSecureGreen() else SlateBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp).testTag("sim_toggle_pause_btn")
                    ) {
                        Text(
                            text = if (isPaused) "Resume" else "Pause",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) Color.White else TextPrimary
                        )
                    }

                    // Toggle Demo Mode vs Pure Production tracking
                    Button(
                        onClick = { onToggleSimulationMode(!isSimulationEnabled) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSimulationEnabled) GlowingMagenta.copy(alpha = 0.8f) else SlateBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp).testTag("sim_toggle_mode_btn")
                    ) {
                        Text(
                            text = if (isSimulationEnabled) "Demo ON" else "Trackers Only",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Expand Home Base Settings Button (Permanent Home Section) - Set and Forget!
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home Base",
                        tint = RadarCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Permanent Home Landmark (Set & Forget)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (expandedHome) "Collapse" else "Configure",
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
                    Text(
                        text = "Set your high-accuracy permanent Home. Once saved, this baseline does not change unless you manually adjust it here.",
                        color = SecondarySlate,
                        fontSize = 10.sp
                    )

                    // Current Coordinates Stats Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Saved Lat", fontSize = 9.sp, color = SecondarySlate)
                            Text(String.format(java.util.Locale.US, "%.6f", homeLat), fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Current Saved Lng", fontSize = 9.sp, color = SecondarySlate)
                            Text(String.format(java.util.Locale.US, "%.6f", homeLng), fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Button: "This is my home, I set it and I forget it"
                    Button(
                        onClick = onCalibrateHome,
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Lock Current Location", modifier = Modifier.size(14.dp), tint = Color.Black)
                            Text(
                                text = "This is my home: lock current location",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Or manually type in custom Home coordinates separately:",
                        color = SecondarySlate,
                        fontSize = 9.sp
                    )

                    // Manual Coordinate Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputLat,
                            onValueChange = { inputLat = it },
                            label = { Text("Home Latitude", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder
                            )
                        )

                        OutlinedTextField(
                            value = inputLng,
                            onValueChange = { inputLng = it },
                            label = { Text("Home Longitude", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = SlateBorder
                            )
                        )
                    }

                    // Button: Save Custom Coordinates
                    Button(
                        onClick = {
                            val latVal = inputLat.toDoubleOrNull()
                            val lngVal = inputLng.toDoubleOrNull()
                            if (latVal != null && lngVal != null) {
                                onSaveCustomHome(latVal, lngVal)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Text(
                            text = "Save Custom Coordinates Separately",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))

            // Expand Register Form button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedRegister = !expandedRegister }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = RadarCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Register Family Tracker Device",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (expandedRegister) "Collapse" else "Open Form",
                    color = RadarCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = expandedRegister) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item A: Name input field (Modern styling)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Device Display Name") },
                        placeholder = { Text("e.g. Louis (Dad)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sim_add_member_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    // Relation selection dropdown (Elegant visual selector instead of boring spinner)
                    Text(text = "Commuter Relation Category", fontSize = 11.sp, color = SecondarySlate, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        relations.take(4).forEach { rel ->
                            val isSel = newRelation == rel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PrimaryCosmic else Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, if (isSel) RadarCyan else SlateBorder, RoundedCornerShape(8.dp))
                                    .clickable { newRelation = rel }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = rel, color = if (isSel) Color.White else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        relations.drop(4).forEach { rel ->
                            val isSel = newRelation == rel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PrimaryCosmic else Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, if (isSel) RadarCyan else SlateBorder, RoundedCornerShape(8.dp))
                                    .clickable { newRelation = rel }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = rel, color = if (isSel) Color.White else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Avatar custom Color layout palette picker
                    Text(text = "Map Pin Accent Theme", fontSize = 11.sp, color = SecondarySlate, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorsList.forEach { col ->
                            val isSel = selectedColorHex == col
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(col)))
                                    .border(
                                        width = if (isSel) 2.5.dp else 0.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = col }
                            )
                        }
                    }

                    // Profile Picture Emoji selector grid
                    Text(text = "Profile Picture / Avatar Icon", fontSize = 11.sp, color = SecondarySlate, fontWeight = FontWeight.Bold)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val chunks = emojisList.chunked(8)
                        chunks.forEach { rowEmojis ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowEmojis.forEach { emo ->
                                    val isSel = selectedEmoji == emo
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) PrimaryCosmic else Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = if (isSel) 1.5.dp else 1.dp,
                                                color = if (isSel) RadarCyan else SlateBorder,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedEmoji = emo },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emo, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Action Button to register Live device
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddMember(newName, newRelation, selectedColorHex, selectedEmoji)
                                newName = ""
                                expandedRegister = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("submit_registered_device_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Register Live Tracker GPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// Inline Dynamic utility styling class
object DynamicColors {
    fun emeraldSecureGreen() = Color(0xFF00C853)
}
