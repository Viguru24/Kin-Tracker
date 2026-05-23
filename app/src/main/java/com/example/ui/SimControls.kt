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
    onAddMember: (String, String, String) -> Unit,
    onCalibrateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedRegister by remember { mutableStateOf(false) }

    // Forms fields
    var newName by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("Husband") }
    val relations = listOf("Husband", "Wife", "Son", "Daughter", "Grandma", "Grandpa", "Pet Tracker", "Vehicle")

    var selectedColorHex by remember { mutableStateOf("#9C27B0") } // Purple default
    val colorsList = listOf(
        "#EC407A", // Magenta Pink
        "#26A69A", // Teal
        "#42A5F5", // Cyan Blue
        "#FF9800", // Gold/Orange
        "#FFEA00", // Yellow-Glow
        "#E040FB", // Hot violet
        "#00FF87"  // Neon green
    )

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
                            text = if (isPaused) "SIMULATION ACTIVE (PAUSED)" else "LIVE COCKPIT FEEDING",
                            color = if (isPaused) ActiveAmber else GlowingEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPaused) "Updates freeze temporarily" else "Real-time positional updates enabled",
                            color = SecondarySlate,
                            fontSize = 9.sp
                        )
                    }
                }

                // Row containing Calibrate GPS and Pause switches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Calibrate GPS Button
                    Button(
                        onClick = onCalibrateHome,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp).testTag("sim_calibrate_gps_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Calibrate GPS",
                                tint = RadarCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Calibrate GPS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

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
                            text = if (isPaused) "Resume Live" else "Pause Feed",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) Color.White else TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

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
                        label = { Text("Family Member Name", fontSize = 11.sp, color = SecondarySlate) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder,
                            cursorColor = RadarCyan,
                            focusedLabelColor = RadarCyan,
                            unfocusedLabelColor = SecondarySlate
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("sim_add_name_field")
                    )

                    // Item B: Dropdown relation picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Relation Circle Type", color = SecondarySlate, fontSize = 9.sp)
                            Text(newRelation, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Compact Row representation of options to select (horizontal buttons)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            BoxWithConstraints {
                                var expandedMenu by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { expandedMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Change", fontSize = 10.sp, color = TextPrimary)
                                }
                                DropdownMenu(
                                    expanded = expandedMenu,
                                    onDismissRequest = { expandedMenu = false },
                                    modifier = Modifier.background(CosmicSlateCard)
                                ) {
                                    relations.forEach { relation ->
                                        DropdownMenuItem(
                                            text = { Text(relation, color = TextPrimary, fontSize = 12.sp) },
                                            onClick = {
                                                newRelation = relation
                                                expandedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Item C: Color Picker
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Select Radar Tracker Color Token:", color = SecondarySlate, fontSize = 10.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp).testTag("color_picker_row")
                        ) {
                            colorsList.forEach { hex ->
                                val colorValue = try {
                                    Color(android.graphics.Color.parseColor(hex))
                                } catch (e: Exception) {
                                    Color(0xFF26A69A)
                                }
                                val isSelected = hex == selectedColorHex
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(colorValue, CircleShape)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 0.dp,
                                            color = if (isSelected) TextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }
                    }

                    // Submit Registration Button
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddMember(newName, newRelation, selectedColorHex)
                                newName = "" // reset
                                expandedRegister = false // collapse
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarCyan,
                            disabledContainerColor = SlateBorder
                        ),
                        enabled = newName.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("sim_register_member_btn")
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
