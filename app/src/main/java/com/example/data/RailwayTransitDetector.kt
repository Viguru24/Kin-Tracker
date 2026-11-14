package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.hypot

object RailwayTransitDetector {

    enum class CorridorVerdict {
        STRONG_RAIL,
        CORRIDOR_HIT,
        ROAD_OR_PATH_ONLY
    }

    private val detectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val isOnRailwayMap = ConcurrentHashMap<String, Boolean>()
    private val manualOverrideMap = ConcurrentHashMap<String, TransitMode?>()
    private val lastCheckTimeMap = ConcurrentHashMap<String, Long>()
    private val consecutiveRoadHits = ConcurrentHashMap<String, Int>()
    private val consecutiveRailHits = ConcurrentHashMap<String, Int>()
    private val lastRailHitCell = ConcurrentHashMap<String, String>()
    private val spatialCache = ConcurrentHashMap<String, Pair<CorridorVerdict, Long>>()
    private val stillnessStartMap = ConcurrentHashMap<String, Long>()

    fun clearAllStateForTests() {
        isOnRailwayMap.clear()
        manualOverrideMap.clear()
        lastCheckTimeMap.clear()
        consecutiveRoadHits.clear()
        consecutiveRailHits.clear()
        lastRailHitCell.clear()
        spatialCache.clear()
        stillnessStartMap.clear()
    }

    fun isMemberOnRailway(memberId: String): Boolean {
        if (memberId.isBlank()) return false
        val cleanId = memberId.lowercase().trim()
        return isOnRailwayMap[cleanId] == true ||
            isOnRailwayMap.entries.firstOrNull { it.key.contains(cleanId) || cleanId.contains(it.key) }?.value == true
    }

    fun getManualOverride(memberId: String): TransitMode? {
        if (memberId.isBlank()) return null
        val cleanId = memberId.lowercase().trim()
        return manualOverrideMap[cleanId] ?:
            manualOverrideMap.entries.firstOrNull { it.key.contains(cleanId) || cleanId.contains(it.key) }?.value
    }

    fun setManualOverride(memberId: String, mode: TransitMode?) {
        if (memberId.isBlank()) return
        val cleanId = memberId.lowercase().trim()
        if (mode == null) {
            manualOverrideMap.remove(cleanId)
        } else {
            manualOverrideMap[cleanId] = mode
            if (mode == TransitMode.TRAIN) {
                isOnRailwayMap[cleanId] = true
            } else if (mode == TransitMode.DRIVING || mode == TransitMode.CYCLING || mode == TransitMode.WALKING) {
                isOnRailwayMap[cleanId] = false
            }
        }
    }

    /**
     * Planar local projection perpendicular distance in meters from point P to segment AB.
     */
    fun pointToSegmentMeters(
        pLat: Double,
        pLng: Double,
        aLat: Double,
        aLng: Double,
        bLat: Double,
        bLng: Double
    ): Double {
        val mPerDegLat = 111_000.0
        val mPerDegLng = 111_000.0 * cos(Math.toRadians(pLat)).coerceAtLeast(0.1)
        val ax = (aLng - pLng) * mPerDegLng
        val ay = (aLat - pLat) * mPerDegLat
        val bx = (bLng - pLng) * mPerDegLng
        val by = (bLat - pLat) * mPerDegLat
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-9) return hypot(ax, ay)
        val t = (-(ax * dx + ay * dy) / lenSq).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return hypot(projX, projY)
    }

    /**
     * Disambiguates rail tracks vs drivable roads vs off-road cycleways/parks.
     * Key invariant: NEVER infer rail simply from being >30m off a car road (parks/towpaths);
     * a mapped OSM railway track must actually be present within track tolerance.
     */
    fun evaluateCorridorEvidence(
        roadDistMeters: Double?,
        trackDistMeters: Double?,
        speedMph: Double
    ): CorridorVerdict {
        if (speedMph >= 78.0) return CorridorVerdict.STRONG_RAIL
        if (trackDistMeters == null || trackDistMeters > 48.0) {
            return CorridorVerdict.ROAD_OR_PATH_ONLY
        }
        val roadDist = roadDistMeters ?: 999.0
        return when {
            roadDist >= 24.0 && trackDistMeters <= 35.0 && speedMph >= 12.0 -> CorridorVerdict.STRONG_RAIL
            speedMph >= 24.0 && trackDistMeters <= roadDist + 8.0 && trackDistMeters <= 32.0 -> CorridorVerdict.STRONG_RAIL
            trackDistMeters <= 38.0 && (roadDist >= 16.0 || speedMph >= 20.0) -> CorridorVerdict.CORRIDOR_HIT
            else -> CorridorVerdict.ROAD_OR_PATH_ONLY
        }
    }

    fun checkRailwayCorridorAsync(memberId: String, lat: Double, lng: Double, speedMph: Double) {
        if (memberId.isBlank() || lat == 0.0 || lng == 0.0) return
        val cleanId = memberId.lowercase().trim()
        val now = System.currentTimeMillis()

        if (speedMph < 1.0) {
            val stillStart = stillnessStartMap.getOrPut(cleanId) { now }
            if (now - stillStart > 90_000L) {
                isOnRailwayMap[cleanId] = false
                consecutiveRoadHits[cleanId] = 0
                consecutiveRailHits[cleanId] = 0
                lastRailHitCell.remove(cleanId)
            }
            return
        }

        stillnessStartMap.remove(cleanId)

        if (speedMph >= 78.0) {
            isOnRailwayMap[cleanId] = true
            consecutiveRoadHits[cleanId] = 0
            return
        }

        // Check rail corridor from 8 mph up to 78 mph so trains slowing through stations are caught
        if (speedMph < 8.0) return

        val lastCheck = lastCheckTimeMap[cleanId] ?: 0L
        if (now - lastCheck < 15_000L) return
        lastCheckTimeMap[cleanId] = now

        val cellKey = "${String.format(Locale.US, "%.3f", lat)}_${String.format(Locale.US, "%.3f", lng)}"
        spatialCache[cellKey]?.let { (cachedVerdict, timestamp) ->
            if (now - timestamp < 7_200_000L) {
                applyVerdict(cleanId, cellKey, cachedVerdict)
                return
            }
        }

        detectorScope.launch {
            try {
                val roadDist = queryNearestOsrmRoadMeters(lat, lng)
                val trackDist = queryNearestOverpassRailwayMeters(lat, lng)
                val verdict = evaluateCorridorEvidence(roadDist, trackDist, speedMph)
                spatialCache[cellKey] = Pair(verdict, now)
                applyVerdict(cleanId, cellKey, verdict)
            } catch (_: Exception) {
                // Keep existing latch on transient network timeouts
            }
        }
    }

    private fun applyVerdict(cleanId: String, cellKey: String, verdict: CorridorVerdict) {
        when (verdict) {
            CorridorVerdict.STRONG_RAIL -> {
                isOnRailwayMap[cleanId] = true
                consecutiveRoadHits[cleanId] = 0
                consecutiveRailHits[cleanId] = 2
                lastRailHitCell[cleanId] = cellKey
            }
            CorridorVerdict.CORRIDOR_HIT -> {
                val prevCell = lastRailHitCell[cleanId]
                val count = if (prevCell == null || prevCell == cellKey) {
                    (consecutiveRailHits[cleanId] ?: 1).coerceAtLeast(1)
                } else {
                    (consecutiveRailHits[cleanId] ?: 0) + 1
                }
                consecutiveRailHits[cleanId] = count
                lastRailHitCell[cleanId] = cellKey
                consecutiveRoadHits[cleanId] = 0
                if (count >= 2) {
                    isOnRailwayMap[cleanId] = true
                }
            }
            CorridorVerdict.ROAD_OR_PATH_ONLY -> {
                consecutiveRailHits[cleanId] = 0
                val roadHits = (consecutiveRoadHits[cleanId] ?: 0) + 1
                consecutiveRoadHits[cleanId] = roadHits
                if (roadHits >= 3) {
                    isOnRailwayMap[cleanId] = false
                    lastRailHitCell.remove(cleanId)
                }
            }
        }
    }

    private fun queryNearestOsrmRoadMeters(lat: Double, lng: Double): Double? {
        return try {
            val urlStr = "https://router.project-osrm.org/nearest/v1/driving/$lng,$lat"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "KinTrackerApp/2.8.7")
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (json.optString("code") == "Ok") {
                    json.optJSONArray("waypoints")?.takeIf { it.length() > 0 }
                        ?.getJSONObject(0)?.optDouble("distance")
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryNearestOverpassRailwayMeters(lat: Double, lng: Double): Double? {
        return try {
            val query = "[out:json][timeout:4];way[\"railway\"~\"^(rail|subway|light_rail|tram|narrow_gauge)\$\"][\"service\"!=\"siding\"][\"service\"!=\"yard\"](around:55,$lat,$lng);out geom;"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://overpass-api.de/api/interpreter?data=$encoded"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "KinTrackerApp/2.8.7")
            if (conn.responseCode != 200) return null
            val elements: JSONArray = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("elements")
                ?: return null
            var minDist = Double.MAX_VALUE
            for (i in 0 until elements.length()) {
                val geom = elements.getJSONObject(i).optJSONArray("geometry") ?: continue
                for (j in 0 until geom.length() - 1) {
                    val a = geom.getJSONObject(j)
                    val b = geom.getJSONObject(j + 1)
                    val d = pointToSegmentMeters(
                        lat, lng,
                        a.getDouble("lat"), a.getDouble("lon"),
                        b.getDouble("lat"), b.getDouble("lon")
                    )
                    if (d < minDist) minDist = d
                }
            }
            if (minDist < Double.MAX_VALUE) minDist else null
        } catch (_: Exception) {
            null
        }
    }
}
