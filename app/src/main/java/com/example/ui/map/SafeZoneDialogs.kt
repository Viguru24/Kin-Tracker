package com.example.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.data.SafeZone
import com.example.ui.theme.*

@Composable
fun DeleteSafeZoneDialog(
    zone: SafeZone?,
    onDismiss: () -> Unit,
    onConfirmDelete: (SafeZone) -> Unit
) {
    if (zone == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delete Safe Zone", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = "Are you sure you want to delete the safe zone \"${zone.name}\"?", color = TextSecondary, fontSize = 13.sp)
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete(zone)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondarySlate)
            }
        },
        containerColor = CosmicSlateCard,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    )
}

@Composable
fun AddSafeZoneDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAddSafeZone: (SafeZone) -> Unit,
    members: List<FamilyMember>,
    homeLat: Double,
    homeLng: Double
) {
    if (!visible) return

    var newZoneName by remember { mutableStateOf("") }
    var newZoneRadius by remember { mutableStateOf(50f) }
    var newZoneIcon by remember { mutableStateOf("home") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Create Safe Zone", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text = "Define a new premium geofence radius on the map.", color = TextSecondary, fontSize = 12.sp)

                OutlinedTextField(
                    value = newZoneName,
                    onValueChange = { newZoneName = it },
                    label = { Text("Zone Name (e.g. School)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryCosmic,
                        unfocusedBorderColor = SlateBorder
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(text = "Radius: ${newZoneRadius.toInt()} meters", color = TextPrimary, fontSize = 12.sp)
                    Slider(
                        value = newZoneRadius,
                        onValueChange = { newZoneRadius = it },
                        valueRange = 20f..300f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryCosmic,
                            activeTrackColor = PrimaryCosmic
                        )
                    )
                }

                Text(text = "Select Zone Icon Type:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("home" to "🏠", "school" to "🏫", "gym" to "💪", "work" to "💼", "shop" to "🛒").forEach { (type, emoji) ->
                        val isSelected = newZoneIcon == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryCosmic else SlateBorder)
                                .clickable { newZoneIcon = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val meLoc = members.firstOrNull { it.id == "me" }
                    val lat = meLoc?.y ?: homeLat
                    val lng = meLoc?.x ?: homeLng
                    onAddSafeZone(
                        SafeZone(
                            id = "zone_" + System.currentTimeMillis(),
                            name = newZoneName.ifBlank { "Safe Zone" },
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = newZoneRadius.toDouble(),
                            iconName = newZoneIcon
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic)
            ) {
                Text("Save Zone", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondarySlate)
            }
        },
        containerColor = CosmicSlateCard,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    )
}
