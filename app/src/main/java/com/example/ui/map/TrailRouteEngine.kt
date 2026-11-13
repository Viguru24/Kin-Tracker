package com.example.ui.map

import com.example.data.FamilyMember
import com.example.data.GeoUtils
import com.example.data.IdentityUtils
import com.example.data.RailwayTransitDetector
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class RouteResolveResult(
    val points: List<Pair<Double, Double>>,
    val usedRoadNetwork: Boolean,
    val allGapsSnapped: Boolean
)

/**
 * Pure road-snapping & OSRM geometry engine for live breadcrumb trails and completed trips.
 * Keeps failed network calls out of [routeCache] so offline straight-line fallbacks never stick.
 */
object TrailRouteEngine {

    const val MAX_DIRECT_SEGMENT_METERS = 65.0
    const val MIN_WAYPOINT_SPACING_METERS = 18.0
    const val MAX_MATCH_WAYPOINTS = 50
    private const val OSRM_BASE_URL = "https://router.project-osrm.org"

    fun buildSegmentCacheKey(profile: String, from: Pair<Double, Double>, to: Pair<Double, Double>): String {
        return "${profile}_${String.format(Locale.US, "%.5f", from.first)}_${String.format(Locale.US, "%.5f", from.second)}_${String.format(Locale.US, "%.5f", to.first)}_${String.format(Locale.US, "%.5f", to.second)}"
    }

    fun selectOsrmProfiles(speedMph: Double, statusText: String, memberId: String): List<String> {
        val statusLower = statusText.lowercase(Locale.US)
        if (statusLower.contains("train") ||
            statusLower.contains("rail") ||
            statusText.contains("🚆") ||
            RailwayTransitDetector.isMemberOnRailway(memberId)
        ) {
            return emptyList()
        }
        return when {
            statusLower.contains("driv") || statusLower.contains("car") || statusText.contains("🚗") || speedMph >= 16.0 ->
                listOf("driving", "walking")
            statusLower.contains("cycl") || statusLower.contains("bike") || statusText.contains("🚲") || speedMph >= 5.0 ->
                listOf("cycling", "walking", "driving")
            else ->
                listOf("walking", "driving")
        }
    }

    fun deduplicateWaypoints(
        points: List<Pair<Double, Double>>,
        minSpacingMeters: Double = MIN_WAYPOINT_SPACING_METERS
    ): List<Pair<Double, Double>> {
        val valid = points.filter { it.first != 0.0 && it.second != 0.0 && it.first.isFinite() && it.second.isFinite() }
        if (valid.size <= 2) return valid

        val out = mutableListOf(valid.first())
        for (i in 1 until valid.size - 1) {
            val prev = out.last()
            val cur = valid[i]
            if (GeoUtils.distanceMeters(prev.first, prev.second, cur.first, cur.second) >= minSpacingMeters) {
                out.add(cur)
            }
        }
        val tail = valid.last()
        val last = out.last()
        if (GeoUtils.distanceMeters(last.first, last.second, tail.first, tail.second) >= 3.0 || out.size == 1) {
            if (last != tail) out.add(tail)
        } else if (out.size >= 2) {
            out[out.lastIndex] = tail
        }
        return out
    }

    fun hasSparseGaps(
        points: List<Pair<Double, Double>>,
        maxGapMeters: Double = MAX_DIRECT_SEGMENT_METERS
    ): Boolean {
        if (points.size < 2) return false
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (GeoUtils.distanceMeters(a.first, a.second, b.first, b.second) > maxGapMeters) {
                return true
            }
        }
        return false
    }

    fun parseOsrmRouteGeometry(json: String): List<Pair<Double, Double>> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            if (!root.optString("code").equals("Ok", ignoreCase = true)) return emptyList()
            val routes = root.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()
            val coords = routes.optJSONObject(0)?.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: return emptyList()
            extractLatLngArray(coords)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseOsrmMatchGeometry(json: String): List<Pair<Double, Double>> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            if (!root.optString("code").equals("Ok", ignoreCase = true)) return emptyList()
            val matchings = root.optJSONArray("matchings") ?: return emptyList()
            if (matchings.length() == 0) return emptyList()
            val stitched = mutableListOf<Pair<Double, Double>>()
            for (i in 0 until matchings.length()) {
                val coords = matchings.optJSONObject(i)?.optJSONObject("geometry")?.optJSONArray("coordinates")
                    ?: continue
                val segment = extractLatLngArray(coords)
                for (pt in segment) {
                    if (stitched.isEmpty() || stitched.last() != pt) {
                        stitched.add(pt)
                    }
                }
            }
            if (stitched.size >= 2) stitched else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractLatLngArray(coords: org.json.JSONArray): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until coords.length()) {
            val pair = coords.optJSONArray(i) ?: continue
            if (pair.length() < 2) continue
            val lng = pair.optDouble(0, Double.NaN)
            val lat = pair.optDouble(1, Double.NaN)
            if (lat.isFinite() && lng.isFinite() && (lat != 0.0 || lng != 0.0)) {
                out.add(Pair(lat, lng))
            }
        }
        return if (out.size >= 2) out else emptyList()
    }

    fun sampleWaypoints(points: List<Pair<Double, Double>>, maxCount: Int = MAX_MATCH_WAYPOINTS): List<Pair<Double, Double>> {
        if (points.size <= maxCount) return points
        val out = ArrayList<Pair<Double, Double>>(maxCount)
        val step = (points.size - 1).toDouble() / (maxCount - 1).toDouble()
        for (i in 0 until maxCount - 1) {
            val idx = kotlin.math.round(i * step).toInt().coerceIn(0, points.size - 1)
            val pt = points[idx]
            if (out.isEmpty() || out.last() != pt) out.add(pt)
        }
        if (out.last() != points.last()) out.add(points.last())
        return out
    }

    fun defaultHttpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "KinTracker-Android/2.8")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun fetchOsrmSegmentRoute(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        profiles: List<String>,
        routeCache: ConcurrentHashMap<String, List<Pair<Double, Double>>>,
        httpGet: (String) -> String? = ::defaultHttpGet
    ): List<Pair<Double, Double>>? {
        if (profiles.isEmpty()) return null
        val distM = GeoUtils.distanceMeters(from.first, from.second, to.first, to.second)
        if (distM < 15.0) return listOf(from, to)

        val primaryProfile = profiles.first()
        val primaryKey = buildSegmentCacheKey(primaryProfile, from, to)
        routeCache[primaryKey]?.let { return it }

        for (profile in profiles) {
            val urlStr = String.format(
                Locale.US,
                "%s/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                OSRM_BASE_URL, profile, from.second, from.first, to.second, to.first
            )
            val body = httpGet(urlStr) ?: continue
            val parsed = parseOsrmRouteGeometry(body)
            if (parsed.size >= 2) {
                routeCache[primaryKey] = parsed
                return parsed
            }
        }
        return null
    }

    private fun tryMatchTrace(
        waypoints: List<Pair<Double, Double>>,
        profile: String,
        httpGet: (String) -> String?
    ): List<Pair<Double, Double>>? {
        if (waypoints.size < 3) return null
        val sampled = sampleWaypoints(waypoints, MAX_MATCH_WAYPOINTS)
        val coordsStr = sampled.joinToString(";") { pt ->
            String.format(Locale.US, "%.6f,%.6f", pt.second, pt.first)
        }
        val radiusStr = sampled.joinToString(";") { "45" }
        val urlStr = "$OSRM_BASE_URL/match/v1/$profile/$coordsStr?overview=full&geometries=geojson&gaps=ignore&tidy=true&radiuses=$radiusStr"
        val body = httpGet(urlStr) ?: return null
        val matched = parseOsrmMatchGeometry(body)
        if (matched.size < 2) return null

        val startErr = GeoUtils.distanceMeters(sampled.first().first, sampled.first().second, matched.first().first, matched.first().second)
        val endErr = GeoUtils.distanceMeters(sampled.last().first, sampled.last().second, matched.last().first, matched.last().second)
        if (startErr > 250.0 || endErr > 250.0) return null
        if (hasSparseGaps(matched, MAX_DIRECT_SEGMENT_METERS * 2.0)) return null
        return matched
    }

    /**
     * Resolves a sparse or un-routed breadcrumb list into a road-following polyline.
     * Failed segments leave [allGapsSnapped] = false and do NOT write to [routeCache].
     */
    fun resolveRoadSnappedTrail(
        rawPoints: List<Pair<Double, Double>>,
        speedMph: Double,
        statusText: String,
        memberId: String,
        routeCache: ConcurrentHashMap<String, List<Pair<Double, Double>>>,
        httpGet: (String) -> String? = ::defaultHttpGet
    ): RouteResolveResult {
        val cleaned = deduplicateWaypoints(rawPoints)
        if (cleaned.size < 2) {
            return RouteResolveResult(cleaned, usedRoadNetwork = false, allGapsSnapped = true)
        }

        val profiles = selectOsrmProfiles(speedMph, statusText, memberId)
        if (profiles.isEmpty() || !hasSparseGaps(cleaned, MAX_DIRECT_SEGMENT_METERS)) {
            return RouteResolveResult(
                points = cleaned,
                usedRoadNetwork = profiles.isEmpty() || cleaned.size > 2,
                allGapsSnapped = true
            )
        }

        // First try multi-point GPS map matching for sparse traces (3..50 waypoints)
        if (cleaned.size >= 3) {
            val primaryProfile = profiles.first()
            val matched = tryMatchTrace(cleaned, primaryProfile, httpGet)
            if (matched != null) {
                return RouteResolveResult(matched, usedRoadNetwork = true, allGapsSnapped = true)
            }
        }

        // Fallback: stitch dense segments directly and road-route each sparse >65m gap cleanly
        val stitched = mutableListOf(cleaned.first())
        var usedRoad = false
        var allSnapped = true

        for (i in 0 until cleaned.size - 1) {
            val from = cleaned[i]
            val to = cleaned[i + 1]
            val segDistM = GeoUtils.distanceMeters(from.first, from.second, to.first, to.second)
            if (segDistM <= MAX_DIRECT_SEGMENT_METERS) {
                if (stitched.last() != to) stitched.add(to)
            } else {
                val routed = fetchOsrmSegmentRoute(from, to, profiles, routeCache, httpGet)
                if (routed != null && routed.size >= 2) {
                    usedRoad = true
                    for (pt in routed) {
                        if (stitched.last() != pt) stitched.add(pt)
                    }
                    if (GeoUtils.distanceMeters(stitched.last().first, stitched.last().second, to.first, to.second) in 5.0..60.0) {
                        stitched.add(to)
                    }
                } else {
                    allSnapped = false
                    if (stitched.last() != to) stitched.add(to)
                }
            }
        }

        return RouteResolveResult(
            points = stitched,
            usedRoadNetwork = usedRoad,
            allGapsSnapped = allSnapped
        )
    }

    /**
     * Looks up a member's trail safely without collapsing "Eloise" with "eloise TAB" or "Isabel" with "Isabels Tab".
     */
    fun findTrailForMember(
        trails: Map<String, List<Pair<Double, Double>>>,
        member: FamilyMember
    ): List<Pair<Double, Double>>? {
        trails[member.id]?.let { if (it.isNotEmpty()) return it }
        val cleanKey = IdentityUtils.getCanonicalPersonKey(member.name, member.id)
        trails[cleanKey]?.let { if (it.isNotEmpty()) return it }
        val lowerName = member.name.lowercase(Locale.US).trim()
        trails[lowerName]?.let { if (it.isNotEmpty()) return it }
        return null
    }
}
