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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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


    // Remember the MapView & MapLibreMap references to trigger zoom & camera animations from Compose UI blocks
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapLibreRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeStyleMode by remember { mutableStateOf<String?>(null) }
    var motionFrameTick by remember { mutableStateOf(0) }
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
                snapped.map { LatLng(it.first, it.second) }
            } else if (trip.breadcrumbs.isNotEmpty()) {
                trip.breadcrumbs.map { LatLng(it.lat, it.lng) }
            } else if (trip.startLat != 0.0 && trip.endLat != 0.0) {
                listOf(LatLng(trip.startLat, trip.startLng), LatLng(trip.endLat, trip.endLng))
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
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(maxLat + latSpan * 0.35, maxLng + lngSpan * 0.35))
                    .include(LatLng(minLat - latSpan * 0.35, minLng - lngSpan * 0.35))
                    .build()
                mapLibreRef?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 400)
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

    val visualCoordinates = remember { java.util.concurrent.ConcurrentHashMap<String, LatLng>() }
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

                val assignedClusterPos = adjustedCoordinates[m.id] ?: LatLng(m.y, m.x)

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

    // High-frequency (30 FPS) continuous road interpolation & camera follow engine
    LaunchedEffect(isSettingsOpen) {
        if (isSettingsOpen) return@LaunchedEffect
        var lastFrameTime = android.os.SystemClock.uptimeMillis()
        while (isActive) {
            kotlinx.coroutines.delay(33) // ~30 FPS smooth motion for GeoJSON symbol updates
            val now = android.os.SystemClock.uptimeMillis()
            val dt = ((now - lastFrameTime) / 1000.0).coerceIn(0.005, 0.08)
            lastFrameTime = now

            var anyMoved = false
            memberVisualStates.forEach { (mId, state) ->
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
                        state.currentGeo = LatLng(nextLat, nextLng)
                        visualCoordinates[mId] = state.currentGeo
                        anyMoved = true
                    } else {
                        // Reached target
                        state.currentGeo = state.targetGeo
                        visualCoordinates[mId] = state.currentGeo
                        anyMoved = true
                    }
                } else {
                    // Stationary: ease gently to settled target
                    if (distToTargetM > 0.1) {
                        val factor = (6.0 * dt).coerceIn(0.0, 1.0)
                        val nextLat = if (distToTargetM < 0.25) tgtLat else curLat + (tgtLat - curLat) * factor
                        val nextLng = if (distToTargetM < 0.25) tgtLng else curLng + (tgtLng - curLng) * factor
                        state.currentGeo = LatLng(nextLat, nextLng)
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
                        mapLibreRef?.let { map ->
                            map.moveCamera(CameraUpdateFactory.newLatLng(state.currentGeo))
                            anyMoved = true
                        }
                    }
                }
            }

            if (anyMoved) {
                motionFrameTick++
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
                mapLibreRef?.let { map ->
                    val targetGeo = visualCoordinates[m.id] ?: LatLng(m.y, m.x)
                    val targetZoom = maxOf(map.cameraPosition.zoom, 16.0).takeIf { map.cameraPosition.zoom < 12.0 }
                        ?: map.cameraPosition.zoom
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(targetGeo, targetZoom), 400)
                }
            }
        } else {
            // Show All Members on Map: fit all active members in view
            val validMembers = members.filter { it.x != 0.0 && it.y != 0.0 }
            if (validMembers.isNotEmpty()) {
                mapLibreRef?.let { map ->
                    val minLat = validMembers.minOf { it.y }
                    val maxLat = validMembers.maxOf { it.y }
                    val minLng = validMembers.minOf { it.x }
                    val maxLng = validMembers.maxOf { it.x }
                    val centerLat = (minLat + maxLat) / 2.0
                    val centerLng = (minLng + maxLng) / 2.0
                    val maxDiff = maxOf(maxLat - minLat, maxLng - minLng)
                    val zoomLevel = when {
                        maxDiff > 0.5 -> 10.0
                        maxDiff > 0.2 -> 11.5
                        maxDiff > 0.05 -> 13.0
                        else -> 14.2
                    }
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), zoomLevel), 400)
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
                mapLibreRef?.let { map ->
                    val minLat = points.minOf { it.first }
                    val maxLat = points.maxOf { it.first }
                    val minLng = points.minOf { it.second }
                    val maxLng = points.maxOf { it.second }
                    val latMargin = maxOf((maxLat - minLat) * 0.35, 0.008)
                    val lngMargin = maxOf((maxLng - minLng) * 0.35, 0.008)
                    try {
                        val bounds = LatLngBounds.Builder()
                            .include(LatLng(maxLat + latMargin, maxLng + lngMargin))
                            .include(LatLng(minLat - latMargin, minLng - lngMargin))
                            .build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 400)
                    } catch (e: Exception) {
                        val centerLat = (minLat + maxLat) / 2.0
                        val centerLng = (minLng + maxLng) / 2.0
                        map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(centerLat, centerLng)), 400)
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
                mapLibreRef?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(me.y, me.x)), 300)
            }
        }
    }

    // Keep camera panning to stay centered on the selected member when follow mode is active!
    LaunchedEffect(selectedMember?.y, selectedMember?.x, selectedMemberId, isFollowingSelectedMember) {
        if (isFollowingSelectedMember && selectedMember != null && selectedMember.x != 0.0 && selectedMember.y != 0.0 && !isRouteTrailEnabled) {
            if (!isUserInteractingWithMap) {
                mapLibreRef?.let { map ->
                    val targetGeo = visualCoordinates[selectedMember.id] ?: LatLng(selectedMember.y, selectedMember.x)
                    map.moveCamera(CameraUpdateFactory.newLatLng(targetGeo))
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mv = mapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mv?.onStart()
                Lifecycle.Event.ON_RESUME -> mv?.onResume()
                Lifecycle.Event.ON_PAUSE -> mv?.onPause()
                Lifecycle.Event.ON_STOP -> mv?.onStop()
                Lifecycle.Event.ON_DESTROY -> mv?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    onCreate(null)
                    onStart()
                    onResume()
                    mapViewRef = this

                    getMapAsync { map ->
                        mapLibreRef = map
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = true
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(homeLat, homeLng))
                            .zoom(15.5)
                            .build()

                        activeStyleMode = mapTypeMode
                        map.setStyle(MapLibreOverlayManager.styleUrlForMode(mapTypeMode)) { style ->
                            MapLibreOverlayManager.ensureLayers(style)
                        }

                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                isUserInteractingWithMap = true
                                isFollowingSelectedMember = false
                                isCameraFollowingMe = false
                            }
                        }

                        map.addOnCameraIdleListener {
                            isUserInteractingWithMap = false
                            val z = map.cameraPosition.zoom
                            if (kotlin.math.abs(z - mapZoomLevel) > 0.05) {
                                mapZoomLevel = z
                            }
                        }

                        map.addOnMapClickListener { latLng ->
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val hitFeatures = map.queryRenderedFeatures(
                                screenPoint,
                                MapLibreOverlayManager.LAYER_MARKERS_STEM,
                                MapLibreOverlayManager.LAYER_MARKERS_CENTER
                            )
                            val firstHit = hitFeatures.firstOrNull()
                            if (firstHit != null) {
                                val kind = firstHit.getStringProperty("kind")
                                val hitId = firstHit.getStringProperty("id")
                                if (kind == "member" && hitId != null) {
                                    val tappedMember = members.firstOrNull { it.id == hitId }
                                    if (tappedMember != null) {
                                        onSelectMember(tappedMember.id)
                                        isFollowingSelectedMember = true
                                        val targetGeo = visualCoordinates[tappedMember.id] ?: LatLng(tappedMember.y, tappedMember.x)
                                        val targetZoom = if (map.cameraPosition.zoom < 12.0) 16.0 else map.cameraPosition.zoom
                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(targetGeo, targetZoom), 400)
                                        memberForContextMenu = tappedMember
                                        return@addOnMapClickListener true
                                    }
                                } else if (kind == "zone" && hitId != null) {
                                    val tappedZone = com.example.data.GeoUtils.withDefaultSafeZones(safeZones)
                                        .firstOrNull { it.id == hitId }
                                    if (tappedZone != null) {
                                        zoneToDelete = tappedZone
                                        return@addOnMapClickListener true
                                    }
                                }
                            }
                            false
                        }
                    }
                }
            },
            update = { _ ->
                // Read reactive state so Compose re-runs overlay sync on zoom, motion, and data changes
                @Suppress("UNUSED_VARIABLE") val tick = motionFrameTick
                @Suppress("UNUSED_VARIABLE") val currentZoom = mapZoomLevel
                val map = mapLibreRef ?: return@AndroidView

                if (activeStyleMode != mapTypeMode) {
                    activeStyleMode = mapTypeMode
                    map.setStyle(MapLibreOverlayManager.styleUrlForMode(mapTypeMode)) { loadedStyle ->
                        MapLibreOverlayManager.ensureLayers(loadedStyle)
                    }
                    return@AndroidView
                }

                val style = map.style ?: return@AndroidView
                if (!style.isFullyLoaded) return@AndroidView
                MapLibreOverlayManager.ensureLayers(style)

                val zoneFillFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val zoneLineFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val haloLineFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val coreLineFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val centerMarkerFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val stemMarkerFeatures = mutableListOf<org.maplibre.geojson.Feature>()

                // 1. Safe Zones geofences & markers (excluding Home)
                com.example.data.GeoUtils.withDefaultSafeZones(safeZones)
                    .filter { it.iconName.lowercase() != "home" && !it.name.lowercase().contains("home") }
                    .forEach { zone ->
                        zoneFillFeatures.add(
                            MapLibreOverlayManager.circlePolygonFeature(
                                zone.latitude, zone.longitude, zone.radiusMeters, "#00E676", 0.08f
                            )
                        )
                        zoneLineFeatures.add(
                            MapLibreOverlayManager.circleOutlineFeature(
                                zone.latitude, zone.longitude, zone.radiusMeters, "#00C853", 2.0f, 0.43f
                            )
                        )
                        val zoneIconId = "zone_${zone.id}_${zone.name}_${zone.iconName}"
                        if (style.getImage(zoneIconId) == null) {
                            MapLibreOverlayManager.registerDrawable(
                                style,
                                zoneIconId,
                                MapMarkerRenderer.getOrCreateZoneIcon(context, zone.name, zone.iconName)
                            )
                        }
                        centerMarkerFeatures.add(
                            MapLibreOverlayManager.markerFeature(zone.latitude, zone.longitude, zoneIconId, "zone", zone.id)
                        )
                    }

                // 2. Work Area geofence & marker if calibrated
                if (isWorkCalibrated && workLat != 0.0 && workLng != 0.0) {
                    zoneFillFeatures.add(
                        MapLibreOverlayManager.circlePolygonFeature(
                            workLat, workLng, workRadiusMeters, "#0284C7", 0.10f
                        )
                    )
                    zoneLineFeatures.add(
                        MapLibreOverlayManager.circleOutlineFeature(
                            workLat, workLng, workRadiusMeters, "#0EA5E9", 2.0f, 0.51f
                        )
                    )
                    val workIconId = "zone_work_Work_work"
                    if (style.getImage(workIconId) == null) {
                        MapLibreOverlayManager.registerDrawable(
                            style,
                            workIconId,
                            MapMarkerRenderer.getOrCreateZoneIcon(context, "Work", "work")
                        )
                    }
                    centerMarkerFeatures.add(
                        MapLibreOverlayManager.markerFeature(workLat, workLng, workIconId, "work", "work")
                    )
                }

                // 3. Co-located Cluster Spokes & Anchor Dots
                val layoutResult = sharedClusterLayout
                val adjustedCoordinates = layoutResult.adjustedCoordinates
                val clusterAnchorPoints = layoutResult.clusterAnchorPoints
                val clusters = layoutResult.clusters

                val zoneIconsDrawn = com.example.data.GeoUtils.withDefaultSafeZones(safeZones)
                    .filter { it.iconName.lowercase() != "home" && !it.name.lowercase().contains("home") }
                    .map { LatLng(it.latitude, it.longitude) } +
                    if (isWorkCalibrated && workLat != 0.0 && workLng != 0.0) listOf(LatLng(workLat, workLng)) else emptyList()
                val drawnAnchorHubs = mutableListOf<LatLng>()

                for (cluster in clusters) {
                    if (cluster.size > 1) {
                        for (mId in cluster) {
                            val anchor = clusterAnchorPoints[mId]
                            val target = adjustedCoordinates[mId]
                            val member = members.firstOrNull { it.id == mId }
                            if (anchor != null && target != null && member != null) {
                                val vibrantColorHex = MapLibreOverlayManager.intColorToHex(
                                    getVibrantSpokeColor(member.avatarColorHex)
                                )
                                haloLineFeatures.add(
                                    MapLibreOverlayManager.lineFeature(listOf(anchor, target), "#FFFFFF", 3.0f, 0.78f)
                                )
                                coreLineFeatures.add(
                                    MapLibreOverlayManager.lineFeature(listOf(anchor, target), vibrantColorHex, 1.8f, 0.90f)
                                )

                                val hasExistingHub = drawnAnchorHubs.any {
                                    com.example.data.GeoUtils.distanceMeters(it.latitude, it.longitude, anchor.latitude, anchor.longitude) < 5.0
                                } || zoneIconsDrawn.any {
                                    com.example.data.GeoUtils.distanceMeters(it.latitude, it.longitude, anchor.latitude, anchor.longitude) < 5.0
                                }
                                if (!hasExistingHub) {
                                    drawnAnchorHubs.add(anchor)
                                    val anchorIconId = "cluster_anchor_dot"
                                    if (style.getImage(anchorIconId) == null) {
                                        MapLibreOverlayManager.registerDrawable(
                                            style,
                                            anchorIconId,
                                            MapMarkerRenderer.getOrCreateClusterAnchorDot(context)
                                        )
                                    }
                                    centerMarkerFeatures.add(
                                        MapLibreOverlayManager.markerFeature(anchor.latitude, anchor.longitude, anchorIconId, "anchor", "anchor_${drawnAnchorHubs.size}")
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Active Family Members, Rings, Trails & Avatar Markers
                val activeMembersOnMap = members.filter {
                    !(it.isLocationPaused || (it.id == "me" && isLocationPaused) || it.statusText.contains("Paused", ignoreCase = true))
                }
                val sortedMembers = activeMembersOnMap.sortedWith(Comparator { m1, m2 ->
                    when {
                        m1.id == "me" && m2.id != "me" -> -1
                        m1.id != "me" && m2.id == "me" -> 1
                        else -> 0
                    }
                })

                sortedMembers.forEach { member ->
                    val visualGeo = memberVisualStates[member.id]?.currentGeo
                    val isDeconflicted = layoutResult.barrierMembership.containsKey(member.id) ||
                        layoutResult.clusters.any { it.contains(member.id) }
                    val displayGeo = if (isDeconflicted) {
                        adjustedCoordinates[member.id] ?: visualGeo ?: LatLng(member.y, member.x)
                    } else {
                        visualGeo ?: adjustedCoordinates[member.id] ?: LatLng(member.y, member.x)
                    }
                    if (isDeconflicted) {
                        visualCoordinates[member.id] = displayGeo
                        memberVisualStates[member.id]?.let { vs ->
                            vs.currentGeo = displayGeo
                            vs.targetGeo = displayGeo
                        }
                    }
                    val trueGeo = visualGeo ?: LatLng(member.y, member.x)
                    val isAway = homeLat != 0.0 && homeLng != 0.0 && (kotlin.math.hypot(member.y - homeLat, member.x - homeLng) * 111.0 > 0.06)
                    val isSelected = member.id == selectedMemberId ||
                        (selectedMemberId != null && (member.id.contains(selectedMemberId) || selectedMemberId.contains(member.id) || member.name.equals(selectedMember?.name, ignoreCase = true)))

                    val memberColor = try {
                        android.graphics.Color.parseColor(member.avatarColorHex)
                    } catch (e: Exception) { android.graphics.Color.BLUE }
                    val memberColorHex = MapLibreOverlayManager.intColorToHex(memberColor)

                    if (member.id == "me") {
                        zoneLineFeatures.add(
                            MapLibreOverlayManager.circleOutlineFeature(
                                trueGeo.latitude, trueGeo.longitude, 60.0, memberColorHex, 1.5f, 0.22f
                            )
                        )
                    }

                    if (isRouteTrailEnabled && (isSelected || (selectedMemberId == null && isAway))) {
                        val trailPoints = com.example.ui.map.TrailRouteEngine.findTrailForMember(locationTrails, member)
                            ?: emptyList()
                        if (trailPoints.size >= 2) {
                            val latLngs = trailPoints.map { LatLng(it.first, it.second) }
                            haloLineFeatures.add(
                                MapLibreOverlayManager.lineFeature(latLngs, "#FFFFFF", 9.0f, 0.70f)
                            )
                            coreLineFeatures.add(
                                MapLibreOverlayManager.lineFeature(latLngs, memberColorHex, 6.0f, 1.0f)
                            )
                        }
                    }

                    val isSos = member.statusText.contains("🚨 EMERGENCY SOS ACTIVE") || member.statusText.contains("🚨 SOS")
                    val markerLabel = if (isSos) "🚨" else if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else (member.name.firstOrNull()?.toString() ?: "M")
                    val markerColorHex = if (isSos) "#FF1744" else member.avatarColorHex
                    val isOffline = member.batteryPercentage <= 5

                    val rawTimeLabel = if (member.id == "me") "now"
                        else if (member.lastActive > 0L) formatTimeAgo(member.lastActive)
                        else "now"
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
                    val showStem = !isInCluster

                    val memberIconId = "m_${member.id}_${markerColorHex}_${markerLabel}_${isSelected || isSos}_${member.photoPath ?: ""}_${isOffline}_${timeLabel}_${locationDurationLabel}_${batteryForBadge}_${member.isCharging}_${animTick}_${showStem}"
                    if (style.getImage(memberIconId) == null) {
                        MapLibreOverlayManager.registerDrawable(
                            style,
                            memberIconId,
                            MapMarkerRenderer.getOrCreateMarkerDrawable(
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
                                showStem = showStem
                            )
                        )
                    }

                    val feature = MapLibreOverlayManager.markerFeature(
                        displayGeo.latitude, displayGeo.longitude, memberIconId, "member", member.id
                    )
                    if (showStem) {
                        stemMarkerFeatures.add(feature)
                    } else {
                        centerMarkerFeatures.add(feature)
                    }

                    memberVisualStates.getOrPut(member.id) {
                        MemberVisualState(
                            currentGeo = displayGeo,
                            targetGeo = displayGeo,
                            speedMph = member.speedMph,
                            bearingDeg = 0.0,
                            lastTargetUpdateTime = System.currentTimeMillis(),
                            isMoving = false
                        )
                    }
                }

                // 5. Selected Trip Route History (Life360 Route Playback)
                selectedTrip?.let { trip ->
                    val snapped = snappedTripRoutes[trip.id]
                    val rawPoints = if (trip.breadcrumbs.isNotEmpty()) {
                        trip.breadcrumbs.map { LatLng(it.lat, it.lng) }
                    } else if (trip.startLat != 0.0 && trip.endLat != 0.0) {
                        listOf(LatLng(trip.startLat, trip.startLng), LatLng(trip.endLat, trip.endLng))
                    } else {
                        emptyList()
                    }
                    val tripPoints = if (!snapped.isNullOrEmpty() && snapped.size >= 2) {
                        snapped.map { LatLng(it.first, it.second) }
                    } else if (trip.transitMode.equals("TRAIN", ignoreCase = true)) {
                        rawPoints
                    } else if (rawPoints.size >= 3 && !com.example.ui.map.TrailRouteEngine.hasSparseGaps(rawPoints.map { Pair(it.latitude, it.longitude) })) {
                        rawPoints
                    } else {
                        emptyList()
                    }

                    if (tripPoints.size >= 2) {
                        haloLineFeatures.add(
                            MapLibreOverlayManager.lineFeature(tripPoints, "#FFFFFF", 11.0f, 0.82f)
                        )
                        val isTrain = trip.transitMode.equals("TRAIN", ignoreCase = true)
                        val tripColorHex = if (isTrain) "#0D9488" else "#00E5FF"
                        coreLineFeatures.add(
                            MapLibreOverlayManager.lineFeature(tripPoints, tripColorHex, 6.5f, 1.0f)
                        )

                        val startIconId = "trip_start_pin"
                        if (style.getImage(startIconId) == null) {
                            MapLibreOverlayManager.registerDrawable(
                                style,
                                startIconId,
                                MapMarkerRenderer.getOrCreateMarkerDrawable(
                                    context = context, colorHex = "#00E676", emoji = "🟢", isSelected = false, showStem = false
                                )
                            )
                        }
                        centerMarkerFeatures.add(
                            MapLibreOverlayManager.markerFeature(tripPoints.first().latitude, tripPoints.first().longitude, startIconId, "pin", "trip_start")
                        )

                        val endIconId = "trip_end_pin"
                        if (style.getImage(endIconId) == null) {
                            MapLibreOverlayManager.registerDrawable(
                                style,
                                endIconId,
                                MapMarkerRenderer.getOrCreateMarkerDrawable(
                                    context = context, colorHex = "#FF1744", emoji = "🏁", isSelected = false, showStem = false
                                )
                            )
                        }
                        centerMarkerFeatures.add(
                            MapLibreOverlayManager.markerFeature(tripPoints.last().latitude, tripPoints.last().longitude, endIconId, "pin", "trip_end")
                        )
                    }
                }

                MapLibreOverlayManager.updateSource(style, MapLibreOverlayManager.SOURCE_ZONES_FILL, zoneFillFeatures)
                MapLibreOverlayManager.updateSource(style, MapLibreOverlayManager.SOURCE_ZONES_LINE, zoneLineFeatures)
                MapLibreOverlayManager.updateSource(style, MapLibreOverlayManager.SOURCE_LINES_HALO, haloLineFeatures)
                MapLibreOverlayManager.updateSource(style, MapLibreOverlayManager.SOURCE_LINES_CORE, coreLineFeatures)
                MapLibreOverlayManager.updateSource(style, MapLibreOverlayManager.SOURCE_MARKERS_CENTER, centerMarkerFeatures)
                MapLibreOverlayManager.updateSource(style, MapLibreOverlayManager.SOURCE_MARKERS_STEM, stemMarkerFeatures)
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
                        mapLibreRef?.let { map ->
                            val targetGeo = visualCoordinates[selectedMember.id] ?: LatLng(selectedMember.y, selectedMember.x)
                            val targetZoom = if (map.cameraPosition.zoom < 12.0) 16.0 else map.cameraPosition.zoom
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(targetGeo, targetZoom), 400)
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
                mapLibreRef?.let { map ->
                    val newZoom = (map.cameraPosition.zoom + 1.0).coerceAtMost(20.0)
                    mapZoomLevel = newZoom
                    val target = if (isFollowingSelectedMember && selectedMember != null) {
                        visualCoordinates[selectedMember.id] ?: LatLng(selectedMember.y, selectedMember.x)
                    } else if (isCameraFollowingMe && me != null) {
                        LatLng(me.y, me.x)
                    } else map.cameraPosition.target
                    if (target != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, newZoom), 250)
                    } else {
                        map.animateCamera(CameraUpdateFactory.zoomTo(newZoom), 250)
                    }
                }
            },
            onZoomOut = {
                mapLibreRef?.let { map ->
                    val newZoom = (map.cameraPosition.zoom - 1.0).coerceAtLeast(2.0)
                    mapZoomLevel = newZoom
                    val target = if (isFollowingSelectedMember && selectedMember != null) {
                        visualCoordinates[selectedMember.id] ?: LatLng(selectedMember.y, selectedMember.x)
                    } else if (isCameraFollowingMe && me != null) {
                        LatLng(me.y, me.x)
                    } else map.cameraPosition.target
                    if (target != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, newZoom), 250)
                    } else {
                        map.animateCamera(CameraUpdateFactory.zoomTo(newZoom), 250)
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
                    mapLibreRef?.let { map ->
                        val targetZoom = if (map.cameraPosition.zoom < 12.0) 15.5 else map.cameraPosition.zoom
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(meLoc.y, meLoc.x), targetZoom), 400)
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
