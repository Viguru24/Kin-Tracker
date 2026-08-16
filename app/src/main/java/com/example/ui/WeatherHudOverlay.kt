package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.FamilyMember
import com.example.ui.theme.*

/**
 * Clean floating Weather HUD with expanded diagnostic dialog.
 */
@Composable
fun WeatherHudOverlay(
    members: List<FamilyMember>,
    selectedMemberId: String?,
    memberWeatherDetailed: Map<String, FamilyViewModel.WeatherInfo>,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val selectedWeather = memberWeatherDetailed[selectedMemberId ?: "me"] ?: memberWeatherDetailed["me"]
    selectedWeather?.let { weather ->
        var isWeatherExpanded by remember { mutableStateOf(false) }
        val selectedName = members.firstOrNull { it.id == (selectedMemberId ?: "me") }?.name ?: "My Device"

        Box(
            modifier = modifier
                .padding(bottom = bottomPadding + 20.dp)
                .zIndex(80f)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(20.dp))
                    .clickable { isWeatherExpanded = !isWeatherExpanded }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = weather.emoji, fontSize = 16.sp)
                    Text(text = "${weather.temp.toInt()}°C", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "•", color = TextSecondary, fontSize = 12.sp)
                    Text(text = weather.description, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(text = if (isWeatherExpanded) "▲" else "▼", color = TextSecondary, fontSize = 8.sp)
                }
            }

            if (isWeatherExpanded) {
                AlertDialog(
                    onDismissRequest = { isWeatherExpanded = false },
                    title = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(weather.emoji, fontSize = 22.sp)
                            Text(
                                text = "Weather Diagnostics: $selectedName",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Condition: ${weather.description}",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Temperature: ${weather.temp}°C / ${String.format(java.util.Locale.US, "%.1f", weather.temp * 1.8 + 32)}°F",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Wind Speed: ${weather.windSpeed} mph",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ℹ️ Live meteorology feeds are queried autonomously from Open-Meteo API based on GPS changes.",
                                color = SecondarySlate,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { isWeatherExpanded = false },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Got it", color = Color.White, fontSize = 12.sp)
                        }
                    },
                    containerColor = CosmicSlateCard,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp
                )
            }
        }
    }
}
