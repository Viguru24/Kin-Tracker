package com.example

import com.example.data.AppConfig
import com.example.data.GeoUtils
import org.junit.Assert.*
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun testHaversineDistanceAccuracy() {
        // Known coordinates: London Big Ben to Tower Bridge (~3.2 km / 3200m)
        val bigBenLat = 51.5007
        val bigBenLng = -0.1246
        val towerBridgeLat = 51.5055
        val towerBridgeLng = -0.0754

        val distanceMeters = GeoUtils.distanceMeters(bigBenLat, bigBenLng, towerBridgeLat, towerBridgeLng)
        
        // Assert distance is within expected range (3.4 km +/- 300m)
        assertEquals(3450.0, distanceMeters, 300.0)
    }

    @Test
    fun testSameLocationZeroDistance() {
        val lat = 51.332308
        val lng = -0.117188

        val distance = GeoUtils.distanceMeters(lat, lng, lat, lng)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun testGardenBoundaryExitThreshold() {
        // Exit hysteresis buffer must be >= 35m to prevent false alarms from indoor GPS jitter
        assertTrue("Exit buffer must be at least 35m", AppConfig.EXIT_HYSTERESIS_METERS >= 35.0)
        
        val homeLat = AppConfig.DEFAULT_HOME_LAT
        val homeLng = AppConfig.DEFAULT_HOME_LNG
        val homeRadius = AppConfig.DEFAULT_HOME_RADIUS_METERS

        // Simulate walking 35m into the garden (should still be safely inside home geofence)
        val isInsideHome = GeoUtils.isInsideGeofence(homeLat + 0.0003, homeLng, homeLat, homeLng, homeRadius + AppConfig.EXIT_HYSTERESIS_METERS)
        assertTrue("Garden walk within hysteresis buffer must stay inside home", isInsideHome)
    }

    @Test
    fun testSafeZoneGeofenceDetection() {
        val centerLat = 51.332308
        val centerLng = -0.117188
        val radiusMeters = 65.0

        // Point inside zone (20 meters away)
        val insideLat = centerLat + 0.00018
        val insideLng = centerLng
        val distInside = GeoUtils.distanceMeters(insideLat, insideLng, centerLat, centerLng)
        assertTrue(distInside <= radiusMeters)
        assertTrue(GeoUtils.isInsideGeofence(insideLat, insideLng, centerLat, centerLng, radiusMeters))

        // Point outside zone (500 meters away)
        val outsideLat = centerLat + 0.005
        val outsideLng = centerLng
        val distOutside = GeoUtils.distanceMeters(outsideLat, outsideLng, centerLat, centerLng)
        assertTrue(distOutside > radiusMeters)
        assertFalse(GeoUtils.isInsideGeofence(outsideLat, outsideLng, centerLat, centerLng, radiusMeters))
    }
}
