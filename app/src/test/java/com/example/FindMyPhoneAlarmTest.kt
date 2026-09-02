package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AlarmHelper
import com.example.ui.FamilyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FindMyPhoneAlarmTest {

    @Test
    fun testFindMyPhoneTriggerSetsAlarmState() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        val targetMemberId = "annette"

        // Trigger Find My Phone
        viewModel.triggerFindMyPhone(targetMemberId)
        ShadowLooper.idleMainLooper()

        // Assert member is marked in active ringing set
        val ringingSet = viewModel.activeRingingMembers.value
        assertTrue("Target member must be in active ringing set", ringingSet.contains(targetMemberId))
    }

    @Test
    fun testFindMyPhoneRemoteStop() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        val targetMemberId = "isabel"

        // Start alarm
        viewModel.triggerFindMyPhone(targetMemberId)
        ShadowLooper.idleMainLooper()
        assertTrue(viewModel.activeRingingMembers.value.contains(targetMemberId))

        // Remotely stop alarm from triggering phone
        viewModel.stopFindMyPhone(targetMemberId)
        ShadowLooper.idleMainLooper()
        val ringingSetAfterStop = viewModel.activeRingingMembers.value
        assertFalse("Target member must be removed after remote stop", ringingSetAfterStop.contains(targetMemberId))
    }

    @Test
    fun testFindMyPhoneToggle() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        val targetMemberId = "eloise"

        // First toggle turns it ON
        viewModel.toggleFindMyPhone(targetMemberId)
        ShadowLooper.idleMainLooper()
        assertTrue(viewModel.activeRingingMembers.value.contains(targetMemberId))

        // Second toggle turns it OFF
        viewModel.toggleFindMyPhone(targetMemberId)
        ShadowLooper.idleMainLooper()
        assertFalse(viewModel.activeRingingMembers.value.contains(targetMemberId))
    }

    @Test
    fun testAlarmHelperSafetyStop() {
        // Calling stop when not ringing must be safe and idempotent
        AlarmHelper.stopAlarm()
        assertFalse(AlarmHelper.isRinging)
    }
}
