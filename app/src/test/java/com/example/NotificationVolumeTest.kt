package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AlertNotificationHelper
import com.example.data.VoiceAnnouncementManager
import com.example.ui.FamilyViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationVolumeTest {

    @Test
    fun testDefaultNotificationVolume() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val viewModel = FamilyViewModel(application)
        val defaultVol = AlertNotificationHelper.getNotificationVolume(application)
        assertTrue("Notification volume should be between 0 and 100", defaultVol in 0..100)
        assertEquals(defaultVol, viewModel.notificationVolume.value)
    }

    @Test
    fun testUpdateNotificationVolumePersists() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val viewModel = FamilyViewModel(application)
        viewModel.updateNotificationVolume(65)

        assertEquals(65, viewModel.notificationVolume.value)
        assertEquals(65, AlertNotificationHelper.getNotificationVolume(application))

        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        assertEquals(65, prefs.getInt("notificationVolume", -1))
    }

    @Test
    fun testNotificationVolumeClamping() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val viewModel = FamilyViewModel(application)
        
        // Test lower bound clamp
        viewModel.updateNotificationVolume(-20)
        assertEquals(0, viewModel.notificationVolume.value)
        assertEquals(0, AlertNotificationHelper.getNotificationVolume(application))

        // Test upper bound clamp
        viewModel.updateNotificationVolume(150)
        assertEquals(100, viewModel.notificationVolume.value)
        assertEquals(100, AlertNotificationHelper.getNotificationVolume(application))
    }

    @Test
    fun testNotificationMuteBehavior() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isVoiceAnnouncementsEnabled", true).putInt("notificationVolume", 0).commit()

        VoiceAnnouncementManager.clearHistory()

        // When muted (volume = 0), announceIfEnabled should silently abort and not crash
        val message = "📍 Arrival Notice: Isabel has arrived at Home!"
        VoiceAnnouncementManager.announceIfEnabled(application, message)

        // Posting alert notification at volume 0 should complete silently without error
        AlertNotificationHelper.postAlertNotification(application, "Arrival", "Isabel arrived", 101)
    }

    @Test
    fun testNotificationPreviewPlayback() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        // Test playing preview at 80% volume
        viewModel.updateNotificationVolume(80)
        viewModel.playNotificationPreview()

        // Test playing preview at 0% (mute)
        viewModel.updateNotificationVolume(0)
        viewModel.playNotificationPreview()
    }
}
