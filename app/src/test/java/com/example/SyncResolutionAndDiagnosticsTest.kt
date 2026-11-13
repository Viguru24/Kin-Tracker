package com.example

import com.example.core.AppLogger
import com.example.data.CloudMember
import com.example.data.CloudShoppingItem
import com.example.data.ShoppingItem
import com.example.data.sync.SyncResolutionHelper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncResolutionAndDiagnosticsTest {

    @Before
    fun setup() {
        AppLogger.clearDiagnostics()
    }

    @After
    fun teardown() {
        AppLogger.clearDiagnostics()
    }

    @Test
    fun testIsValidSyncTokenRejectsBlankAndZeroHash() {
        assertFalse(SyncResolutionHelper.isValidSyncToken(null))
        assertFalse(SyncResolutionHelper.isValidSyncToken(""))
        assertFalse(SyncResolutionHelper.isValidSyncToken("   "))
        assertFalse(SyncResolutionHelper.isValidSyncToken("00000000_"))
        assertFalse(SyncResolutionHelper.isValidSyncToken("00000000_pin_group"))
        assertTrue(SyncResolutionHelper.isValidSyncToken("81e5632c_pin_group"))
    }

    @Test
    fun testShouldTriggerLocalAlarmTargetsOnlyMatchingDevice() {
        val now = 1_700_000_000_000L
        val tabletRinging = CloudMember(
            id = "device_eloisetab_r52xb0",
            name = "eloise TAB",
            statusText = "🚨 ALARM",
            alarmRequestedAt = now - 5_000L
        )
        val phoneIdle = CloudMember(
            id = "device_louis_999999",
            name = "Louis",
            statusText = "At Home",
            alarmRequestedAt = 0L
        )
        val members = listOf(tabletRinging, phoneIdle)

        assertFalse(
            "Tablet alarm must not ring Louis's phone",
            SyncResolutionHelper.shouldTriggerLocalAlarm(
                members = members,
                myCloudId = "device_louis_999999",
                myDeviceUuid = "999999",
                myDeviceName = "Louis",
                nowMs = now
            )
        )
        assertFalse(
            "Tablet alarm must not ring Eloise's phone via name substring",
            SyncResolutionHelper.shouldTriggerLocalAlarm(
                members = members,
                myCloudId = "device_eloise_abc123",
                myDeviceUuid = "abc123",
                myDeviceName = "Eloise",
                nowMs = now
            )
        )
        assertTrue(
            "Matching device UUID must trigger local alarm",
            SyncResolutionHelper.shouldTriggerLocalAlarm(
                members = members,
                myCloudId = "device_eloisetab_r52xb0",
                myDeviceUuid = "r52xb0",
                myDeviceName = "eloise TAB",
                nowMs = now
            )
        )

        val staleAlarmMember = CloudMember(
            id = "device_eloisetab_r52xb0",
            name = "eloise TAB",
            statusText = "At Home",
            alarmRequestedAt = now - 120_000L
        )
        assertFalse(
            "Expired alarmRequestedAt > 60s without alarm status must not trigger alarm",
            SyncResolutionHelper.shouldTriggerLocalAlarm(
                members = listOf(staleAlarmMember),
                myCloudId = "device_eloisetab_r52xb0",
                myDeviceUuid = "r52xb0",
                myDeviceName = "eloise TAB",
                nowMs = now
            )
        )
    }

    @Test
    fun testMergeShoppingListsRespectsTombstonesAndRetention() {
        val now = 1_700_000_000_000L
        val fortyDaysMs = 40L * 24 * 60 * 60 * 1000L
        val oneHourMs = 60L * 60 * 1000L

        val localItems = listOf(
            ShoppingItem(id = 1, name = "Milk", isChecked = false, addedByMemberId = "me", addedByMemberName = "Louis", timestamp = now - 2 * oneHourMs),
            ShoppingItem(id = 2, name = "Eggs", isChecked = false, addedByMemberId = "me", addedByMemberName = "Louis", timestamp = now - 3 * oneHourMs)
        )
        val cloudItems = listOf(
            CloudShoppingItem(name = "Milk", isChecked = true, addedByMemberId = "d2", addedByMemberName = "Annette", timestamp = now - oneHourMs),
            CloudShoppingItem(name = "Bread", isChecked = false, addedByMemberId = "d2", addedByMemberName = "Annette", timestamp = now - oneHourMs)
        )
        val localDeletions = mapOf(
            "eggs" to (now - oneHourMs),
            "expireditem" to (now - fortyDaysMs)
        )
        val incomingDeletions = mapOf(
            "oldremoved" to (now - fortyDaysMs)
        )

        val merged = SyncResolutionHelper.mergeShoppingListsPure(
            localItems = localItems,
            incomingItems = cloudItems,
            localDeletions = localDeletions,
            incomingDeletions = incomingDeletions,
            nowMs = now
        )

        val names = merged.finalItems.map { it.name }.toSet()
        assertEquals(setOf("Milk", "Bread"), names)
        assertTrue("Newer cloud checked state should win", merged.finalItems.first { it.name == "Milk" }.isChecked)
        assertTrue("Active 1h tombstone should be preserved", merged.mergedDeletions.containsKey("eggs"))
        assertFalse("Expired local >30d tombstone should be pruned", merged.mergedDeletions.containsKey("expireditem"))
        assertFalse("Expired cloud >30d tombstone should be pruned", merged.mergedDeletions.containsKey("oldremoved"))
    }

    @Test
    fun testAppLoggerDiagnosticRingBufferCapsAndCapturesErrors() {
        AppLogger.w("SyncTest") { "Transient warning" }
        AppLogger.e("SyncTest", "Critical sync fault", IllegalStateException("Timeout"))

        val initial = AppLogger.getRecentDiagnostics()
        assertEquals(2, initial.size)
        assertEquals("WARN", initial[0].level)
        assertEquals("Transient warning", initial[0].message)
        assertEquals("ERROR", initial[1].level)
        assertTrue(initial[1].throwableSummary?.contains("IllegalStateException") == true)

        for (i in 0 until 120) {
            AppLogger.e("StressTag", "Error #$i")
        }

        val capped = AppLogger.getRecentDiagnostics()
        assertEquals(AppLogger.MAX_DIAGNOSTIC_ENTRIES, capped.size)
        assertEquals("Error #20", capped.first().message)
        assertEquals("Error #119", capped.last().message)
    }
}
