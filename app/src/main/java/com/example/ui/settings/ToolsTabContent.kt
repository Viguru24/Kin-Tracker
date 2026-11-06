package com.example.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ToolsTabContent(
    mapTypeMode: String,
    onSelectMapType: (String) -> Unit,
    onOpenCircleDigest: (() -> Unit)?,
    onOpenShoppingList: (() -> Unit)?,
    onOpenAddSafeZone: (() -> Unit)?,
    shoppingActiveCount: Int = 0
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE) }
    var currentMode by remember {
        mutableStateOf(prefs.getString("mapTypeMode", mapTypeMode).let { if (it == "satellite" || it.isNullOrBlank()) "streets" else it })
    }
    var showOfflineInfoDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 1. Map Theme Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🗺️", fontSize = 20.sp)
                    Column {
                        Text(
                            "Map Theme & Layer",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Choose visual rendering mode for the radar map",
                            fontSize = 11.sp,
                            color = SecondarySlate
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("streets", "🗺️ Streets", "Day mode"),
                        Triple("hybrid", "🌙 Dark", "Night mode"),
                        Triple("radar", "🟢 Tactical", "Radar grid")
                    ).forEach { (mode, label, desc) ->
                        val isSelected = currentMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    currentMode = mode
                                    prefs.edit().putString("mapTypeMode", mode).apply()
                                    onSelectMapType(mode)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) RadarCyan.copy(alpha = 0.12f) else Color(0xFFF4F6F9),
                            border = BorderStroke(1.5.dp, if (isSelected) RadarCyan else SlateBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) RadarCyan else TextPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = desc,
                                    fontSize = 10.sp,
                                    color = if (isSelected) RadarCyan else SecondarySlate,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Weekly Circle Digest Card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onOpenCircleDigest != null) { onOpenCircleDigest?.invoke() },
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("📊", fontSize = 22.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Weekly Circle Digest",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Travel summaries, top destinations & commute analytics",
                            fontSize = 11.sp,
                            color = SecondarySlate
                        )
                    }
                }
                Text("›", color = RadarCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── 3. Shared Shopping List Card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onOpenShoppingList != null) { onOpenShoppingList?.invoke() },
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("🛒", fontSize = 22.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Shared Shopping List",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (shoppingActiveCount > 0) {
                                Surface(
                                    color = RadarCyan.copy(alpha = 0.12f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        "$shoppingActiveCount active",
                                        color = RadarCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "Shared group checklist for groceries, errands & supplies",
                            fontSize = 11.sp,
                            color = SecondarySlate
                        )
                    }
                }
                Text("›", color = RadarCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── 4. Custom Safe Zone Creation Card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onOpenAddSafeZone != null) { onOpenAddSafeZone?.invoke() },
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("🛡️", fontSize = 22.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Add Safe Zone",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Create custom geofenced alerts for schools, gym, or friends",
                            fontSize = 11.sp,
                            color = SecondarySlate
                        )
                    }
                }
                Text("›", color = RadarCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── 5. Offline Map Storage Card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showOfflineInfoDialog = true },
            colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("💾", fontSize = 22.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Offline Map Storage",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Surface(
                                color = GlowingEmerald.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlowingEmerald,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Vector map tiles cached locally (~25 MB). Works offline",
                            fontSize = 11.sp,
                            color = SecondarySlate
                        )
                    }
                }
                Text("›", color = RadarCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showOfflineInfoDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineInfoDialog = false },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🗺️", fontSize = 20.sp)
                    Text("Offline Map Storage Active", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Vector and street map tiles around Home, Work, and Circle zones are pre-cached locally on this device.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        "• Tiles: ~25 MB cached locally\n• Automatic fallback without cellular data or signal\n• Fast rendering and low battery impact",
                        color = GlowingEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showOfflineInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyan)
                ) {
                    Text("Got it", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CosmicSlateCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
