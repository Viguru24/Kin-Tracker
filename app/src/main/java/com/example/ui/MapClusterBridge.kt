package com.example.ui

import com.example.data.FamilyMember
import com.example.data.SafeZone

typealias ClusterLayoutResult = com.example.ui.map.ClusterLayoutResult
typealias MemberVisualState = com.example.ui.map.MemberVisualState

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
): com.example.ui.map.ClusterLayoutResult = com.example.ui.map.computeClusterLayout(
    members,
    safeZones,
    homeLat,
    homeLng,
    homeRadiusMeters,
    isWorkCalibrated,
    workLat,
    workLng,
    workRadiusMeters,
    previousBarrierMembership,
    exitBufferMeters,
    barrierBreakoutSpeedMph,
    zoomLevel,
    density
)

fun metersPerPixelAtZoom(latitude: Double, zoom: Double): Double =
    com.example.ui.map.metersPerPixelAtZoom(latitude, zoom)

fun getVibrantSpokeColor(hexColor: String): Int =
    com.example.ui.map.getVibrantSpokeColor(hexColor)
