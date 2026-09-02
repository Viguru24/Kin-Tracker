package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FamilyRepository
import com.example.data.GroupPinMapping
import com.example.ui.FamilyViewModel
import kotlinx.coroutines.flow.first
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
class CircleGroupPinTest {

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
    fun testRepositoryGroupPinOperations() = runBlocking {
        // Seed two circles
        val circle1 = GroupPinMapping(
            pinCode = "1234",
            groupName = "Family Home",
            creatorId = "device_1",
            groupToken = "token_1",
            createdTimestamp = System.currentTimeMillis(),
            isOwner = true
        )
        val circle2 = GroupPinMapping(
            pinCode = "5678",
            groupName = "Holiday Trip",
            creatorId = "device_1",
            groupToken = "token_2",
            createdTimestamp = System.currentTimeMillis(),
            isOwner = true
        )

        repository.insertGroupPinMapping(circle1)
        repository.insertGroupPinMapping(circle2)

        val mappings = repository.groupPinMappings.first { it.size == 2 }
        assertEquals(2, mappings.size)
        assertTrue(mappings.any { it.pinCode == "1234" })
        assertTrue(mappings.any { it.pinCode == "5678" })

        // Activate circle 2
        repository.activateGroup("5678")
        val activeMapping = repository.getGroupPinMappingByPin("5678")
        assertNotNull(activeMapping)
        assertTrue(activeMapping!!.isActive)

        // Delete circle 1
        repository.deleteGroupPinMapping(circle1)
        val mappingsAfterDelete = repository.groupPinMappings.first { it.size == 1 }
        assertEquals(1, mappingsAfterDelete.size)
        assertNull(repository.getGroupPinMappingByPin("1234"))
    }
}
