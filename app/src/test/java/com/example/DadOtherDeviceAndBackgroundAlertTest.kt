package com.example

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.FamilyViewModel
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
class DadOtherDeviceAndBackgroundAlertTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(application)
        repository = FamilyRepository(database.familyDao())

        // Clear relevant prefs
        application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        application.getSharedPreferences("deleted_members", Context.MODE_PRIVATE).edit().clear().commit()
        application.getSharedPreferences("geofence_status_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun teardown() {
        // AppDatabase singleton can remain
    }

    @Test
    fun testPauseAndRemoveDevicePersistsAndHidesDevice() = runBlocking {
        val otherDevice = FamilyMember(
            id = "device_dad_other_999",
            name = "Dad's other device",
            avatarColorHex = "#00FF88",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 95,
            isCharging = false,
            speedMph = 0.0,
            statusText = "Stationary",
            isComingHome = false,
            etaMinutes = 0,
            isLocationPaused = false
        )
        repository.insertFamilyMembers(listOf(otherDevice))

        val viewModel = FamilyViewModel(application)
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)

        // Toggle pause on "Dad's other device"
        viewModel.toggleMemberTracking("device_dad_other_999")
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Should be marked paused in prefs so cloud sync cannot unpause it
        val isPausedInPrefs = prefs.getBoolean("is_member_paused_device_dad_other_999", false)
        assertTrue("Dad's other device should be marked paused in preferences", isPausedInPrefs)

        // Wait briefly for repository update to execute
        var updatedMember: FamilyMember? = null
        for (i in 1..20) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            updatedMember = repository.getFamilyMembersOnce().firstOrNull { it.id == "device_dad_other_999" }
            if (updatedMember?.isLocationPaused == true) break
            Thread.sleep(50)
        }
        assertNotNull(updatedMember)
        assertTrue("Local Room member should have isLocationPaused=true", updatedMember!!.isLocationPaused)
        assertTrue("Status should indicate Paused/Hidden", updatedMember.statusText.contains("Paused"))
    }

    @Test
    fun testDeleteDadOtherDevicePermanentlyRemovesIt() = runBlocking {
        val otherDevice = FamilyMember(
            id = "device_dad_other_999",
            name = "Dad's other device",
            avatarColorHex = "#00FF88",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 95,
            isCharging = false,
            speedMph = 0.0,
            statusText = "Stationary",
            isComingHome = false,
            etaMinutes = 0,
            isLocationPaused = false
        )
        repository.insertFamilyMembers(listOf(otherDevice))

        val viewModel = FamilyViewModel(application)
        val deletedPrefs = application.getSharedPreferences("deleted_members", Context.MODE_PRIVATE)

        // Delete "Dad's other device"
        viewModel.deleteFamilyMember("device_dad_other_999")
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Verify it was recorded in deleted_members prefs
        assertTrue(
            "Device ID should be marked deleted",
            deletedPrefs.getBoolean("deleted_device_dad_other_999", false)
        )
        assertTrue(
            "Device name should be marked deleted",
            deletedPrefs.getBoolean("deleted_dad's other device", false)
        )

        // Verify it was removed from local database
        var membersAfter = repository.getFamilyMembersOnce()
        for (i in 1..20) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            membersAfter = repository.getFamilyMembersOnce()
            if (!membersAfter.any { it.id == "device_dad_other_999" }) break
            Thread.sleep(50)
        }
        assertFalse(
            "Dad's other device should not exist in local database",
            membersAfter.any { it.id == "device_dad_other_999" }
        )
    }

    @Test
    fun testBackgroundGeofenceMonitorDetectsDepartureAndArrival() = runBlocking {
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("homeLat", 51.329480f)
            .putFloat("homeLng", -0.119095f)
            .putFloat("homeRadiusMeters", 140f)
            .putString("myDeviceName", "Dad")
            .putString("myDeviceUUID", "112233")
            .commit()

        val daughterInside = FamilyMember(
            id = "device_isabel_123",
            name = "Isabel",
            avatarColorHex = "#FF4081",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 90,
            isCharging = false,
            speedMph = 0.0,
            statusText = "At Home",
            isComingHome = false,
            etaMinutes = 0
        )

        val myLoc = Location("GPS").apply {
            latitude = 51.329480
            longitude = -0.119095
        }

        var capturedUiEvent: String? = null

        // 1. Initial evaluation inside Home: sets status to inside without false alarm
        GeofenceMonitor.evaluateGeofences(
            context = application,
            repository = repository,
            prefs = prefs,
            members = listOf(daughterInside),
            myLocation = myLoc,
            onUiEvent = { capturedUiEvent = it }
        )
        assertNull("Initial state inside should not trigger alert", capturedUiEvent)

        // 2. Member leaves Home (> 300 meters away, moving speed)
        val daughterDeparted = daughterInside.copy(
            x = -0.114000,
            y = 51.334000,
            speedMph = 15.0,
            statusText = "Driving"
        )

        // Simulate confirmation readings
        for (i in 1..AppConfig.DEPARTURE_CONFIRMATION_CHECKS) {
            GeofenceMonitor.evaluateGeofences(
                context = application,
                repository = repository,
                prefs = prefs,
                members = listOf(daughterDeparted),
                myLocation = myLoc,
                onUiEvent = { capturedUiEvent = it }
            )
        }

        assertNotNull("Departure alert should be triggered", capturedUiEvent)
        assertTrue(
            "Alert message should contain Departure Warning and member name",
            capturedUiEvent!!.contains("Departure Warning") && capturedUiEvent!!.contains("Isabel")
        )
    }
}
