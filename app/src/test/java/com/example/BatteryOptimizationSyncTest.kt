package com.example

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BackgroundSyncProcessor
import com.example.data.FamilyMember
import com.example.data.FamilyRepository
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
class BatteryOptimizationSyncTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(application)
        repository = FamilyRepository(database.familyDao())

        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("groupSyncToken", "test_circle_battery_save")
            .putString("myDeviceName", "Dad")
            .commit()
    }

    @After
    fun teardown() {
        // Safe clean up
    }

    @Test
    fun testStationaryLocationUpdateUpdatesRoomDatabaseCleanly() = runBlocking {
        val meInitial = FamilyMember(
            id = "me",
            name = "Dad",
            avatarColorHex = "#AA22FF",
            x = -0.118000,
            y = 51.330000,
            batteryPercentage = 80,
            isCharging = false,
            speedMph = 0.0,
            statusText = "At Home (Live GPS)"
        )
        repository.insertFamilyMembers(listOf(meInitial))

        // Create a stationary location
        val loc = Location("gps").apply {
            latitude = 51.330005 // 0.5m jitter
            longitude = -0.118002
            speed = 0.0f
            accuracy = 10.0f
            time = System.currentTimeMillis()
        }

        // Process location update
        BackgroundSyncProcessor.processLocationUpdate(application, loc, forceSync = false)

        val updatedMe = repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }
        assertNotNull("Local member must be retained and updated", updatedMe)
        assertEquals(0.0, updatedMe!!.speedMph, 0.01)
        assertTrue(updatedMe.statusText.contains("Home") || updatedMe.statusText.contains("GPS"))
    }
}
