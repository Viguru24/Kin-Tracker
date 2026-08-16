package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.FamilyViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceAnnouncementsTest {

    @Test
    fun testVoiceAnnouncementsDefaultAndToggle() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)
        
        // Assert voice announcements setting is off by default
        assertFalse(viewModel.isVoiceAnnouncementsEnabled.value)
        
        // Toggle it on and assert
        viewModel.toggleVoiceAnnouncements(true)
        assertTrue(viewModel.isVoiceAnnouncementsEnabled.value)
        
        // Toggle it off and assert
        viewModel.toggleVoiceAnnouncements(false)
        assertFalse(viewModel.isVoiceAnnouncementsEnabled.value)
    }
}
