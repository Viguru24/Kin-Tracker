package com.example.ui.map

import com.example.data.FamilyMember
import com.example.data.SafeZone
import com.example.data.TransitMode
import com.example.data.classifyTransitMode
import org.maplibre.android.geometry.LatLng

data class MemberVisualState(
    var currentGeo: LatLng,
    var targetGeo: LatLng,
    var speedMph: Double,
    var bearingDeg: Double,
    var lastTargetUpdateTime: Long,
    var isMoving: Boolean,
    var markerRef: Any? = null
)

data class ClusterLayoutResult(
    val adjustedCoordinates: Map<String, LatLng>,
    val clusterAnchorPoints: Map<String, LatLng>,
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
    val adjustedCoordinates = mutableMapOf<String, LatLng>()
    val clusterAnchorPoints = mutableMapOf<String, LatLng>()
    val clusters = mutableListOf<List<String>>()
    val barrierMembership = mutableMapOf<String, String>()

    val effectiveSafeZones = com.example.data.GeoUtils.withDefaultSafeZones(safeZones)
    val activeMembers = members.filter { it.x != 0.0 && it.y != 0.0 }
    val homeClusterMembers = mutableListOf<String>()
    val safeZoneClusterMembers = mutableMapOf<String, MutableList<String>>()
    val workClusterMembers = mutableListOf<String>()
    val unassignedMembers = mutableListOf<FamilyMember>()

    activeMembers.forEach { m ->
        // Hysteresis: a member already locked inside a barrier only leaves once beyond radius + exit buffer.
        // This stops GPS drift at the boundary from flipping cluster membership (which re-slots every face).
        val prevBarrier = previousBarrierMembership[m.id]

        val distToHome = if (homeLat != 0.0 && homeLng != 0.0) {
            com.example.data.GeoUtils.distanceMeters(m.y, m.x, homeLat, homeLng)
        } else Double.MAX_VALUE
        val effectiveHomeRadius = com.example.data.GeoUtils.effectiveHomeRadiusMeters(homeRadiusMeters)
        val scaledHomeExitBuffer = com.example.data.GeoUtils.homeExitBufferMeters(homeRadiusMeters, exitBufferMeters)
        val isMovingFromHome = m.speedMph >= com.example.data.AppConfig.MIN_EXIT_SPEED_MPH || statusIndicatesTravel(m)
        val homeLimit = if (prevBarrier == "home" && !isMovingFromHome) effectiveHomeRadius + scaledHomeExitBuffer else effectiveHomeRadius
        val atHomeStatus = m.statusText.contains("At Home", ignoreCase = true)
        val isHome = (homeLat != 0.0 && homeLng != 0.0) && (
            distToHome <= homeLimit ||
                (atHomeStatus && !isMovingFromHome && distToHome <= effectiveHomeRadius + scaledHomeExitBuffer + 40.0)
            )

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

        // Inside a barrier, ignore low GPS-noise speeds; only explicit travel or a real speed breaks the lock.
        val isActivelyMoving = if (inAnyBarrier) {
            m.isComingHome || (!(atHomeStatus || atSafeZoneStatus) && (statusIndicatesTravel(m) || m.speedMph >= barrierBreakoutSpeedMph))
        } else {
            m.isComingHome || m.speedMph >= 0.6 || statusIndicatesTravel(m) ||
                classifyTransitMode(m.speedMph, m.statusText, m.id) != TransitMode.STATIONARY
        }

        if (isActivelyMoving) {
            adjustedCoordinates[m.id] = LatLng(m.y, m.x)
            return@forEach
        }

        if (isHome) {
            homeClusterMembers.add(m.id)
            barrierMembership[m.id] = "home"
        } else if (matchedZone != null) {
            safeZoneClusterMembers.getOrPut(matchedZone.id) { mutableListOf() }.add(m.id)
            barrierMembership[m.id] = "zone:${matchedZone.id}"
        } else if (isInsideWork) {
            workClusterMembers.add(m.id)
            barrierMembership[m.id] = "work"
        } else {
            unassignedMembers.add(m)
        }
    }

    // A. Form Home Cluster (a lone member is pinned to the Home centre so GPS drift never moves them)
    if (homeClusterMembers.size > 1) {
        clusters.add(homeClusterMembers.sorted())
    } else if (homeClusterMembers.size == 1) {
        adjustedCoordinates[homeClusterMembers[0]] = LatLng(homeLat, homeLng)
    }

    // B. Form Safe Zone Clusters (including default Woodcote school zones)
    effectiveSafeZones.forEach { zone ->
        val zoneMembers = safeZoneClusterMembers[zone.id]
        if (zoneMembers != null) {
            if (zoneMembers.size > 1) {
                clusters.add(zoneMembers.sorted())
            } else if (zoneMembers.size == 1) {
                adjustedCoordinates[zoneMembers[0]] = LatLng(zone.latitude, zone.longitude)
            }
        }
    }

    // C. Form Work Cluster
    if (workClusterMembers.size > 1) {
        clusters.add(workClusterMembers.sorted())
    } else if (workClusterMembers.size == 1) {
        adjustedCoordinates[workClusterMembers[0]] = LatLng(workLat, workLng)
    }

    // D. Form Ad-Hoc Co-Located Clusters for members outside zones
    val visited = mutableSetOf<String>()
    val collisionThresholdPx = 40.0 * density
    val avgLatForAdhoc = if (unassignedMembers.isNotEmpty()) unassignedMembers[0].y else (if (homeLat != 0.0) homeLat else 51.33)
    val clusterThresholdMeters = maxOf(40.0, collisionThresholdPx * metersPerPixelAtZoom(avgLatForAdhoc, zoomLevel))

    for (i in unassignedMembers.indices) {
        val m1 = unassignedMembers[i]
        if (visited.contains(m1.id)) continue

        val currentCluster = mutableListOf(m1.id)
        visited.add(m1.id)

        for (j in i + 1 until unassignedMembers.size) {
            val m2 = unassignedMembers[j]
            if (visited.contains(m2.id)) continue

            val distM = com.example.data.GeoUtils.distanceMeters(m1.y, m1.x, m2.y, m2.x)
            if (distM < clusterThresholdMeters) {
                currentCluster.add(m2.id)
                visited.add(m2.id)
            }
        }

        if (currentCluster.size > 1) {
            val sortedCluster = currentCluster.sorted()
            clusters.add(sortedCluster)
            val clusterKey = "adhoc:${sortedCluster.first()}"
            sortedCluster.forEach { mId ->
                barrierMembership[mId] = clusterKey
            }
        } else {
            val mId = currentCluster[0]
            val member = activeMembers.first { it.id == mId }
            adjustedCoordinates[mId] = LatLng(member.y, member.x)
        }
    }

    // Lay out each cluster in a static, peaceful radial orbit around the barrier anchor.
    // Screen-space scaling: radius is specified in DP and scaled by metersPerPixelAtZoom
    // so faces NEVER overlap no matter how much you zoom in or zoom out!
    for (cluster in clusters) {
        val isHomeCluster = cluster.any { homeClusterMembers.contains(it) }
        val matchedZone = effectiveSafeZones.firstOrNull { zone ->
            safeZoneClusterMembers[zone.id]?.any { cluster.contains(it) } == true
        }
        val isWorkCluster = cluster.any { workClusterMembers.contains(it) }

        val anchorGeo = when {
            isHomeCluster && homeLat != 0.0 && homeLng != 0.0 -> LatLng(homeLat, homeLng)
            matchedZone != null -> LatLng(matchedZone.latitude, matchedZone.longitude)
            isWorkCluster && workLat != 0.0 && workLng != 0.0 -> LatLng(workLat, workLng)
            else -> {
                var sumLat = 0.0
                var sumLng = 0.0
                for (mId in cluster) {
                    val member = activeMembers.first { it.id == mId }
                    sumLat += member.y
                    sumLng += member.x
                }
                LatLng(sumLat / cluster.size, sumLng / cluster.size)
            }
        }

        // Screen-space radius scaled to compact 30dp face circles so spokes remain short & tight
        val radiusDp = when (cluster.size) {
            2 -> 24.0
            3 -> 25.0
            4 -> 26.0
            else -> maxOf(26.0, 17.5 / kotlin.math.sin(Math.PI / cluster.size))
        }
        val radiusPx = radiusDp * density
        val metersPerPx = metersPerPixelAtZoom(anchorGeo.latitude, zoomLevel)
        val spreadRadiusMeters = radiusPx * metersPerPx

        val cosLat = kotlin.math.cos(Math.toRadians(anchorGeo.latitude)).coerceAtLeast(0.01)

        for (idx in cluster.indices) {
            val mId = cluster[idx]
            // For 2 members: Idx 0 is angled North-West (135┬░ = 3¤Ç/4), Idx 1 is angled North-East (45┬░ = ¤Ç/4)
            // Both faces sit side by side bouncing off each other right above the place pin,
            // creating beautiful angled spoke lines down to the place icon!
            val angle = when (cluster.size) {
                2 -> if (idx == 0) (3.0 * Math.PI / 4.0) else (Math.PI / 4.0)
                3 -> when (idx) {
                    0 -> Math.PI / 2.0 // North
                    1 -> -Math.PI / 6.0 // South-East
                    else -> 7.0 * Math.PI / 6.0 // South-West
                }
                4 -> when (idx) {
                    0 -> 3.0 * Math.PI / 4.0 // North-West
                    1 -> Math.PI / 4.0 // North-East
                    2 -> -Math.PI / 4.0 // South-East
                    else -> -3.0 * Math.PI / 4.0 // South-West
                }
                else -> (Math.PI / 2.0) + (idx * 2.0 * Math.PI / cluster.size)
            }

            val deltaLat = (spreadRadiusMeters * kotlin.math.sin(angle)) / 111139.0
            val deltaLng = (spreadRadiusMeters * kotlin.math.cos(angle)) / (111139.0 * cosLat)

            val orbitGeo = LatLng(anchorGeo.latitude + deltaLat, anchorGeo.longitude + deltaLng)
            adjustedCoordinates[mId] = orbitGeo
            clusterAnchorPoints[mId] = anchorGeo
        }
    }

    // Universal screen-space deconfliction pass: guarantees that NO TWO compact 30dp face circles overlap on screen.
    val minMarkerSeparationPx = 33.0 * density // Minimum 33dp center-to-center separation for 30dp bubbles
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
                        // Exactly co-located (e.g. sharing car or identical GPS): spread horizontally (West/East)
                        val shiftLng = neededShiftM / (111139.0 * cosLat)
                        adjustedCoordinates[idA] = LatLng(posA.latitude, posA.longitude - shiftLng)
                        adjustedCoordinates[idB] = LatLng(posB.latitude, posB.longitude + shiftLng)
                    } else {
                        // Push apart along their existing separation axis
                        val dLat = posB.latitude - posA.latitude
                        val dLng = (posB.longitude - posA.longitude) * cosLat
                        val angle = kotlin.math.atan2(dLat, dLng)
                        val shiftLat = (neededShiftM * kotlin.math.sin(angle)) / 111139.0
                        val shiftLng = (neededShiftM * kotlin.math.cos(angle)) / (111139.0 * cosLat)

                        adjustedCoordinates[idA] = LatLng(posA.latitude - shiftLat, posA.longitude - shiftLng)
                        adjustedCoordinates[idB] = LatLng(posB.latitude + shiftLat, posB.longitude + shiftLng)
                    }
                }
            }
        }
    }

    return ClusterLayoutResult(adjustedCoordinates, clusterAnchorPoints, clusters, barrierMembership)
}
