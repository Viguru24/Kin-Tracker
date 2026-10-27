package com.example.data

import kotlin.math.*

/**
 * Single source of truth for geographical mathematics, distance calculations,
 * geofencing evaluation, and spatial utility functions across the Kin Tracker application.
 */
object GeoUtils {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val KM_PER_DEGREE_LAT = 111.0

    /**
     * Calculates the exact Haversine distance between two GPS coordinates in kilometers.
     */
    fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0 || lat2 == 0.0 && lon2 == 0.0) return 0.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Fast equirectangular approximation distance in kilometers (ideal for tight local loop checks).
     */
    fun fastDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0 || lat2 == 0.0 && lon2 == 0.0) return 0.0
        val xDistKm = (lon2 - lon1) * KM_PER_DEGREE_LAT * cos(Math.toRadians((lat1 + lat2) / 2.0))
        val yDistKm = (lat2 - lat1) * KM_PER_DEGREE_LAT
        return hypot(xDistKm, yDistKm)
    }

    /**
     * Distance in meters between two GPS coordinates.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return fastDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    /**
     * Evaluates if a given point is within a circular geofence boundary (in meters).
     */
    fun isInsideGeofence(pointLat: Double, pointLng: Double, centerLat: Double, centerLng: Double, radiusMeters: Double): Boolean {
        if (pointLat == 0.0 && pointLng == 0.0 || centerLat == 0.0 && centerLng == 0.0) return false
        return distanceMeters(pointLat, pointLng, centerLat, centerLng) <= radiusMeters
    }

    /**
     * Formats distance compactly (e.g. "350 m" or "1.4 km").
     */
    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
        } else {
            "${meters.toInt()} m"
        }
    }

    /**
     * Strict phone number sanitizer for international dialing (E.164-safe format).
     */
    fun sanitizePhoneNumber(raw: String): String {
        val digitsOnly = raw.replace(Regex("[^0-9+]"), "")
        return if (digitsOnly.startsWith("+")) digitsOnly else digitsOnly.replace("+", "")
    }

    val DEFAULT_WOODCOTE_HIGH_SCHOOL = SafeZone(
        id = "zone_woodcote_high_school",
        name = "Woodcote High School",
        latitude = 51.3301600,
        longitude = -0.1422829,
        radiusMeters = 220.0,
        iconName = "school"
    )

    /**
     * Computes a realistic effective radius for a SafeZone so large school campuses
     * and indoor Wi-Fi/cellular triangulation drift do not cause boundary bounce.
     */
    fun effectiveSafeZoneRadiusMeters(zone: SafeZone): Double {
        val isWoodcoteHigh = zone.id == "zone_woodcote_high_school" ||
            (zone.name.contains("Woodcote", ignoreCase = true) && zone.name.contains("High", ignoreCase = true)) ||
            zone.name.contains("Woodcoat", ignoreCase = true)
        val isSchool = zone.iconName.equals("school", ignoreCase = true) ||
            zone.name.contains("school", ignoreCase = true) ||
            zone.name.contains("college", ignoreCase = true) ||
            zone.name.contains("academy", ignoreCase = true)

        return when {
            isWoodcoteHigh -> maxOf(zone.radiusMeters, 220.0)
            isSchool -> maxOf(zone.radiusMeters, 180.0)
            else -> maxOf(zone.radiusMeters, 100.0)
        }
    }

    /**
     * Merges user-configured SafeZones with the default family school zones, filtering out
     * rogue/stale school zones (e.g. Byron or duplicate primary school), and upgrades
     * any legacy narrow school radii to their full campus effective radii.
     */
    fun withDefaultSafeZones(customZones: List<SafeZone>): List<SafeZone> {
        val result = customZones.filterNot { zone ->
            zone.id == "zone_oasis_academy_byron" ||
                zone.id == "zone_woodcote_school" ||
                zone.name.contains("Byron", ignoreCase = true) ||
                (zone.name.contains("Woodcote", ignoreCase = true) && zone.name.contains("Primary", ignoreCase = true))
        }.map { zone ->
            // If user has a legacy Woodcote High pin stuck at Byron Ave (51.3205711), auto-correct to real Woodcote High
            val correctedZone = if (zone.id == "zone_woodcote_high_school" && Math.abs(zone.latitude - 51.3205711) < 0.001) {
                zone.copy(latitude = DEFAULT_WOODCOTE_HIGH_SCHOOL.latitude, longitude = DEFAULT_WOODCOTE_HIGH_SCHOOL.longitude)
            } else zone
            correctedZone.copy(radiusMeters = effectiveSafeZoneRadiusMeters(correctedZone))
        }.toMutableList()

        val hasHighSchool = result.any {
            it.id == "zone_woodcote_high_school" ||
                (it.name.contains("Woodcote", ignoreCase = true) && it.name.contains("High", ignoreCase = true)) ||
                it.name.contains("Woodcoat", ignoreCase = true)
        }
        if (!hasHighSchool) {
            result.add(DEFAULT_WOODCOTE_HIGH_SCHOOL)
        }

        return result
    }

    /**
     * Evaluates whether a coordinate is inside any SafeZone, applying exit hysteresis when
     * the member's current status or previous barrier indicates they are already at that zone.
     * When stationary (e.g. phone in a drawer at school), hysteresis comfortably absorbs
     * indoor cellular tower / Wi-Fi multipath jitter so the person stays locked.
     */
    fun matchSafeZone(
        lat: Double,
        lng: Double,
        customZones: List<SafeZone>,
        currentStatusText: String = "",
        previousZoneId: String? = null,
        exitBufferMeters: Double = 60.0,
        speedMph: Double = 0.0
    ): SafeZone? {
        if (lat == 0.0 && lng == 0.0) return null
        val zones = withDefaultSafeZones(customZones)
        val matchingZones = zones.filter { zone ->
            val baseRadius = effectiveSafeZoneRadiusMeters(zone)
            val alreadyAtThisZone = previousZoneId == zone.id ||
                previousZoneId == "zone:${zone.id}" ||
                currentStatusText.contains("At ${zone.name}", ignoreCase = true)
            val isSchool = zone.iconName.equals("school", ignoreCase = true) ||
                zone.name.contains("school", ignoreCase = true) ||
                zone.name.contains("college", ignoreCase = true) ||
                zone.name.contains("academy", ignoreCase = true)

            // When a person is already at a school and stationary (phone in office drawer),
            // absorb indoor cell mast jumps up to 200m beyond the campus edge.
            val effectiveBuffer = if (alreadyAtThisZone && isSchool && speedMph < 1.8) {
                maxOf(exitBufferMeters, 180.0)
            } else if (alreadyAtThisZone) {
                exitBufferMeters
            } else {
                15.0
            }

            val limit = baseRadius + effectiveBuffer
            distanceMeters(lat, lng, zone.latitude, zone.longitude) <= limit
        }

        if (matchingZones.isEmpty()) return null

        // Priority 1: If already locked into an active zone, retain it for stability
        val existingActiveZone = matchingZones.firstOrNull { zone ->
            previousZoneId == zone.id ||
                previousZoneId == "zone:${zone.id}" ||
                currentStatusText.contains("At ${zone.name}", ignoreCase = true)
        }
        if (existingActiveZone != null) return existingActiveZone

        // Priority 2: School campus boundaries always take precedence over overlapping residential safe zones
        val schoolZone = matchingZones.firstOrNull { zone ->
            zone.iconName.equals("school", ignoreCase = true) ||
                zone.name.contains("school", ignoreCase = true) ||
                zone.name.contains("college", ignoreCase = true) ||
                zone.name.contains("academy", ignoreCase = true)
        }
        if (schoolZone != null) return schoolZone

        // Priority 3: Fall back to closest center pin
        return matchingZones.minByOrNull { zone ->
            distanceMeters(lat, lng, zone.latitude, zone.longitude)
        }
    }
}
