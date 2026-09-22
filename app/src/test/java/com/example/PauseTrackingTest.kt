package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BackgroundLocationService
import com.example.data.BootReceiver
import com.example.data.FamilyRepository
import com.example.data.SyncAlarmReceiver
import com.example.ui.FamilyViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PauseTrackingTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
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
    fun testBootReceiverDoesNotStartServiceWhenTrackingPaused() {
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isCloudSyncEnabled", true)
            .putBoolean("isLocationPaused", true)
            .commit()

        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(application, bootIntent)

        val shadowApp = shadowOf(application)
        val nextService = shadowApp.nextStartedService
        assertNull("BackgroundLocationService should NOT be started when tracking is paused", nextService)
    }

    @Test
    fun testBootReceiverStartsServiceWhenTrackingNotPaused() {
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isCloudSyncEnabled", true)
            .putBoolean("isLocationPaused", false)
            .commit()

        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(application, bootIntent)

        val shadowApp = shadowOf(application)
        val nextService = shadowApp.nextStartedService
        assertTrue(
            "BackgroundLocationService should be started when tracking is active",
            nextService?.component?.className == BackgroundLocationService::class.java.name
        )
    }

    @Test
    fun testViewModelToggleLocationPausedPersistsState() = runBlocking {
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val viewModel = FamilyViewModel(application)
        assertFalse("Default location paused state should be false", viewModel.isLocationPaused.value)

        viewModel.toggleLocationPaused(true)
        assertTrue("isLocationPaused StateFlow should be true", viewModel.isLocationPaused.value)
        assertTrue("SharedPreferences should have isLocationPaused=true", prefs.getBoolean("isLocationPaused", false))

        viewModel.toggleLocationPaused(false)
        assertFalse("isLocationPaused StateFlow should be false after resume", viewModel.isLocationPaused.value)
        assertFalse("SharedPreferences should have isLocationPaused=false after resume", prefs.getBoolean("isLocationPaused", true))
    }
}
