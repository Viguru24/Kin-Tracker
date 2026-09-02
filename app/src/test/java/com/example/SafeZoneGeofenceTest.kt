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
}
