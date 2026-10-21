package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FamilyRepository
import com.example.data.FamilyMember
import com.example.data.SafeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeZoneGeofenceTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository

    @Before
    fun setup() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FamilyRepository(database.familyDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testAddAndRemoveSafeZones() = runBlocking {
        val schoolZone = SafeZone(
            id = "zone_school_1",
            name = "High School",
            latitude = 51.340000,
            longitude = -0.120000,
            radiusMeters = 100.0,
            iconName = "school"
        )

        // Add Safe Zone
        repository.insertSafeZone(schoolZone)
        val zones = repository.safeZones.first { it.isNotEmpty() }
        assertTrue("Safe zones list must contain added zone", zones.any { it.id == schoolZone.id })

        // Remove Safe Zone
        repository.deleteSafeZone(schoolZone)
        val zonesAfterRemoval = repository.safeZones.first { it.isEmpty() }
        assertFalse("Safe zones list must not contain removed zone", zonesAfterRemoval.any { it.id == schoolZone.id })
    }

    @Test
    fun testEloiseInOfficeDrawerAtOasisAcademyByronStaysLockedAcrossCellTowerJitter() {
        // Eloise's actual office drawer telemetry at Oasis Academy Byron (53m from center pin)
        val eloiseDrawer = com.example.data.FamilyMember(
            id = "device_eloise_a9e3d7",
            name = "Eloise",
            avatarColorHex = "#FF4081",
            x = -0.1288279,
            y = 51.3146758,
            batteryPercentage = 63,
            speedMph = 0.0,
            statusText = "Live GPS tracking (1.86 km away)"
        )

        val matched = com.example.data.GeoUtils.matchSafeZone(
            lat = eloiseDrawer.y,
            lng = eloiseDrawer.x,
            customZones = emptyList(),
            currentStatusText = eloiseDrawer.statusText,
            speedMph = 0.0
        )
        assertNotNull("Eloise's office drawer position must match Oasis Academy Byron safe zone", matched)
        assertEquals("Oasis Academy Byron", matched!!.name)

        // First layout pass: Eloise must be locked to Oasis Academy Byron center pin
        val layout1 = com.example.ui.computeClusterLayout(
            members = listOf(eloiseDrawer),
            safeZones = emptyList(),
            homeLat = 51.3300000,
            homeLng = -0.1180000,
            homeRadiusMeters = 140.0,
            isWorkCalibrated = false,
            workLat = 0.0,
            workLng = 0.0,
            workRadiusMeters = 75.0
        )

        assertEquals(
            "zone:zone_oasis_academy_byron",
            layout1.barrierMembership["device_eloise_a9e3d7"]
        )
        val pinnedPos1 = layout1.adjustedCoordinates["device_eloise_a9e3d7"]!!
        assertEquals(51.3141000, pinnedPos1.latitude, 0.00001)
        assertEquals(-0.1288000, pinnedPos1.longitude, 0.00001)

        // Second layout pass: simulate cell tower jitter to Rutherwick Rise / perimeter gate (180m away) while phone sits stationary in drawer
        val eloiseDrifted = eloiseDrawer.copy(
            y = 51.3156571,
            x = -0.1299272,
            statusText = "At Oasis Academy Byron",
            speedMph = 0.0 // Stationary in drawer
        )

        val matchedDrifted = com.example.data.GeoUtils.matchSafeZone(
            lat = eloiseDrifted.y,
            lng = eloiseDrifted.x,
            customZones = emptyList(),
            currentStatusText = eloiseDrifted.statusText,
            previousZoneId = "zone_oasis_academy_byron",
            speedMph = 0.0
        )
        assertNotNull("Eloise must remain matched to Oasis Academy Byron across cell tower jitter", matchedDrifted)
        assertEquals("Oasis Academy Byron", matchedDrifted!!.name)

        val layout2 = com.example.ui.computeClusterLayout(
            members = listOf(eloiseDrifted),
            safeZones = emptyList(),
            homeLat = 51.3300000,
            homeLng = -0.1180000,
            homeRadiusMeters = 140.0,
            isWorkCalibrated = false,
            workLat = 0.0,
            workLng = 0.0,
            workRadiusMeters = 75.0,
            previousBarrierMembership = layout1.barrierMembership
        )

        assertEquals(
            "Eloise must remain locked inside Oasis Academy Byron barrier across indoor drawer drift",
            "zone:zone_oasis_academy_byron",
            layout2.barrierMembership["device_eloise_a9e3d7"]
        )
        val pinnedPos2 = layout2.adjustedCoordinates["device_eloise_a9e3d7"]!!
        assertEquals(51.3141000, pinnedPos2.latitude, 0.00001)
        assertEquals(-0.1288000, pinnedPos2.longitude, 0.00001)
    }

    @Test
    fun testRealWoodcoteHighSchoolCampusMatching() {
        val studentAtHighSchool = com.example.data.FamilyMember(
            id = "device_isabel_4ec9dd",
            name = "Isabel",
            avatarColorHex = "#42A5F5",
            x = -0.1420000,
            y = 51.3302000,
            batteryPercentage = 80,
            speedMph = 0.0,
            statusText = "Live GPS tracking (2.1 km away)"
        )

        val matched = com.example.data.GeoUtils.matchSafeZone(
            lat = studentAtHighSchool.y,
            lng = studentAtHighSchool.x,
            customZones = emptyList(),
            currentStatusText = studentAtHighSchool.statusText,
            speedMph = 0.0
        )
        assertNotNull("Student on campus must match Woodcote High School safe zone", matched)
        assertEquals("Woodcote High School", matched!!.name)
    }

    @Test
    fun testOverlappingResidentialSafeZoneDoesNotOverrideSchool() {
        // Residential safe zone right next to or overlapping the school campus
        val residentialZone = SafeZone(
            id = "zone_residential_neighbor",
            name = "Neighbor House",
            latitude = 51.3142000, // Right next to school
            longitude = -0.1287000,
            radiusMeters = 100.0,
            iconName = "home"
        )

        // Eloise at school campus
        val matched = com.example.data.GeoUtils.matchSafeZone(
            lat = 51.3146758,
            lng = -0.1288279,
            customZones = listOf(residentialZone),
            currentStatusText = "Live GPS tracking (1.86 km away)",
            speedMph = 0.0
        )

        assertNotNull("Must match a safe zone", matched)
        assertEquals(
            "School campus must take precedence over overlapping residential zone",
            "Oasis Academy Byron",
            matched!!.name
        )
    }

    @Test
    fun testFacesNeverOverlapAcrossZoomLevels() {
        val dadAtHome = FamilyMember(
            id = "me",
            name = "Louis (Dad)",
            avatarColorHex = "#42A5F5",
            y = 51.3300000,
            x = -0.1180000,
            statusText = "At Home",
            speedMph = 0.0
        )
        val eloiseAtHome = FamilyMember(
            id = "device_eloise_a9e3d7",
            name = "Eloise",
            avatarColorHex = "#EC407A",
            y = 51.3300100,
            x = -0.1180050,
            statusText = "At Home",
            speedMph = 0.0
        )

        val zoomLevels = listOf(11.0, 13.0, 15.5, 18.0, 20.0)
        val density = 3.0f

        for (zoom in zoomLevels) {
            val layout = com.example.ui.computeClusterLayout(
                members = listOf(dadAtHome, eloiseAtHome),
                safeZones = emptyList(),
                homeLat = 51.3300000,
                homeLng = -0.1180000,
                homeRadiusMeters = 140.0,
                isWorkCalibrated = false,
                workLat = 0.0,
                workLng = 0.0,
                workRadiusMeters = 75.0,
                zoomLevel = zoom,
                density = density
            )

            val posDad = layout.adjustedCoordinates["me"]
            val posEloise = layout.adjustedCoordinates["device_eloise_a9e3d7"]

            assertNotNull("Dad must have adjusted coordinate at zoom $zoom", posDad)
            assertNotNull("Eloise must have adjusted coordinate at zoom $zoom", posEloise)

            val distMeters = com.example.data.GeoUtils.distanceMeters(
                posDad!!.latitude, posDad.longitude,
                posEloise!!.latitude, posEloise.longitude
            )
            val metersPerPx = com.example.ui.metersPerPixelAtZoom((posDad.latitude + posEloise.latitude) / 2.0, zoom)
            val screenPixels = distMeters / metersPerPx
            val screenDp = screenPixels / density

            assertTrue(
                "At zoom $zoom, screen separation ($screenDp dp) must be at least 64dp to prevent face overlapping (bubble width is ~54dp)",
                screenDp >= 64.0
            )

            // Verify they flank Home horizontally (one West, one East)
            val dadIsWest = posDad.longitude < -0.1180000
            val eloiseIsEast = posEloise.longitude > -0.1180000
            val dadIsEast = posDad.longitude > -0.1180000
            val eloiseIsWest = posEloise.longitude < -0.1180000
            assertTrue(
                "Faces must flank the home icon horizontally without vertical clipping at zoom $zoom",
                (dadIsWest && eloiseIsEast) || (dadIsEast && eloiseIsWest)
            )
        }
    }

    @Test
    fun testAdhocAndMovingFacesNeverOverlapWhenSharingLocation() {
        // Dad and Eloise in the car or walking down the high street (sharing exact GPS coordinates)
        val dadMoving = FamilyMember(
            id = "me",
            name = "Louis (Dad)",
            avatarColorHex = "#42A5F5",
            y = 51.3500000,
            x = -0.1000000,
            statusText = "Driving (24 mph)",
            speedMph = 24.0
        )
        val eloiseMoving = FamilyMember(
            id = "device_eloise_a9e3d7",
            name = "Eloise",
            avatarColorHex = "#EC407A",
            y = 51.3500000, // Identical GPS location
            x = -0.1000000,
            statusText = "Driving (24 mph)",
            speedMph = 24.0
        )

        val zoom = 15.0
        val density = 3.0f

        val layout = com.example.ui.computeClusterLayout(
            members = listOf(dadMoving, eloiseMoving),
            safeZones = emptyList(),
            homeLat = 51.3300000,
            homeLng = -0.1180000,
            homeRadiusMeters = 140.0,
            isWorkCalibrated = false,
            workLat = 0.0,
            workLng = 0.0,
            workRadiusMeters = 75.0,
            zoomLevel = zoom,
            density = density
        )

        val posDad = layout.adjustedCoordinates["me"]
        val posEloise = layout.adjustedCoordinates["device_eloise_a9e3d7"]

        assertNotNull("Dad coordinate must exist", posDad)
        assertNotNull("Eloise coordinate must exist", posEloise)

        val distMeters = com.example.data.GeoUtils.distanceMeters(
            posDad!!.latitude, posDad.longitude,
            posEloise!!.latitude, posEloise.longitude
        )
        val metersPerPx = com.example.ui.metersPerPixelAtZoom(51.3500000, zoom)
        val screenDp = (distMeters / metersPerPx) / density

        assertTrue(
            "Moving co-located members must be separated by at least 60dp on screen (was $screenDp dp)",
            screenDp >= 60.0
        )
    }
}
