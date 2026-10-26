package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
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
class TripTransitEngineTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository
    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext<Application>()
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
    fun testTrainFromBrightonHeadingTowardsLondon() {
        // Isabel is traveling northward along the Brighton Main Line (~50.85 -> 50.90)
        val brightonStationLat = 50.8290
        val brightonStationLng = -0.1410
        val currentTrainLat = 50.8800
        val currentTrainLng = -0.1410

        RailwayTransitDetector.setManualOverride("device_isabel", TransitMode.TRAIN)

        val destination = TripTransitEngine.determineHeadingDestination(
            curLat = currentTrainLat,
            curLng = currentTrainLng,
            prevLat = brightonStationLat,
            prevLng = brightonStationLng,
            safeZones = emptyList(),
            homeLat = 51.329480,
            homeLng = -0.119095,
            speedMph = 55.0,
            isRailway = true
        )

        assertNotNull("Destination should be detected for northbound train", destination)
        assertTrue(
            "Destination should be London or Home, was: $destination",
            destination!!.contains("London", ignoreCase = true) || destination.contains("Home", ignoreCase = true)
        )

        val origin = TripTransitEngine.resolveOriginName(
            lat = brightonStationLat,
            lng = brightonStationLng
        )
        assertEquals("Brighton", origin)

        val msg = TripTransitEngine.formatDepartureMessage(
            memberName = "Isabel",
            originName = origin,
            transitMode = TransitMode.TRAIN,
            destinationName = destination,
            speedMph = 55.0
        )

        // Expected format: "Isabel has left Brighton and is on a train heading towards London!"
        assertTrue("Message should say Isabel has left Brighton", msg.contains("Isabel has left Brighton"))
        assertTrue("Message should say on a train", msg.contains("is on a train"))
        assertTrue("Message should say heading towards", msg.contains("heading towards"))
    }

    @Test
    fun testCarLeavingHomeHeadingTowardsSchool() {
        val homeLat = 51.329480
        val homeLng = -0.119095
        val woodcoteSchool = SafeZone(
            id = "zone_woodcote_high_school",
            name = "Woodcote High School",
            latitude = 51.3301600,
            longitude = -0.1422829,
            radiusMeters = 220.0,
            iconName = "school"
        )

        // Isabel moves west from Home towards Woodcote High School
        val movingCarLat = 51.3298
        val movingCarLng = -0.1280

        val destination = TripTransitEngine.determineHeadingDestination(
            curLat = movingCarLat,
            curLng = movingCarLng,
            prevLat = homeLat,
            prevLng = homeLng,
            safeZones = listOf(woodcoteSchool),
            homeLat = homeLat,
            homeLng = homeLng,
            speedMph = 28.0,
            isRailway = false
        )

        assertEquals("Woodcote High School", destination)

        val msg = TripTransitEngine.formatDepartureMessage(
            memberName = "Isabel",
            originName = "the house",
            transitMode = TransitMode.DRIVING,
            destinationName = destination,
            speedMph = 28.0
        )

        assertEquals("Isabel has left the house and is in a car heading towards Woodcote High School!", msg)
    }

    @Test
    fun testCarLeavingSchoolHeadingTowardsHome() {
        val homeLat = 51.329480
        val homeLng = -0.119095
        val woodcoteSchool = SafeZone(
            id = "zone_woodcote_high_school",
            name = "Woodcote High School",
            latitude = 51.3301600,
            longitude = -0.1422829,
            radiusMeters = 220.0,
            iconName = "school"
        )

        // Isabel moves east from Woodcote High School towards Home
        val movingCarLat = 51.3298
        val movingCarLng = -0.1300

        val destination = TripTransitEngine.determineHeadingDestination(
            curLat = movingCarLat,
            curLng = movingCarLng,
            prevLat = woodcoteSchool.latitude,
            prevLng = woodcoteSchool.longitude,
            safeZones = listOf(woodcoteSchool),
            homeLat = homeLat,
            homeLng = homeLng,
            speedMph = 25.0,
            isRailway = false
        )

        assertEquals("Home", destination)

        val msg = TripTransitEngine.formatDepartureMessage(
            memberName = "Isabel",
            originName = "Woodcote High School",
            transitMode = TransitMode.DRIVING,
            destinationName = destination,
            speedMph = 25.0
        )

        assertEquals("Isabel has left Woodcote High School and is in a car heading towards Home!", msg)
    }

    @Test
    fun testStationaryDepartureFallback() {
        val msg = TripTransitEngine.formatDepartureMessage(
            memberName = "Isabel",
            originName = "the house",
            transitMode = TransitMode.STATIONARY,
            destinationName = null
        )

        assertEquals("Isabel has left the house!", msg)
    }

    @Test
    fun testGeofenceMonitorGeneratesSmartTransitDepartureAlert() = runBlocking {
        val prefs = application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("homeLat", 51.329480f)
            .putFloat("homeLng", -0.119095f)
            .putFloat("homeRadiusMeters", 140.0f)
            .putBoolean("isDepartureAlertsEnabled", true)
            .apply()

        val daughterInside = FamilyMember(
            id = "device_isabel",
            name = "Isabel",
            avatarColorHex = "#FF4081",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 90,
            isCharging = false,
            speedMph = 0.0,
            statusText = "At Home"
        )

        var capturedUiEvent: String? = null

        // 1. Initial baseline inside
        GeofenceMonitor.evaluateGeofences(
            context = application,
            repository = repository,
            prefs = prefs,
            members = listOf(daughterInside),
            myLocation = null,
            onUiEvent = { capturedUiEvent = it }
        )
        assertNull(capturedUiEvent)

        // 2. Member leaves Home driving west towards Woodcote High School at 25 mph
        val daughterDrivingAway = daughterInside.copy(
            x = -0.126000,
            y = 51.330000,
            speedMph = 25.0,
            statusText = "Driving (25 mph)"
        )

        for (i in 1..AppConfig.DEPARTURE_CONFIRMATION_CHECKS) {
            GeofenceMonitor.evaluateGeofences(
                context = application,
                repository = repository,
                prefs = prefs,
                members = listOf(daughterDrivingAway),
                myLocation = null,
                onUiEvent = { capturedUiEvent = it }
            )
        }

        assertNotNull("Departure alert must be emitted", capturedUiEvent)
        assertTrue("Must contain Departure Warning", capturedUiEvent!!.contains("Departure Warning"))
        assertTrue("Must contain Isabel", capturedUiEvent!!.contains("Isabel"))
        assertTrue("Must mention transit mode (car or heading)", capturedUiEvent!!.contains("car") || capturedUiEvent!!.contains("heading"))
    }
}
