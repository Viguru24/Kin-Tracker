package com.example

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MapMarkerRenderer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapMarkerRendererTest {

    @Test
    fun `unselected profile marker is compact and smaller than selected marker`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density

        val unselected = MapMarkerRenderer.getOrCreateMarkerDrawable(
            context = context,
            colorHex = "#10B981",
            emoji = "👩",
            isSelected = false
        ) as BitmapDrawable

        val selected = MapMarkerRenderer.getOrCreateMarkerDrawable(
            context = context,
            colorHex = "#10B981",
            emoji = "👩",
            isSelected = true
        ) as BitmapDrawable

        val unselectedWidthDp = unselected.bitmap.width / density
        val unselectedHeightDp = unselected.bitmap.height / density

        assertTrue(
            "Expected compact unselected width <= 42dp, got $unselectedWidthDp",
            unselectedWidthDp <= 42f
        )
        assertTrue(
            "Expected compact unselected height <= 46dp, got $unselectedHeightDp",
            unselectedHeightDp <= 46f
        )
        assertTrue(
            "Selected marker width should exceed unselected width",
            selected.bitmap.width > unselected.bitmap.width
        )
        assertTrue(
            "Selected marker height should exceed unselected height",
            selected.bitmap.height > unselected.bitmap.height
        )

        val clustered = MapMarkerRenderer.getOrCreateMarkerDrawable(
            context = context,
            colorHex = "#10B981",
            emoji = "👩",
            isSelected = false,
            showStem = false
        ) as BitmapDrawable
        assertTrue(
            "Clustered marker without stem should be shorter than stemmed marker",
            clustered.bitmap.height < unselected.bitmap.height
        )
        val anchorV = MapMarkerRenderer.getMarkerAnchorV(context, isSelected = false, showStem = false)
        assertTrue("Clustered marker vertical anchor should center around 0.5 (got $anchorV)", anchorV in 0.40f..0.60f)
        val hubDot = MapMarkerRenderer.getOrCreateClusterAnchorDot(context) as BitmapDrawable
        assertTrue("Anchor dot must remain compact (<= 14dp)", hubDot.bitmap.width / density <= 14f)
    }

    @Test
    fun `home and zone icons are compact and below 48dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density

        val home = MapMarkerRenderer.getOrCreateHomeMarkerDrawable(context) as BitmapDrawable
        val zone = MapMarkerRenderer.getOrCreateZoneIcon(context, "School", "school") as BitmapDrawable

        assertTrue("Home icon width should be <= 48dp", home.bitmap.width / density <= 48f)
        assertTrue("Home icon height should be <= 56dp", home.bitmap.height / density <= 56f)
        assertTrue("Zone icon should be <= 34dp", zone.bitmap.width / density <= 34f)
    }

    @Test
    fun `wide pills and corner badges expand bitmap width to prevent clipping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val bare = MapMarkerRenderer.getOrCreateMarkerDrawable(
            context = context,
            colorHex = "#38BDF8",
            emoji = "👨",
            isSelected = false
        ) as BitmapDrawable

        val withPillsAndBadges = MapMarkerRenderer.getOrCreateMarkerDrawable(
            context = context,
            colorHex = "not-a-color",
            emoji = "👨",
            isSelected = false,
            photoPath = "/non/existent/photo.jpg",
            relativeTime = "14m ago",
            locationDuration = "🚗 45 mph",
            batteryPercentage = 15,
            isCharging = false
        ) as BitmapDrawable

        assertNotNull(withPillsAndBadges.bitmap)
        assertTrue(
            "Wide status pills and corner badges must widen bitmap beyond bare bubble (${bare.bitmap.width}px)",
            withPillsAndBadges.bitmap.width >= bare.bitmap.width
        )
        assertTrue(
            "Top and bottom pills must increase bitmap height beyond bare bubble (${bare.bitmap.height}px)",
            withPillsAndBadges.bitmap.height > bare.bitmap.height
        )
    }
}
