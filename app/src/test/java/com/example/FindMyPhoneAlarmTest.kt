package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AlarmHelper
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
class FindMyPhoneAlarmTest {

    @Before
    fun setup() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("groupSyncToken", "test_circle_isolated_unit_test")
            .commit()
    }

    @After
    fun teardown() {
        AlarmHelper.stopAlarm()
    }

    @Test
    fun testFindMyPhoneTriggerSetsAlarmState() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FamilyViewModel(application)

        val targetMemberId = "test_member_alpha"

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

        val targetMemberId = "test_member_beta"

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

        val targetMemberId = "test_member_gamma"

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
