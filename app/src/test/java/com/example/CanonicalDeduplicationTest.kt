package com.example

import android.app.Application
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
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanonicalDeduplicationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository
    private lateinit var application: Application

    @Before
    fun setup() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            application = ApplicationProvider.getApplicationContext<Application>()
            database = AppDatabase.getDatabase(application)
            database.clearAllTables()
            application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            application.getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            application.getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            repository = FamilyRepository(database.familyDao())
        }
    }

    @After
    fun teardown() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
            application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            application.getSharedPreferences("kintracker_contacts", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            application.getSharedPreferences("deleted_members", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun testIdentityUtilsCanonicalKeys() {
        // Dad variations
        assertEquals("canonical_dad", IdentityUtils.getCanonicalPersonKey("Dad"))
        assertEquals("canonical_dad", IdentityUtils.getCanonicalPersonKey("Louis"))
        assertEquals("canonical_dad", IdentityUtils.getCanonicalPersonKey("Louis (Dad)"))
        assertEquals("canonical_dad", IdentityUtils.getCanonicalPersonKey("Dad (You)"))
        assertEquals("canonical_dad", IdentityUtils.getCanonicalPersonKey("Louis de Souza"))
        assertEquals("canonical_dad", IdentityUtils.getCanonicalPersonKey("", "device_dad_1234"))
        assertEquals("canonical_dad", IdentityUtils.getCanonicalPersonKey("", "device_louis_5678"))

        // Eloise variations
        assertEquals("canonical_eloise", IdentityUtils.getCanonicalPersonKey("Eloise"))
        assertEquals("canonical_eloise", IdentityUtils.getCanonicalPersonKey("Eloisa"))
        assertEquals("canonical_eloise", IdentityUtils.getCanonicalPersonKey("Eloise De Souza"))
        assertEquals("canonical_eloise", IdentityUtils.getCanonicalPersonKey("Eloise (Daughter)"))
        assertEquals("canonical_eloise", IdentityUtils.getCanonicalPersonKey("Eloise (Younger Daughter)"))
        assertEquals("canonical_eloise", IdentityUtils.getCanonicalPersonKey("", "device_eloisedesouza_3f9a"))

        // Mama variations
        assertEquals("canonical_mama", IdentityUtils.getCanonicalPersonKey("Mama"))
        assertEquals("canonical_mama", IdentityUtils.getCanonicalPersonKey("Annette"))
        assertEquals("canonical_mama", IdentityUtils.getCanonicalPersonKey("Annette (Mama)"))
        assertEquals("canonical_mama", IdentityUtils.getCanonicalPersonKey("Mama (Wife)"))
        assertEquals("canonical_mama", IdentityUtils.getCanonicalPersonKey("Wife"))
        assertEquals("canonical_mama", IdentityUtils.getCanonicalPersonKey("", "device_annette_7788"))

        // Isabel variations
        assertEquals("canonical_isabel", IdentityUtils.getCanonicalPersonKey("Isabel"))
        assertEquals("canonical_isabel", IdentityUtils.getCanonicalPersonKey("Isabelle"))
        assertEquals("canonical_isabel", IdentityUtils.getCanonicalPersonKey("Isabel (Older Daughter)"))
        assertEquals("canonical_isabel", IdentityUtils.getCanonicalPersonKey("", "device_isabel_9900"))
    }

    @Test
    fun testViewModelDeduplicatesLegacyAndLiveDeviceRecords() = runBlocking {
        // Suppose Dad's phone has:
        // 1. "me" (Dad)
        // 2. Legacy Eloise stub (100% battery, old lastActive)
        // 3. Live Eloise device (charging, recent lastActive)
        // 4. Legacy Mama stub
        // 5. Live Mama device
        val me = FamilyMember(
            id = "me",
            name = "Louis (Dad)",
            avatarColorHex = "#AA22FF",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 85,
            isCharging = false,
            speedMph = 0.0,
            statusText = "At Home",
            isComingHome = false,
            etaMinutes = 0,
            lastActive = System.currentTimeMillis()
        )
        val legacyEloise = FamilyMember(
            id = "eloise",
            name = "Eloise",
            avatarColorHex = "#FFAA00",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 100,
            isCharging = false,
            speedMph = 0.0,
            statusText = "At Home",
            isComingHome = false,
            etaMinutes = 0,
            lastActive = 1000L
        )
        val liveEloise = FamilyMember(
            id = "device_eloisedesouza_3f9a",
            name = "Eloise De Souza",
            avatarColorHex = "#FFAA00",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 95,
            isCharging = true,
            speedMph = 0.0,
            statusText = "At Home (Live GPS)",
            isComingHome = false,
            etaMinutes = 0,
            lastActive = System.currentTimeMillis()
        )
        val legacyMama = FamilyMember(
            id = "annette",
            name = "Mama",
            avatarColorHex = "#EC407A",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 100,
            isCharging = false,
            speedMph = 0.0,
            statusText = "At Home",
            isComingHome = false,
            etaMinutes = 0,
            lastActive = 1000L
        )
        val liveMama = FamilyMember(
            id = "device_annette_7788",
            name = "Annette (Mama)",
            avatarColorHex = "#EC407A",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 72,
            isCharging = true,
            speedMph = 0.0,
            statusText = "At Home",
            isComingHome = false,
            etaMinutes = 0,
            lastActive = System.currentTimeMillis()
        )

        repository.insertFamilyMembers(listOf(me, legacyEloise, liveEloise, legacyMama, liveMama))

        val viewModel = FamilyViewModel(application)
        viewModel.isSimulationModeEnabled.value = false
        ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(200)
        ShadowLooper.idleMainLooper()

        val emittedMembers = viewModel.familyMembers.value
        
        // Assert total member count is strictly 3 (Me/Dad, Eloise, Mama) - NO duplicates!
        assertEquals(3, emittedMembers.size)

        // Assert exactly 1 Eloise exists and it's the live charging one
        val eloiseList = emittedMembers.filter { IdentityUtils.getCanonicalPersonKey(it.name, it.id) == "canonical_eloise" }
        assertEquals(1, eloiseList.size)
        assertEquals("device_eloisedesouza_3f9a", eloiseList[0].id)
        assertTrue(eloiseList[0].isCharging)

        // Assert exactly 1 Mama exists and it's the live one
        val mamaList = emittedMembers.filter { IdentityUtils.getCanonicalPersonKey(it.name, it.id) == "canonical_mama" }
        assertEquals(1, mamaList.size)
        assertEquals("device_annette_7788", mamaList[0].id)

        // Assert exactly 1 Dad exists ("me")
        val dadList = emittedMembers.filter { IdentityUtils.getCanonicalPersonKey(it.name, it.id) == "canonical_dad" }
        assertEquals(1, dadList.size)
        assertEquals("me", dadList[0].id)
    }
}
