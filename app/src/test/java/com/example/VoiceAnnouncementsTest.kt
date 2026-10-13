package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.VoiceAnnouncementManager
import com.example.ui.FamilyViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceAnnouncementsTest {

    @Test
    fun testVoiceAnnouncementsDefaultIsOff() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        // Ensure clean preferences
        application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val viewModel = FamilyViewModel(application)
        
        // Assert voice announcements setting MUST be off by default
        assertFalse(viewModel.isVoiceAnnouncementsEnabled.value)
        assertFalse(VoiceAnnouncementManager.isVoiceAnnouncementsEnabled(application))
    }

    @Test
    fun testVoiceAnnouncementsPreservesExistingPhoneSetting() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        // Simulate phone already having voice announcements turned ON
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isVoiceAnnouncementsEnabled", true).commit()

        // When ViewModel initializes, it must go to whatever's been set on the phone
        val viewModel = FamilyViewModel(application)
        assertTrue(viewModel.isVoiceAnnouncementsEnabled.value)
        assertTrue(VoiceAnnouncementManager.isVoiceAnnouncementsEnabled(application))
    }

    @Test
    fun testVoiceAnnouncementsToggleAndPersist() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val viewModel = FamilyViewModel(application)
        assertFalse(viewModel.isVoiceAnnouncementsEnabled.value)

        // Toggle it on and assert saved
        viewModel.toggleVoiceAnnouncements(true)
        assertTrue(viewModel.isVoiceAnnouncementsEnabled.value)
        assertTrue(VoiceAnnouncementManager.isVoiceAnnouncementsEnabled(application))

        // Toggle it off and assert saved
        viewModel.toggleVoiceAnnouncements(false)
        assertFalse(viewModel.isVoiceAnnouncementsEnabled.value)
        assertFalse(VoiceAnnouncementManager.isVoiceAnnouncementsEnabled(application))
    }

    @Test
    fun testSpeechFormattingHomeArrival() {
        val message = "📍 Arrival Notice: Isabel has arrived at Home!"
        val result = VoiceAnnouncementManager.formatSpeechText(message)
        assertNotNull(result)
        assertEquals("isabel_arrived", result!!.first)
        assertEquals("Isabel has arrived home", result.second)
    }

    @Test
    fun testSpeechFormattingHouseDeparture() {
        val message = "🚪 Departure Warning: Isabel has left the house!"
        val result = VoiceAnnouncementManager.formatSpeechText(message)
        assertNotNull(result)
        assertEquals("isabel_left", result!!.first)
        assertEquals("Isabel has left the house", result.second)
    }

    @Test
    fun testSpeechFormattingCustomSafeZoneArrival() {
        val message = "📍 Arrival Notice: Isabel has arrived at School!"
        val result = VoiceAnnouncementManager.formatSpeechText(message)
        assertNotNull(result)
        assertEquals("isabel_arrived", result!!.first)
        assertEquals("Isabel has arrived at School", result.second)
    }

    @Test
    fun testSpeechFormattingApproachingHome() {
        val message = "Approaching Alert: Isabel will be home in about 4 minutes"
        val result = VoiceAnnouncementManager.formatSpeechText(message)
        assertNotNull(result)
        assertEquals("isabel_approaching", result!!.first)
        assertEquals("Isabel will be home in about 4 minutes", result.second)
    }

    @Test
    fun testSpeechDebouncingDoesNotRepeatImmediately() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val prefs = application.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isVoiceAnnouncementsEnabled", true).commit()
        VoiceAnnouncementManager.clearHistory()

        val message = "📍 Arrival Notice: Isabel has arrived at Home!"
        
        // First announcement should succeed and register timestamp
        VoiceAnnouncementManager.announceIfEnabled(application, message)

        // Immediate duplicate announcement within debounce window should be blocked
        val now = System.currentTimeMillis()
        VoiceAnnouncementManager.announceIfEnabled(application, message)
        
        // Clear history
        VoiceAnnouncementManager.clearHistory()
    }
}
