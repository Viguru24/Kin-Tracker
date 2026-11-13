package com.example

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FamilyMember
import com.example.data.FamilyRepository
import com.example.data.GeoUtils
import com.example.ui.FamilyViewModel
import com.example.ui.computeClusterLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationArbitrationAndHomeGeofenceTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(application)
        repository = FamilyRepository(database.familyDao())
        GeoUtils.resetLocationArbiterForTests()
    }

    @After
    fun teardown() {
        GeoUtils.resetLocationArbiterForTests()
    }

    @Test
    fun testPickBetterLocationPrefersAccurateRecentGpsOverCoarseNetwork() {
        val now = 1_794_603_000_000L
        val churchGps = Location("gps").apply {
            latitude = 51.33135
            longitude = -0.11800
            accuracy = 11f
            time = now - 5_000L
            elapsedRealtimeNanos = 500_000_000_000L
        }
        val homeNetwork = Location("network").apply {
            latitude = 51.33000
            longitude = -0.11800
            accuracy = 95f
            time = now
            elapsedRealtimeNanos = 505_000_000_000L
        }

        val pickedForward = GeoUtils.pickBetterLocation(churchGps, homeNetwork)
        val pickedReverse = GeoUtils.pickBetterLocation(homeNetwork, churchGps)

        assertEquals("gps", pickedForward?.provider)
        assertEquals(51.33135, pickedForward!!.latitude, 0.00001)
        assertEquals("gps", pickedReverse?.provider)
    }

    @Test
    fun testAcceptLocationUpdateBlocksHomeNetworkBounceAfterChurchGps() {
        val now = 1_794_603_000_000L
        val churchGps = Location("gps").apply {
            latitude = 51.33135
            longitude = -0.11800
            accuracy = 12f
            time = now
            elapsedRealtimeNanos = 600_000_000_000L
        }
        val staleHomeNetworkBounce = Location("network").apply {
            latitude = 51.33000
            longitude = -0.11800
            accuracy = 60f
            time = now + 1_000L
            elapsedRealtimeNanos = 601_000_000_000L
        }

        assertTrue(GeoUtils.acceptLocationUpdate(churchGps))
        assertFalse(
            "Cached/coarse Home network fix arriving 1s after church GPS must be dropped",
            GeoUtils.acceptLocationUpdate(staleHomeNetworkBounce)
        )
    }

    @Test
    fun testMapClusterEngineRespects60mHomeRadiusAtNearbyChurch() {
        val homeLat = 51.330000
        val homeLng = -0.118000
        val churchLat = 51.331350 // ~150m north of Home

        val dadAtChurch = FamilyMember(
            id = "me",
            name = "Dad",
            avatarColorHex = "#42A5F5",
            x = homeLng,
            y = churchLat,
            batteryPercentage = 80,
            speedMph = 0.0,
            statusText = "Stationary"
        )
        val eloiseAtHome = FamilyMember(
            id = "device_eloise_a9e3d7",
            name = "Eloise",
            avatarColorHex = "#EC407A",
            x = homeLng,
            y = homeLat + 0.00010, // ~11m from Home pin
            batteryPercentage = 75,
            speedMph = 0.0,
            statusText = "At Home (Live GPS)"
        )

        val layout = computeClusterLayout(
            members = listOf(dadAtChurch, eloiseAtHome),
            safeZones = emptyList(),
            homeLat = homeLat,
            homeLng = homeLng,
            homeRadiusMeters = 60.0,
            isWorkCalibrated = false,
            workLat = 0.0,
            workLng = 0.0,
            workRadiusMeters = 75.0,
            zoomLevel = 17.0,
            previousBarrierMembership = mapOf("me" to "home", "device_eloise_a9e3d7" to "home")
        )

        assertNull("Dad at church 150m away must NOT stay trapped inside 60m Home barrier", layout.barrierMembership["me"])
        assertEquals("home", layout.barrierMembership["device_eloise_a9e3d7"])

        val dadPos = layout.adjustedCoordinates["me"]!!
        assertEquals("Dad's marker must move to the church latitude", churchLat, dadPos.latitude, 0.00005)
    }

    @Test
    fun testFamilyViewModelMovesMeToNearbyChurchWhenHomeRadiusIs60m() = runBlocking {
        val homeLat = 51.330000
        val homeLng = -0.118000
        val churchLat = 51.331350 // ~150m north of Home

        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("homeLat", homeLat.toFloat())
            .putFloat("homeLng", homeLng.toFloat())
            .putBoolean("isHomeCalibrated", true)
            .putFloat("homeRadiusMeters", 60.0f)
            .putString("myDeviceName", "Dad")
            .putString("myDeviceUUID", "336a12")
            .commit()

        repository.insertFamilyMembers(
            listOf(
                FamilyMember(
                    id = "me",
                    name = "Dad",
                    avatarColorHex = "#42A5F5",
                    x = homeLng,
                    y = homeLat,
                    batteryPercentage = 80,
                    speedMph = 0.0,
                    statusText = "At Home (Live GPS)"
                )
            )
        )

        val viewModel = FamilyViewModel(application)
        viewModel.updateHomeRadius(60.0)

        viewModel.updateUserLocation(
            lat = churchLat,
            lng = homeLng,
            speed = 2.0f, // ~4.5 mph walking/slow driving
            batteryLevel = 80,
            isCharging = false
        )

        var updatedMe: FamilyMember? = null
        for (i in 1..40) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            updatedMe = repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }
            if (updatedMe != null && updatedMe.y > 51.3310) break
            Thread.sleep(25)
        }

        assertNotNull(updatedMe)
        assertEquals("me must move to church coordinate instead of snapping to Home", churchLat, updatedMe!!.y, 0.0001)
        assertFalse("Status must not claim At Home at 150m when radius is 60m", updatedMe.statusText.contains("At Home", ignoreCase = true))
    }
}
