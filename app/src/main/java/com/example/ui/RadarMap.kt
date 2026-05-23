package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@OptIn(ExperimentalTextApi::class)
@Composable
fun RadarMap(
    members: List<FamilyMember>,
    selectedMemberId: String?,
    onSelectMember: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Street model tracking state: "streets", "radar", "hybrid" (default)
    var mapTypeMode by remember { mutableStateOf("hybrid") }

    // Zoom and pan navigation states (pinch-to-zoom & touch drag)
    var zoomScale by remember { mutableStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // 1. Sweep rotation animation: 0 to 360f over 3500ms periodically
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    // 2. Halo pulsing animation for members and Home
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    // Vector Painter for modern Home icon drawing in the center
    val homePainter = rememberVectorPainter(Icons.Filled.Home)

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.0f) // Keep map square
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFFE0E2EC))
            .border(2.dp, SlateBorder, RoundedCornerShape(32.dp))
            .testTag("radar_map_container")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures(
                        panZoomLock = false
                    ) { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 4.0f)
                        val maxPanX = size.width.toFloat() * 1.5f
                        val maxPanY = size.height.toFloat() * 1.5f
                        panOffset = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    }
                }
                .pointerInput(members, zoomScale, panOffset) {
                    detectTapGestures { tapOffset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val centerScreen = Offset(width / 2f, height / 2f)
                        val maxRadius = minOf(width, height) / 2f
                        
                        // Discover if user clicked near any member dot
                        var clickedMemberId: String? = null
                        for (member in members) {
                            val targetX = centerScreen.x + panOffset.x + (member.x / 1.5).toFloat() * maxRadius * zoomScale
                            val targetY = centerScreen.y + panOffset.y + (member.y / 1.5).toFloat() * maxRadius * zoomScale
                            val clickDist = hypot(tapOffset.x - targetX, tapOffset.y - targetY)
                            
                            if (clickDist < 36.dp.toPx()) { // generous 36dp touch target
                                clickedMemberId = member.id
                                break
                            }
                        }
                        
                        // If center Home clicked, deselect or do action
                        val homeX = centerScreen.x + panOffset.x
                        val homeY = centerScreen.y + panOffset.y
                        val homeDist = hypot(tapOffset.x - homeX, tapOffset.y - homeY)
                        
                        if (homeDist < 24.dp.toPx() && clickedMemberId == null) {
                            onSelectMember(null)
                        } else {
                            if (clickedMemberId != null) {
                                onSelectMember(clickedMemberId)
                            } else {
                                onSelectMember(null) // tap blank space resets selection
                            }
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val maxRadius = size.minDimension / 2f
            val pannedCenter = Offset(center.x + panOffset.x, center.y + panOffset.y)

            // Save canvas to draw scaled and translated background elements Map/Grid/Radar rings
            drawContext.canvas.save()
            drawContext.canvas.translate(center.x + panOffset.x, center.y + panOffset.y)
            drawContext.canvas.scale(zoomScale, zoomScale)
            drawContext.canvas.translate(-center.x, -center.y)

            // 0. VIBRANT LOCAL STREETS & PARK MAP BACKGROUND DRAWING
            if (mapTypeMode != "radar") {
                // Clear background with soft minimalist street maps color
                drawRect(color = Color(0xFFF1F3F9))

                // Beautiful green suburban zone (Oakwood Park)
                drawRoundRect(
                    color = Color(0xFFE2F3E4), // soft map park green
                    topLeft = Offset(width * 0.12f, height * 0.12f),
                    size = Size(width * 0.32f, height * 0.22f),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                
                val parkText = textMeasurer.measure(
                    text = "Oakwood Park",
                    style = TextStyle(
                        color = Color(0xFF388E3C),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(parkText, topLeft = Offset(width * 0.15f, height * 0.14f))

                // Secondary sports field/lake park zone
                drawRoundRect(
                    color = Color(0xFFE2F3E4),
                    topLeft = Offset(width * 0.65f, height * 0.58f),
                    size = Size(width * 0.25f, height * 0.15f),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )

                // Beautiful emerald/river flowing water bodies
                val riverPath = Path().apply {
                    moveTo(0f, height * 0.82f)
                    cubicTo(
                        width * 0.3f, height * 0.76f,
                        width * 0.6f, height * 0.94f,
                        width, height * 0.85f
                    )
                    lineTo(width, height * 0.94f)
                    cubicTo(
                        width * 0.6f, height * 1.02f,
                        width * 0.3f, height * 0.84f,
                        0f, height * 0.90f
                    )
                    close()
                }
                drawPath(path = riverPath, color = Color(0xFFD4E3FC)) // soft map water blue

                val riverText = textMeasurer.measure(
                    text = "Emerald River",
                    style = TextStyle(
                        color = Color(0xFF1E3A8A).copy(alpha = 0.5f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                drawText(riverText, topLeft = Offset(width * 0.45f, height * 0.85f))

                // MAP STREET GRID SYSTEM (Secondary thin streets)
                val streetsList = listOf(
                    Pair(Offset(0f, height * 0.25f), Offset(width, height * 0.25f)),
                    Pair(Offset(0f, height * 0.70f), Offset(width, height * 0.70f)),
                    Pair(Offset(width * 0.26f, 0f), Offset(width * 0.26f, height)),
                    Pair(Offset(width * 0.76f, 0f), Offset(width * 0.76f, height))
                )

                for (street in streetsList) {
                    // Draw base street border backing
                    drawLine(
                        color = Color(0xFFE1E2E9),
                        start = street.first,
                        end = street.second,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Draw main street white body
                    drawLine(
                        color = Color.White,
                        start = street.first,
                        end = street.second,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // PRIMARY TRANSIT BROADWAY AVE (Thick beautiful boulevard)
                drawLine(
                    color = Color(0xFFECEFF1),
                    start = Offset(0f, height * 0.5f),
                    end = Offset(width, height * 0.5f),
                    strokeWidth = 14.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(0f, height * 0.5f),
                    end = Offset(width, height * 0.5f),
                    strokeWidth = 10.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val broadwayText = textMeasurer.measure(
                    text = "Broadway Ave",
                    style = TextStyle(
                        color = Color(0xFF78909C),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(broadwayText, topLeft = Offset(width * 0.12f, height * 0.51f))

                // HIGHWAY/GRAND AVENUE (Vertical super highway)
                drawLine(
                    color = Color(0xFFECEFF1),
                    start = Offset(width * 0.52f, 0f),
                    end = Offset(width * 0.52f, height),
                    strokeWidth = 16.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(width * 0.52f, 0f),
                    end = Offset(width * 0.52f, height),
                    strokeWidth = 12.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val grandAveText = textMeasurer.measure(
                    text = "Grand Hwy",
                    style = TextStyle(
                        color = Color(0xFF78909C),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(grandAveText, topLeft = Offset(width * 0.54f, height * 0.35f))
            } else {
                // Classic radar mode dark background
                drawRect(color = Color(0xFFECEFF4))
            }

            // DRAW CONCENTRIC RADAR RINGS (Only in classic or hybrid modes for high contrast safety sweeping)
            if (mapTypeMode != "streets") {
                val ringCount = 3
                for (i in 1..ringCount) {
                    val ringRadius = maxRadius * (i.toFloat() / ringCount)
                    drawCircle(
                        color = Color(0xFF44474E).copy(alpha = 0.15f),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    
                    // Overlay distance markers text (e.g. 1km, 5km, 10km)
                    val distText = when(i) {
                        1 -> "500m"
                        2 -> "2.5km"
                        else -> "5km Safe Zone"
                    }
                    val textLayoutResult = textMeasurer.measure(
                        text = distText,
                        style = TextStyle(color = SecondarySlate.copy(alpha = 0.8f), fontSize = 10.sp)
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(center.x - textLayoutResult.size.width / 2f, center.y - ringRadius + 4.dp.toPx())
                    )
                }

                // DRAW NORTH-SOUTH-EAST-WEST AXIS LINES
                drawLine(
                    color = Color(0xFF44474E).copy(alpha = 0.12f),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF44474E).copy(alpha = 0.12f),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = 1.dp.toPx()
                )

                // DRAW SWEEPING SHADED SECTOR (THE RADAR BEAM)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            RadarCyan.copy(alpha = 0.04f),
                            RadarCyan.copy(alpha = 0.12f),
                            RadarCyan.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = sweepAngle - 45f,
                    sweepAngle = 45f,
                    useCenter = true,
                    size = Size(maxRadius * 2, maxRadius * 2),
                    topLeft = Offset(center.x - maxRadius, center.y - maxRadius)
                )

                // DRAW SWEEP EDGE LINE FOR ENHANCED CONTRAST
                val sweepRadialAngle = Math.toRadians(sweepAngle.toDouble())
                val sweepX = center.x + maxRadius * cos(sweepRadialAngle).toFloat()
                val sweepY = center.y + maxRadius * sin(sweepRadialAngle).toFloat()
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, RadarCyan.copy(alpha = 0.5f)),
                        start = center,
                        end = Offset(sweepX, sweepY)
                    ),
                    start = center,
                    end = Offset(sweepX, sweepY),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            // Restore canvas transformation so that Pins and Labels are drawn with exact original physical scale (unblurred)
            drawContext.canvas.restore()

            // DRAW HOMESTEAD SAFE ANCHOR (HOME IN THE CENTER)
            // Pulse ring under Home
            drawCircle(
                color = GlowingEmerald.copy(alpha = (1.5f - pulseScale).coerceIn(0.0f, 0.4f)),
                radius = 24.dp.toPx() * pulseScale,
                center = pannedCenter
            )
            // Static round background
            drawCircle(
                color = CosmicSlateCard,
                radius = 16.dp.toPx(),
                center = pannedCenter
            )
            drawCircle(
                color = GlowingEmerald,
                radius = 16.dp.toPx(),
                center = pannedCenter,
                style = Stroke(width = 2.dp.toPx())
            )
            // Draw vector icon in center
            translate(left = pannedCenter.x - 10.dp.toPx(), top = pannedCenter.y - 10.dp.toPx()) {
                with(homePainter) {
                    draw(
                        size = Size(20.dp.toPx(), 20.dp.toPx()),
                        colorFilter = ColorFilter.tint(GlowingEmerald)
                    )
                }
            }

            // DRAW ROUTE TRAILS AND FAMILY MEMBERS
            for (member in members) {
                val targetColor = try {
                    Color(android.graphics.Color.parseColor(member.avatarColorHex))
                } catch (e: Exception) {
                    Color(0xFF26A69A) // Default safety fallback
                }
                val targetX = center.x + panOffset.x + (member.x / 1.5).toFloat() * maxRadius * zoomScale
                val targetY = center.y + panOffset.y + (member.y / 1.5).toFloat() * maxRadius * zoomScale
                val mOffset = Offset(targetX, targetY)

                val isSelected = member.id == selectedMemberId

                // 1. Draw connecting trail path if coming home
                if (member.isComingHome) {
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(GlowingEmerald.copy(alpha = 0.3f), targetColor.copy(alpha = 0.8f)),
                            start = pannedCenter,
                            end = mOffset
                        ),
                        start = pannedCenter,
                        end = mOffset,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                }

                // 2. Pulse indicator Halo around member if moving or selected
                if (member.speedMph > 0.0 || isSelected) {
                    val baseRadius = if (isSelected) 18.dp else 12.dp
                    drawCircle(
                        color = targetColor.copy(alpha = (1.8f - pulseScale).coerceIn(0.0f, 0.5f)),
                        radius = baseRadius.toPx() * pulseScale,
                        center = mOffset
                    )
                }

                // 3. Draw outer marker ring
                val markerRadius = if (isSelected) 14.dp.toPx() else 10.dp.toPx()
                drawCircle(
                    color = targetColor,
                    radius = markerRadius,
                    center = mOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = markerRadius - 2.5.dp.toPx(),
                    center = mOffset
                )
                drawCircle(
                    color = targetColor,
                    radius = markerRadius - 4.dp.toPx(),
                    center = mOffset
                )

                // 4. Smart label tag beside member icon (with white/clean backing for readability)
                val labelText = member.name
                val tagBgColor = if (isSelected) PrimaryCosmic else Color.White
                val tagTextColor = if (isSelected) Color.White else TextPrimary
                val nameLayout = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = tagTextColor,
                        fontSize = if (isSelected) 11.sp else 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )

                // Draw background box for label
                val labelPadding = 4.dp.toPx()
                val tagX = targetX - nameLayout.size.width / 2f
                val tagY = targetY + markerRadius + 4.dp.toPx()

                drawRoundRect(
                    color = tagBgColor,
                    topLeft = Offset(tagX - labelPadding, tagY - labelPadding / 2f),
                    size = Size(nameLayout.size.width + labelPadding * 2, nameLayout.size.height + labelPadding),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                
                drawRoundRect(
                    color = SlateBorder,
                    topLeft = Offset(tagX - labelPadding, tagY - labelPadding / 2f),
                    size = Size(nameLayout.size.width + labelPadding * 2, nameLayout.size.height + labelPadding),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Draw text
                drawText(
                    textLayoutResult = nameLayout,
                    topLeft = Offset(tagX, tagY)
                )

                // Battery Badge small dot on top right of dot
                if (member.batteryPercentage <= 20) {
                    drawCircle(
                        color = ErrorRed,
                        radius = 4.dp.toPx(),
                        center = Offset(targetX + markerRadius * 0.7f, targetY - markerRadius * 0.7f)
                    )
                }
            }
        }

        // Overlay status indicators for selected member in floating top panel
        if (selectedMemberId != null) {
            val selectedM = members.firstOrNull { it.id == selectedMemberId }
            if (selectedM != null) {
                Surface(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    color = Color.White.copy(alpha = 0.95f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SlateBorder),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val parsedColor = try {
                            Color(android.graphics.Color.parseColor(selectedM.avatarColorHex))
                        } catch (e: Exception) {
                            Color(0xFF26A69A)
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(parsedColor, CircleShape)
                        )
                        Text(
                            text = "${selectedM.name} — ${selectedM.statusText}",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // FLOATING MAP STYLE TOGGLE BADGE (Radar / Streets / Hybrid)
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart),
            color = Color.White.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("hybrid", "Hybrid", "Concentric sweeps & streets"),
                    Triple("streets", "Map", "Street-level map focus"),
                    Triple("radar", "Radar", "Classic scans")
                ).forEach { (mode, label, desc) ->
                    val isSelected = mapTypeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryCosmic else Color.Transparent)
                            .clickable { mapTypeMode = mode }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // FLOATING COMPASS / RECENTER SELECTION FAB (MATCHING THE TAILWIND DESIGN LAYOUT SPECS)
        Surface(
            modifier = Modifier
                .padding(14.dp)
                .align(Alignment.BottomEnd)
                .size(42.dp)
                .clickable {
                    onSelectMember(null) // Resets active selection to clear any selected pin
                    zoomScale = 1.0f     // Reset zoom
                    panOffset = Offset.Zero // Reset pan/drag offset
                },
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 6.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Outer custom compass outline
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, PrimaryCosmic, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner beautiful pulse dot
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(PrimaryCosmic, CircleShape)
                    )
                }
            }
        }

        // FLOATING ZOOM HUD (Pinch to Zoom visual indicator)
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopEnd),
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Zoom",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
