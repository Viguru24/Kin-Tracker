package com.example

import com.example.data.FamilyMember
import com.example.ui.map.TrailRouteEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailRouteEngineTest {

    @Test
    fun `parseOsrmRouteGeometry decodes GeoJSON lng-lat to lat-lng and rejects errors`() {
        val okJson = """
            {
              "code": "Ok",
              "routes": [{
                "geometry": {
                  "type": "LineString",
                  "coordinates": [[-0.119095, 51.329480], [-0.121000, 51.333000], [-0.125187, 51.341475]]
                }
              }]
            }
        """.trimIndent()

        val parsed = TrailRouteEngine.parseOsrmRouteGeometry(okJson)
        assertEquals(3, parsed.size)
        assertEquals(51.329480, parsed[0].first, 1e-6)
        assertEquals(-0.119095, parsed[0].second, 1e-6)
        assertEquals(51.341475, parsed[2].first, 1e-6)
        assertEquals(-0.125187, parsed[2].second, 1e-6)

        assertTrue(TrailRouteEngine.parseOsrmRouteGeometry("""{"code":"NoRoute","routes":[]}""").isEmpty())
        assertTrue(TrailRouteEngine.parseOsrmRouteGeometry("").isEmpty())
    }

    @Test
    fun `parseOsrmMatchGeometry stitches matchings cleanly`() {
        val matchJson = """
            {
              "code": "Ok",
              "matchings": [
                {"geometry": {"coordinates": [[-0.119095, 51.329480], [-0.120500, 51.331500]]}},
                {"geometry": {"coordinates": [[-0.120500, 51.331500], [-0.122000, 51.334000], [-0.125187, 51.341475]]}}
              ]
            }
        """.trimIndent()

        val matched = TrailRouteEngine.parseOsrmMatchGeometry(matchJson)
        assertEquals(4, matched.size)
        assertEquals(51.329480, matched.first().first, 1e-6)
        assertEquals(51.341475, matched.last().first, 1e-6)
    }

    @Test
    fun `failed OSRM response is never cached and retries recover full street geometry`() {
        val cache = ConcurrentHashMap<String, List<Pair<Double, Double>>>()
        val raw = listOf(
            Pair(51.329480, -0.119095),
            Pair(51.341475, -0.125187)
        )

        val first = TrailRouteEngine.resolveRoadSnappedTrail(
            rawPoints = raw,
            speedMph = 3.2,
            statusText = "Walking",
            memberId = "annette",
            routeCache = cache,
            httpGet = { null }
        )
        assertFalse("Transient failure must report unsnapped", first.usedRoadNetwork)
        assertFalse("Transient failure must report sparse gaps remaining", first.allGapsSnapped)
        assertTrue("Failures must never be written to routeCache", cache.isEmpty())

        val roadJson = """
            {
              "code": "Ok",
              "routes": [{
                "geometry": {
                  "coordinates": [
                    [-0.119095, 51.329480],
                    [-0.120200, 51.331100],
                    [-0.121900, 51.334200],
                    [-0.123500, 51.338100],
                    [-0.125187, 51.341475]
                  ]
                }
              }]
            }
        """.trimIndent()

        val second = TrailRouteEngine.resolveRoadSnappedTrail(
            rawPoints = raw,
            speedMph = 3.2,
            statusText = "Walking",
            memberId = "annette",
            routeCache = cache,
            httpGet = { roadJson }
        )
        assertTrue(second.usedRoadNetwork)
        assertTrue(second.allGapsSnapped)
        assertEquals(5, second.points.size)
        assertFalse(cache.isEmpty())
    }

    @Test
    fun `sparse trail with 3 or more waypoints still road-snaps every gap above 65m`() {
        val cache = ConcurrentHashMap<String, List<Pair<Double, Double>>>()
        val sparseWaypoints = listOf(
            Pair(51.329480, -0.119095),
            Pair(51.335000, -0.121000),
            Pair(51.341475, -0.125187)
        )
        val calls = AtomicInteger(0)

        val res = TrailRouteEngine.resolveRoadSnappedTrail(
            rawPoints = sparseWaypoints,
            speedMph = 24.0,
            statusText = "Driving",
            memberId = "dad",
            routeCache = cache,
            httpGet = { url ->
                calls.incrementAndGet()
                if (url.contains("/match/v1/")) {
                    null // Force fallback to per-segment /route/v1/ snapping
                } else if (url.contains("51.329480")) {
                    """{"code":"Ok","routes":[{"geometry":{"coordinates":[[-0.119095,51.329480],[-0.119800,51.331800],[-0.120400,51.333500],[-0.121000,51.335000]]}}]}"""
                } else {
                    """{"code":"Ok","routes":[{"geometry":{"coordinates":[[-0.121000,51.335000],[-0.122500,51.337900],[-0.124100,51.339800],[-0.125187,51.341475]]}}]}"""
                }
            }
        )

        assertTrue(calls.get() >= 3)
        assertTrue(res.usedRoadNetwork)
        assertTrue(res.allGapsSnapped)
        assertEquals(7, res.points.size)
    }

    @Test
    fun `dense trail under 65m and rail trips do not query OSRM`() {
        val cache = ConcurrentHashMap<String, List<Pair<Double, Double>>>()
        val calls = AtomicInteger(0)
        val densePoints = listOf(
            Pair(51.329480, -0.119095),
            Pair(51.329680, -0.119120),
            Pair(51.329890, -0.119150),
            Pair(51.330100, -0.119180)
        )

        val denseRes = TrailRouteEngine.resolveRoadSnappedTrail(
            rawPoints = densePoints,
            speedMph = 3.0,
            statusText = "Walking",
            memberId = "annette",
            routeCache = cache,
            httpGet = {
                calls.incrementAndGet()
                throw AssertionError("Dense trail should not call OSRM")
            }
        )
        assertEquals(0, calls.get())
        assertTrue(denseRes.allGapsSnapped)
        assertEquals(4, denseRes.points.size)

        val sparseRail = listOf(
            Pair(51.329480, -0.119095),
            Pair(51.375000, -0.100000)
        )
        val railRes = TrailRouteEngine.resolveRoadSnappedTrail(
            rawPoints = sparseRail,
            speedMph = 55.0,
            statusText = "TRAIN",
            memberId = "rail_tester",
            routeCache = cache,
            httpGet = {
                calls.incrementAndGet()
                throw AssertionError("Rail trip should not call OSRM")
            }
        )
        assertEquals(0, calls.get())
        assertEquals(2, railRes.points.size)
    }

    @Test
    fun `findTrailForMember does not confuse Eloise with eloise TAB`() {
        val eloisePhone = FamilyMember(id = "eloise_phone", name = "Eloise", avatarColorHex = "#10B981")
        val eloiseTab = FamilyMember(id = "eloise_tab", name = "eloise TAB", avatarColorHex = "#10B981")
        val isabelTab = FamilyMember(id = "isabels_tab", name = "Isabels Tab", avatarColorHex = "#10B981")

        val trails = mapOf(
            "eloise_phone" to listOf(Pair(51.330, -0.120), Pair(51.335, -0.122))
        )

        assertEquals(2, TrailRouteEngine.findTrailForMember(trails, eloisePhone)?.size)
        assertNull("Tablet must not inherit phone's trail via prefix match", TrailRouteEngine.findTrailForMember(trails, eloiseTab))
        assertNull(TrailRouteEngine.findTrailForMember(trails, isabelTab))
    }
}
