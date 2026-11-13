package com.example.ui
import com.example.ui.map.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import com.example.data.AppConfig
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
import kotlinx.coroutines.isActive
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

/**
 * Map tile provider is controlled from the VPS remote config (map.provider): "esri_street" (default) or
 * "osm" for the classic OpenStreetMap (Mapnik) tiles. Change it on the server to switch everyone instantly.
 */

/** Esri World Street Map: clean, Google-Maps-like free basemap (no API key). Esri tile URLs use z/y/x order. */
private val cartoVoyagerTileSource = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
    "EsriWorldStreet", 0, 19, 256, ".png",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"),
    "Tiles © Esri"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex) + "/" +
            org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) + "/" +
            org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
}

private val kinBaseTileSource
    get() = if (com.example.data.RemoteConfig.mapProvider.equals("osm", ignoreCase = true)) TileSourceFactory.MAPNIK else cartoVoyagerTileSource



@Composable
fun RadarMap(
    members: List<FamilyMember>,
    selectedMemberId: String?,
    onSelectMember: (String?) -> Unit,
    homeLat: Double,
    homeLng: Double,
    locationTrails: Map<String, List<Pair<Double, Double>>> = emptyMap(),
    snappedTripRoutes: Map<String, List<Pair<Double, Double>>> = emptyMap(),
    onRequestMemberTrailSnap: (String) -> Unit = {},
    onRequestTripRouteSnap: (com.example.data.CloudCompletedTrip) -> Unit = {},
    onTriggerSOS: () -> Unit = {},
    onTriggerCheckIn: () -> Unit = {},
    onSendReaction: (String, String) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit = {},
    onOpenWhatsApp: (FamilyMember) -> Unit = {},
    onOpenGoogleMaps: (FamilyMember) -> Unit = {},
    onUpdateMember: (FamilyMember) -> Unit = {},
    onDeleteMember: (String) -> Unit = {},
    onTriggerAlarm: (String) -> Unit = {},
    onToggleMemberTracking: (String) -> Unit = {},
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
    onJoinGroupWithPin: (String) -> Unit = {},
    onJoinGroupWithPinWithResult: ((String, (Boolean, String) -> Unit) -> Unit)? = null,
    onCreateGroupWithPin: (String) -> Unit = {},
    isLocationPaused: Boolean = false,
    onToggleLocationPaused: (Boolean) -> Unit = {},
    isSettingsOpen: Boolean = false,
    onOpenNotificationHistory: () -> Unit = {},
    recentAlertCount: Int = 0,
    bottomPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Server-controlled settings (cached on the phone; defaults if the VPS has never been reached)
    LaunchedEffect(Unit) {
        com.example.data.RemoteConfig.loadCached(context)
        com.example.data.RemoteConfig.refresh(context)
    }
    // A Work zone saved on top of a school duplicates it (school + briefcase circle). Hide it so only the school shows.
    // Controlled remotely via zones.hideWorkNearSchool / zones.hideWorkZone in the VPS config.
    val isWorkCalibrated = isWorkCalibrated && workLat != 0.0 && workLng != 0.0 &&
        !com.example.data.RemoteConfig.hideWorkZone &&
        !(com.example.data.RemoteConfig.hideWorkNearSchool && (safeZones + listOf(
            com.example.data.GeoUtils.DEFAULT_WOODCOTE_HIGH_SCHOOL
        )).any { z ->
            z.iconName.equals("school", ignoreCase = true) &&
                com.example.data.GeoUtils.distanceMeters(workLat, workLng, z.latitude, z.longitude) <= maxOf(z.radiusMeters, 160.0) + 100.0
        })
    val prefs = remember { context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE) }
    var mapTypeMode by remember { mutableStateOf(prefs.getString("mapTypeMode", "streets").let { if (it == "satellite" || it.isNullOrBlank()) "streets" else it }) }
    LaunchedEffect(isSettingsOpen) {
        if (!isSettingsOpen) {
            val updated = prefs.getString("mapTypeMode", "streets").let { if (it == "satellite" || it.isNullOrBlank()) "streets" else it }
            if (updated != mapTypeMode) {
                mapTypeMode = updated
            }
        }
    }
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
        "#10B981", // Emerald green
        "#00FF87"  // Neon green
    )


    // Remember the MapView reference to trigger zoom & camera animations from Compose UI blocks
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isCameraFollowingMe by remember { mutableStateOf(false) }
    var isFollowingSelectedMember by remember { mutableStateOf(true) }
    var mapZoomLevel by remember { mutableStateOf(15.5) }
    var isRouteTrailEnabled by remember { mutableStateOf(false) }
    var isUserInteractingWithMap by remember { mutableStateOf(false) }

    // Life360 selected completed trip route playback
    val selectedTrip by com.example.data.TelemetryWebSocketManager.selectedTrip.collectAsState()
    LaunchedEffect(selectedTrip, snappedTripRoutes) {
        selectedTrip?.let { trip ->
            onRequestTripRouteSnap(trip)
            val snapped = snappedTripRoutes[trip.id]
            val points = if (!snapped.isNullOrEmpty()) {
                snapped.map { GeoPoint(it.first, it.second) }
            } else if (trip.breadcrumbs.isNotEmpty()) {
                trip.breadcrumbs.map { GeoPoint(it.lat, it.lng) }
            } else if (trip.startLat != 0.0 && trip.endLat != 0.0) {
                listOf(GeoPoint(trip.startLat, trip.startLng), GeoPoint(trip.endLat, trip.endLng))
            } else {
                emptyList()
            }
            if (points.isNotEmpty()) {
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLng = points.minOf { it.longitude }
                val maxLng = points.maxOf { it.longitude }
                val latSpan = (maxLat - minLat).coerceAtLeast(0.008)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.008)
                val box = org.osmdroid.util.BoundingBox(
                    maxLat + latSpan * 0.35,
                    maxLng + lngSpan * 0.35,
                    minLat - latSpan * 0.35,
                    minLng - lngSpan * 0.35
                )
                mapViewRef?.zoomToBoundingBox(box, true, 400)
            }
        }
    }

    var animTick by remember { mutableStateOf(0) }
    val hasMovingMembers = remember(members) {
        members.any { it.speedMph >= 0.6 || classifyTransitMode(it.speedMph, it.statusText, it.id) != TransitMode.STATIONARY }
    }
    LaunchedEffect(hasMovingMembers, isSettingsOpen) {
        if (hasMovingMembers && !isSettingsOpen) {
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
            val cacheDir = File(context.filesDir, "osmdroid")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            Configuration.getInstance().osmdroidTileCache = cacheDir
            Configuration.getInstance().osmdroidBasePath = cacheDir
            // Keep viewed map tiles on the phone (persistent storage, large cache, 60-day expiry) so they are not re-downloaded
            Configuration.getInstance().tileFileSystemCacheMaxBytes = 2000L * 1024 * 1024
            Configuration.getInstance().tileFileSystemCacheTrimBytes = 1700L * 1024 * 1024
            Configuration.getInstance().expirationOverrideDuration = 60L * 24 * 60 * 60 * 1000
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    val visualCoordinates = remember { java.util.concurrent.ConcurrentHashMap<String, GeoPoint>() }
    val memberVisualStates = remember { mutableMapOf<String, MemberVisualState>() }

    // Sticky barrier membership carried between layout passes (hysteresis so faces don't re-slot on GPS drift)
    val stickyBarrierMembership = remember { mutableMapOf<String, String>() }
    val activeLayoutMembers = remember(members, isLocationPaused) {
        members.filter {
            !(it.isLocationPaused || (it.id == "me" && isLocationPaused) || it.statusText.contains("Paused", ignoreCase = true))
        }
    }
    val density = context.resources.displayMetrics.density
    // Single shared layout used by BOTH the motion engine and the marker renderer so they never disagree
    val sharedClusterLayout = remember(
        activeLayoutMembers, safeZones, homeLat, homeLng, homeRadiusMeters,
        isWorkCalibrated, workLat, workLng, workRadiusMeters,
        mapZoomLevel, density
    ) {
        computeClusterLayout(
            members = activeLayoutMembers,
            safeZones = safeZones,
            homeLat = homeLat,
            homeLng = homeLng,
            homeRadiusMeters = homeRadiusMeters,
            isWorkCalibrated = isWorkCalibrated,
            workLat = workLat,
            workLng = workLng,
            workRadiusMeters = workRadiusMeters,
            previousBarrierMembership = stickyBarrierMembership.toMap(),
            zoomLevel = mapZoomLevel,
            density = density
        ).also {
            stickyBarrierMembership.clear()
            stickyBarrierMembership.putAll(it.barrierMembership)
        }
    }

    // Synchronize incoming member target updates with barrier-aware static locking
    LaunchedEffect(sharedClusterLayout, activeLayoutMembers) {
        val now = android.os.SystemClock.uptimeMillis()
        val activeMembers = activeLayoutMembers
        val clusterLayout = sharedClusterLayout
        val adjustedCoordinates = clusterLayout.adjustedCoordinates

        activeMembers.forEach { m ->
            if (m.x != 0.0 && m.y != 0.0) {
                val state = memberVisualStates[m.id]
                val transit = classifyTransitMode(m.speedMph, m.statusText, m.id)

                val isActivelyTraveling = transit != TransitMode.STATIONARY ||
                    m.speedMph >= 0.6 ||
                    m.isComingHome ||
                    m.statusText.contains("walk", ignoreCase = true) ||
                    m.statusText.contains("moving", ignoreCase = true) ||
                    m.statusText.contains("driving", ignoreCase = true) ||
                    m.statusText.contains("bike", ignoreCase = true) ||
                    m.statusText.contains("transit", ignoreCase = true) ||
                    m.statusText.contains("commute", ignoreCase = true)

                // Locked = parked inside Home / Safe Zone / Work per the shared sticky layout
                val isLocked = clusterLayout.barrierMembership.containsKey(m.id)

                val isReportedMoving = if (isLocked) {
                    false
                } else if (isActivelyTraveling) {
                    true
                } else {
                    m.speedMph >= 1.5
                }

                val assignedClusterPos = adjustedCoordinates[m.id] ?: GeoPoint(m.y, m.x)

                if (state == null) {
                    val initialGeo = assignedClusterPos
                    memberVisualStates[m.id] = MemberVisualState(
                        currentGeo = initialGeo,
                        targetGeo = initialGeo,
                        speedMph = if (isReportedMoving) m.speedMph else 0.0,
                        bearingDeg = 0.0,
                        lastTargetUpdateTime = now,
                        isMoving = isReportedMoving
                    )
                    visualCoordinates[m.id] = initialGeo
                } else {
                    if (isLocked) {
                        // Locked static inside barrier or cluster — snap immediately to assigned deconflicted slot
                        state.isMoving = false
                        state.speedMph = 0.0
                        state.targetGeo = assignedClusterPos
                        state.currentGeo = assignedClusterPos
                        state.markerRef?.position = assignedClusterPos
                        visualCoordinates[m.id] = assignedClusterPos
                    } else {
                        // Truly moving outside barrier or actively walking/traveling
                        val distMovedM = com.example.data.GeoUtils.distanceMeters(
                            state.currentGeo.latitude, state.currentGeo.longitude,
                            m.y, m.x
                        )
                        // Allow small walking steps (0.3m) when walking/traveling, suppress jitter (3.0m) when stationary
                        val minMovementMeters = if (isReportedMoving) 0.3 else 3.0
                        if (distMovedM >= minMovementMeters) {
                            val dLat = m.y - state.currentGeo.latitude
                            val dLng = m.x - state.currentGeo.longitude
                            val cosLat = kotlin.math.cos(Math.toRadians(m.y))
                            val rawBearing = Math.toDegrees(kotlin.math.atan2(dLng * cosLat, dLat))
                            state.bearingDeg = (rawBearing + 360.0) % 360.0
                            state.targetGeo = assignedClusterPos
                            val elapsedSec = ((now - state.lastTargetUpdateTime) / 1000.0).coerceAtLeast(0.5)
                            state.lastTargetUpdateTime = now
                            state.speedMph = if (m.speedMph >= 0.5) m.speedMph else (distMovedM / elapsedSec) * 2.23694
                            state.isMoving = true
                        } else {
                            state.isMoving = isReportedMoving
                            if (!isReportedMoving) {
                                state.speedMph = 0.0
                                state.targetGeo = assignedClusterPos
                                state.currentGeo = assignedClusterPos
                                state.markerRef?.position = assignedClusterPos
                                visualCoordinates[m.id] = assignedClusterPos
                            }
                        }
                    }
                }
            }
        }
    }

    val currentSelectedMemberId by rememberUpdatedState(selectedMemberId)
    val currentIsFollowingSelectedMember by rememberUpdatedState(isFollowingSelectedMember)
    val currentIsRouteTrailEnabled by rememberUpdatedState(isRouteTrailEnabled)
    val currentIsUserInteractingWithMap by rememberUpdatedState(isUserInteractingWithMap)

    // High-frequency (60 FPS) continuous road interpolation & camera follow engine
    LaunchedEffect(isSettingsOpen) {
        if (isSettingsOpen) return@LaunchedEffect
        var lastFrameTime = android.os.SystemClock.uptimeMillis()
        while (isActive) {
            kotlinx.coroutines.delay(16) // ~60 FPS smooth motion
            val now = android.os.SystemClock.uptimeMillis()
            val dt = ((now - lastFrameTime) / 1000.0).coerceIn(0.005, 0.08)
            lastFrameTime = now

            var anyMoved = false
            memberVisualStates.forEach { (mId, state) ->
                val marker = state.markerRef ?: return@forEach
                val curLat = state.currentGeo.latitude
                val curLng = state.currentGeo.longitude
                val tgtLat = state.targetGeo.latitude
                val tgtLng = state.targetGeo.longitude
                val distToTargetM = com.example.data.GeoUtils.distanceMeters(curLat, curLng, tgtLat, tgtLng)

                if (state.isMoving) {
                    val speedMps = (state.speedMph * 0.44704).coerceAtLeast(0.6)
                    // Catchup speed smoothly scales across the update window for continuous fluid gliding
                    val catchupSpeedMps = maxOf(speedMps * 0.95, distToTargetM / 3.5)
                    val stepM = catchupSpeedMps * dt

                    if (distToTargetM > stepM && distToTargetM > 0.2) {
                        // Smoothly advance toward target
                        val frac = (stepM / distToTargetM).coerceIn(0.0, 1.0)
                        val nextLat = curLat + (tgtLat - curLat) * frac
                        val nextLng = curLng + (tgtLng - curLng) * frac
                        state.currentGeo = GeoPoint(nextLat, nextLng)
                        marker.position = state.currentGeo
                        visualCoordinates[mId] = state.currentGeo
                        anyMoved = true
                    } else {
                        // Reached target
                        state.currentGeo = state.targetGeo
                        marker.position = state.currentGeo
                        visualCoordinates[mId] = state.currentGeo
                        anyMoved = true
                    }
                } else {
                    // Stationary: ease gently to settled target
                    if (distToTargetM > 0.1) {
                        val factor = (6.0 * dt).coerceIn(0.0, 1.0)
                        val nextLat = if (distToTargetM < 0.25) tgtLat else curLat + (tgtLat - curLat) * factor
                        val nextLng = if (distToTargetM < 0.25) tgtLng else curLng + (tgtLng - curLng) * factor
                        state.currentGeo = GeoPoint(nextLat, nextLng)
                        marker.position = state.currentGeo
                        visualCoordinates[mId] = state.currentGeo
                        anyMoved = true
                    }
                }

                // Smooth camera follow for selected member when follow mode is active
                val selId = currentSelectedMemberId
                val isThisMemberSelected = selId != null && (
                    mId == selId || 
                    mId.contains(selId, ignoreCase = true) || 
                    selId.contains(mId, ignoreCase = true)
                )
                if (isThisMemberSelected && currentIsFollowingSelectedMember && !currentIsRouteTrailEnabled) {
                    if (!currentIsUserInteractingWithMap) {
                        mapViewRef?.let { map ->
                            map.controller.setCenter(state.currentGeo)
                            anyMoved = true
                        }
                    }
                }
            }

            if (anyMoved) {
                mapViewRef?.postInvalidate()
            }
        }
    }

    val selectedMember = members.firstOrNull { 
        it.id == selectedMemberId || 
        (selectedMemberId != null && (
            it.id.contains(selectedMemberId, ignoreCase = true) || 
            selectedMemberId.contains(it.id, ignoreCase = true) || 
            it.name.equals(selectedMemberId, ignoreCase = true)
        ))
    }

    // Centering & Camera Animation on Member Selection change or Deselection (fit all)
    LaunchedEffect(selectedMemberId) {
        if (selectedMemberId != null) {
            isFollowingSelectedMember = true
            isRouteTrailEnabled = false
            val m = members.firstOrNull { 
                it.id == selectedMemberId || 
                it.id.contains(selectedMemberId, ignoreCase = true) || 
                selectedMemberId.contains(it.id, ignoreCase = true) || 
                it.name.equals(selectedMemberId, ignoreCase = true) 
            }
            if (m != null && m.x != 0.0 && m.y != 0.0) {
                mapViewRef?.let { map ->
                    val targetGeo = visualCoordinates[m.id] ?: GeoPoint(m.y, m.x)
                    map.controller.animateTo(targetGeo)
                    if (map.zoomLevelDouble < 12.0) {
                        map.controller.setZoom(16.0)
                    }
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

    // Request road-snapped trails for away/selected members and fit camera once per toggle/selection
    var lastAutoFittedTrailKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isRouteTrailEnabled, selectedMemberId, members) {
        if (!isRouteTrailEnabled) {
            lastAutoFittedTrailKey = null
            return@LaunchedEffect
        }
        if (selectedMember != null) {
            onRequestMemberTrailSnap(selectedMember.id)
        } else {
            members.filter {
                homeLat != 0.0 && homeLng != 0.0 &&
                    com.example.data.GeoUtils.distanceMeters(homeLat, homeLng, it.y, it.x) > 60.0
            }.forEach { onRequestMemberTrailSnap(it.id) }
        }
    }
    LaunchedEffect(isRouteTrailEnabled, selectedMemberId, locationTrails) {
        if (!isRouteTrailEnabled) return@LaunchedEffect
        val fitKey = "${isRouteTrailEnabled}_${selectedMemberId ?: "auto"}"
        if (lastAutoFittedTrailKey == fitKey) return@LaunchedEffect
        val targetMember = selectedMember ?: members.firstOrNull {
            it.id != "me" && homeLat != 0.0 && homeLng != 0.0 &&
                com.example.data.GeoUtils.distanceMeters(homeLat, homeLng, it.y, it.x) > 60.0
        } ?: members.firstOrNull {
            homeLat != 0.0 && homeLng != 0.0 &&
                com.example.data.GeoUtils.distanceMeters(homeLat, homeLng, it.y, it.x) > 60.0
        }
        if (targetMember != null) {
            val points = com.example.ui.map.TrailRouteEngine.findTrailForMember(locationTrails, targetMember)
                ?: emptyList()
            if (points.size >= 2) {
                lastAutoFittedTrailKey = fitKey
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

    // Keep camera panning to stay centered on the traveler when follow mode is active!
    val me = members.firstOrNull { it.id == "me" }
    LaunchedEffect(me?.y, me?.x, isCameraFollowingMe) {
        if (isCameraFollowingMe && me != null && me.y != 0.0 && me.x != 0.0) {
            if (!isUserInteractingWithMap) {
                mapViewRef?.let { map ->
                    map.controller.animateTo(GeoPoint(me.y, me.x))
                }
            }
        }
    }

    // Keep camera panning to stay centered on the selected member when follow mode is active!
    LaunchedEffect(selectedMember?.y, selectedMember?.x, selectedMemberId, isFollowingSelectedMember) {
        if (isFollowingSelectedMember && selectedMember != null && selectedMember.x != 0.0 && selectedMember.y != 0.0 && !isRouteTrailEnabled) {
            if (!isUserInteractingWithMap) {
                mapViewRef?.let { map ->
                    val targetGeo = visualCoordinates[selectedMember.id] ?: GeoPoint(selectedMember.y, selectedMember.x)
                    map.controller.setCenter(targetGeo)
                    mapViewRef?.postInvalidate()
                }
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
                    setTileSource(kinBaseTileSource)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                    val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
                    var touchDownX = 0f
                    var touchDownY = 0f
                    var isMultiTouchGesture = false
                    var hasZoomedInCurrentGesture = false
                    var wasFollowingSelectedAtDown = false
                    var wasFollowingMeAtDown = false

                    // Allow panning and swiping on the Map without losing follow on pinch/zoom
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                touchDownX = event.x
                                touchDownY = event.y
                                isMultiTouchGesture = false
                                hasZoomedInCurrentGesture = false
                                wasFollowingSelectedAtDown = isFollowingSelectedMember
                                wasFollowingMeAtDown = isCameraFollowingMe
                                isUserInteractingWithMap = true
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                                // Multi-finger touch: pinch-to-zoom in progress!
                                isMultiTouchGesture = true
                                // Preserve follow state across pinch zooms
                                if (wasFollowingSelectedAtDown) isFollowingSelectedMember = true
                                if (wasFollowingMeAtDown) isCameraFollowingMe = true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount > 1) {
                                    isMultiTouchGesture = true
                                }
                                if (!isMultiTouchGesture && !hasZoomedInCurrentGesture) {
                                    val dx = Math.abs(event.x - touchDownX)
                                    val dy = Math.abs(event.y - touchDownY)
                                    if (dx > touchSlop * 3.5f || dy > touchSlop * 3.5f) {
                                        // User is deliberately single-finger panning the map away: release camera lock!
                                        isFollowingSelectedMember = false
                                        isCameraFollowingMe = false
                                    }
                                }
                            }
                            android.view.MotionEvent.ACTION_POINTER_UP -> {
                                isMultiTouchGesture = true
                            }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                isUserInteractingWithMap = false

                                if (isMultiTouchGesture || hasZoomedInCurrentGesture) {
                                    // Pinch zoom or gesture zoom completed! Retain follow at the newly chosen zoom level!
                                    if (wasFollowingSelectedAtDown) {
                                        isFollowingSelectedMember = true
                                    }
                                    if (wasFollowingMeAtDown) {
                                        isCameraFollowingMe = true
                                    }

                                    // Center camera directly on target at this newly zoomed level
                                    val targetGeo = if (wasFollowingSelectedAtDown && selectedMember != null) {
                                        visualCoordinates[selectedMember.id] ?: GeoPoint(selectedMember.y, selectedMember.x)
                                    } else if (wasFollowingMeAtDown && me != null) {
                                        GeoPoint(me.y, me.x)
                                    } else null

                                    if (targetGeo != null) {
                                        controller.setCenter(targetGeo)
                                        postInvalidate()
                                    }
                                }

                                isMultiTouchGesture = false
                                hasZoomedInCurrentGesture = false
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
                            hasZoomedInCurrentGesture = true
                            if (!isUserInteractingWithMap) {
                                if (isFollowingSelectedMember && selectedMember != null) {
                                    val targetGeo = visualCoordinates[selectedMember.id] ?: GeoPoint(selectedMember.y, selectedMember.x)
                                    post { controller.setCenter(targetGeo) }
                                } else if (isCameraFollowingMe && me != null) {
                                    post { controller.setCenter(GeoPoint(me.y, me.x)) }
                                }
                            }
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
                com.example.data.GeoUtils.withDefaultSafeZones(safeZones).filter { it.iconName.lowercase() != "home" && !it.name.lowercase().contains("home") }.forEach { zone ->
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
                        fillPaint.color = android.graphics.Color.parseColor("#0284C7") // Sky/Cyan
                        fillPaint.alpha = 25
                        outlinePaint.color = android.graphics.Color.parseColor("#0EA5E9")
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



                // --- LIFE360-STYLE CO-LOCATED CLUSTER & DECONFLICTION ENGINE ---
                val layoutResult = sharedClusterLayout
                val adjustedCoordinates = layoutResult.adjustedCoordinates
                val clusterAnchorPoints = layoutResult.clusterAnchorPoints
                val clusters = layoutResult.clusters
                // --- END OF LIFE360-STYLE CO-LOCATED CLUSTER & DECONFLICTION ENGINE ---

                // Draw sleek spoke lines from location dot to each compact clustered face
                val zoneIconsDrawn = com.example.data.GeoUtils.withDefaultSafeZones(safeZones)
                    .filter { it.iconName.lowercase() != "home" && !it.name.lowercase().contains("home") }
                    .map { GeoPoint(it.latitude, it.longitude) } +
                    if (isWorkCalibrated && workLat != 0.0 && workLng != 0.0) listOf(GeoPoint(workLat, workLng)) else emptyList()
                val drawnAnchorHubs = mutableListOf<GeoPoint>()
                for (cluster in clusters) {
                    if (cluster.size > 1) {
                        for (mId in cluster) {
                            val anchor = clusterAnchorPoints[mId]
                            val target = adjustedCoordinates[mId]
                            val member = members.firstOrNull { it.id == mId }
                            if (anchor != null && target != null && member != null) {
                                val vibrantColor = getVibrantSpokeColor(member.avatarColorHex)

                                val haloLine = Polyline(mapView).apply {
                                    setPoints(listOf(anchor, target))
                                    outlinePaint.color = android.graphics.Color.WHITE
                                    outlinePaint.strokeWidth = 2.6f * density
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.alpha = 200
                                }
                                mapView.overlays.add(haloLine)

                                val coreLine = Polyline(mapView).apply {
                                    setPoints(listOf(anchor, target))
                                    outlinePaint.color = vibrantColor
                                    outlinePaint.strokeWidth = 1.6f * density
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.alpha = 230
                                }
                                mapView.overlays.add(coreLine)

                                val hasExistingHub = drawnAnchorHubs.any {
                                    com.example.data.GeoUtils.distanceMeters(it.latitude, it.longitude, anchor.latitude, anchor.longitude) < 5.0
                                } || zoneIconsDrawn.any {
                                    com.example.data.GeoUtils.distanceMeters(it.latitude, it.longitude, anchor.latitude, anchor.longitude) < 5.0
                                }
                                if (!hasExistingHub) {
                                    drawnAnchorHubs.add(anchor)
                                    val anchorDot = object : Marker(mapView) {
                                        override fun showInfoWindow() {}
                                    }.apply {
                                        position = anchor
                                        icon = MapMarkerRenderer.getOrCreateClusterAnchorDot(context)
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        setOnMarkerClickListener { _, _ -> true }
                                    }
                                    mapView.overlays.add(anchorDot)
                                }
                            }
                        }
                    }
                }

                // Filter out paused devices — remove from screen temporarily until un-pause
                val activeMembersOnMap = members.filter {
                    !(it.isLocationPaused || (it.id == "me" && isLocationPaused) || it.statusText.contains("Paused", ignoreCase = true))
                }
                // Draw "me" first so other family members are drawn on top of "me" (z-order dominance)
                val sortedMembers = activeMembersOnMap.sortedWith(Comparator { m1, m2 ->
                    when {
                        m1.id == "me" && m2.id != "me" -> -1
                        m1.id != "me" && m2.id == "me" -> 1
                        else -> 0
                    }
                })

                // 3. Draw active family members and connect transit paths
                sortedMembers.forEach { member ->

                    val visualGeo = memberVisualStates[member.id]?.currentGeo
                    val isDeconflicted = layoutResult.barrierMembership.containsKey(member.id) ||
                        layoutResult.clusters.any { it.contains(member.id) }
                    // For deconflicted members, immediately use zoom-adapted coordinate to eliminate zoom lag
                    val displayGeo = if (isDeconflicted) {
                        adjustedCoordinates[member.id] ?: visualGeo ?: GeoPoint(member.y, member.x)
                    } else {
                        visualGeo ?: adjustedCoordinates[member.id] ?: GeoPoint(member.y, member.x)
                    }
                    if (isDeconflicted) {
                        visualCoordinates[member.id] = displayGeo
                        memberVisualStates[member.id]?.let { vs ->
                            vs.currentGeo = displayGeo
                            vs.targetGeo = displayGeo
                            vs.markerRef?.position = displayGeo
                        }
                    }
                    val trueGeo = visualGeo ?: GeoPoint(member.y, member.x)
                    val isAway = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(member.y - homeLat, member.x - homeLng) * 111.0 > 0.06)
                    val isSelected = member.id == selectedMemberId ||
                            (selectedMemberId != null && (member.id.contains(selectedMemberId) || selectedMemberId.contains(member.id) || member.name.equals(selectedMember?.name, ignoreCase = true)))

                    val memberColor = try {
                        android.graphics.Color.parseColor(member.avatarColorHex)
                    } catch (e: Exception) { android.graphics.Color.BLUE }

                    // Draw a subtle ring around the true location (Only for 'me' to avoid map clutter)
                    if (member.id == "me") {
                        val ring = Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(trueGeo, 60.0) // 60 meters radius
                            fillPaint.color = android.graphics.Color.TRANSPARENT
                            outlinePaint.color = memberColor
                            outlinePaint.strokeWidth = 1.4f * density
                            outlinePaint.alpha = 55 // Light translucent outline ring
                        }
                        mapView.overlays.add(ring)
                    }

                    // Draw visual breadcrumb trails showing previous location history ONLY when enabled & member is selected or away!
                    if (isRouteTrailEnabled && (isSelected || (selectedMemberId == null && isAway))) {
                        val recordedPoints = com.example.ui.map.TrailRouteEngine.findTrailForMember(locationTrails, member)
                            ?: emptyList()
                        val trailPoints = if (recordedPoints.size >= 2) {
                            recordedPoints
                        } else {
                            emptyList()
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
                    val rawTimeLabel = if (member.id == "me") "now"
                                       else if (member.lastActive > 0L) formatTimeAgo(member.lastActive)
                                       else "now"

                    // Location duration / transit pill; hide stationary clutter unless selected/SOS
                    val rawDurationLabel = formatTransitBadge(member.speedMph, member.statusText, member.locationSince, member.id)
                    val isMovingTransit = rawDurationLabel.startsWith("👣") ||
                        rawDurationLabel.startsWith("🚗") ||
                        rawDurationLabel.startsWith("🚲") ||
                        rawDurationLabel.startsWith("🚆")

                    val timeLabel = if (isSelected || isSos || isOffline || rawTimeLabel != "now") rawTimeLabel else ""
                    val locationDurationLabel = if (isSelected || isSos || isMovingTransit) rawDurationLabel else ""
                    val showBatteryBadge = member.id != "me" && (isSelected || isSos || member.batteryPercentage <= 25 || member.isCharging)
                    val isInCluster = layoutResult.clusters.any { it.contains(member.id) }
                    val batteryForBadge = if (showBatteryBadge) member.batteryPercentage else -1

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
                            batteryPercentage = batteryForBadge,
                            isCharging = member.isCharging,
                            animFrame = animTick,
                            showStem = !isInCluster
                        )
                        setAnchor(
                            0.5f,
                            MapMarkerRenderer.getMarkerAnchorV(
                                context = context,
                                isSelected = isSelected || isSos,
                                relativeTime = timeLabel,
                                locationDuration = locationDurationLabel,
                                batteryPercentage = batteryForBadge,
                                showStem = !isInCluster
                            )
                        )
                        setOnMarkerClickListener { _, _ ->
                            onSelectMember(member.id)
                            isFollowingSelectedMember = true
                            mapViewRef?.let { map ->
                                val targetGeo = visualCoordinates[member.id] ?: GeoPoint(member.y, member.x)
                                map.controller.animateTo(targetGeo)
                                if (map.zoomLevelDouble < 12.0) {
                                    map.controller.setZoom(16.0)
                                }
                            }
                            memberForContextMenu = member
                            true
                        }
                    }
                    mapView.overlays.add(memberMarker)
                    val visualState = memberVisualStates.getOrPut(member.id) {
                        MemberVisualState(
                            currentGeo = displayGeo,
                            targetGeo = displayGeo,
                            speedMph = member.speedMph,
                            bearingDeg = 0.0,
                            lastTargetUpdateTime = System.currentTimeMillis(),
                            isMoving = false
                        )
                    }
                    visualState.markerRef = memberMarker
                }

                // 4. Draw Selected Trip Route History (Life360 Route Playback)
                selectedTrip?.let { trip ->
                    val snapped = snappedTripRoutes[trip.id]
                    val rawPoints = if (trip.breadcrumbs.isNotEmpty()) {
                        trip.breadcrumbs.map { GeoPoint(it.lat, it.lng) }
                    } else if (trip.startLat != 0.0 && trip.endLat != 0.0) {
                        listOf(GeoPoint(trip.startLat, trip.startLng), GeoPoint(trip.endLat, trip.endLng))
                    } else {
                        emptyList()
                    }
                    val tripPoints = if (!snapped.isNullOrEmpty() && snapped.size >= 2) {
                        snapped.map { GeoPoint(it.first, it.second) }
                    } else if (trip.transitMode.equals("TRAIN", ignoreCase = true)) {
                        rawPoints
                    } else if (rawPoints.size >= 3 && !com.example.ui.map.TrailRouteEngine.hasSparseGaps(rawPoints.map { Pair(it.latitude, it.longitude) })) {
                        rawPoints
                    } else {
                        emptyList()
                    }

                    if (tripPoints.size >= 2) {
                        // High-contrast outer glow
                        val tripGlow = Polyline(mapView).apply {
                            setPoints(tripPoints)
                            outlinePaint.color = android.graphics.Color.WHITE
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.strokeWidth = 11.0f * density
                            outlinePaint.alpha = 210
                        }
                        mapView.overlays.add(tripGlow)

                        val isTrain = trip.transitMode.equals("TRAIN", ignoreCase = true)
                        val tripColor = if (isTrain) android.graphics.Color.parseColor("#0D9488") else android.graphics.Color.parseColor("#00E5FF")

                        val tripRoute = Polyline(mapView).apply {
                            setPoints(tripPoints)
                            outlinePaint.color = tripColor
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.strokeWidth = 6.5f * density
                            outlinePaint.alpha = 255
                        }
                        mapView.overlays.add(tripRoute)

                        // Start Pin
                        val startMarker = Marker(mapView).apply {
                            position = tripPoints.first()
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = MapMarkerRenderer.getOrCreateMarkerDrawable(
                                context = context,
                                colorHex = "#00E676",
                                emoji = "🟢",
                                isSelected = false,
                                showStem = false
                            )
                            setOnMarkerClickListener { _, _ -> true }
                        }
                        mapView.overlays.add(startMarker)

                        // End Pin
                        val endMarker = Marker(mapView).apply {
                            position = tripPoints.last()
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = MapMarkerRenderer.getOrCreateMarkerDrawable(
                                context = context,
                                colorHex = "#FF1744",
                                emoji = "🏁",
                                isSelected = false,
                                showStem = false
                            )
                            setOnMarkerClickListener { _, _ -> true }
                        }
                        mapView.overlays.add(endMarker)
                    }
                }

                // Apply OpenStreetMap customizable styling options using tile sources and matrices
                when (mapTypeMode) {
                    "streets" -> {
                        if (mapView.tileProvider.tileSource.name() != kinBaseTileSource.name()) {
                            mapView.setTileSource(kinBaseTileSource)
                        }
                        // Standard crisp full-color streets
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }
                    "hybrid" -> {
                        if (mapView.tileProvider.tileSource.name() != kinBaseTileSource.name()) {
                            mapView.setTileSource(kinBaseTileSource)
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
                        if (mapView.tileProvider.tileSource.name() != kinBaseTileSource.name()) {
                            mapView.setTileSource(kinBaseTileSource)
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
                        if (mapView.tileProvider.tileSource.name() != kinBaseTileSource.name()) {
                            mapView.setTileSource(kinBaseTileSource)
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
        var addDeviceInitialTab by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, start = 10.dp, end = 10.dp)
                .align(Alignment.TopCenter)
                .zIndex(95f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapTopHudBar(
                recentAlertCount = recentAlertCount,
                onOpenNotificationHistory = onOpenNotificationHistory,
                activeGroupPinCode = activeGroupPinCode,
                groupPinMappings = groupPinMappings,
                memberCount = members.size,
                isCircleSwitcherOpen = showCircleSwitcher,
                onToggleCircleSwitcher = { showCircleSwitcher = !showCircleSwitcher },
                onSettingsClick = {
                    showCircleSwitcher = false
                    onSettingsClick()
                }
            )
            TripPlaybackCard(selectedTrip = selectedTrip)
            MemberFollowTrailHud(
                selectedMember = selectedMember,
                isLocationPaused = isLocationPaused,
                isFollowingSelectedMember = isFollowingSelectedMember,
                onFollowMember = {
                    isFollowingSelectedMember = true
                    if (selectedMember != null && selectedMember.x != 0.0 && selectedMember.y != 0.0) {
                        mapViewRef?.let { map ->
                            val targetGeo = visualCoordinates[selectedMember.id] ?: org.osmdroid.util.GeoPoint(selectedMember.y, selectedMember.x)
                            map.controller.animateTo(targetGeo)
                            if (map.zoomLevelDouble < 12.0) {
                                map.controller.setZoom(16.0)
                            }
                        }
                    }
                },
                onDismissMemberSelection = { onSelectMember(null) },
                isRouteTrailEnabled = isRouteTrailEnabled,
                onToggleRouteTrail = { isRouteTrailEnabled = !isRouteTrailEnabled },
                routeTimeFilter = routeTimeFilter,
                onSelectRouteTimeFilter = onSelectRouteTimeFilter
            )
            // 3. LIVE WEATHER STATUS PILL (Floats cleanly below action bar and follow banner)
            WeatherHudOverlay(
                members = members,
                selectedMemberId = selectedMemberId,
                memberWeatherDetailed = memberWeatherDetailed
            )

            // 4. SLIM SHOPPING LIST MARQUEE TICKER (Positioned cleanly at bottom of top HUD stack)
            val activeItemsText = remember(shoppingItems) {
                shoppingItems.filter { !it.isChecked }.joinToString("   •   ") { it.name }
            }
            if (activeItemsText.isNotBlank()) {
                Surface(
                    modifier = Modifier
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

        MapControlsOverlay(
            isCameraFollowingMe = isCameraFollowingMe,
            onZoomIn = {
                mapViewRef?.let { map ->
                    map.controller.zoomIn()
                    mapZoomLevel = map.zoomLevelDouble + 1.0
                    val target = if (isFollowingSelectedMember && selectedMember != null) {
                        visualCoordinates[selectedMember.id] ?: org.osmdroid.util.GeoPoint(selectedMember.y, selectedMember.x)
                    } else if (isCameraFollowingMe && me != null) {
                        org.osmdroid.util.GeoPoint(me.y, me.x)
                    } else null
                    if (target != null) {
                        map.controller.setCenter(target)
                    }
                }
            },
            onZoomOut = {
                mapViewRef?.let { map ->
                    map.controller.zoomOut()
                    mapZoomLevel = (map.zoomLevelDouble - 1.0).coerceAtLeast(2.0)
                    val target = if (isFollowingSelectedMember && selectedMember != null) {
                        visualCoordinates[selectedMember.id] ?: org.osmdroid.util.GeoPoint(selectedMember.y, selectedMember.x)
                    } else if (isCameraFollowingMe && me != null) {
                        org.osmdroid.util.GeoPoint(me.y, me.x)
                    } else null
                    if (target != null) {
                        map.controller.setCenter(target)
                    }
                }
            },
            onWhereAmINow = {
                val meLoc = members.firstOrNull { it.id == "me" }
                if (meLoc != null && meLoc.y != 0.0 && meLoc.x != 0.0) {
                    onSelectMember("me")
                    isCameraFollowingMe = !isCameraFollowingMe
                    if (isCameraFollowingMe) {
                        isFollowingSelectedMember = true
                    }
                    mapViewRef?.let {
                        it.controller.animateTo(org.osmdroid.util.GeoPoint(meLoc.y, meLoc.x))
                        if (it.zoomLevelDouble < 12.0) {
                            it.controller.setZoom(15.5)
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd)
        )

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
                onOpenGoogleMaps = onOpenGoogleMaps,
                onTriggerSOS = onTriggerSOS,
                onSendReaction = onSendReaction,
                onTriggerAlarm = onTriggerAlarm,
                onToggleTracking = onToggleMemberTracking,
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

    DeleteSafeZoneDialog(
        zone = zoneToDelete,
        onDismiss = { zoneToDelete = null },
        onConfirmDelete = { onDeleteSafeZone(it) }
    )

    AddSafeZoneDialog(
        visible = showAddZoneDialog,
        onDismiss = { showAddZoneDialog = false },
        onAddSafeZone = onAddSafeZone,
        members = members,
        homeLat = homeLat,
        homeLng = homeLng
    )
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
            members = members,
            onJoinGroupWithPin = onJoinGroupWithPin,
            onJoinGroupWithPinWithResult = onJoinGroupWithPinWithResult,
            onCreateGroupWithPin = onCreateGroupWithPin,
            onDeleteMemberFromCircle = { member -> onDeleteMember(member.id) },
            initialTab = addDeviceInitialTab,
            onDismiss = { showAddDeviceDialog = false }
        )
    }
    CircleSwitcherOverlay(
        visible = showCircleSwitcher,
        onDismiss = { showCircleSwitcher = false },
        groupPinMappings = groupPinMappings,
        activeGroupPinCode = activeGroupPinCode,
        memberCount = members.size,
        onSwitchCircle = onSwitchCircle,
        onOpenAddDeviceTab = { tab ->
            addDeviceInitialTab = tab
            showAddDeviceDialog = true
        }
    )



    }
}
