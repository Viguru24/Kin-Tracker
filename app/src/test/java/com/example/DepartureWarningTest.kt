package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppConfig
import com.example.ui.FamilyViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DepartureWarningTest {

    @Test
    fun testDepartureAlertsToggleAndDefaults() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        // Enabled by default
        assertTrue(viewModel.isDepartureAlertsEnabled.value)

        // Toggle off
        viewModel.toggleDepartureAlerts(false)
        assertFalse(viewModel.isDepartureAlertsEnabled.value)

        // Toggle on
        viewModel.toggleDepartureAlerts(true)
        assertTrue(viewModel.isDepartureAlertsEnabled.value)
    }

    @Test
    fun testWorkAreaStipulationAndClearing() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        // Stipulate custom Work coordinates and radius
        val testWorkLat = 51.380000
        val testWorkLng = -0.095000
        val testRadius = 80.0

        viewModel.setWorkLocation(testWorkLat, testWorkLng, testRadius)

        assertTrue(viewModel.isWorkCalibrated)
        assertTrue(viewModel.isWorkCalibratedFlow.value)
        assertEquals(testWorkLat, viewModel.workLat, 0.0001)
        assertEquals(testWorkLng, viewModel.workLng, 0.0001)
        assertEquals(testRadius, viewModel.workRadiusMeters, 0.1)

        // Clear Work Area
        viewModel.clearWorkLocation()
        assertFalse(viewModel.isWorkCalibrated)
        org.junit.Assert.assertNotEquals(51.33016, viewModel.workLat, 0.0001)
        assertEquals(0.0, viewModel.workLng, 0.0001)
    }

    @Test
    fun testHomeRadiusUpdate() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        assertEquals(AppConfig.DEFAULT_HOME_RADIUS_METERS, viewModel.homeRadiusMeters, 0.1)

        viewModel.updateHomeRadius(90.0)
        assertEquals(90.0, viewModel.homeRadiusMeters, 0.1)
        assertEquals(90.0, viewModel.homeRadiusFlow.value, 0.1)
    }

    @Test
    fun testHysteresisConstantsPreventFalseAlarms() {
        // Exit hysteresis buffer must be >= 35m to prevent false alarms from indoor GPS jitter
        assertTrue(AppConfig.EXIT_HYSTERESIS_METERS >= 35.0)
        // Minimum exit speed threshold should require movement >= 0.5 mph
        assertTrue(AppConfig.MIN_EXIT_SPEED_MPH >= 0.5)
    }
    @Test
    fun testEloiseLeavingSchoolSaysSheHasJustLeftHerSchool() = kotlinx.coroutines.runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(application, com.example.data.AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = com.example.data.FamilyRepository(database.familyDao())
        val prefs = application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isDepartureAlertsEnabled", true)
            .putFloat("workLat", 51.3279926f)
            .putFloat("workLng", -0.1405488f)
            .putBoolean("isWorkCalibrated", true)
            .apply()

        // Woodcote High School safe zone
        val school = com.example.data.SafeZone(
            id = "zone_woodcote_high_school",
            name = "Woodcote High School",
            latitude = 51.3301600,
            longitude = -0.1422829,
            radiusMeters = 220.0,
            iconName = "school"
        )
        repository.insertSafeZone(school)

        val eloiseInside = com.example.data.FamilyMember(
            id = "device_eloise_a9e3d7",
            name = "Eloise",
            avatarColorHex = "#FF4081",
            x = -0.1422829,
            y = 51.3301600,
            speedMph = 0.0,
            statusText = "At Woodcote High School"
        )

        val capturedEvents = mutableListOf<String>()

        // 1. Initial state inside
        com.example.data.GeofenceMonitor.evaluateGeofences(
            context = application,
            repository = repository,
            prefs = prefs,
            members = listOf(eloiseInside),
            myLocation = null,
            onUiEvent = { capturedEvents.add(it) }
        )
        assertTrue("No alert on initial inside state", capturedEvents.isEmpty())

        // 2. Eloise leaves school grounds walking
        val eloiseDeparting = eloiseInside.copy(
            x = -0.140000,
            y = 51.326500,
            speedMph = 2.5,
            statusText = "Walking (2.5 mph)"
        )

        for (i in 1..com.example.data.AppConfig.DEPARTURE_CONFIRMATION_CHECKS) {
            com.example.data.GeofenceMonitor.evaluateGeofences(
                context = application,
                repository = repository,
                prefs = prefs,
                members = listOf(eloiseDeparting),
                myLocation = null,
                onUiEvent = { capturedEvents.add(it) }
            )
        }

        assertEquals("Exactly one departure alert should be emitted", 1, capturedEvents.size)
        val alert = capturedEvents.first()
        assertTrue("Must state: Eloise has just left her school!", alert.contains("Eloise has just left her school!"))
        assertFalse("Must NOT say arrived at Work", alert.contains("Work", ignoreCase = true))

        // 3. Ensure moving through Work coordinates does not trigger arrival at work or repeated departures
        capturedEvents.clear()
        val eloiseNearWork = eloiseInside.copy(
            x = -0.1405488,
            y = 51.3279926,
            speedMph = 2.0,
            statusText = "Walking (2.0 mph)"
        )

        for (i in 1..5) {
            com.example.data.GeofenceMonitor.evaluateGeofences(
                context = application,
                repository = repository,
                prefs = prefs,
                members = listOf(eloiseNearWork),
                myLocation = null,
                onUiEvent = { capturedEvents.add(it) }
            )
        }
        assertTrue("Eloise must NEVER trigger arrival at Work or repeated departures", capturedEvents.isEmpty())

        database.close()
    }
    @Test
    fun testWorkAreaCalibrationRejectedNearSchool() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        // Attempt to set Work right at Woodcote High School (51.33016, -0.14228)
        viewModel.setWorkLocation(51.33016, -0.14228, 75.0)

        // Must be rejected by guardrail
        assertFalse("Work calibration must be rejected within 400m of school", viewModel.isWorkCalibrated)
        org.junit.Assert.assertNotEquals(51.33016, viewModel.workLat, 0.0001)
    }

    @Test
    fun testAlertArbiterDropsWorkplaceAlertsForChildren() {
        com.example.data.AlertArbiter.reset()

        // Eloise workplace alert must be dropped
        val allowedEloise = com.example.data.AlertArbiter.canEmitAlert(
            title = "📍 Arrival Notice",
            message = "Eloise has arrived at Work!",
            memberName = "Eloise"
        )
        assertFalse("AlertArbiter must drop workplace alert for Eloise", allowedEloise)

        // Isabel workplace alert must be dropped
        val allowedIsabel = com.example.data.AlertArbiter.canEmitAlert(
            title = "🚪 Departure Warning",
            message = "Isabel has left Work",
            memberName = "Isabel"
        )
        assertFalse("AlertArbiter must drop workplace alert for Isabel", allowedIsabel)
    }

    @Test
    fun testAlertArbiterPreventsDuplicateOrBackToBackDepartures() {
        com.example.data.AlertArbiter.reset()

        val firstDeparture = com.example.data.AlertArbiter.canEmitAlert(
            title = "🚪 Departure Warning",
            message = "Eloise has just left her school!",
            memberName = "Eloise",
            now = 1000L
        )
        assertTrue("First departure should be allowed", firstDeparture)

        // Second departure 10 seconds later without arriving anywhere
        val secondDeparture = com.example.data.AlertArbiter.canEmitAlert(
            title = "🚪 Departure Warning",
            message = "Eloise has left Woodcote High School",
            memberName = "Eloise",
            now = 10000L
        )
        assertFalse("Duplicate / back-to-back departure must be blocked by AlertArbiter", secondDeparture)
    }
}
