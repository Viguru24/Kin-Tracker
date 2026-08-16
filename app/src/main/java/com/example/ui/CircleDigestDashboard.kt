package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleDigestDashboard(
    isReset: Boolean,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CosmicBlack,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = SecondarySlate.copy(alpha = 0.4f))
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weekly Circle Digest",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Circle travel diagnostics & activity analytics",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔄 Reset",
                        color = RadarCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onReset() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    
                    Surface(
                        color = RadarCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RadarCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "PREMIUM",
                            color = RadarCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Metric Overview Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Commutes",
                    value = if (isReset) "0.0 mi" else "34.6 mi",
                    subtitle = if (isReset) "All metrics cleared" else "+4.2 mi vs last week",
                    color = RadarCyan
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Avg Battery",
                    value = if (isReset) "100%" else "78%",
                    subtitle = "All devices healthy",
                    color = Color(0xFF00C853)
                )
            }

            // 1. TOTAL DISTANCE COMMUTED CHART (Curved Arc Gauge)
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Active Commute Progress",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(160.dp)) {
                            // Grey track arc
                            drawArc(
                                color = SecondarySlate.copy(alpha = 0.2f),
                                startAngle = 140f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = 16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                            
                            // High-end glowing gradient arc representing progress
                            val gradientBrush = Brush.sweepGradient(
                                colors = listOf(Color(0xFF8E24AA), RadarCyan, Color(0xFF00FF87), Color(0xFF8E24AA))
                            )
                            drawArc(
                                brush = gradientBrush,
                                startAngle = 140f,
                                sweepAngle = if (isReset) 0f else 185f, // ~71% of total
                                useCenter = false,
                                style = Stroke(width = 16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isReset) "0%" else "71%",
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Circle Goal",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. ACTIVE COMMUTING TIMES (Vertical Bar Chart)
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Active Hours (Daily Avg Commutes)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val activeHoursData = if (isReset) listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) else listOf(0.1f, 0.05f, 0.4f, 0.8f, 0.95f, 0.3f, 0.7f, 0.85f, 0.2f, 0.1f)
                    val activeLabels = listOf("8A", "9A", "10A", "11A", "12P", "1P", "2P", "3P", "4P", "5P")

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        val barCount = activeHoursData.size
                        val spacing = size.width / (barCount * 1.5f)
                        val barWidth = spacing * 0.75f
                        val maxBarHeight = size.height - 30f

                        activeHoursData.forEachIndexed { index, value ->
                            val left = index * (barWidth + spacing) + spacing / 2
                            val barHeight = value * maxBarHeight
                            val top = size.height - 25f - barHeight

                            // Glow under active hours
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF00FF87).copy(alpha = 0.6f),
                                        RadarCyan.copy(alpha = 0.02f)
                                    )
                                ),
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                            
                            // Top capping solid pill
                            drawRoundRect(
                                color = if (value > 0.8f) Color(0xFF00FF87) else RadarCyan,
                                topLeft = Offset(left, top),
                                size = Size(barWidth, 8f),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        activeLabels.forEach { label ->
                            Text(
                                text = label,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 3. TOP VISITED LOCATIONS LIST CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Frequent Hotspot Locations",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isReset) {
                        Text(
                            text = "No hotspot locations tracked. Complete active travel commutes to build analytics history.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LocationItem("🏫 High School Safe Zone", "Isabel spent 31.4 hours here this week", "92% match")
                        HorizontalDivider(color = SlateBorder, thickness = 0.5.dp)
                        LocationItem("💪 Fitness Gym Safe Zone", "Eloise visited 3 times (5.6 hrs total)", "80% match")
                        HorizontalDivider(color = SlateBorder, thickness = 0.5.dp)
                        LocationItem("🛒 Supermarket Zone", "Annette visited twice for grocery refills", "Frequent")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(text = subtitle, color = TextSecondary.copy(alpha = 0.8f), fontSize = 9.sp)
        }
    }
}

@Composable
fun LocationItem(
    title: String,
    description: String,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = description, color = TextSecondary, fontSize = 10.sp)
        }
        Surface(
            color = Color(0xFF1E1E24),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, SecondarySlate.copy(alpha = 0.5f))
        ) {
            Text(
                text = tag,
                color = RadarCyan,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}
