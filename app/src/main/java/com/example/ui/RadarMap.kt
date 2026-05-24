package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.FamilyMember
import com.example.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun RadarMap(
    members: List<FamilyMember>,
    selectedMemberId: String?,
    onSelectMember: (String?) -> Unit,
    homeLat: Double,
    homeLng: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapTypeMode by remember { mutableStateOf("streets") } // streets, hybrid (midnight), radar (neon)

    // Remember the MapView reference to trigger zoom & camera animations from Compose UI blocks
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Synchronize OSMDroid Global configurations safely
    LaunchedEffect(Unit) {
        try {
            Configuration.getInstance().load(context.applicationContext, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = context.packageName
            val cacheDir = File(context.cacheDir, "osmdroid")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            Configuration.getInstance().osmdroidTileCache = cacheDir
            Configuration.getInstance().osmdroidBasePath = cacheDir
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    // Target zoom and center only when selection changes
    LaunchedEffect(selectedMemberId) {
        if (selectedMemberId != null) {
            val member = members.firstOrNull { it.id == selectedMemberId }
            if (member != null) {
                mapViewRef?.let { map ->
                    map.controller.animateTo(GeoPoint(member.y, member.x))
                    map.controller.setZoom(15.5)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.85f) // Physically taller and dominant center stage presence
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE0E2EC))
            .border(2.dp, SlateBorder, RoundedCornerShape(24.dp))
            .testTag("radar_map_container")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    clipToOutline = true
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                    // Allow panning and swiping on the Map without triggering outer container scrolling
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }

                    // Target CR8 4DS by default and zoom closer
                    controller.setZoom(15.5)
                    controller.setCenter(GeoPoint(homeLat, homeLng))
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                val density = context.resources.displayMetrics.density

                // 1. Calculate people currently At Home
                val atHomeMembers = members.filter { member ->
                    val dist = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                    dist < 0.05 || member.statusText.contains("At Home")
                }
                val atHomeNames = atHomeMembers.joinToString(", ") { it.name }
                val atHomeEmojis = atHomeMembers.map { if (it.avatarEmoji.isNotBlank()) it.avatarEmoji else it.name.first().toString() }.joinToString(" ")

                // Draw central HOME baseline anchor marker pin
                val homeMarker = Marker(mapView).apply {
                    position = GeoPoint(homeLat, homeLng)
                    icon = createHomeMarkerDrawable(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = if (atHomeEmojis.isNotEmpty()) "Home base [$atHomeEmojis]" else "Home base"
                    snippet = if (atHomeMembers.isEmpty()) "Nobody is home at the moment" else "At Home right now: $atHomeNames"
                    setOnMarkerClickListener { m, _ ->
                        onSelectMember(null)
                        m.showInfoWindow()
                        true
                    }
                }
                mapView.overlays.add(homeMarker)

                // 2. Draw active family members and connect transit paths
                members.forEach { member ->
                    val memberLat = member.y
                    val memberLng = member.x
                    val memberGeo = GeoPoint(memberLat, memberLng)

                    val isSelected = member.id == selectedMemberId

                    val dist = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                    val isMemberAtHome = dist < 0.05 || member.statusText.contains("At Home")

                    // Connect connecting route line to home core if they are away from home
                    if (!isMemberAtHome) {
                        val polyline = Polyline(mapView).apply {
                            val points = listOf(GeoPoint(homeLat, homeLng), memberGeo)
                            setPoints(points)
                            outlinePaint.color = try {
                                android.graphics.Color.parseColor(member.avatarColorHex)
                            } catch (e: Exception) {
                                android.graphics.Color.BLUE
                            }
                            val isHighlighted = isSelected || member.isComingHome
                            outlinePaint.strokeWidth = if (isHighlighted) 3.5f * density else 1.5f * density
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(
                                if (isHighlighted) floatArrayOf(15f, 15f) else floatArrayOf(8f, 12f),
                                0f
                            )
                            if (!isHighlighted) {
                                outlinePaint.alpha = 100 // Subtle line for non-selected away members
                            }
                        }
                        mapView.overlays.add(polyline)
                    }

                    // Dynamically build colored, initials/emoji-based density-scaled marker pin
                    val markerLabel = if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else (member.name.firstOrNull()?.toString() ?: "M")
                    val memberMarker = Marker(mapView).apply {
                        position = memberGeo
                        icon = createColoredMarkerDrawable(context, member.avatarColorHex, markerLabel, isSelected)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = member.name
                        snippet = "${member.statusText} (${member.batteryPercentage}% power)"
                        setOnMarkerClickListener { m, _ ->
                            onSelectMember(member.id)
                            m.showInfoWindow()
                            true
                        }
                    }
                    mapView.overlays.add(memberMarker)
                }

                // Apply OpenStreetMap customizable styling options using matrices
                when (mapTypeMode) {
                    "streets" -> {
                        // Standard crisp full-color streets
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }
                    "hybrid" -> {
                        // Cyberpunk Midnight Dark matrix filter
                        val matrix = floatArrayOf(
                            -0.85f, 0f, 0f, 0f, 255f,
                            0f, -0.85f, 0f, 0f, 255f,
                            0f, 0f, -0.55f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        )
                        mapView.overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(matrix))
                    }
                    "radar" -> {
                        // High-tech Retro Sonar green matrix filter
                        val matrix = floatArrayOf(
                            0f, 0f, 0f, 0f, 0f,
                            0f, 1.4f, 0f, 0f, 40f,
                            0f, 0f, 0f, 0f, 0f,
                            -1f, -1f, -1f, 1.2f, 255f
                        )
                        mapView.overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(matrix))
                    }
                }

                mapView.invalidate()
            }
        )

        // FLOWING OVERLAY #1: STYLE SELECTION PANEL (Map, Midnight, Retro Green) - Highly Compact Design
        Surface(
            modifier = Modifier
                .padding(6.dp)
                .align(Alignment.TopStart),
            color = Color.White.copy(alpha = 0.95f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("streets", "Real Map", "Standard style"),
                    Triple("hybrid", "Midnight", "Dark theme"),
                    Triple("radar", "Retro Green", "Radar HUD Style")
                ).forEach { (mode, label, desc) ->
                    val isSelected = mapTypeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) PrimaryCosmic else Color.Transparent)
                            .clickable { mapTypeMode = mode }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // COHESIVE MAP CONTROLS FLOATING CONTAINER (BottomEnd)
        Column(
            modifier = Modifier
                .padding(14.dp)
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // WHERE AM I NOW (FIND ME ON MAP) FAB
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        val me = members.firstOrNull { it.id == "me" }
                        if (me != null && me.y != 0.0 && me.x != 0.0) {
                            onSelectMember("me") // Focus selection
                            mapViewRef?.let {
                                it.controller.animateTo(GeoPoint(me.y, me.x))
                                it.controller.setZoom(15.5)
                            }
                        }
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
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Where am I now",
                        tint = RadarCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ANCHOR RECENTER COMPASS FAB (HOME)
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        onSelectMember(null) // Reset selected member focus
                        mapViewRef?.let {
                            it.controller.animateTo(GeoPoint(homeLat, homeLng))
                            it.controller.setZoom(15.5)
                        }
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
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(1.5.dp, PrimaryCosmic, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PrimaryCosmic, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

// Helper factories to design density-independent colored text vector marker icons
private fun createColoredMarkerDrawable(
    context: Context,
    colorHex: String,
    initials: String,
    isSelected: Boolean
): Drawable {
    val sizeDp = if (isSelected) 46 else 38
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val parsedColor = try {
        android.graphics.Color.parseColor(colorHex)
    } catch (e: Exception) {
        android.graphics.Color.DKGRAY
    }

    val paint = Paint().apply {
        isAntiAlias = true
    }

    val center = sizePx / 2f
    val radius = sizePx / 2.3f

    // Ambient glow outer background layer for selected marker
    if (isSelected) {
        paint.color = parsedColor
        paint.alpha = 80
        canvas.drawCircle(center, center, radius, paint)
    }

    // High contrast white ring layout
    paint.alpha = 255
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, radius - (if (isSelected) 4 * density else 2 * density), paint)

    // Solid core colored circle
    paint.color = parsedColor
    canvas.drawCircle(center, center, radius - (if (isSelected) 6 * density else 4 * density), paint)

    // Embedded initials display text
    paint.color = android.graphics.Color.WHITE
    val isEmoji = initials.any { it.code > 127 }
    if (isEmoji) {
        paint.textSize = (if (isSelected) 19f else 16f) * density
    } else {
        paint.textSize = (if (isSelected) 13f else 11f) * density
    }
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER

    val textY = center - (paint.descent() + paint.ascent()) / 2f
    val drawTextString = if (isEmoji) initials else initials.take(2).uppercase()
    canvas.drawText(drawTextString, center, textY, paint)

    return BitmapDrawable(context.resources, bitmap)
}

// Helper to design central green homestead indicator drawable
private fun createHomeMarkerDrawable(context: Context): Drawable {
    val sizeDp = 44
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
    }

    val center = sizePx / 2f
    val radius = sizePx / 2.3f

    // Glowing green pulsing surround
    paint.color = android.graphics.Color.parseColor("#2E7D32")
    paint.alpha = 80
    canvas.drawCircle(center, center, radius, paint)

    // Protective high-contrast white boundaries
    paint.alpha = 255
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, radius - 3 * density, paint)

    // Midnight dark solid core matching theme aesthetics
    paint.color = android.graphics.Color.parseColor("#1A1C1E")
    canvas.drawCircle(center, center, radius - 5 * density, paint)

    // Bold Home 'H' title
    paint.color = android.graphics.Color.parseColor("#2E7D32")
    paint.textSize = 14 * density
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    val textY = center - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText("H", center, textY, paint)

    return BitmapDrawable(context.resources, bitmap)
}
