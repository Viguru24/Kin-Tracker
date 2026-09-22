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



data class MemberVisualState(
    var currentGeo: GeoPoint,
    var targetGeo: GeoPoint,
    var speedMph: Double,
    var bearingDeg: Double,
    var lastTargetUpdateTime: Long,
    var isMoving: Boolean,
    var markerRef: Marker? = null
)

data class ClusterLayoutResult(
    val adjustedCoordinates: Map<String, GeoPoint>,
    val clusterAnchorPoints: Map<String, GeoPoint>,
    val clusters: List<List<String>>
)

fun computeClusterLayout(
    members: List<FamilyMember>,
    safeZones: List<SafeZone>,
    homeLat: Double,
    homeLng: Double,
    homeRadiusMeters: Double,
    isWorkCalibrated: Boolean,
    workLat: Double,
    workLng: Double,
    workRadiusMeters: Double
): ClusterLayoutResult {
    val adjustedCoordinates = mutableMapOf<String, GeoPoint>()
    val clusterAnchorPoints = mutableMapOf<String, GeoPoint>()
    val clusters = mutableListOf<List<String>>()

    val activeMembers = members.filter { it.x != 0.0 && it.y != 0.0 }
    val homeClusterMembers = mutableListOf<String>()
    val safeZoneClusterMembers = mutableMapOf<String, MutableList<String>>()
    val workClusterMembers = mutableListOf<String>()
    val unassignedMembers = mutableListOf<FamilyMember>()

    activeMembers.forEach { m ->
        val distToHome = if (homeLat != 0.0 && homeLng != 0.0) {
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, homeLat, homeLng)
        } else Double.MAX_VALUE
        val isHome = distToHome <= homeRadiusMeters || m.statusText.contains("At Home", ignoreCase = true)

        val matchedZone = safeZones.firstOrNull { zone ->
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, zone.latitude, zone.longitude) <= zone.radiusMeters
        }
        val isInsideWork = isWorkCalibrated && workLat != 0.0 && workLng != 0.0 &&
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, workLat, workLng) <= workRadiusMeters

        if (isHome) {
            homeClusterMembers.add(m.id)
        } else if (matchedZone != null) {
            safeZoneClusterMembers.getOrPut(matchedZone.id) { mutableListOf() }.add(m.id)
        } else if (isInsideWork) {
            workClusterMembers.add(m.id)
        } else {
            unassignedMembers.add(m)
        }
    }

    // A. Form Home Cluster
    if (homeClusterMembers.size > 1 && homeLat != 0.0 && homeLng != 0.0) {
        clusters.add(homeClusterMembers.sorted())
    } else if (homeClusterMembers.size == 1) {
        val mId = homeClusterMembers[0]
        val member = activeMembers.first { it.id == mId }
        adjustedCoordinates[mId] = GeoPoint(member.y, member.x)
    }

    // B. Form Safe Zone Clusters
    safeZones.forEach { zone ->
        val zoneMembers = safeZoneClusterMembers[zone.id]
        if (zoneMembers != null) {
            if (zoneMembers.size > 1) {
                clusters.add(zoneMembers.sorted())
            } else if (zoneMembers.size == 1) {
                val mId = zoneMembers[0]
                val member = activeMembers.first { it.id == mId }
                adjustedCoordinates[mId] = GeoPoint(member.y, member.x)
            }
        }
    }

    // C. Form Work Cluster
    if (workClusterMembers.size > 1 && workLat != 0.0 && workLng != 0.0) {
        clusters.add(workClusterMembers.sorted())
    } else if (workClusterMembers.size == 1) {
        val mId = workClusterMembers[0]
        val member = activeMembers.first { it.id == mId }
        adjustedCoordinates[mId] = GeoPoint(member.y, member.x)
    }

    // D. Form Ad-Hoc Co-Located Clusters for members outside zones (within 40m)
    val visited = mutableSetOf<String>()
    for (i in unassignedMembers.indices) {
        val m1 = unassignedMembers[i]
        if (visited.contains(m1.id)) continue

        val currentCluster = mutableListOf(m1.id)
        visited.add(m1.id)

        for (j in i + 1 until unassignedMembers.size) {
            val m2 = unassignedMembers[j]
            if (visited.contains(m2.id)) continue

            val distM = com.example.data.GeoUtils.distanceMeters(m1.y, m1.x, m2.y, m2.x)
            if (distM < 40.0) {
                currentCluster.add(m2.id)
                visited.add(m2.id)
            }
        }

        if (currentCluster.size > 1) {
            clusters.add(currentCluster.sorted())
        } else {
            val mId = currentCluster[0]
            val member = activeMembers.first { it.id == mId }
            adjustedCoordinates[mId] = GeoPoint(member.y, member.x)
        }
    }

    // Lay out each cluster in a static, peaceful radial orbit around the barrier anchor
    for (cluster in clusters) {
        val isHomeCluster = cluster.any { homeClusterMembers.contains(it) }
        val matchedZone = safeZones.firstOrNull { zone ->
            safeZoneClusterMembers[zone.id]?.any { cluster.contains(it) } == true
        }
        val isWorkCluster = cluster.any { workClusterMembers.contains(it) }

        val anchorGeo = when {
            isHomeCluster && homeLat != 0.0 && homeLng != 0.0 -> GeoPoint(homeLat, homeLng)
            matchedZone != null -> GeoPoint(matchedZone.latitude, matchedZone.longitude)
            isWorkCluster && workLat != 0.0 && workLng != 0.0 -> GeoPoint(workLat, workLng)
            else -> {
                var sumLat = 0.0
                var sumLng = 0.0
                for (mId in cluster) {
                    val member = activeMembers.first { it.id == mId }
                    sumLat += member.y
                    sumLng += member.x
                }
                GeoPoint(sumLat / cluster.size, sumLng / cluster.size)
            }
        }

        val spreadRadiusMeters = when (cluster.size) {
            2 -> 16.0
            3 -> 20.0
            4 -> 24.0
            else -> maxOf(24.0, (cluster.size * 7.5))
        }

        val startAngle = when (cluster.size) {
            2 -> -Math.PI / 2.0
            3 -> -Math.PI / 2.0
            4 -> -Math.PI / 4.0
            else -> -Math.PI / 2.0
        }

        val angleStep = (2.0 * Math.PI) / cluster.size
        val cosLat = kotlin.math.cos(Math.toRadians(anchorGeo.latitude))

        for (idx in cluster.indices) {
            val mId = cluster[idx]
            val angle = startAngle + (idx * angleStep)
            val deltaLat = (spreadRadiusMeters * kotlin.math.sin(angle)) / 111139.0
            val deltaLng = (spreadRadiusMeters * kotlin.math.cos(angle)) / (111139.0 * cosLat)

            val orbitGeo = GeoPoint(anchorGeo.latitude + deltaLat, anchorGeo.longitude + deltaLng)
            adjustedCoordinates[mId] = orbitGeo
            clusterAnchorPoints[mId] = anchorGeo
        }
    }

    return ClusterLayoutResult(adjustedCoordinates, clusterAnchorPoints, clusters)
}

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
    onCreateGroupWithPin: (String) -> Unit = {},
    isLocationPaused: Boolean = false,
    onToggleLocationPaused: (Boolean) -> Unit = {},
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
        members.any { it.speedMph >= 0.6 || classifyTransitMode(it.speedMph, it.statusText, it.id) != TransitMode.STATIONARY }
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

    val visualCoordinates = remember { java.util.concurrent.ConcurrentHashMap<String, GeoPoint>() }
    val memberVisualStates = remember { mutableMapOf<String, MemberVisualState>() }

    // Synchronize incoming member target updates with barrier-aware static locking
    LaunchedEffect(members, safeZones, homeLat, homeLng, isWorkCalibrated, workLat, workLng, isLocationPaused) {
        val now = android.os.SystemClock.uptimeMillis()
        val activeMembers = members.filter {
            !(it.isLocationPaused || (it.id == "me" && isLocationPaused) || it.statusText.contains("Paused", ignoreCase = true))
        }
        val clusterLayout = computeClusterLayout(
            members = activeMembers,
            safeZones = safeZones,
            homeLat = homeLat,
            homeLng = homeLng,
            homeRadiusMeters = homeRadiusMeters,
            isWorkCalibrated = isWorkCalibrated,
            workLat = workLat,
            workLng = workLng,
            workRadiusMeters = workRadiusMeters
        )
        val adjustedCoordinates = clusterLayout.adjustedCoordinates

        activeMembers.forEach { m ->
            if (m.x != 0.0 && m.y != 0.0) {
                val state = memberVisualStates[m.id]
                val transit = classifyTransitMode(m.speedMph, m.statusText, m.id)

                // Check barrier containment (Home, Safe Zones, Work)
                val distToHome = if (homeLat != 0.0 && homeLng != 0.0) {
                    com.example.data.GeoUtils.distanceMeters(m.y, m.x, homeLat, homeLng)
                } else Double.MAX_VALUE
                val isAtHome = distToHome <= homeRadiusMeters || m.statusText.contains("At Home", ignoreCase = true)

                val matchedSafeZone = safeZones.firstOrNull { zone ->
                    com.example.data.GeoUtils.distanceMeters(m.y, m.x, zone.latitude, zone.longitude) <= zone.radiusMeters
                }
                val isInsideWork = isWorkCalibrated && workLat != 0.0 && workLng != 0.0 &&
                    com.example.data.GeoUtils.distanceMeters(m.y, m.x, workLat, workLng) <= workRadiusMeters

                val isInsideBarrier = isAtHome || (matchedSafeZone != null) || isInsideWork

                // Break-out condition: member must exceed barrier boundary + 20m hysteresis AND maintain speed >= 1.8 mph
                val isBreakingOut = when {
                    isAtHome -> distToHome > (homeRadiusMeters + 20.0) && m.speedMph >= 1.8
                    matchedSafeZone != null -> {
                        val d = com.example.data.GeoUtils.distanceMeters(m.y, m.x, matchedSafeZone.latitude, matchedSafeZone.longitude)
                        d > (matchedSafeZone.radiusMeters + 20.0) && m.speedMph >= 1.8
                    }
                    isInsideWork -> {
                        val d = com.example.data.GeoUtils.distanceMeters(m.y, m.x, workLat, workLng)
                        d > (workRadiusMeters + 20.0) && m.speedMph >= 1.8
                    }
                    else -> false
                }

                // If inside a barrier and not breaking out, the member is strictly STATIC!
                val isReportedMoving = if (isInsideBarrier && !isBreakingOut) {
                    false
                } else {
                    m.speedMph >= 1.8 || (transit != TransitMode.STATIONARY && m.speedMph >= 1.2)
                }

                val assignedClusterPos = adjustedCoordinates[m.id] ?: GeoPoint(m.y, m.x)

                if (state == null) {
                    val initialGeo = if (isInsideBarrier && !isBreakingOut) assignedClusterPos else GeoPoint(m.y, m.x)
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
                    if (isInsideBarrier && !isBreakingOut) {
                        // Locked static inside barrier — zero GPS jitter motion
                        state.isMoving = false
                        state.speedMph = 0.0
                        state.targetGeo = assignedClusterPos
                        val distToTarget = com.example.data.GeoUtils.distanceMeters(
                            state.currentGeo.latitude, state.currentGeo.longitude,
                            assignedClusterPos.latitude, assignedClusterPos.longitude
                        )
                        if (distToTarget < 2.0) {
                            state.currentGeo = assignedClusterPos
                            state.markerRef?.position = assignedClusterPos
                        }
                        visualCoordinates[m.id] = state.currentGeo
                    } else {
                        // Truly moving outside barrier
                        val distMovedM = com.example.data.GeoUtils.distanceMeters(
                            state.targetGeo.latitude, state.targetGeo.longitude,
                            m.y, m.x
                        )
                        // Suppress micro-jitter (< 4.0m) when speed is low
                        if (distMovedM > 4.0 || (m.speedMph >= 1.8 && distMovedM > 1.5)) {
                            val dLat = m.y - state.currentGeo.latitude
                            val dLng = m.x - state.currentGeo.longitude
                            val cosLat = kotlin.math.cos(Math.toRadians(m.y))
                            val rawBearing = Math.toDegrees(kotlin.math.atan2(dLng * cosLat, dLat))
                            state.bearingDeg = (rawBearing + 360.0) % 360.0
                            state.targetGeo = GeoPoint(m.y, m.x)
                            val elapsedSec = ((now - state.lastTargetUpdateTime) / 1000.0).coerceAtLeast(0.5)
                            state.lastTargetUpdateTime = now
                            state.speedMph = if (m.speedMph >= 1.0) m.speedMph else (distMovedM / elapsedSec) * 2.23694
                            state.isMoving = isReportedMoving || state.speedMph >= 1.8
                        } else {
                            state.isMoving = isReportedMoving
                            if (!isReportedMoving) {
                                state.speedMph = 0.0
                            }
                        }
                    }
                }
            }
        }
    }

    // High-frequency (60 FPS) continuous road interpolation & dead-reckoning engine
    LaunchedEffect(Unit) {
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
                    val speedMps = (state.speedMph * 0.44704).coerceAtLeast(1.2)
                    // Catchup speed smoothly scales to ensure it tracks incoming pings without lag
                    val catchupSpeedMps = maxOf(speedMps, distToTargetM / 1.5)
                    val stepM = catchupSpeedMps * dt

                    if (distToTargetM > stepM && distToTargetM > 0.5) {
                        // Smoothly advance toward target
                        val frac = (stepM / distToTargetM).coerceIn(0.0, 1.0)
                        val nextLat = curLat + (tgtLat - curLat) * frac
                        val nextLng = curLng + (tgtLng - curLng) * frac
                        state.currentGeo = GeoPoint(nextLat, nextLng)
                        marker.position = state.currentGeo
                        visualCoordinates[mId] = state.currentGeo
                        anyMoved = true
                    } else {
                        // Reached target! DEAD RECKONING EXTRAPOLATION:
                        // Continue moving forward along road heading at current speed!
                        val timeSinceGps = now - state.lastTargetUpdateTime
                        if (timeSinceGps < 30_000L) { // Extrapolate for up to 30s
                            val decay = if (timeSinceGps > 15_000L) {
                                (1.0 - (timeSinceGps - 15_000L) / 15_000.0).coerceIn(0.2, 1.0)
                            } else 1.0
                            val deadStepM = speedMps * decay * dt
                            val rad = Math.toRadians(state.bearingDeg)
                            val dLat = (deadStepM * kotlin.math.cos(rad)) / 111000.0
                            val dLng = (deadStepM * kotlin.math.sin(rad)) / (111000.0 * kotlin.math.cos(Math.toRadians(curLat)))
                            val nextLat = curLat + dLat
                            val nextLng = curLng + dLng
                            state.currentGeo = GeoPoint(nextLat, nextLng)
                            state.targetGeo = state.currentGeo
                            marker.position = state.currentGeo
                            visualCoordinates[mId] = state.currentGeo
                            anyMoved = true
                        } else {
                            state.currentGeo = state.targetGeo
                            marker.position = state.currentGeo
                            visualCoordinates[mId] = state.currentGeo
                            state.isMoving = false
                            anyMoved = true
                        }
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

                // Smooth camera follow for selected member ONLY when moving along roads
                val isThisMemberSelected = mId == selectedMemberId || 
                    (selectedMemberId != null && (mId.contains(selectedMemberId, ignoreCase = true) || selectedMemberId.contains(mId, ignoreCase = true)))
                if (isThisMemberSelected && isFollowingSelectedMember && !isRouteTrailEnabled && state.isMoving) {
                    mapViewRef?.let { map ->
                        map.controller.setCenter(state.currentGeo)
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
                    if (map.zoomLevelDouble < 14.5) {
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

                    var touchDownX = 0f
                    var touchDownY = 0f
                    // Allow panning and swiping on the Map without triggering outer container scrolling
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                touchDownX = event.x
                                touchDownY = event.y
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                val dx = Math.abs(event.x - touchDownX)
                                val dy = Math.abs(event.y - touchDownY)
                                if (dx > 12f || dy > 12f) {
                                    // User is actively panning/scrolling the map: release camera lock!
                                    isFollowingSelectedMember = false
                                    isCameraFollowingMe = false
                                }
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



                // --- LIFE360-STYLE CO-LOCATED CLUSTER & DECONFLICTION ENGINE ---
                val layoutResult = computeClusterLayout(
                    members = members,
                    safeZones = safeZones,
                    homeLat = homeLat,
                    homeLng = homeLng,
                    homeRadiusMeters = homeRadiusMeters,
                    isWorkCalibrated = isWorkCalibrated,
                    workLat = workLat,
                    workLng = workLng,
                    workRadiusMeters = workRadiusMeters
                )
                val adjustedCoordinates = layoutResult.adjustedCoordinates
                val clusterAnchorPoints = layoutResult.clusterAnchorPoints
                val clusters = layoutResult.clusters
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
                    val isMemberMoving = memberVisualStates[member.id]?.isMoving == true
                    val inCluster = adjustedCoordinates.containsKey(member.id) && clusterAnchorPoints.containsKey(member.id)
                    val displayGeo = if (inCluster && !isMemberMoving) {
                        adjustedCoordinates[member.id]!!
                    } else {
                        visualGeo ?: GeoPoint(member.y, member.x)
                    }
                    val trueGeo = visualGeo ?: GeoPoint(member.y, member.x)
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
                    val locationDurationLabel = formatTransitBadge(member.speedMph, member.statusText, member.locationSince, member.id)

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
                    memberVisualStates[member.id]?.let { it.markerRef = memberMarker }
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
            // 1. FLOATING TOP HUD ACTION BAR — Settings, Digest, Map Theme, Circle Switcher, Offline Badge (All on the same horizontal level!)
            // 1. FLOATING TOP HUD ACTION BAR — Settings, Live/Paused Tracking Pill, Circle Switcher, Theme, Digest, Offline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── LEFT: Settings Trigger Button ──
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onSettingsClick() }
                        .testTag("settings_button"),
                    color = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SlateBorder),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("⚙️", fontSize = 14.sp)
                    }
                }

                // ── CENTRE: Circle Switcher Pill ──
                Box(contentAlignment = Alignment.TopCenter) {
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .widthIn(min = 100.dp, max = 160.dp)
                            .clickable { showCircleSwitcher = !showCircleSwitcher },
                        color = Color(0xF0121218),
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.5f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "👥", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (activeGroupPinCode.isNotBlank()) {
                                    val active = groupPinMappings.firstOrNull { it.pinCode == activeGroupPinCode }
                                    active?.groupName?.ifBlank { "Code $activeGroupPinCode" } ?: "Code $activeGroupPinCode"
                                } else if (groupPinMappings.isNotEmpty()) {
                                    groupPinMappings.first().groupName.ifBlank { "Family Circle" }
                                } else "Family Circle",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = if (showCircleSwitcher) "▲" else "▼", color = RadarCyan, fontSize = 8.sp)
                        }
                    }

                    // Dropdown list of circles + Add Device action
                    if (showCircleSwitcher) {
                        Surface(
                            modifier = Modifier
                                .padding(top = 40.dp)
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
                                                    text = "Code: ${circle.pinCode}",
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

                                // 👥 Join a Circle (Enter Code)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCircleSwitcher = false
                                            addDeviceInitialTab = 0
                                            showAddDeviceDialog = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
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
                                        Text("👥", fontSize = 11.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Join a Circle",
                                            color = RadarCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Enter 6-character code",
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                // ➕ Invite to Current Circle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCircleSwitcher = false
                                            addDeviceInitialTab = 1
                                            showAddDeviceDialog = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryCosmic.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("➕", fontSize = 11.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Invite to This Circle",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Share Code ${activeGroupPinCode.ifBlank { AppConfig.DEFAULT_CIRCLE_INVITE_CODE }}",
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                // 🗑️ Delete from Circle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCircleSwitcher = false
                                            addDeviceInitialTab = 2
                                            showAddDeviceDialog = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🗑️", fontSize = 11.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "Delete from Circle",
                                            color = Color(0xFFFF5252),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Remove devices or members",
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── RIGHT GROUP: Map Theme, Digest & Offline Cache ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Map Theme Trigger Button
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { showMapStyleMenu = !showMapStyleMenu },
                            color = Color.White,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, SlateBorder),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = when (mapTypeMode) {
                                        "hybrid" -> "🌙"
                                        "radar" -> "🟢"
                                        else -> "🗺️"
                                    },
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Floating Map Theme Menu Dropdown
                        if (showMapStyleMenu) {
                            Surface(
                                modifier = Modifier
                                    .padding(top = 40.dp)
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

                    // Circle Digest trigger button
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { isDigestOpen = true }
                            .testTag("weekly_digest_button"),
                        color = Color.White,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, SlateBorder),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("📊", fontSize = 14.sp)
                        }
                    }

                    // OFFLINE MAP CACHE STATUS
                    var showOfflineInfoDialog by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { showOfflineInfoDialog = true }
                            .testTag("offline_cache_badge"),
                        color = Color(0xE81A2F1D),
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, Color(0xFF00C853).copy(alpha = 0.6f)),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(Color(0xFF00FF87), CircleShape)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text("✅", fontSize = 9.sp, lineHeight = 10.sp)
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
            }

            // 1c. FLOATING PAUSED DEVICES BANNER (Temporarily removed from screen until un-pause)
            val pausedMembers = members.filter {
                it.isLocationPaused || (it.id == "me" && isLocationPaused) || it.statusText.contains("Paused", ignoreCase = true)
            }
            AnimatedVisibility(
                visible = pausedMembers.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    color = Color(0xF21C1917),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ActiveAmber.copy(alpha = 0.7f)),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pausedMembers.forEach { pMember ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⏸️", fontSize = 16.sp)
                                    Column {
                                        Text(
                                            text = "${pMember.name} is Paused",
                                            color = ActiveAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Temporarily removed from screen",
                                            color = com.example.ui.theme.TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (pMember.id == "me") {
                                            onToggleLocationPaused(false)
                                        } else {
                                            onToggleMemberTracking(pMember.id)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Un-pause",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. LIVE MEMBER FOLLOW & ROUTE TRAIL HUD (Cleanly stacked below action bar without any overlap)
            AnimatedVisibility(
                visible = selectedMember != null && !pausedMembers.any { it.id == selectedMember.id },

                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                if (selectedMember != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live Follow Pill — Box with weight(1f) in RowScope, Surface fills it
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            isFollowingSelectedMember = true
                                            if (selectedMember.x != 0.0 && selectedMember.y != 0.0) {
                                                mapViewRef?.let { map ->
                                                    val targetGeo = visualCoordinates[selectedMember.id] ?: GeoPoint(selectedMember.y, selectedMember.x)
                                                    map.controller.animateTo(targetGeo)
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
                                        val transitMode = classifyTransitMode(selectedMember.speedMph, selectedMember.statusText, selectedMember.id)
                                        val transitBadge = formatTransitBadge(selectedMember.speedMph, selectedMember.statusText, selectedMember.locationSince, selectedMember.id)
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
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
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
                            }

                            // Route Trail Button — compact, no weight needed
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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text("🛤️", fontSize = 12.sp)
                                    Text(
                                        text = if (isRouteTrailEnabled) "Trail: ON" else "Trail: OFF",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Time window filter chips: Today, 7 Days, 30 Days (when trail is active)
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
            }

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
            members = members,
            onJoinGroupWithPin = onJoinGroupWithPin,
            onCreateGroupWithPin = onCreateGroupWithPin,
            onDeleteMemberFromCircle = { member -> onDeleteMember(member.id) },
            initialTab = addDeviceInitialTab,
            onDismiss = { showAddDeviceDialog = false }
        )
    }

    }
}

