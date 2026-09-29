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
class DeviceRenameSyncTest {

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
    fun testRenameOwnDeviceUpdatesLocalStateAndMeRecord() = runBlocking {
        // Seed "me" member
        val initialMe = FamilyMember(
            id = "me",
            name = "Dad",
            avatarColorHex = "#AA22FF",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 100,
            isCharging = false,
            speedMph = 0.0,
            statusText = "Stationary",
            isComingHome = false,
            etaMinutes = 0
        )
        repository.insertFamilyMembers(listOf(initialMe))

        // Create viewModel
        val viewModel = FamilyViewModel(application)
        viewModel.isSimulationModeEnabled.value = false
        ShadowLooper.idleMainLooper()

        // Rename own device from "Dad" to "Louis's S23"
        val updatedMe = initialMe.copy(name = "Louis's S23")
        viewModel.updateFamilyMember(updatedMe)
        ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(100)
        ShadowLooper.idleMainLooper()

        // Check viewModel device name
        assertEquals("Louis's S23", viewModel.myDeviceName.value)

        // Check "me" record in database
        val meInDb = repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }
        assertNotNull(meInDb)
        assertEquals("Louis's S23", meInDb!!.name)
    }

    @Test
    fun testRenameOtherMemberInCircleUpdatesDatabaseWithoutDuplicates() = runBlocking {
        val initialDevice = FamilyMember(
            id = "device_oldphone_abc123",
            name = "Old Phone",
            avatarColorHex = "#26A69A",
            x = -0.12,
            y = 51.33,
            batteryPercentage = 80,
            isCharging = false,
            speedMph = 0.0,
            statusText = "Active",
            isComingHome = false,
            etaMinutes = 0
        )
        repository.insertFamilyMembers(listOf(initialDevice))

        val viewModel = FamilyViewModel(application)
        viewModel.isSimulationModeEnabled.value = false
        ShadowLooper.idleMainLooper()

        // Rename this member to "Isabel's iPhone"
        val renamed = initialDevice.copy(name = "Isabel's iPhone")
        viewModel.updateFamilyMember(renamed)
        ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(100)
        ShadowLooper.idleMainLooper()

        val allMembers = repository.getFamilyMembersOnce()
        val matching = allMembers.filter { it.id == "device_oldphone_abc123" }
        assertEquals(1, matching.size)
        assertEquals("Isabel's iPhone", matching.first().name)
    }

    @Test
    fun testCloudMemberMatchingReplacesOldMemberWithSameDeviceUUID() = runBlocking {
        // Suppose local DB has an old record with ID "device_dad_xyz789"
        val oldLocal = FamilyMember(
            id = "device_dad_xyz789",
            name = "Dad",
            avatarColorHex = "#AA22FF",
            x = -0.119095,
            y = 51.329480,
            batteryPercentage = 90,
            isCharging = false,
            speedMph = 0.0,
            statusText = "Active",
            isComingHome = false,
            etaMinutes = 0
        )
        repository.insertFamilyMembers(listOf(oldLocal))

        // When device is renamed to "Louis", new ID becomes "device_louis_xyz789" (same UUID xyz789)
        val cloudDeviceUUID = "xyz789"
        val incomingId = "device_louis_xyz789"

        val existingLocal = repository.getFamilyMembersOnce()
        val matchingLocal = existingLocal.firstOrNull { it.id == incomingId }
            ?: existingLocal.firstOrNull {
                cloudDeviceUUID.length >= 4 && it.id.startsWith("device_") && it.id.endsWith("_$cloudDeviceUUID")
            }

        assertNotNull(matchingLocal)
        assertEquals("device_dad_xyz789", matchingLocal!!.id)

        // Verify replacement logic (delete old ID, insert new ID)
        val newMapped = matchingLocal.copy(id = incomingId, name = "Louis")
        if (matchingLocal.id != incomingId) {
            repository.deleteMember(matchingLocal)
            repository.insertFamilyMembers(listOf(newMapped))
        }

        val afterSync = repository.getFamilyMembersOnce()
        val membersWithThisUuid = afterSync.filter { it.id.endsWith("_$cloudDeviceUUID") }
        // Should only have 1 member for this device UUID, with the new ID and new name
        assertEquals(1, membersWithThisUuid.size)
        assertEquals(incomingId, membersWithThisUuid.first().id)
        assertEquals("Louis", membersWithThisUuid.first().name)
    }
}
