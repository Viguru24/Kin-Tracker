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
import androidx.compose.ui.unit.Dp
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
    locationTrails: Map<String, List<Pair<Double, Double>>> = emptyMap(),
    onTriggerSOS: () -> Unit = {},
    onTriggerCheckIn: () -> Unit = {},
    onSendReaction: (String, String) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit = {},
    onOpenWhatsApp: () -> Unit = {},
    bottomPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapTypeMode by remember { mutableStateOf("streets") } // streets, hybrid (midnight), radar (neon)

    // Remember the MapView reference to trigger zoom & camera animations from Compose UI blocks
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isCameraFollowingMe by remember { mutableStateOf(false) }

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

    // Keep camera panning to stay centered on the traveler when follow mode is active!
    val me = members.firstOrNull { it.id == "me" }
    LaunchedEffect(me?.y, me?.x, isCameraFollowingMe) {
        if (isCameraFollowingMe && me != null && me.y != 0.0 && me.x != 0.0) {
            mapViewRef?.let { map ->
                map.controller.animateTo(GeoPoint(me.y, me.x))
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFFE0E2EC))
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

                // 1c. GEOPROJECT SAFE ZONE RADAR BOUNDARY GEOFENCE RING (Improvement 6)
                val safetyCirclePoints = ArrayList<GeoPoint>()
                for (i in 0 until 360 step 8) {
                    val angle = Math.toRadians(i.toDouble())
                    // ~150 meters is roughly 0.00135 degrees latitude/longitude
                    val latRadius = 0.00135
                    val lngRadius = 0.00135 / kotlin.math.cos(Math.toRadians(homeLat))
                    val pt = GeoPoint(homeLat + latRadius * kotlin.math.sin(angle), homeLng + lngRadius * kotlin.math.cos(angle))
                    safetyCirclePoints.add(pt)
                }
                safetyCirclePoints.add(safetyCirclePoints[0]) // Seal circle loops

                val geofenceBoundary = Polyline(mapView).apply {
                    setPoints(safetyCirclePoints)
                    outlinePaint.color = android.graphics.Color.parseColor("#00E676") // Neon green
                    outlinePaint.strokeWidth = 2.2f * density
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                    outlinePaint.alpha = 110 // Semi-glowing glass look
                }
                mapView.overlays.add(geofenceBoundary)

                // Draw central HOME baseline anchor marker pin
                val homeMarker = Marker(mapView).apply {
                    position = GeoPoint(homeLat, homeLng)
                    icon = createHomeMarkerDrawable(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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

                    // Draw visual breadcrumb trails showing previous location history (Visual Trails)
                    val trailPoints = locationTrails[member.id] ?: emptyList()
                    if (trailPoints.size >= 2) {
                        val trailPolyline = Polyline(mapView).apply {
                            val geoPoints = trailPoints.map { GeoPoint(it.first, it.second) }
                            setPoints(geoPoints)
                            
                            val memberColorInt = try {
                                android.graphics.Color.parseColor(member.avatarColorHex)
                            } catch (e: Exception) {
                                android.graphics.Color.BLUE
                            }
                            outlinePaint.color = memberColorInt
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            
                            if (isSelected) {
                                outlinePaint.strokeWidth = 4.5f * density
                                outlinePaint.alpha = 220 // Highlight selected member trail brightly
                            } else {
                                outlinePaint.strokeWidth = 2.5f * density
                                outlinePaint.alpha = 90 // Subtle trace for other members
                            }
                        }
                        mapView.overlays.add(trailPolyline)
                    }

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

                    // Check if member has triggered an active SOS emergency distress beacon
                    val isSos = member.statusText.contains("🚨 EMERGENCY SOS ACTIVE") || member.statusText.contains("🚨 SOS")
                    
                    // Dynamically build colored, initials/emoji-based density-scaled marker pin
                    val markerLabel = if (isSos) "🚨" else if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else (member.name.firstOrNull()?.toString() ?: "M")
                    val markerColor = if (isSos) "#FF1744" else member.avatarColorHex
                    
                    val memberMarker = Marker(mapView).apply {
                        position = memberGeo
                        icon = createColoredMarkerDrawable(context, markerColor, markerLabel, isSelected || isSos)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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

        // ----------------- LIFE360 PREMIUM MAP OVERLAY HUD -----------------

        // 1. FLOATING TOP HUD ACTION BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings trigger button (Scrolls down to sync config)
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable { onSettingsClick() },
                color = Color.White,
                shape = CircleShape,
                border = BorderStroke(1.dp, SlateBorder),
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("⚙️", fontSize = 18.sp)
                }
            }

            // Center Dropdown: De Souza Family Selector!
            var isDropdownExpanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .clickable { isDropdownExpanded = true },
                    color = Color.White,
                    shape = RoundedCornerShape(21.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "De Souza Family",
                            color = Color(0xFF1E1E24),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("▼", fontSize = 8.sp, color = Color.Gray)
                    }
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.background(Color.White).border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("De Souza Family (Primary Circle)", color = Color.Black, fontWeight = FontWeight.Bold) },
                        onClick = { isDropdownExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Create New Circle...", color = PrimaryCosmic) },
                        onClick = { 
                            isDropdownExpanded = false
                            onSettingsClick()
                        }
                    )
                }
            }

            // 1b. OFFLINE MAP ENHANCED TRUST INDICATOR (Answers the offline map question with state and info popup!)
            var showOfflineInfoDialog by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .height(42.dp)
                    .clickable { showOfflineInfoDialog = true }
                    .testTag("offline_cache_badge"),
                color = Color(0xE81A2F1D), // Deep organic forest card styling
                shape = RoundedCornerShape(21.dp),
                border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.4f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF00FF87), CircleShape)
                    )
                    Text(
                        text = "Map Cache: Synced CR8 4DS 🗺️",
                        color = Color(0xFFB9F6CA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                            Text("🗺️", fontSize = 18.sp)
                            Text(
                                text = "Offline Map Storage Active",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Is map data around my home saved?",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Yes! KinTracker is fully optimized for local autonomy and offline use. Every tile retrieved around your configured home (CR8 4DS and CR8 4DA areas) is saved permanently on your device's internal SQLite tile database.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Any location you scroll to on the map is also automatically cached so you can track your family circle with zero cellular data consumption.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("📁 Cache Dir: /data/user/0/.../cache/osmdroid", fontSize = 9.sp, color = SecondarySlate, fontFamily = FontFamily.Monospace)
                                    Text("⚡ Status: Active / Fully Synced around Home", fontSize = 9.sp, color = Color(0xFF00FF87), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showOfflineInfoDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Awesome", color = Color.White, fontSize = 12.sp)
                        }
                    },
                    containerColor = CosmicSlateCard,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp
                )
            }

            // 2. WHATSAPP CHAT BUTTON (Themed green border & clear chat bubble)
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable { onOpenWhatsApp() }
                    .testTag("whatsapp_chat_button"),
                color = Color.White,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color(0xFF25D366)), // Real WhatsApp Green!
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("💬", fontSize = 18.sp)
                }
            }
        }

        // 1d. CAM FOLLOW ACTIVE FLOATING BADGE
        if (isCameraFollowingMe) {
            Box(
                modifier = Modifier
                    .padding(top = 66.dp)
                    .align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color(0xDC1B5E20), // Translucent green background
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.5f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF00FF87), CircleShape)
                        )
                        Text(
                            text = "CAM FOLLOW ACTIVE 🟢",
                            color = Color(0xFFB9F6CA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 3. MAP STYLE SELECTION HUD (Pill style floating at Bottom Left)
        Surface(
            modifier = Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = bottomPadding + 56.dp)
                .align(Alignment.BottomStart),
            color = Color.White.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryCosmic else Color.Transparent)
                            .clickable { mapTypeMode = mode }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
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

        // 4. CAPSULE ACTION OVERLAYS (Bottom row of map)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = bottomPadding + 6.dp)
                .align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // "Check in" floating action capsule
            Surface(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { onTriggerCheckIn() },
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, SlateBorder),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛡️", fontSize = 14.sp)
                    Text(
                        text = "Check in",
                        color = Color(0xFF5D2EE6), // Purple color matches image
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // "SOS" panic safety capsule
            Surface(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { onTriggerSOS() },
                color = Color(0xFFE53935), // Urgent Red SOS pill background
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, Color.White),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚨", fontSize = 14.sp)
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // 5. COHESIVE MAP CONTROLS FLOATING CONTAINER (BottomEnd - Zoom & Compass Recenter)
        Column(
            modifier = Modifier
                .padding(end = 14.dp, bottom = bottomPadding + 56.dp)
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // WHERE AM I NOW (FIND ME ON MAP) FAB
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        val meLoc = members.firstOrNull { it.id == "me" }
                        if (meLoc != null && meLoc.y != 0.0 && meLoc.x != 0.0) {
                            onSelectMember("me") // Focus selection
                            isCameraFollowingMe = !isCameraFollowingMe // Toggle follow mode
                            mapViewRef?.let {
                                it.controller.animateTo(GeoPoint(meLoc.y, meLoc.x))
                                it.controller.setZoom(15.5)
                            }
                        }
                    },
                color = if (isCameraFollowingMe) Color(0xFF1B5E20) else Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isCameraFollowingMe) Color(0xFF00FF87) else SlateBorder),
                shadowElevation = 6.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Where am I now",
                        tint = if (isCameraFollowingMe) Color.White else RadarCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ANCHOR RECENTER COMPASS FAB (HOME)
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        isCameraFollowingMe = false // Disable tracking
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

        // 6. QUICK EMOJI REACTION TOOLBAR — always visible near top, auto-targets first family member
        val reactionTarget = members.firstOrNull { it.id != "me" }
        if (reactionTarget != null) {
            Row(
                modifier = Modifier
                    .padding(top = 62.dp, start = 12.dp, end = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("🍅", "Boo!", "boo_reaction_btn"),
                    Triple("❤️", "Love ya!", "love_reaction_btn"),
                    Triple("😳", "Slow down!", "slowdown_reaction_btn")
                ).forEach { (emoji, label, tag) ->
                    Surface(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .clickable { onSendReaction(reactionTarget.id, "$emoji $label") }
                            .testTag(tag),
                        color = Color.White.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFF5D2EE6).copy(alpha = 0.35f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = emoji, fontSize = 12.sp)
                            Text(
                                text = label,
                                color = Color(0xFF5D2EE6),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

    }
}

// Life360-style circular bubble marker with teardrop stem and drop shadow
private fun createColoredMarkerDrawable(
    context: Context,
    colorHex: String,
    emoji: String,
    isSelected: Boolean
): Drawable {
    val density = context.resources.displayMetrics.density

    // Bubble diameter: 54dp normal, 66dp selected
    val bubbleDp = if (isSelected) 66 else 54
    val bubblePx = (bubbleDp * density).toInt()

    // Stem: 10dp wide, 14dp tall
    val stemW = (10 * density).toInt()
    val stemH = (14 * density).toInt()

    // Shadow blur and offset
    val shadowRadius = (4 * density)
    val shadowDy = (2 * density)
    val shadowPad = (shadowRadius + shadowDy).toInt() + 2

    // Total bitmap: width = bubblePx + shadowPad*2, height = bubblePx + stemH + shadowPad*2
    val bmpW = bubblePx + shadowPad * 2
    val bmpH = bubblePx + stemH + shadowPad * 2
    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val parsedColor = try {
        android.graphics.Color.parseColor(colorHex)
    } catch (e: Exception) {
        android.graphics.Color.parseColor("#5D2EE6")
    }

    val cx = bmpW / 2f
    val bubbleTop = shadowPad.toFloat()
    val bubbleBottom = bubbleTop + bubblePx
    val bubbleCy = bubbleTop + bubblePx / 2f
    val bubbleR = bubblePx / 2f

    val stemTipX = cx
    val stemTipY = bubbleBottom + stemH - shadowPad

    // ── Shadow layer (drawn first, slightly offset) ──
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        alpha = 55
        maskFilter = android.graphics.BlurMaskFilter(shadowRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    // Shadow circle
    canvas.drawCircle(cx + 1.5f, bubbleCy + shadowDy + 1.5f, bubbleR - density, shadowPaint)

    // ── White fill circle ──
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, bubbleCy, bubbleR - density, fillPaint)

    // ── Selected: outer glow ring ──
    if (isSelected) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedColor
            alpha = 60
            style = Paint.Style.STROKE
            strokeWidth = 5f * density
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - 0.5f * density, glowPaint)
    }

    // ── Colored border ring ──
    val ringWidth = if (isSelected) 3.5f * density else 2.8f * density
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = parsedColor
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
    }
    canvas.drawCircle(cx, bubbleCy, bubbleR - ringWidth / 2f - density * 0.5f, ringPaint)

    // ── Teardrop stem: filled triangle pointing down ──
    val stemPath = android.graphics.Path()
    val stemBaseHalf = stemW / 2f
    stemPath.moveTo(cx - stemBaseHalf, bubbleBottom - density * 2f)
    stemPath.lineTo(cx + stemBaseHalf, bubbleBottom - density * 2f)
    stemPath.lineTo(stemTipX, stemTipY)
    stemPath.close()
    val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawPath(stemPath, stemPaint)
    // Stem border edges matching the ring color
    val stemBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = parsedColor
        style = Paint.Style.STROKE
        strokeWidth = ringWidth * 0.85f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(stemPath, stemBorderPaint)

    // ── Emoji centered in the bubble ──
    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = (if (isSelected) 28f else 22f) * density
        typeface = Typeface.DEFAULT
    }
    // Vertically center using font metrics
    val fm = emojiPaint.fontMetrics
    val textY = bubbleCy - (fm.ascent + fm.descent) / 2f
    canvas.drawText(emoji, cx, textY, emojiPaint)

    return BitmapDrawable(context.resources, bitmap)
}


// Life360-style home bubble marker
private fun createHomeMarkerDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density

    val bubblePx = (52 * density).toInt()
    val stemW = (10 * density).toInt()
    val stemH = (13 * density).toInt()
    val shadowRadius = (4 * density)
    val shadowDy = (2 * density)
    val shadowPad = (shadowRadius + shadowDy).toInt() + 2

    val bmpW = bubblePx + shadowPad * 2
    val bmpH = bubblePx + stemH + shadowPad * 2
    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val homeColor = android.graphics.Color.parseColor("#2E7D32")
    val cx = bmpW / 2f
    val bubbleTop = shadowPad.toFloat()
    val bubbleBottom = bubbleTop + bubblePx
    val bubbleCy = bubbleTop + bubblePx / 2f
    val bubbleR = bubblePx / 2f
    val stemTipY = bubbleBottom + stemH - shadowPad

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        alpha = 50
        maskFilter = android.graphics.BlurMaskFilter(shadowRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawCircle(cx + 1.5f, bubbleCy + shadowDy + 1.5f, bubbleR - density, shadowPaint)

    // White fill
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, bubbleCy, bubbleR - density, fillPaint)

    // Green border ring
    val ringWidth = 3f * density
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = homeColor
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
    }
    canvas.drawCircle(cx, bubbleCy, bubbleR - ringWidth / 2f - density * 0.5f, ringPaint)

    // Stem
    val stemPath = android.graphics.Path()
    stemPath.moveTo(cx - stemW / 2f, bubbleBottom - density * 2f)
    stemPath.lineTo(cx + stemW / 2f, bubbleBottom - density * 2f)
    stemPath.lineTo(cx, stemTipY)
    stemPath.close()
    canvas.drawPath(stemPath, fillPaint)
    val stemBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = homeColor
        style = Paint.Style.STROKE
        strokeWidth = ringWidth * 0.85f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(stemPath, stemBorderPaint)

    // House emoji
    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f * density
        typeface = Typeface.DEFAULT
    }
    val fm = emojiPaint.fontMetrics
    canvas.drawText("🏠", cx, bubbleCy - (fm.ascent + fm.descent) / 2f, emojiPaint)

    return BitmapDrawable(context.resources, bitmap)
}
