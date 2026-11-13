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

    @Test
    fun testConfiguredSmallHomeRadiusDoesNotSwallowNearbyChurch() {
        val homeLat = 51.330000
        val homeLng = -0.118000
        val configuredRadiusMeters = 60.0

        // Inside 45m stays at Home
        assertTrue(
            "Inside 60m radius must count as Home",
            GeoUtils.isWithinHomeGeofence(
                lat = homeLat + 0.00040, // ~44m north
                lng = homeLng,
                homeLat = homeLat,
                homeLng = homeLng,
                homeRadiusMeters = configuredRadiusMeters
            )
        )

        // Indoor stationary jitter (~85m) stays inside Home hysteresis (60m + 36m = 96m)
        assertTrue(
            "Stationary indoor GPS drift inside scaled buffer should retain Home lock",
            GeoUtils.isWithinHomeGeofence(
                lat = homeLat + 0.00076, // ~84m north
                lng = homeLng,
                homeLat = homeLat,
                homeLng = homeLng,
                homeRadiusMeters = configuredRadiusMeters,
                wasAlreadyAtHome = true,
                speedMph = 0.0
            )
        )

        // Stopping at a church ~145m up the road must NOT snap back to Home when Home radius is 60m
        assertFalse(
            "Stationary stop at nearby church 145m away must not snap to 60m Home",
            GeoUtils.isWithinHomeGeofence(
                lat = homeLat + 0.00131, // ~145m north
                lng = homeLng,
                homeLat = homeLat,
                homeLng = homeLng,
                homeRadiusMeters = configuredRadiusMeters,
                wasAlreadyAtHome = false,
                speedMph = 0.0
            )
        )

        // Moving past 60m boundary (> 1.8 mph) must exit Home cleanly without 200m lag
        assertFalse(
            "Moving past 60m radius must exit Home immediately",
            GeoUtils.isWithinHomeGeofence(
                lat = homeLat + 0.00070, // ~78m north
                lng = homeLng,
                homeLat = homeLat,
                homeLng = homeLng,
                homeRadiusMeters = configuredRadiusMeters,
                wasAlreadyAtHome = true,
                speedMph = 10.0
            )
        )
    }

    @Test
    fun testLocationArbiterPrefersRecentGpsOverCoarseOrStaleNetwork() {
        val t0 = 1_794_600_000_000L
        val churchGps = GeoUtils.LocationSampleMeta(
            provider = "gps",
            accuracyMeters = 10f,
            hasAccuracy = true,
            timestampMs = t0,
            latitude = 51.33135,
            longitude = -0.11800,
            elapsedNanos = 100_000_000_000L
        )
        val homeCoarseNetwork = GeoUtils.LocationSampleMeta(
            provider = "network",
            accuracyMeters = 90f,
            hasAccuracy = true,
            timestampMs = t0 + 2_000L,
            latitude = 51.33000,
            longitude = -0.11800,
            elapsedNanos = 102_000_000_000L
        )
        val staleNetwork = homeCoarseNetwork.copy(
            accuracyMeters = 25f,
            timestampMs = t0 - 5_000L,
            elapsedNanos = 95_000_000_000L
        )
        val freshIndoorNetworkAfterGpsSilence = GeoUtils.LocationSampleMeta(
            provider = "network",
            accuracyMeters = 45f,
            hasAccuracy = true,
            timestampMs = t0 + 180_000L,
            latitude = 51.33135,
            longitude = -0.11800,
            elapsedNanos = 280_000_000_000L
        )

        assertFalse(
            "Coarse NETWORK_PROVIDER fix must not overwrite recent GPS fix",
            GeoUtils.shouldAcceptLocationUpdate(homeCoarseNetwork, churchGps)
        )
        assertFalse(
            "Out-of-order older NETWORK_PROVIDER fix must be rejected",
            GeoUtils.shouldAcceptLocationUpdate(staleNetwork, churchGps)
        )
        assertTrue(
            "Crisp NETWORK_PROVIDER fix should be allowed after GPS has been silent indoors for 3 minutes",
            GeoUtils.shouldAcceptLocationUpdate(freshIndoorNetworkAfterGpsSilence, churchGps)
        )

        val initialMacroCell = homeCoarseNetwork.copy(accuracyMeters = 250f)
        assertFalse(
            "Coarse 250m initial cellular fix must be rejected",
            GeoUtils.shouldAcceptLocationUpdate(initialMacroCell, null)
        )
    }
}
