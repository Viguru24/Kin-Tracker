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
        assertEquals(0.0, viewModel.workLat, 0.0001)
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
}
