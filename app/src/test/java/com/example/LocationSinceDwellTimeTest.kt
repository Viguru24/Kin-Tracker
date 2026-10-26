package com.example

import com.example.data.formatDuration
import com.example.data.formatTransitBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSinceDwellTimeTest {

    @Test
    fun testFormatDurationLongStationaryDwell() {
        val now = System.currentTimeMillis()
        val tenHoursThirtyTwoMinutesMs = (10 * 3600 + 32 * 60) * 1000L
        val arrivalTime = now - tenHoursThirtyTwoMinutesMs

        val durationStr = formatDuration(arrivalTime)
        assertEquals("10h 32m", durationStr)

        val badge = formatTransitBadge(
            speedMph = 0.0,
            statusText = "Live GPS tracking (56.61 km away)",
            locationSince = arrivalTime
        )
        assertEquals("📍 here 10h 32m", badge)
    }

    @Test
    fun testFormatDurationDoesNotArbitrarilyRevertToFortyEightMinutes() {
        val now = System.currentTimeMillis()
        val tenHoursAgo = now - (10L * 3600 * 1000L)
        val fortyEightMinsAgo = now - (48L * 60 * 1000L)

        val tenHourBadge = formatTransitBadge(0.0, "Live GPS", tenHoursAgo)
        val fortyEightBadge = formatTransitBadge(0.0, "Live GPS", fortyEightMinsAgo)

        assertTrue(tenHourBadge.contains("10h"))
        assertTrue(fortyEightBadge.contains("48m"))
        // Confirms that a 10h dwell is not collapsed into 48m
        assertTrue(tenHourBadge != fortyEightBadge)
    }

    @Test
    fun testFormatDurationMovingResetsDwell() {
        val now = System.currentTimeMillis()
        val tenHoursAgo = now - (10L * 3600 * 1000L)

        // Moving at 4.0 mph (walking)
        val movingBadge = formatTransitBadge(4.0, "Moving", tenHoursAgo)
        assertEquals("👣 4.0 mph", movingBadge)

        // Driving at 30.0 mph
        val drivingBadge = formatTransitBadge(30.0, "Driving", tenHoursAgo)
        assertEquals("🚗 30 mph", drivingBadge)
    }
}
