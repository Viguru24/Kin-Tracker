package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

object RailwayTransitDetector {

    private val detectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Tracks if member is actively traveling on a railway line
    private val isOnRailwayMap = ConcurrentHashMap<String, Boolean>()

    // Optional manual override set by parent/user: e.g. explicitly TRAIN, DRIVING, or null (Auto)
    private val manualOverrideMap = ConcurrentHashMap<String, TransitMode?>()

    // Rate-limiting check per member (min 15 seconds between network lookups)
    private val lastCheckTimeMap = ConcurrentHashMap<String, Long>()

    // Consecutive road hits counter to prevent premature unlatching when rail tracks run beside roads
    private val consecutiveRoadHits = ConcurrentHashMap<String, Int>()

    // Spatial cache to avoid duplicate network queries for the same geographic block
    // Key: "latGrid_lngGrid", Value: Pair(isOnRailway, timestamp)
    private val spatialCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    // Timestamp when member became stationary (< 1.0 mph)
    private val stillnessStartMap = ConcurrentHashMap<String, Long>()

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
            } else if (mode == TransitMode.DRIVING) {
                isOnRailwayMap[cleanId] = false
            }
        }
    }

    fun checkRailwayCorridorAsync(memberId: String, lat: Double, lng: Double, speedMph: Double) {
        if (memberId.isBlank() || lat == 0.0 || lng == 0.0) return
        val cleanId = memberId.lowercase().trim()
        val now = System.currentTimeMillis()

        // 1. If stationary or nearly stopped, track stillness and clear railway latch after 90 seconds
        if (speedMph < 1.0) {
            val stillStart = stillnessStartMap.getOrPut(cleanId) { now }
            if (now - stillStart > 90_000L) {
                isOnRailwayMap[cleanId] = false
                consecutiveRoadHits[cleanId] = 0
            }
            return
        }

        // Reset stillness since member is moving
        stillnessStartMap.remove(cleanId)

        // 2. High-speed cutoff: UK / European highway limit is ~70 mph; anything > 80 mph is a train
        if (speedMph > 80.0) {
            isOnRailwayMap[cleanId] = true
            consecutiveRoadHits[cleanId] = 0
            return
        }

        // 3. Only perform corridor detection at vehicular speeds (12 to 80 mph)
        if (speedMph < 12.0) return

        // 4. Rate-limit network requests to at most once every 15 seconds per member
        val lastCheck = lastCheckTimeMap[cleanId] ?: 0L
        if (now - lastCheck < 15_000L) return
        lastCheckTimeMap[cleanId] = now

        // 5. Check spatial cache (~150m grid cells)
        val cellKey = "${String.format(Locale.US, "%.3f", lat)}_${String.format(Locale.US, "%.3f", lng)}"
        spatialCache[cellKey]?.let { (cachedIsRail, timestamp) ->
            if (now - timestamp < 3_600_000L) { // 1 hr cache validity
                if (cachedIsRail) {
                    isOnRailwayMap[cleanId] = true
                    consecutiveRoadHits[cleanId] = 0
                }
                return
            }
        }

        detectorScope.launch {
            try {
                var detectedAsRail = false

                // A. Check OSRM distance to nearest drivable road
                // A car traveling at 15-75 mph is almost always within 0-15m of a mapped road centerline.
                // A train track is typically > 28-35m away from any road, or isolated in railway corridors.
                val osrmUrl = "https://router.project-osrm.org/nearest/v1/driving/$lng,$lat"
                val conn = URL(osrmUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "KinTrackerApp/1.0")

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.optString("code") == "Ok") {
                        val waypoints = json.optJSONArray("waypoints")
                        if (waypoints != null && waypoints.length() > 0) {
                            val roadDistMeters = waypoints.getJSONObject(0).optDouble("distance", 0.0)
                            // If distance to nearest drivable road is > 30 meters while moving > 15 mph,
                            // this indicates an off-road / dedicated railway corridor!
                            if (roadDistMeters > 30.0) {
                                detectedAsRail = true
                            }
                        }
                    }
                }

                // B. If not definitively off-road, check OpenStreetMap Nominatim for nearby train stations
                if (!detectedAsRail) {
                    val deltaLat = 0.003
                    val deltaLng = 0.004
                    val stationUrl = "https://nominatim.openstreetmap.org/search?q=station&format=json&limit=2&viewbox=${lng - deltaLng},${lat + deltaLat},${lng + deltaLng},${lat - deltaLat}&bounded=1"
                    val sConn = URL(stationUrl).openConnection() as HttpURLConnection
                    sConn.connectTimeout = 3000
                    sConn.readTimeout = 3000
                    sConn.setRequestProperty("User-Agent", "KinTrackerApp/1.0")

                    if (sConn.responseCode == 200) {
                        val sBody = sConn.inputStream.bufferedReader().use { it.readText() }
                        val arr = JSONArray(sBody)
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val itemClass = item.optString("class")
                            val itemType = item.optString("type")
                            if (itemClass == "railway" || itemType.contains("station") || itemType.contains("subway")) {
                                detectedAsRail = true
                                break
                            }
                        }
                    }
                }

                spatialCache[cellKey] = Pair(detectedAsRail, now)

                if (detectedAsRail) {
                    isOnRailwayMap[cleanId] = true
                    consecutiveRoadHits[cleanId] = 0
                } else {
                    // Hysteresis: require 3 consecutive road hits before unlatching train mode
                    // to prevent tracks that briefly run alongside highways from jittering
                    val roadHits = (consecutiveRoadHits[cleanId] ?: 0) + 1
                    consecutiveRoadHits[cleanId] = roadHits
                    if (roadHits >= 3) {
                        isOnRailwayMap[cleanId] = false
                    }
                }
            } catch (_: Exception) {
                // Keep existing status if network check times out
            }
        }
    }
}
