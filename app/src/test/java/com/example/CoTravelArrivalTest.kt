package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FamilyMember
import com.example.data.FamilyRepository
import com.example.ui.ProximityEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoTravelArrivalTest {

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
    fun testApproachingHomeAlertSuppressedWhenTravelingTogetherInCar() = runBlocking {
        val uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val engine = ProximityEngine(repository, uiEvents)

        val homeLat = 51.350000
        val homeLng = -0.150000

        // User ("Dad") and Wife are traveling in the car together (outside home, ~1.5km away, speed 30 mph)
        // Co-located: Dad and Wife are at virtually identical coordinates (< 20m apart)
        val initialDad = FamilyMember(
            id = "me",
            name = "Dad (You)",
            avatarColorHex = "#AA22FF",
            x = homeLng + 0.015,
            y = homeLat + 0.015,
            batteryPercentage = 90,
            isCharging = false,
            speedMph = 30.0,
            statusText = "Driving",
            isComingHome = true,
            etaMinutes = 3,
            avatarEmoji = "👨"
        )
        val initialWife = FamilyMember(
            id = "annette",
            name = "Annette (Mama)",
            avatarColorHex = "#EC407A",
            x = homeLng + 0.01505, // ~4 meters from Dad
            y = homeLat + 0.01505,
            batteryPercentage = 85,
            isCharging = false,
            speedMph = 30.0,
            statusText = "Driving",
            isComingHome = true,
            etaMinutes = 3,
            avatarEmoji = "👩"
        )

        // First evaluation: establishes baseline previous distance
        engine.evaluateProximity(
            membersList = listOf(initialDad, initialWife),
            homeLat = homeLat,
            homeLng = homeLng,
            proximityThresholdMeters = 2000,
            myDeviceName = "Dad"
        )

        // Second evaluation: car moves closer to home (closer by 200m, still co-located in same car)
        val closerDad = initialDad.copy(
            x = homeLng + 0.008,
            y = homeLat + 0.008
        )
        val closerWife = initialWife.copy(
            x = homeLng + 0.00805,
            y = homeLat + 0.00805
        )

        val receivedEvents = mutableListOf<String>()
        val job = launch {
            uiEvents.collect { receivedEvents.add(it) }
        }

        engine.evaluateProximity(
            membersList = listOf(closerDad, closerWife),
            homeLat = homeLat,
            homeLng = homeLng,
            proximityThresholdMeters = 2000,
            myDeviceName = "Dad"
        )

        kotlinx.coroutines.delay(100)
        job.cancel()

        // Approaching Home alert MUST be suppressed because Wife is traveling in the car WITH Dad!
        val hasApproachingAlert = receivedEvents.any { it.contains("Approaching Alert", ignoreCase = true) }
        assertFalse("Approaching alert must be suppressed when partner is in the car with user", hasApproachingAlert)
    }

    @Test
    fun testApproachingHomeAlertFiresWhenUserAtHomeAndPartnerReturnsAlone() = runBlocking {
        val uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val engine = ProximityEngine(repository, uiEvents)

        val homeLat = 51.350000
        val homeLng = -0.150000

        // User ("Dad") is stationary at Home
        val dadAtHome = FamilyMember(
            id = "me",
            name = "Dad (You)",
            avatarColorHex = "#AA22FF",
            x = homeLng,
            y = homeLat,
            batteryPercentage = 95,
            isCharging = true,
            speedMph = 0.0,
            statusText = "At Home",
            isComingHome = false,
            etaMinutes = 0,
            avatarEmoji = "👨"
        )

        // Partner is 1.8 km away, driving home alone
        val initialWife = FamilyMember(
            id = "annette",
            name = "Annette (Mama)",
            avatarColorHex = "#EC407A",
            x = homeLng + 0.018,
            y = homeLat + 0.018,
            batteryPercentage = 85,
            isCharging = false,
            speedMph = 28.0,
            statusText = "Driving",
            isComingHome = true,
            etaMinutes = 4,
            avatarEmoji = "👩"
        )

        // Establish baseline distance
        engine.evaluateProximity(
            membersList = listOf(dadAtHome, initialWife),
            homeLat = homeLat,
            homeLng = homeLng,
            proximityThresholdMeters = 2000,
            myDeviceName = "Dad"
        )

        // Partner moves closer to Home (now 0.8 km away)
        val closerWife = initialWife.copy(
            x = homeLng + 0.007,
            y = homeLat + 0.007
        )

        val receivedEvents = mutableListOf<String>()
        val job = launch {
            uiEvents.collect { receivedEvents.add(it) }
        }

        engine.evaluateProximity(
            membersList = listOf(dadAtHome, closerWife),
            homeLat = homeLat,
            homeLng = homeLng,
            proximityThresholdMeters = 2000,
            myDeviceName = "Dad"
        )

        kotlinx.coroutines.delay(100)
        job.cancel()

        // Approaching Home alert MUST fire because user is at Home and Wife is returning alone
        val hasApproachingAlert = receivedEvents.any { it.contains("Approaching Alert", ignoreCase = true) && it.contains("Annette") }
        assertTrue("Approaching alert must fire when user is at home and partner returns alone", hasApproachingAlert)
    }

    @Test
    fun testApproachingHomeAlertSuppressedWhenUserIsNotAtHome() = runBlocking {
        val uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val engine = ProximityEngine(repository, uiEvents)

        val homeLat = 51.350000
        val homeLng = -0.150000

        // User ("Dad") is in Brighton (~70 km away from Home)
        val dadInBrighton = FamilyMember(
            id = "me",
            name = "Dad (You)",
            avatarColorHex = "#AA22FF",
            x = homeLng,
            y = homeLat - 0.65, // Far away in Brighton
            batteryPercentage = 80,
            isCharging = false,
            speedMph = 0.0,
            statusText = "In Brighton",
            isComingHome = false,
            etaMinutes = 0,
            avatarEmoji = "👨"
        )

        // Partner is approaching home from 1.5 km away
        val initialWife = FamilyMember(
            id = "annette",
            name = "Annette (Mama)",
            avatarColorHex = "#EC407A",
            x = homeLng + 0.015,
            y = homeLat + 0.015,
            batteryPercentage = 85,
            isCharging = false,
            speedMph = 25.0,
            statusText = "Driving Home",
            isComingHome = true,
            etaMinutes = 3,
            avatarEmoji = "👩"
        )

        engine.evaluateProximity(
            membersList = listOf(dadInBrighton, initialWife),
            homeLat = homeLat,
            homeLng = homeLng,
            proximityThresholdMeters = 2000,
            myDeviceName = "Dad"
        )

        val closerWife = initialWife.copy(
            x = homeLng + 0.007,
            y = homeLat + 0.007
        )

        val receivedEvents = mutableListOf<String>()
        val job = launch {
            uiEvents.collect { receivedEvents.add(it) }
        }

        engine.evaluateProximity(
            membersList = listOf(dadInBrighton, closerWife),
            homeLat = homeLat,
            homeLng = homeLng,
            proximityThresholdMeters = 2000,
            myDeviceName = "Dad"
        )

        kotlinx.coroutines.delay(100)
        job.cancel()

        // User is in Brighton (not waiting at home), so Approaching Home alert must be suppressed!
        val hasApproachingAlert = receivedEvents.any { it.contains("Approaching Alert", ignoreCase = true) }
        assertFalse("Approaching Home alert must not be sent to user when user is away from Home", hasApproachingAlert)
    }
}
