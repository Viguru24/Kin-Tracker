package com.example.ui.map

import com.example.data.FamilyMember
import com.example.data.SafeZone
import com.example.data.TransitMode
import com.example.data.classifyTransitMode
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

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
    val clusters: List<List<String>>,
    /** memberId -> barrier key ("home", "zone:<id>", "work") for members locked stationary inside a barrier. */
    val barrierMembership: Map<String, String> = emptyMap()
)

/** True when the status text explicitly describes travel (not inferred from noisy GPS speed). */
fun statusIndicatesTravel(m: FamilyMember): Boolean {
    if (classifyTransitMode(0.0, m.statusText, m.id) != TransitMode.STATIONARY) return true
    val s = m.statusText
    return s.contains("walk", ignoreCase = true) ||
        s.contains("moving", ignoreCase = true) ||
        s.contains("driving", ignoreCase = true) ||
        s.contains("bike", ignoreCase = true) ||
        s.contains("transit", ignoreCase = true) ||
        s.contains("commute", ignoreCase = true)
}

/**
 * Calculates ground resolution (meters per screen pixel) at a given latitude and map zoom level.
 * Used for dynamic, zoom-adaptive marker deconfliction so faces NEVER overlap at ANY zoom level.
 */
fun metersPerPixelAtZoom(latitude: Double, zoom: Double): Double {
    val cosLat = kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
    return (156543.03392 * cosLat) / Math.pow(2.0, zoom.coerceIn(2.0, 21.0))
}

/**
 * Boosts saturation and brightness of member avatar colors so leader spoke lines
 * remain brilliantly vivid and crystal clear across both dark canvas and street map tiles.
 */
fun getVibrantSpokeColor(hexColor: String): Int {
    return try {
        val parsed = android.graphics.Color.parseColor(hexColor)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed, hsv)
        hsv[1] = maxOf(hsv[1], 0.70f)
        hsv[2] = maxOf(hsv[2], 0.92f)
        android.graphics.Color.HSVToColor(hsv)
    } catch (_: Exception) {
        android.graphics.Color.parseColor("#00E5FF")
    }
}

fun computeClusterLayout(
    members: List<FamilyMember>,
    safeZones: List<SafeZone>,
    homeLat: Double,
    homeLng: Double,
    homeRadiusMeters: Double,
    isWorkCalibrated: Boolean,
    workLat: Double,
    workLng: Double,
    workRadiusMeters: Double,
    previousBarrierMembership: Map<String, String> = emptyMap(),
    exitBufferMeters: Double = 60.0,
    barrierBreakoutSpeedMph: Double = 4.0,
    zoomLevel: Double = 15.5,
    density: Float = 3.0f
): ClusterLayoutResult {
    val adjustedCoordinates = mutableMapOf<String, GeoPoint>()
    val clusterAnchorPoints = mutableMapOf<String, GeoPoint>()
    val clusters = mutableListOf<List<String>>()
    val barrierMembership = mutableMapOf<String, String>()

    val effectiveSafeZones = com.example.data.GeoUtils.withDefaultSafeZones(safeZones)
    val activeMembers = members.filter { it.x != 0.0 && it.y != 0.0 }
    val homeClusterMembers = mutableListOf<String>()
    val safeZoneClusterMembers = mutableMapOf<String, MutableList<String>>()
    val workClusterMembers = mutableListOf<String>()
    val unassignedMembers = mutableListOf<FamilyMember>()

    activeMembers.forEach { m ->
        val prevBarrier = previousBarrierMembership[m.id]

        val distToHome = if (homeLat != 0.0 && homeLng != 0.0) {
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, homeLat, homeLng)
        } else Double.MAX_VALUE
        val effectiveHomeRadius = maxOf(homeRadiusMeters, 140.0)
        val homeLimit = if (prevBarrier == "home") effectiveHomeRadius + exitBufferMeters else effectiveHomeRadius
        val atHomeStatus = m.statusText.contains("At Home", ignoreCase = true)
        val isHome = (homeLat != 0.0 && homeLng != 0.0) && (distToHome <= homeLimit || atHomeStatus)

        val matchedZone = effectiveSafeZones.filter { zone ->
            val effRadius = com.example.data.GeoUtils.effectiveSafeZoneRadiusMeters(zone)
            val atThisZoneStatus = m.statusText.contains("At ${zone.name}", ignoreCase = true)
            val limit = if (prevBarrier == "zone:${zone.id}" || atThisZoneStatus) effRadius + exitBufferMeters else effRadius + 15.0
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, zone.latitude, zone.longitude) <= limit || atThisZoneStatus
        }.minByOrNull { zone ->
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, zone.latitude, zone.longitude)
        }
        val atSafeZoneStatus = matchedZone != null && m.statusText.contains("At ${matchedZone.name}", ignoreCase = true)

        val workLimit = if (prevBarrier == "work") workRadiusMeters + exitBufferMeters else workRadiusMeters
        val isInsideWork = isWorkCalibrated && workLat != 0.0 && workLng != 0.0 &&
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, workLat, workLng) <= workLimit

        val inAnyBarrier = isHome || matchedZone != null || isInsideWork

        val isActivelyMoving = if (inAnyBarrier) {
            m.speedMph >= barrierBreakoutSpeedMph || statusIndicatesTravel(m)
        } else {
            m.speedMph >= 1.5 || statusIndicatesTravel(m)
        }

        if (isHome && !isActivelyMoving) {
            homeClusterMembers.add(m.id)
            barrierMembership[m.id] = "home"
        } else if (matchedZone != null && !isActivelyMoving) {
            safeZoneClusterMembers.getOrPut(matchedZone.id) { mutableListOf() }.add(m.id)
            barrierMembership[m.id] = "zone:${matchedZone.id}"
        } else if (isInsideWork && !isActivelyMoving) {
            workClusterMembers.add(m.id)
            barrierMembership[m.id] = "work"
        } else {
            unassignedMembers.add(m)
        }
    }

    // Helper: place members in ring around center
    fun layoutRadialCluster(center: GeoPoint, memberIds: List<String>, clusterKey: String, baseRadiusMeters: Double) {
        val count = memberIds.size
        if (count == 0) return
        clusterAnchorPoints[clusterKey] = center
        clusters.add(memberIds)

        val metersPerPx = metersPerPixelAtZoom(center.latitude, zoomLevel)
        val minRingPx = 48.0 * density
        val dynamicRadiusMeters = maxOf(baseRadiusMeters, minRingPx * metersPerPx)
        val cosLat = kotlin.math.cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01)

        val sorted = memberIds.sorted()
        sorted.forEachIndexed { i, id ->
            val angle = (2.0 * Math.PI * i) / count - (Math.PI / 2.0)
            val dLat = (dynamicRadiusMeters * kotlin.math.sin(angle)) / 111139.0
            val dLng = (dynamicRadiusMeters * kotlin.math.cos(angle)) / (111139.0 * cosLat)
            adjustedCoordinates[id] = GeoPoint(center.latitude + dLat, center.longitude + dLng)
        }
    }

    if (homeClusterMembers.isNotEmpty()) {
        layoutRadialCluster(GeoPoint(homeLat, homeLng), homeClusterMembers, "home", maxOf(homeRadiusMeters * 0.55, 60.0))
    }

    safeZoneClusterMembers.forEach { (zoneId, membersInZone) ->
        val zone = effectiveSafeZones.first { it.id == zoneId }
        val effRadius = com.example.data.GeoUtils.effectiveSafeZoneRadiusMeters(zone)
        layoutRadialCluster(GeoPoint(zone.latitude, zone.longitude), membersInZone, "zone:$zoneId", maxOf(effRadius * 0.55, 50.0))
    }

    if (workClusterMembers.isNotEmpty()) {
        layoutRadialCluster(GeoPoint(workLat, workLng), workClusterMembers, "work", maxOf(workRadiusMeters * 0.55, 50.0))
    }

    val assignedProximity = mutableSetOf<String>()
    val proximityThresholdMeters = 35.0

    for (i in 0 until unassignedMembers.size) {
        val mA = unassignedMembers[i]
        if (assignedProximity.contains(mA.id)) continue

        val localCluster = mutableListOf(mA)
        for (j in i + 1 until unassignedMembers.size) {
            val mB = unassignedMembers[j]
            if (assignedProximity.contains(mB.id)) continue
            val dist = com.example.data.GeoUtils.distanceMeters(mA.y, mA.x, mB.y, mB.x)
            if (dist <= proximityThresholdMeters) {
                localCluster.add(mB)
            }
        }

        if (localCluster.size > 1) {
            val avgLat = localCluster.map { it.y }.average()
            val avgLng = localCluster.map { it.x }.average()
            val center = GeoPoint(avgLat, avgLng)
            val clusterKey = "peer_${mA.id}"
            localCluster.forEach { assignedProximity.add(it.id) }
            layoutRadialCluster(center, localCluster.map { it.id }, clusterKey, 28.0)
        } else {
            adjustedCoordinates[mA.id] = GeoPoint(mA.y, mA.x)
        }
    }

    val minMarkerSeparationPx = 54.0 * density
    val activeIds = activeMembers.map { it.id }
    repeat(2) {
        for (i in 0 until activeIds.size) {
            val idA = activeIds[i]
            val posA = adjustedCoordinates[idA] ?: continue
            for (j in i + 1 until activeIds.size) {
                val idB = activeIds[j]
                val posB = adjustedCoordinates[idB] ?: continue

                val avgLat = (posA.latitude + posB.latitude) / 2.0
                val metersPerPx = metersPerPixelAtZoom(avgLat, zoomLevel)
                val minSeparationMeters = minMarkerSeparationPx * metersPerPx
                val distM = com.example.data.GeoUtils.distanceMeters(posA.latitude, posA.longitude, posB.latitude, posB.longitude)

                if (distM < minSeparationMeters) {
                    val neededShiftM = (minSeparationMeters - distM) / 2.0 + 1.0
                    val cosLat = kotlin.math.cos(Math.toRadians(avgLat)).coerceAtLeast(0.01)

                    if (distM < 0.1) {
                        val shiftLng = neededShiftM / (111139.0 * cosLat)
                        adjustedCoordinates[idA] = GeoPoint(posA.latitude, posA.longitude - shiftLng)
                        adjustedCoordinates[idB] = GeoPoint(posB.latitude, posB.longitude + shiftLng)
                    } else {
                        val dLat = posB.latitude - posA.latitude
                        val dLng = (posB.longitude - posA.longitude) * cosLat
                        val angle = kotlin.math.atan2(dLat, dLng)
                        val shiftLat = (neededShiftM * kotlin.math.sin(angle)) / 111139.0
                        val shiftLng = (neededShiftM * kotlin.math.cos(angle)) / (111139.0 * cosLat)

                        adjustedCoordinates[idA] = GeoPoint(posA.latitude - shiftLat, posA.longitude - shiftLng)
                        adjustedCoordinates[idB] = GeoPoint(posB.latitude + shiftLat, posB.longitude + shiftLng)
                    }
                }
            }
        }
    }

    return ClusterLayoutResult(adjustedCoordinates, clusterAnchorPoints, clusters, barrierMembership)
}
