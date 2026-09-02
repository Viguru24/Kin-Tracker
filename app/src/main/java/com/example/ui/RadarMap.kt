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
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.FamilyMember
import com.example.data.SafeZone
import com.example.data.ShoppingItem
import com.example.data.formatTimeAgo
import com.example.data.formatDuration
import com.example.data.formatExactTime
import com.example.data.formatTransitBadge
import com.example.data.classifyTransitMode
import com.example.data.TransitMode
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
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
    activeRingingMembers: Set<String> = emptySet(),
    activeGroupCreatorId: String = "",
    myDeviceUUID: String = "",
    onKickMember: (String) -> Unit = {},
    homeRadiusMeters: Double = 65.0,
    workLat: Double = 0.0,
    workLng: Double = 0.0,
    isWorkCalibrated: Boolean = false,
    workRadiusMeters: Double = 75.0,
    safeZones: List<SafeZone> = emptyList(),
    onAddSafeZone: (SafeZone) -> Unit = {},
    onDeleteSafeZone: (SafeZone) -> Unit = {},
    memberWeatherDetailed: Map<String, FamilyViewModel.WeatherInfo> = emptyMap(),
    isCircleDigestReset: Boolean = false,
    onResetCircleDigest: () -> Unit = {},
    groupPinMappings: List<com.example.data.GroupPinMapping> = emptyList(),
    activeGroupPinCode: String = "",
    onSwitchCircle: (String) -> Unit = {},
    routeTimeFilter: String = "today",
    onSelectRouteTimeFilter: (String) -> Unit = {},
    shoppingItems: List<ShoppingItem> = emptyList(),
    onOpenShoppingList: () -> Unit = {},
    bottomPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE) }
    var mapTypeMode by remember { mutableStateOf(prefs.getString("mapTypeMode", "streets").let { if (it == "satellite" || it.isNullOrBlank()) "streets" else it }) }
    var showMapStyleMenu by remember { mutableStateOf(false) }
    
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
    var isFollowingSelectedMember by remember { mutableStateOf(true) }
    var mapZoomLevel by remember { mutableStateOf(15.5) }
    var isRouteTrailEnabled by remember { mutableStateOf(false) }

    var animTick by remember { mutableStateOf(0) }
    val hasMovingMembers = remember(members) {
        members.any { it.speedMph >= 0.6 || classifyTransitMode(it.speedMph, it.statusText) != TransitMode.STATIONARY }
    }
    LaunchedEffect(hasMovingMembers) {
        if (hasMovingMembers) {
            while (true) {
                kotlinx.coroutines.delay(400)
                animTick = (animTick + 1) % 4
            }
        }
    }

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

    val visualCoordinates = remember { mutableStateMapOf<String, GeoPoint>() }
    val activeAnimators = remember { mutableMapOf<String, android.animation.ValueAnimator>() }

    // Smooth coordinate & camera interpolation engine (Dead Reckoning & ValueAnimator)
    LaunchedEffect(members) {
        members.forEach { m ->
            if (m.x != 0.0 && m.y != 0.0) {
                val targetLat = m.y
                val targetLng = m.x
                val start = visualCoordinates[m.id]

                if (start == null) {
                    visualCoordinates[m.id] = GeoPoint(targetLat, targetLng)
                } else {
                    val dLat = targetLat - start.latitude
                    val dLng = targetLng - start.longitude
                    val distKm = kotlin.math.hypot(dLng * 111.0 * Math.cos(Math.toRadians(targetLat)), dLat * 111.0)
                    if (distKm > 0.0002) { // Movement > 0.2 meters
                        activeAnimators[m.id]?.cancel()
                        val startLat = start.latitude
                        val startLng = start.longitude
                        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = 2000L
                            interpolator = android.view.animation.LinearInterpolator()
                            addUpdateListener { anim ->
                                val frac = anim.animatedFraction
                                val currentLat = startLat + dLat * frac
                                val currentLng = startLng + dLng * frac
                                val interpGeo = GeoPoint(currentLat, currentLng)
                                visualCoordinates[m.id] = interpGeo

                                if (m.id == selectedMemberId && isFollowingSelectedMember && !isRouteTrailEnabled) {
                                    mapViewRef?.let { map ->
                                        map.controller.setCenter(interpGeo)
                                    }
                                }
                            }
                        }
                        activeAnimators[m.id] = animator
                        animator.start()
                    }
                }
            }
        }
    }

    val selectedMember = members.firstOrNull { it.id == selectedMemberId }
    val selectedMemberCoord = selectedMember?.let { if (it.x != 0.0 && it.y != 0.0) Pair(it.y, it.x) else null }

    // Reset follow state whenever a new member is selected
    LaunchedEffect(selectedMemberId) {
        isFollowingSelectedMember = true
        isRouteTrailEnabled = false
        selectedMember?.let { m ->
            if (m.x != 0.0 && m.y != 0.0) {
                mapViewRef?.let { map ->
                    val targetGeo = visualCoordinates[m.id] ?: GeoPoint(m.y, m.x)
                    map.controller.animateTo(targetGeo)
                    if (map.zoomLevelDouble < 14.5) {
                        map.controller.setZoom(16.0)
                    }
                }
            }
        }
    }

    // Continuous Live Camera Follow Mode: initial centering when selected or fitting all members
    LaunchedEffect(selectedMemberId, selectedMemberCoord, isFollowingSelectedMember) {
        if (selectedMember != null && selectedMemberCoord != null && isFollowingSelectedMember && !isRouteTrailEnabled) {
            mapViewRef?.let { map ->
                val targetGeo = visualCoordinates[selectedMember.id] ?: GeoPoint(selectedMember.y, selectedMember.x)
                if (map.zoomLevelDouble < 14.5) {
                    map.controller.animateTo(targetGeo)
                    map.controller.setZoom(16.0)
                }
            }
        } else if (selectedMemberId == null) {
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
    LaunchedEffect(isRouteTrailEnabled, selectedMemberId) {
        if (isRouteTrailEnabled) {
            val targetMember = selectedMember ?: members.firstOrNull { it.id != "me" && (kotlin.math.hypot(it.y - homeLat, it.x - homeLng) * 111.0 > 0.06) }
            if (targetMember != null) {
                val recordedPoints = locationTrails[targetMember.id]
                    ?: locationTrails.entries.firstOrNull { it.key.contains(targetMember.name.lowercase().take(4)) || targetMember.id.contains(it.key) }?.value
                    ?: emptyList()
                val isAway = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(targetMember.y - homeLat, targetMember.x - homeLng) * 111.0 > 0.06)
                val points = if (recordedPoints.size >= 2) recordedPoints else if (isAway) listOf(Pair(homeLat, homeLng), Pair(targetMember.y, targetMember.x)) else emptyList()
                if (points.size >= 2) {
                    mapViewRef?.let { map ->
                        val minLat = points.minOf { it.first }
                        val maxLat = points.maxOf { it.first }
                        val minLng = points.minOf { it.second }
                        val maxLng = points.maxOf { it.second }
                        val latMargin = maxOf((maxLat - minLat) * 0.35, 0.008)
                        val lngMargin = maxOf((maxLng - minLng) * 0.35, 0.008)
                        try {
                            val box = BoundingBox(maxLat + latMargin, maxLng + lngMargin, minLat - latMargin, minLng - lngMargin)
                            map.zoomToBoundingBox(box, true, 120)
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

                // Draw custom Safe Zones geofences (excluding Home to keep map clean)
                safeZones.filter { it.iconName.lowercase() != "home" && !it.name.lowercase().contains("home") }.forEach { zone ->
                    val zoneCircle = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(zone.latitude, zone.longitude), zone.radiusMeters)
                        fillPaint.color = android.graphics.Color.parseColor("#00E676") // Light green
                        fillPaint.alpha = 20
                        outlinePaint.color = android.graphics.Color.parseColor("#00C853") // Green border
                        outlinePaint.strokeWidth = 2.0f * density
                        outlinePaint.alpha = 110
                    }
                    mapView.overlays.add(zoneCircle)
                    
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

                // Draw Work Area geofence & marker if calibrated
                if (isWorkCalibrated && workLat != 0.0 && workLng != 0.0) {
                    val workCircle = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(workLat, workLng), workRadiusMeters)
                        fillPaint.color = android.graphics.Color.parseColor("#3D5AFE") // Indigo blue
                        fillPaint.alpha = 25
                        outlinePaint.color = android.graphics.Color.parseColor("#536DFE")
                        outlinePaint.strokeWidth = 2.0f * density
                        outlinePaint.alpha = 130
                    }
                    mapView.overlays.add(workCircle)

                    val workMarker = object : Marker(mapView) {
                        override fun showInfoWindow() {}
                    }.apply {
                        position = GeoPoint(workLat, workLng)
                        icon = MapMarkerRenderer.getOrCreateZoneIcon(context, "Work", "work")
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    mapView.overlays.add(workMarker)
                }



                // --- START OF LIFE360-STYLE CO-LOCATED CLUSTER & DECONFLICTION ENGINE ---
                val adjustedCoordinates = mutableMapOf<String, GeoPoint>()
                val clusterAnchorPoints = mutableMapOf<String, GeoPoint>() // Maps memberId to cluster center anchor
                val clusters = mutableListOf<MutableList<String>>()
                val visited = mutableSetOf<String>()

                val projection = mapView.projection
                // Life360 markers are ~54dp wide. Threshold to detect co-location is 65dp in screen space (or within 45m)
                val clusterThresholdPx = 65.0f * density

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

                        val distKm = kotlin.math.hypot((m1.x - m2.x) * 111.0 * Math.cos(Math.toRadians(m1.y)), (m1.y - m2.y) * 111.0)
                        val isGeographicallyCoLocated = distKm < 0.045 // 45 meters

                        if (pixelDist < clusterThresholdPx || isGeographicallyCoLocated) {
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
                        val isHomeCluster = cluster.any { mId ->
                            val member = members.first { it.id == mId }
                            val dist = kotlin.math.hypot(member.x - homeLng, member.y - homeLat) * 111.0
                            dist < 0.06 || member.statusText.contains("At Home")
                        }

                        val anchorGeo = if (isHomeCluster && homeLat != 0.0 && homeLng != 0.0) {
                            GeoPoint(homeLat, homeLng)
                        } else {
                            var sumLat = 0.0
                            var sumLng = 0.0
                            for (mId in cluster) {
                                val member = members.first { it.id == mId }
                                sumLat += member.y
                                sumLng += member.x
                            }
                            GeoPoint(sumLat / cluster.size, sumLng / cluster.size)
                        }

                        val centerPt = android.graphics.Point()
                        projection.toPixels(anchorGeo, centerPt)

                        // Optimal non-overlapping radial orbit spread distance
                        val spreadRadiusPx = when (cluster.size) {
                            2 -> 38.0f * density // 76dp separation
                            3 -> 46.0f * density
                            4 -> 54.0f * density
                            else -> maxOf(54.0f, (cluster.size * 24.0f) / Math.PI.toFloat()) * density
                        }

                        val startAngle = when (cluster.size) {
                            2 -> -Math.PI / 2.0
                            3 -> -Math.PI / 2.0
                            4 -> -Math.PI / 4.0
                            else -> -Math.PI / 2.0
                        }

                        val angleStep = (2.0 * Math.PI) / cluster.size
                        for (idx in cluster.indices) {
                            val mId = cluster[idx]
                            val angle = startAngle + (idx * angleStep)
                            val offsetX = spreadRadiusPx * kotlin.math.cos(angle)
                            val offsetY = spreadRadiusPx * kotlin.math.sin(angle)

                            val targetX = (centerPt.x + offsetX).toInt()
                            val targetY = (centerPt.y + offsetY).toInt()

                            val geoPt = projection.fromPixels(targetX, targetY)
                            adjustedCoordinates[mId] = GeoPoint(geoPt.latitude, geoPt.longitude)
                            clusterAnchorPoints[mId] = anchorGeo
                        }
                    }
                }
                // --- END OF LIFE360-STYLE CO-LOCATED CLUSTER & DECONFLICTION ENGINE ---

                // Draw Life360 elegant dashed leader lines from cluster anchor to each fanned-out avatar
                for (cluster in clusters) {
                    if (cluster.size > 1) {
                        for (mId in cluster) {
                            val anchor = clusterAnchorPoints[mId]
                            val target = adjustedCoordinates[mId]
                            val member = members.firstOrNull { it.id == mId }
                            if (anchor != null && target != null && member != null) {
                                val memberColor = try {
                                    android.graphics.Color.parseColor(member.avatarColorHex)
                                } catch (_: Exception) { android.graphics.Color.DKGRAY }

                                val leaderLine = Polyline(mapView).apply {
                                    setPoints(listOf(anchor, target))
                                    outlinePaint.color = memberColor
                                    outlinePaint.strokeWidth = 2.2f * density
                                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
                                    outlinePaint.alpha = 160
                                }
                                mapView.overlays.add(leaderLine)
                            }
                        }
                    }
                }

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
                    val isAway = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(member.y - homeLat, member.x - homeLng) * 111.0 > 0.06)
                    val isSelected = member.id == selectedMemberId ||
                            (selectedMemberId != null && (member.id.contains(selectedMemberId) || selectedMemberId.contains(member.id) || member.name.equals(selectedMember?.name, ignoreCase = true)))

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

                    // Draw visual breadcrumb trails showing previous location history ONLY when enabled & member is selected or away!
                    if (isRouteTrailEnabled && (isSelected || (selectedMemberId == null && isAway))) {
                        val recordedPoints = locationTrails[member.id]
                            ?: locationTrails.entries.firstOrNull { it.key.contains(member.name.lowercase().take(4)) || member.id.contains(it.key) }?.value
                            ?: emptyList()
                        val trailPoints = if (recordedPoints.size >= 2) {
                            recordedPoints
                        } else if (isAway) {
                            listOf(Pair(homeLat, homeLng), Pair(member.y, member.x))
                        } else {
                            recordedPoints
                        }

                        if (trailPoints.size >= 2) {
                            val glowPolyline = Polyline(mapView).apply {
                                val geoPoints = trailPoints.map { GeoPoint(it.first, it.second) }
                                setPoints(geoPoints)
                                outlinePaint.color = android.graphics.Color.WHITE
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.strokeWidth = 9.0f * density
                                outlinePaint.alpha = 180
                            }
                            mapView.overlays.add(glowPolyline)

                            val trailPolyline = Polyline(mapView).apply {
                                val geoPoints = trailPoints.map { GeoPoint(it.first, it.second) }
                                setPoints(geoPoints)
                                outlinePaint.color = memberColor
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.strokeWidth = 6.0f * density
                                outlinePaint.alpha = 255
                            }
                            mapView.overlays.add(trailPolyline)
                        }
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

                    // Location duration badge or live movement activity badge (walking feet 👣, driving 🚗, bicycle 🚲, train 🚆)
                    val locationDurationLabel = formatTransitBadge(member.speedMph, member.statusText, member.locationSince)

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
                            isCharging = member.isCharging,
                            animFrame = animTick
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

                // Apply OpenStreetMap customizable styling options using tile sources and matrices
                when (mapTypeMode) {
                    "streets" -> {
                        if (mapView.tileProvider.tileSource.name() != TileSourceFactory.MAPNIK.name()) {
                            mapView.setTileSource(TileSourceFactory.MAPNIK)
                        }
                        // Standard crisp full-color streets
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }
                    "hybrid" -> {
                        if (mapView.tileProvider.tileSource.name() != TileSourceFactory.MAPNIK.name()) {
                            mapView.setTileSource(TileSourceFactory.MAPNIK)
                        }
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
                        if (mapView.tileProvider.tileSource.name() != TileSourceFactory.MAPNIK.name()) {
                            mapView.setTileSource(TileSourceFactory.MAPNIK)
                        }
                        // High-tech Retro Sonar green matrix filter
                        val matrix = floatArrayOf(
                            0f, 0f, 0f, 0f, 0f,
                            0f, 1.4f, 0f, 0f, 40f,
                            0f, 0f, 0f, 0f, 0f,
                            -1f, -1f, -1f, 1.2f, 255f
                        )
                        mapView.overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(matrix))
                    }
                    else -> {
                        if (mapView.tileProvider.tileSource.name() != TileSourceFactory.MAPNIK.name()) {
                            mapView.setTileSource(TileSourceFactory.MAPNIK)
                        }
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }
                }

                mapView.invalidate()
            }
        )

        // ----------------- LIFE360 PREMIUM MAP OVERLAY HUD -----------------
        var showCircleSwitcher by remember { mutableStateOf(false) }
        var showAddDeviceDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .align(Alignment.TopCenter)
                .zIndex(95f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. FLOATING TOP HUD ACTION BAR — Settings, Digest, Map Theme, Circle Switcher, Offline Badge (All on the same horizontal level!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Settings trigger button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onSettingsClick() },
                        color = Color.White,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, SlateBorder),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("⚙️", fontSize = 16.sp)
                        }
                    }

                    // Circle Digest trigger button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { isDigestOpen = true }
                            .testTag("weekly_digest_button"),
                        color = Color.White,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, SlateBorder),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("📊", fontSize = 16.sp)
                        }
                    }

                    // Dedicated Map Layer / Theme Trigger Button
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { showMapStyleMenu = !showMapStyleMenu },
                            color = Color.White,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, SlateBorder),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = when (mapTypeMode) {
                                        "hybrid" -> "🌙"
                                        "radar" -> "🟢"
                                        else -> "🗺️"
                                    },
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // Floating Map Theme Menu Dropdown
                        if (showMapStyleMenu) {
                            Surface(
                                modifier = Modifier
                                    .padding(top = 44.dp)
                                    .widthIn(min = 180.dp, max = 220.dp)
                                    .zIndex(150f),
                                color = Color(0xF5121218),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.5f)),
                                shadowElevation = 20.dp
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        text = "MAP THEME",
                                        color = RadarCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                    )
                                    listOf(
                                        Triple("streets", "🗺️ Real Map", "Standard crisp streets"),
                                        Triple("hybrid", "🌙 Midnight Dark", "Dark contrast mode"),
                                        Triple("radar", "🟢 Retro Sonar", "Tactical radar HUD")
                                    ).forEach { (mode, label, desc) ->
                                        val isSelected = mapTypeMode == mode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    mapTypeMode = mode
                                                    prefs.edit().putString("mapTypeMode", mode).apply()
                                                    showMapStyleMenu = false
                                                }
                                                .background(
                                                    if (isSelected) RadarCyan.copy(alpha = 0.18f)
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
                                                    color = Color(0xFFB0BEC5),
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

                // ---- CENTRE: Circle Switcher Pill (Height 38dp, same level as other buttons!) ----
                Box(contentAlignment = Alignment.TopCenter) {
                    Surface(
                        modifier = Modifier
                            .height(38.dp)
                            .widthIn(min = 120.dp, max = 200.dp)
                            .clickable { showCircleSwitcher = !showCircleSwitcher },
                        color = Color(0xF0121218),
                        shape = RoundedCornerShape(19.dp),
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
                                       else if (groupPinMappings.isNotEmpty()) groupPinMappings.first().groupName.ifBlank { "Family Circle" }
                                       else "Family Circle",
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

                    // Dropdown list of circles + Add Device action (Floats on top of all layers with zIndex 150f)
                    if (showCircleSwitcher) {
                        Surface(
                            modifier = Modifier
                                .padding(top = 44.dp)
                                .widthIn(min = 210.dp, max = 270.dp)
                                .zIndex(150f),
                            color = Color(0xF8121218),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.5f)),
                            shadowElevation = 20.dp
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (groupPinMappings.isNotEmpty()) {
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
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                                }

                                // ➕ Add Device / Member to Circle Button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCircleSwitcher = false
                                            showAddDeviceDialog = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(RadarCyan.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("➕", fontSize = 11.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Add Device to Circle",
                                            color = RadarCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "PIN ${activeGroupPinCode.ifBlank { "8156" }}",
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 1b. OFFLINE MAP CACHE STATUS — compact circle (38dp, same level!)
                var showOfflineInfoDialog by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .size(38.dp)
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
                                    .size(5.dp)
                                    .background(Color(0xFF00FF87), CircleShape)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text("✅", fontSize = 10.sp, lineHeight = 11.sp)
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
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "• Tiles: ~25 MB cached\n• Works without data or signal\n• Fast rendering",
                                    color = Color(0xFF00FF87),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showOfflineInfoDialog = false }) {
                                Text("OK", color = RadarCyan)
                            }
                        },
                        containerColor = CosmicSlateCard,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // 2. LIVE WEATHER STATUS PILL (Floats cleanly at the top of the map below action bar)
            WeatherHudOverlay(
                members = members,
                selectedMemberId = selectedMemberId,
                memberWeatherDetailed = memberWeatherDetailed,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 3. SLIM SHOPPING LIST MARQUEE TICKER (Positioned cleanly below top action bar)
            val activeItemsText = remember(shoppingItems) {
                shoppingItems.filter { !it.isChecked }.joinToString("   •   ") { it.name }
            }
            if (activeItemsText.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.94f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .border(BorderStroke(1.dp, RadarCyan.copy(alpha = 0.5f)), RoundedCornerShape(17.dp))
                        .clickable { onOpenShoppingList() }
                        .zIndex(40f),
                    color = CosmicSlateCard,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🛒", fontSize = 13.sp)
                        Text(
                            text = "Need: $activeItemsText",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }
        }

        // 1c. FLOATING FOLLOW STATUS & ROUTE TRAIL HUD (Visible when a member is selected)
        if (selectedMember != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live Follow Pill
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            isFollowingSelectedMember = true
                            if (selectedMember.x != 0.0 && selectedMember.y != 0.0) {
                                mapViewRef?.let { map ->
                                    map.controller.animateTo(GeoPoint(selectedMember.y, selectedMember.x))
                                    map.controller.setZoom(16.0)
                                }
                            }
                        },
                    color = Color(0xF012121A),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, if (isFollowingSelectedMember) RadarCyan else Color(0xFFFFB300)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isFollowingSelectedMember) RadarCyan else Color(0xFFFFB300), CircleShape)
                        )
                        val transitMode = classifyTransitMode(selectedMember.speedMph, selectedMember.statusText)
                        val transitBadge = formatTransitBadge(selectedMember.speedMph, selectedMember.statusText, selectedMember.locationSince)
                        if (transitMode != TransitMode.STATIONARY) {
                            AnimatedTransitIcon(mode = transitMode, size = 12.dp)
                        }
                        val detailsText = if (transitBadge.isNotBlank()) {
                            if (transitMode != TransitMode.STATIONARY) " • ${transitBadge.drop(2).trim()}" else " • $transitBadge"
                        } else ""
                        Text(
                            text = if (isFollowingSelectedMember) "🎯 Following ${selectedMember.name}$detailsText" else "🎯 Re-center on ${selectedMember.name}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { onSelectMember(null) },
                            color = Color(0x33FFFFFF),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("✕", color = Color(0xFFECEFF1), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { isRouteTrailEnabled = !isRouteTrailEnabled },
                    color = if (isRouteTrailEnabled) PrimaryCosmic else Color(0xF01E1E28),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isRouteTrailEnabled) RadarCyan else SlateBorder),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🛤️", fontSize = 12.sp)
                        Text(
                            text = if (isRouteTrailEnabled) "Route Trail: ON" else "Route Trail: OFF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Time window filter chips: Today, 7 Days, 30 Days
                if (isRouteTrailEnabled) {
                    Surface(
                        color = Color(0xF0161622),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Pair("today", "Today"),
                                Pair("7days", "7 Days"),
                                Pair("30days", "30 Days")
                            ).forEach { (filterKey, label) ->
                                val isSelectedFilter = routeTimeFilter == filterKey
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectRouteTimeFilter(filterKey) },
                                    color = if (isSelectedFilter) RadarCyan.copy(alpha = 0.25f) else Color.Transparent,
                                    border = if (isSelectedFilter) BorderStroke(1.dp, RadarCyan) else null,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelectedFilter) RadarCyan else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelectedFilter) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }





        // 5. COHESIVE MAP CONTROLS FLOATING CONTAINER (BottomEnd - Recenter GPS & Safe Zones)
        Column(
            modifier = Modifier
                .padding(end = 16.dp, bottom = 24.dp)
                .align(Alignment.BottomEnd)
                .zIndex(75f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // 1. WHERE AM I NOW (FIND ME ON MAP) FAB
            Surface(
                modifier = Modifier
                    .size(46.dp)
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
                color = if (isCameraFollowingMe) Color(0xFF1B5E20) else CosmicSlateCard,
                shape = CircleShape,
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

            // 2. ADD SAFE ZONE FAB
            Surface(
                modifier = Modifier
                    .size(46.dp)
                    .clickable {
                        showAddZoneDialog = true
                    },
                color = CosmicSlateCard,
                shape = CircleShape,
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
        }

        // --- CONTEXT MENU DIALOG OVERLAY FOR MAP MARKER TAP ---
        memberForContextMenu?.let { member ->
            MapContextMenu(
                member = member,
                myDeviceUUID = myDeviceUUID,
                activeGroupCreatorId = activeGroupCreatorId,
                isRouteTrailEnabled = isRouteTrailEnabled,
                onToggleRouteTrail = { isRouteTrailEnabled = !isRouteTrailEnabled },
                isRinging = activeRingingMembers.contains(member.id) || activeRingingMembers.contains(member.name),
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

    // Add New Device Dialog
    if (showAddDeviceDialog) {
        val activeCircle = groupPinMappings.firstOrNull { it.pinCode == activeGroupPinCode }
        val activeCircleName = activeCircle?.groupName?.ifBlank { "Family Circle" } ?: "Family Circle"
        AddDeviceDialog(
            activeGroupPinCode = activeGroupPinCode,
            activeGroupName = activeCircleName,
            onDismiss = { showAddDeviceDialog = false }
        )
    }

    }
}

