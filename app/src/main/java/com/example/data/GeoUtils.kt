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
}
