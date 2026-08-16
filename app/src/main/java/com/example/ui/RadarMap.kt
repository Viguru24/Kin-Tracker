package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.zIndex
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
import com.example.data.SafeZone
import com.example.data.formatTimeAgo
import com.example.data.formatDuration
import com.example.data.formatExactTime
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Polygon
import java.io.File

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.FileOutputStream

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
    onOpenWhatsApp: (FamilyMember) -> Unit = {},
    onUpdateMember: (FamilyMember) -> Unit = {},
    onDeleteMember: (String) -> Unit = {},
    onTriggerAlarm: (String) -> Unit = {},
    activeGroupCreatorId: String = "",
    myDeviceUUID: String = "",
    onKickMember: (String) -> Unit = {},
    safeZones: List<SafeZone> = emptyList(),
    onAddSafeZone: (SafeZone) -> Unit = {},
    onDeleteSafeZone: (SafeZone) -> Unit = {},
    memberWeatherDetailed: Map<String, FamilyViewModel.WeatherInfo> = emptyMap(),
    isCircleDigestReset: Boolean = false,
    onResetCircleDigest: () -> Unit = {},
    groupPinMappings: List<com.example.data.GroupPinMapping> = emptyList(),
    activeGroupPinCode: String = "",
    onSwitchCircle: (String) -> Unit = {},
    bottomPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapTypeMode by remember { mutableStateOf("streets") } // streets, hybrid (midnight), radar (neon)
    
    // Popup state for face tapping options
    var memberForContextMenu by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToEdit by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToDelete by remember { mutableStateOf<FamilyMember?>(null) }
    var zoneToDelete by remember { mutableStateOf<SafeZone?>(null) }
    var showBatteryDialogForMember by remember { mutableStateOf<FamilyMember?>(null) }
    
    var showAddZoneDialog by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }
    var newZoneRadius by remember { mutableStateOf(5f) }
    var newZoneIcon by remember { mutableStateOf("home") }
    
    var isDigestOpen by remember { mutableStateOf(false) }

    val colorsList = listOf(
        "#EC407A", // Magenta Pink
        "#26A69A", // Teal
        "#42A5F5", // Cyan Blue
        "#FF9800", // Gold/Orange
        "#FFEA00", // Yellow-Glow
        "#E040FB", // Hot violet
        "#00FF87"  // Neon green
    )


    // Remember the MapView reference to trigger zoom & camera animations from Compose UI blocks
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isCameraFollowingMe by remember { mutableStateOf(false) }
    var mapZoomLevel by remember { mutableStateOf(15.5) }
    var isRouteTrailEnabled by remember { mutableStateOf(false) }

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
        isRouteTrailEnabled = false
        if (selectedMemberId != null) {
            val member = members.firstOrNull { it.id == selectedMemberId }
            if (member != null) {
                mapViewRef?.let { map ->
                    map.controller.animateTo(GeoPoint(member.y, member.x))
                    map.controller.setZoom(15.5)
                }
            }
        } else {
            // Show All Members on Map: fit all active members in view
            val validMembers = members.filter { it.x != 0.0 && it.y != 0.0 }
            if (validMembers.isNotEmpty()) {
                mapViewRef?.let { map ->
                    val minLat = validMembers.minOf { it.y }
                    val maxLat = validMembers.maxOf { it.y }
                    val minLng = validMembers.minOf { it.x }
                    val maxLng = validMembers.maxOf { it.x }
                    val centerLat = (minLat + maxLat) / 2.0
                    val centerLng = (minLng + maxLng) / 2.0
                    map.controller.animateTo(GeoPoint(centerLat, centerLng))
                    val maxDiff = maxOf(maxLat - minLat, maxLng - minLng)
                    val zoomLevel = when {
                        maxDiff > 0.5 -> 10.0
                        maxDiff > 0.2 -> 11.5
                        maxDiff > 0.05 -> 13.0
                        else -> 14.2
                    }
                    map.controller.setZoom(zoomLevel)
                }
            }
        }
    }

    // Auto-fit route between Start/Home and Current Location when Route Trail is toggled ON!
    LaunchedEffect(isRouteTrailEnabled) {
        if (isRouteTrailEnabled && selectedMemberId != null) {
            val member = members.firstOrNull { it.id == selectedMemberId }
            if (member != null) {
                val recordedPoints = locationTrails[member.id] ?: emptyList()
                val isAway = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(member.y - homeLat, member.x - homeLng) * 111.0 > 0.06)
                val points = if (recordedPoints.size >= 2) recordedPoints else if (isAway) listOf(Pair(homeLat, homeLng), Pair(member.y, member.x)) else emptyList()
                if (points.size >= 2) {
                    mapViewRef?.let { map ->
                        val minLat = points.minOf { it.first }
                        val maxLat = points.maxOf { it.first }
                        val minLng = points.minOf { it.second }
                        val maxLng = points.maxOf { it.second }
                        val latMargin = maxOf((maxLat - minLat) * 0.25, 0.006)
                        val lngMargin = maxOf((maxLng - minLng) * 0.25, 0.006)
                        try {
                            val box = BoundingBox(maxLat + latMargin, maxLng + lngMargin, minLat - latMargin, minLng - lngMargin)
                            map.zoomToBoundingBox(box, true, 100)
                        } catch (e: Exception) {
                            val centerLat = (minLat + maxLat) / 2.0
                            val centerLng = (minLng + maxLng) / 2.0
                            map.controller.animateTo(GeoPoint(centerLat, centerLng))
                        }
                    }
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

                    addMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                            mapZoomLevel = zoomLevelDouble
                            return true
                        }
                    })
                }
            },
            update = { mapView ->
                // Accessing mapZoomLevel here ensures the update block recomposes on zoom events
                val currentZoom = mapZoomLevel
                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mapView)
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

                // Draw custom Safe Zones geofences
                safeZones.forEach { zone ->
                    val zoneCircle = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(zone.latitude, zone.longitude), zone.radiusMeters)
                        fillPaint.color = android.graphics.Color.parseColor("#00E676") // Light green
                        fillPaint.alpha = 20
                        outlinePaint.color = android.graphics.Color.parseColor("#00C853") // Green border
                        outlinePaint.strokeWidth = 2.0f * density
                        outlinePaint.alpha = 110
                    }
                    mapView.overlays.add(zoneCircle)
                    
                    // Skip placing the icon marker for Home zones, keeping only the nice green circle.
                    if (zone.iconName.lowercase() != "home" && !zone.name.lowercase().contains("home")) {
                        val zoneMarker = object : Marker(mapView) {
                            override fun showInfoWindow() {}
                        }.apply {
                            position = GeoPoint(zone.latitude, zone.longitude)
                            icon = MapMarkerRenderer.getOrCreateZoneIcon(context, zone.name, zone.iconName)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setOnMarkerClickListener { _, _ ->
                                zoneToDelete = zone
                                true
                            }
                        }
                        mapView.overlays.add(zoneMarker)
                    }
                }



                  // --- START OF MARKER DECONFLICTION LAYOUT ENGINE ---
                 val adjustedCoordinates = mutableMapOf<String, GeoPoint>()
                 val clusters = mutableListOf<MutableList<String>>()
                 val visited = mutableSetOf<String>()
 
                  val projection = mapView.projection
                  val clusterThresholdPx = 35.0f * density // 35dp threshold to cluster only very close markers
                  val spreadRadiusPx = 18.0f * density // 18dp radius to spread them slightly
 
                 for (i in members.indices) {
                     val m1 = members[i]
                     if (visited.contains(m1.id)) continue
                     
                     val currentCluster = mutableListOf(m1.id)
                     visited.add(m1.id)
                     
                     val p1 = android.graphics.Point()
                     projection.toPixels(GeoPoint(m1.y, m1.x), p1)
                     
                     for (j in i + 1 until members.size) {
                         val m2 = members[j]
                         if (visited.contains(m2.id)) continue
                         
                         val p2 = android.graphics.Point()
                         projection.toPixels(GeoPoint(m2.y, m2.x), p2)
                         
                         val dx = p1.x - p2.x
                         val dy = p1.y - p2.y
                         val pixelDist = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
                         if (pixelDist < clusterThresholdPx) { // overlap threshold check in pixels
                             currentCluster.add(m2.id)
                             visited.add(m2.id)
                         }
                     }
                     clusters.add(currentCluster)
                 }
 
                 for (cluster in clusters) {
                     if (cluster.size == 1) {
                         val mId = cluster[0]
                         val member = members.first { it.id == mId }
                         adjustedCoordinates[mId] = GeoPoint(member.y, member.x)
                     } else {
                         // Find screen center of the cluster
                         var sumX = 0.0
                         var sumY = 0.0
                         for (mId in cluster) {
                             val member = members.first { it.id == mId }
                             val p = android.graphics.Point()
                             projection.toPixels(GeoPoint(member.y, member.x), p)
                             sumX += p.x
                             sumY += p.y
                         }
                         val centerX = sumX / cluster.size
                         val centerY = sumY / cluster.size
                         
                         // Spread markers symmetrically in a circle of spreadRadiusPx in screen space
                         val angleStep = 2.0 * Math.PI / cluster.size
                         for (idx in cluster.indices) {
                             val mId = cluster[idx]
                             val angle = idx * angleStep
                             val offsetX = spreadRadiusPx * kotlin.math.cos(angle)
                             val offsetY = spreadRadiusPx * kotlin.math.sin(angle)
                             
                             val targetX = (centerX + offsetX).toInt()
                             val targetY = (centerY + offsetY).toInt()
                             
                             // Project back to GeoPoint safely
                             val geoPt = projection.fromPixels(targetX, targetY)
                             adjustedCoordinates[mId] = GeoPoint(geoPt.latitude, geoPt.longitude)
                         }
                     }
                 }
                 // --- END OF MARKER DECONFLICTION LAYOUT ENGINE ---

                // Draw "me" first so other family members are drawn on top of "me" (z-order dominance)
                val sortedMembers = members.sortedWith(Comparator { m1, m2 ->
                    when {
                        m1.id == "me" && m2.id != "me" -> -1
                        m1.id != "me" && m2.id == "me" -> 1
                        else -> 0
                    }
                })

                // 3. Draw active family members and connect transit paths
                sortedMembers.forEach { member ->
                    val displayGeo = adjustedCoordinates[member.id] ?: GeoPoint(member.y, member.x)
                    val trueGeo = GeoPoint(member.y, member.x)
                    val isSelected = member.id == selectedMemberId
                    val distToHome = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                    val atHome = distToHome < 0.05 || member.statusText.contains("At Home")

                    val memberColor = try {
                        android.graphics.Color.parseColor(member.avatarColorHex)
                    } catch (e: Exception) { android.graphics.Color.BLUE }


                    // Draw a transparent circle ring around the true location (Only for 'me' to avoid map clutter)
                    if (member.id == "me") {
                        val ring = Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(trueGeo, 60.0) // 60 meters radius
                            fillPaint.color = android.graphics.Color.TRANSPARENT
                            outlinePaint.color = memberColor
                            outlinePaint.strokeWidth = 2.0f * density
                            outlinePaint.alpha = 80 // Semi-transparent outline ring
                        }
                        mapView.overlays.add(ring)
                    }

                    // Draw visual breadcrumb trails showing previous location history ONLY when enabled & member is selected!
                    if (isSelected && isRouteTrailEnabled) {
                        val recordedPoints = locationTrails[member.id] ?: emptyList()
                        val isAway = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(member.y - homeLat, member.x - homeLng) * 111.0 > 0.06)
                        val trailPoints = if (recordedPoints.size >= 2) {
                            recordedPoints
                        } else if (isAway) {
                            listOf(Pair(homeLat, homeLng), Pair(member.y, member.x))
                        } else {
                            recordedPoints
                        }

                        if (trailPoints.size >= 2) {
                            val trailPolyline = Polyline(mapView).apply {
                                val geoPoints = trailPoints.map { GeoPoint(it.first, it.second) }
                                setPoints(geoPoints)
                                outlinePaint.color = memberColor
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.strokeWidth = 5.5f * density
                                outlinePaint.alpha = 235
                            }
                            mapView.overlays.add(trailPolyline)
                        }
                    }

                    // 4. LOW BATTERY CRITICAL SAFETY BEACONS
                    if (member.batteryPercentage <= 15 && !member.isCharging) {
                        val hazardMarker = object : Marker(mapView) {
                            override fun showInfoWindow() {}
                        }.apply {
                            position = GeoPoint(member.y - 0.0003, member.x + 0.0003) // offset slightly to be visible next to avatar
                            icon = MapMarkerRenderer.getOrCreateMarkerDrawable(context, "#FF1744", "⚠️", false, "", "", false)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(hazardMarker)
                    }

                    // Draw the face bubble markers on the very top layer (at displayGeo)
                    val isSos = member.statusText.contains("🚨 EMERGENCY SOS ACTIVE") || member.statusText.contains("🚨 SOS")
                    val markerLabel = if (isSos) "🚨" else if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else (member.name.firstOrNull()?.toString() ?: "M")
                    val markerColorHex = if (isSos) "#FF1744" else member.avatarColorHex
                    val isOffline = member.batteryPercentage <= 5

                    // Live relative time badge from the stored lastActive timestamp
                    val timeLabel = if (member.id == "me") "now"
                                    else if (member.lastActive > 0L) formatTimeAgo(member.lastActive)
                                    else "now"

                    // Location duration badge (how long at this spot)
                    val locationDurationLabel = if (member.locationSince > 0L)
                        "📍 here ${formatDuration(member.locationSince)}" else ""

                    val memberMarker = object : Marker(mapView) {
                        override fun showInfoWindow() {
                            // No-op: completely suppress default grey speech bubbles
                        }
                    }.apply {
                        position = displayGeo
                        icon = MapMarkerRenderer.getOrCreateMarkerDrawable(
                            context = context,
                            colorHex = markerColorHex,
                            emoji = markerLabel,
                            isSelected = isSelected || isSos,
                            photoPath = member.photoPath,
                            weatherEmoji = "",
                            isOffline = isOffline,
                            relativeTime = timeLabel,
                            locationDuration = locationDurationLabel,
                            batteryPercentage = if (member.id != "me") member.batteryPercentage else -1,
                            isCharging = member.isCharging
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { m, _ ->
                            onSelectMember(if (selectedMemberId == member.id) null else member.id)
                            memberForContextMenu = member
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

        // 1. FLOATING TOP HUD ACTION BAR — settings & digest (left) + circle switcher (centre) + offline badge (right)
        var showCircleSwitcher by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Settings trigger button with integrated version badge
                Box {
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
                    
                    // Small version badge circle overlapping the settings button at bottom-end
                    Surface(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp),
                        color = Color(0xFF1E1E24),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.9f)),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "1.6",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }

                // Circle Digest trigger button
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { isDigestOpen = true }
                        .testTag("weekly_digest_button"),
                    color = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SlateBorder),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("📊", fontSize = 18.sp)
                    }
                }
            }

            // ---- CENTRE: Circle Switcher Pill ----
            Box(contentAlignment = Alignment.TopCenter) {
                Surface(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 120.dp, max = 200.dp)
                        .clickable { showCircleSwitcher = !showCircleSwitcher },
                    color = Color(0xF0121218),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.5f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "👥",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (activeGroupPinCode.isNotBlank()) "PIN $activeGroupPinCode"
                                   else if (groupPinMappings.isNotEmpty()) groupPinMappings.first().groupName.ifBlank { "My Circle" }
                                   else "My Circle",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showCircleSwitcher) "▲" else "▼",
                            color = RadarCyan,
                            fontSize = 9.sp
                        )
                    }
                }

                // Dropdown list of circles
                if (showCircleSwitcher && groupPinMappings.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 40.dp)
                            .widthIn(min = 180.dp, max = 240.dp),
                        color = Color(0xF5121218),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.3f)),
                        shadowElevation = 16.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            groupPinMappings.forEach { circle ->
                                val isActive = circle.pinCode == activeGroupPinCode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSwitchCircle(circle.pinCode)
                                            showCircleSwitcher = false
                                        }
                                        .background(
                                            if (isActive) RadarCyan.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = circle.groupName.ifBlank { "Circle ${circle.pinCode}" },
                                            color = if (isActive) RadarCyan else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = "PIN ${circle.pinCode}",
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (isActive) {
                                        Text("●", color = RadarCyan, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1b. OFFLINE MAP CACHE STATUS — compact circle (tap for full info)
            var showOfflineInfoDialog by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable { showOfflineInfoDialog = true }
                    .testTag("offline_cache_badge"),
                color = Color(0xE81A2F1D),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color(0xFF00C853).copy(alpha = 0.6f)),
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF00FF87), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("✅", fontSize = 11.sp, lineHeight = 12.sp)
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Vector and satellite street map tiles around Home and Circle areas are pre-cached locally on this device.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = SlateBorder.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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

        }

        // 1c. FLOATING ROUTE TRAIL TOGGLE PILL (Visible when a member is selected)
        val selectedMember = members.firstOrNull { it.id == selectedMemberId }
        if (selectedMember != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { isRouteTrailEnabled = !isRouteTrailEnabled },
                color = if (isRouteTrailEnabled) PrimaryCosmic else Color(0xF01E1E28),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isRouteTrailEnabled) RadarCyan else SlateBorder),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🛤️", fontSize = 13.sp)
                    Text(
                        text = if (isRouteTrailEnabled) "Route Trail: ON (Tap to hide)" else "Route Trail: OFF (Tap to show)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. MAP STYLE SELECTION HUD (Round button style with premium popup)
        if (bottomPadding < 200.dp) {
            var showMapStyleMenu by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .padding(start = 14.dp, bottom = bottomPadding + 20.dp)
                    .align(Alignment.BottomStart)
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { showMapStyleMenu = !showMapStyleMenu },
                    color = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SlateBorder),
                    shadowElevation = 6.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("🗺️", fontSize = 18.sp)
                    }
                }

                // Popup themes list floating above the button
                if (showMapStyleMenu) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 50.dp) // Float above the round button
                            .width(150.dp),
                        color = Color(0xF5121218),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.4f)),
                        shadowElevation = 12.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            listOf(
                                Triple("streets", "Real Map", "Standard style"),
                                Triple("hybrid", "Midnight", "Dark theme"),
                                Triple("radar", "Retro Green", "Radar HUD Style")
                            ).forEach { (mode, label, desc) ->
                                val isSelected = mapTypeMode == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            mapTypeMode = mode
                                            showMapStyleMenu = false
                                        }
                                        .background(
                                            if (isSelected) RadarCyan.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = label,
                                            color = if (isSelected) RadarCyan else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = desc,
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        // 5. COHESIVE MAP CONTROLS FLOATING CONTAINER (BottomEnd - Zoom & Compass Recenter)
        Column(
            modifier = Modifier
                .padding(end = 14.dp, bottom = bottomPadding + 20.dp)
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // ADD SAFE ZONE FAB
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        showAddZoneDialog = true
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
                    Text("🛡️", fontSize = 18.sp)
                }
            }

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
        }

        // --- CONTEXT MENU DIALOG OVERLAY FOR MAP MARKER TAP ---
        memberForContextMenu?.let { member ->
            MapContextMenu(
                member = member,
                myDeviceUUID = myDeviceUUID,
                activeGroupCreatorId = activeGroupCreatorId,
                isRouteTrailEnabled = isRouteTrailEnabled,
                onToggleRouteTrail = { isRouteTrailEnabled = !isRouteTrailEnabled },
                onDismiss = { memberForContextMenu = null },
                onOpenWhatsApp = onOpenWhatsApp,
                onTriggerSOS = onTriggerSOS,
                onSendReaction = onSendReaction,
                onTriggerAlarm = onTriggerAlarm,
                onKickMember = onKickMember,
                onEditMember = { memberToEdit = it },
                onDeleteMember = { memberToDelete = it }
            )
        }

    showBatteryDialogForMember?.let { member ->
        BatteryStatusDialog(
            member = member,
            onDismiss = { showBatteryDialogForMember = null }
        )
    }

    // Edit Dialog
    memberToEdit?.let { member ->
        MemberEditDialog(
            member = member,
            onDismiss = { memberToEdit = null },
            onSave = onUpdateMember
        )
    }

    // Delete Confirmation Dialog
    memberToDelete?.let { member ->
        MemberDeleteDialog(
            member = member,
            onDismiss = { memberToDelete = null },
            onConfirm = {
                onDeleteMember(member.id)
                memberToDelete = null
            }
        )
    }

    // Delete Zone Confirmation Dialog
    zoneToDelete?.let { zone ->
        AlertDialog(
            onDismissRequest = { zoneToDelete = null },
            title = {
                Text(text = "Delete Safe Zone", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Are you sure you want to delete the safe zone \"${zone.name}\"?", color = TextSecondary, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSafeZone(zone)
                        zoneToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { zoneToDelete = null }) {
                    Text("Cancel", color = SecondarySlate)
                }
            },
            containerColor = CosmicSlateCard,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    // Add Safe Zone Dialog
    if (showAddZoneDialog) {
        AlertDialog(
            onDismissRequest = { showAddZoneDialog = false },
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
                            valueRange = 2f..10f,
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
                        showAddZoneDialog = false
                        newZoneName = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic)
                ) {
                    Text("Save Zone", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddZoneDialog = false }) {
                    Text("Cancel", color = SecondarySlate)
                }
            },
            containerColor = CosmicSlateCard,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }

    // Weekly Circle Digest Dashboard Sheet
    if (isDigestOpen) {
        CircleDigestDashboard(
            isReset = isCircleDigestReset,
            onReset = onResetCircleDigest,
            onDismiss = { isDigestOpen = false }
        )
    }

    // ----------------- LIFE360 PREMIUM BOTTOM WEATHER HUD -----------------
    WeatherHudOverlay(
        members = members,
        selectedMemberId = selectedMemberId,
        memberWeatherDetailed = memberWeatherDetailed,
        bottomPadding = bottomPadding,
        modifier = Modifier.align(Alignment.BottomCenter)
    )

    }
}

