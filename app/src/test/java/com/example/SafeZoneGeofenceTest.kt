package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FamilyRepository
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
    fun testEloiseClassroomAndIndoorDriftStaysLockedInWoodcoteHighSchool() {
        // Eloise's real classroom telemetry at Woodcote High School (62.4m from center pin)
        val eloiseClassroom = com.example.data.FamilyMember(
            id = "device_eloise_a9e3d7",
            name = "Eloise",
            avatarColorHex = "#E91E63",
            x = -0.1284886,
            y = 51.3202444,
            batteryPercentage = 51,
            speedMph = 0.0,
            statusText = "Live GPS tracking (1.30 km away)"
        )

        val matched = com.example.data.GeoUtils.matchSafeZone(
            lat = eloiseClassroom.y,
            lng = eloiseClassroom.x,
            customZones = emptyList(),
            currentStatusText = eloiseClassroom.statusText
        )
        assertNotNull("Eloise's classroom must match Woodcote High School safe zone", matched)
        assertEquals("Woodcote High School", matched!!.name)

        // First layout pass: Eloise must be locked to Woodcote High School center pin
        val layout1 = com.example.ui.computeClusterLayout(
            members = listOf(eloiseClassroom),
            safeZones = emptyList(),
            homeLat = 51.3316345,
            homeLng = -0.1217125,
            homeRadiusMeters = 140.0,
            isWorkCalibrated = false,
            workLat = 0.0,
            workLng = 0.0,
            workRadiusMeters = 75.0
        )

        assertEquals(
            "zone:zone_woodcote_high_school",
            layout1.barrierMembership["device_eloise_a9e3d7"]
        )
        val pinnedPos1 = layout1.adjustedCoordinates["device_eloise_a9e3d7"]!!
        assertEquals(51.3205711, pinnedPos1.latitude, 0.00001)
        assertEquals(-0.1292201, pinnedPos1.longitude, 0.00001)

        // Second layout pass: simulate indoor Wi-Fi/cell drift 210m away from school pin while locked
        val driftedLat = 51.3205711 + (210.0 / 111139.0)
        val eloiseDrifted = eloiseClassroom.copy(
            y = driftedLat,
            x = -0.1292201,
            statusText = "At Woodcote High School",
            speedMph = 2.2 // Indoor GPS jitter speed
        )

        val layout2 = com.example.ui.computeClusterLayout(
            members = listOf(eloiseDrifted),
            safeZones = emptyList(),
            homeLat = 51.3316345,
            homeLng = -0.1217125,
            homeRadiusMeters = 140.0,
            isWorkCalibrated = false,
            workLat = 0.0,
            workLng = 0.0,
            workRadiusMeters = 75.0,
            previousBarrierMembership = layout1.barrierMembership
        )

        assertEquals(
            "Eloise must remain locked inside Woodcote High School barrier across indoor drift",
            "zone:zone_woodcote_high_school",
            layout2.barrierMembership["device_eloise_a9e3d7"]
        )
        val pinnedPos2 = layout2.adjustedCoordinates["device_eloise_a9e3d7"]!!
        assertEquals(51.3205711, pinnedPos2.latitude, 0.00001)
        assertEquals(-0.1292201, pinnedPos2.longitude, 0.00001)
    }
}
