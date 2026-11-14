package com.example.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
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


private data class PlaceSearchHit(val label: String, val lat: Double, val lng: Double)

private suspend fun searchPlaces(query: String): List<PlaceSearchHit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val url = java.net.URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=gb&q=$q")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "KinTracker/2.8.7 (family safe zones)")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = org.json.JSONArray(body)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PlaceSearchHit(o.optString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble())
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Create or edit a custom place. When [editingZone] is set the dialog edits it in place (same id).
 * Location can come from an address/postcode search, current GPS, or the default (home).
 */
@Composable
fun AddSafeZoneDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAddSafeZone: (SafeZone) -> Unit,
    members: List<FamilyMember>,
    homeLat: Double,
    homeLng: Double,
    editingZone: SafeZone? = null
) {
    if (!visible) return

    val meLoc = members.firstOrNull { it.id == "me" }
    var name by remember(editingZone) { mutableStateOf(editingZone?.name ?: "") }
    var radius by remember(editingZone) { mutableStateOf((editingZone?.radiusMeters ?: 100.0).toFloat()) }
    var icon by remember(editingZone) { mutableStateOf(editingZone?.iconName ?: "home") }
    var lat by remember(editingZone) { mutableStateOf(editingZone?.latitude ?: meLoc?.y?.takeIf { it != 0.0 } ?: homeLat) }
    var lng by remember(editingZone) { mutableStateOf(editingZone?.longitude ?: meLoc?.x?.takeIf { it != 0.0 } ?: homeLng) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PlaceSearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchedOnce by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingZone != null) "Edit Place" else "Add Place",
                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Text(text = "Shared with everyone in your circle.", color = TextSecondary, fontSize = 12.sp)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Westside Care Home)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryCosmic, unfocusedBorderColor = SlateBorder
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search address or postcode") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryCosmic, unfocusedBorderColor = SlateBorder
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = query.isNotBlank() && !searching,
                        onClick = {
                            searching = true
                            scope.launch {
                                hits = searchPlaces(query.trim())
                                searchedOnce = true
                                searching = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (searching) "Searching…" else "🔍 Search", fontSize = 12.sp) }
                    OutlinedButton(
                        enabled = meLoc != null && meLoc.y != 0.0,
                        onClick = {
                            meLoc?.let { lat = it.y; lng = it.x }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("📍 My GPS", fontSize = 12.sp) }
                }
                if (searchedOnce && hits.isEmpty() && !searching) {
                    Text("No results found. Try a postcode.", color = TextSecondary, fontSize = 11.sp)
                }
                hits.forEach { hit ->
                    Text(
                        text = hit.label,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateBorder)
                            .clickable {
                                lat = hit.lat
                                lng = hit.lng
                                if (name.isBlank()) name = hit.label.substringBefore(",")
                                hits = emptyList()
                            }
                            .padding(8.dp)
                    )
                }

                Text(
                    text = "Location: ${"%.5f".format(java.util.Locale.US, lat)}, ${"%.5f".format(java.util.Locale.US, lng)}",
                    color = TextSecondary, fontSize = 11.sp
                )

                Column {
                    Text(text = "Radius: ${radius.toInt()} meters", color = TextPrimary, fontSize = 12.sp)
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 20f..300f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryCosmic, activeTrackColor = PrimaryCosmic)
                    )
                }

                Text(text = "Icon:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("home" to "🏠", "school" to "🏫", "gym" to "💪", "work" to "💼", "shop" to "🛒").forEach { (type, emoji) ->
                        val isSelected = icon == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryCosmic else SlateBorder)
                                .clickable { icon = type }
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
                    onAddSafeZone(
                        SafeZone(
                            id = editingZone?.id ?: ("zone_" + System.currentTimeMillis()),
                            name = name.ifBlank { "Safe Zone" },
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = radius.toDouble(),
                            iconName = icon
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic)
            ) {
                Text(if (editingZone != null) "Save Changes" else "Save Place", color = Color.White)
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
