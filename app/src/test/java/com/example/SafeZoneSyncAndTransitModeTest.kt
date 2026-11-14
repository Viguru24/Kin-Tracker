package com.example

import com.example.data.*
import com.example.data.sync.SyncResolutionHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SafeZoneSyncAndTransitModeTest {

    @Before
    fun reset() {
        TransitClassifier.clearForTests()
        InertialActivityDetector.clearForTests()
    }

    private fun zone(id: String, name: String = "Place $id") =
        SafeZone(id = id, name = name, latitude = 51.34, longitude = -0.13, radiusMeters = 100.0, iconName = "work")

    // ── Safe zone sync ─────────────────────────────────────────────────────

    @Test
    fun incomingCloudZoneIsAddedToLocal() {
        val now = 10_000_000L
        val cloud = CloudSafeZone("zone_a", "Westside Care Home", 51.3432902, -0.1330773, 150.0, "work", now - 1000)
        val r = SyncResolutionHelper.mergeSafeZonesPure(emptyList(), listOf(cloud), emptyMap(), emptyMap(), emptyMap(), now)
        assertEquals(listOf("zone_a"), r.finalZones.map { it.id })
    }

    @Test
    fun newerCloudEditWinsOverOlderLocal() {
        val now = 10_000_000L
        val cloud = CloudSafeZone("zone_a", "New Name", 51.34, -0.13, 120.0, "work", now - 100)
        val r = SyncResolutionHelper.mergeSafeZonesPure(
            listOf(zone("zone_a", "Old Name")), listOf(cloud), mapOf("zone_a" to now - 5000), emptyMap(), emptyMap(), now
        )
        assertEquals("New Name", r.finalZones.single().name)
    }

    @Test
    fun deletionTombstoneRemovesOlderZone() {
        val now = 10_000_000L
        val r = SyncResolutionHelper.mergeSafeZonesPure(
            listOf(zone("zone_a")), emptyList(), mapOf("zone_a" to now - 5000), emptyMap(), mapOf("zone_a" to now - 100), now
        )
        assertTrue(r.finalZones.isEmpty())
        assertTrue(r.mergedDeletions.containsKey("zone_a"))
    }

    @Test
    fun zoneReaddedAfterDeletionSurvives() {
        val now = 10_000_000L
        val cloud = CloudSafeZone("zone_a", "Back", 51.34, -0.13, 100.0, "work", now - 10)
        val r = SyncResolutionHelper.mergeSafeZonesPure(
            emptyList(), listOf(cloud), emptyMap(), emptyMap(), mapOf("zone_a" to now - 5000), now
        )
        assertEquals(1, r.finalZones.size)
        assertFalse(r.mergedDeletions.containsKey("zone_a"))
    }

    @Test
    fun seededWorkplacesPresent() {
        val ids = GeoUtils.INITIAL_SEEDED_WORKPLACES.map { it.id }
        assertTrue(ids.contains("zone_westside_care_home"))
        assertTrue(ids.contains("zone_westminster_college"))
    }

    // ── Rail corridor evidence ─────────────────────────────────────────────

    @Test
    fun parkTrailWithoutTracksIsNeverRail() {
        // 20 mph, far from any road, but no mapped railway track => bike trail, not train
        assertEquals(
            RailwayTransitDetector.CorridorVerdict.ROAD_OR_PATH_ONLY,
            RailwayTransitDetector.evaluateCorridorEvidence(roadDistMeters = 120.0, trackDistMeters = null, speedMph = 20.0)
        )
    }

    @Test
    fun fastOnTrackAwayFromRoadIsRail() {
        assertEquals(
            RailwayTransitDetector.CorridorVerdict.STRONG_RAIL,
            RailwayTransitDetector.evaluateCorridorEvidence(roadDistMeters = 60.0, trackDistMeters = 10.0, speedMph = 45.0)
        )
    }

    @Test
    fun carOnRoadParallelToTrackIsNotStrongRail() {
        val v = RailwayTransitDetector.evaluateCorridorEvidence(roadDistMeters = 3.0, trackDistMeters = 40.0, speedMph = 30.0)
        assertEquals(RailwayTransitDetector.CorridorVerdict.ROAD_OR_PATH_ONLY, v)
    }

    // ── Transit classifier ─────────────────────────────────────────────────

    @Test
    fun carSlowingInTrafficStaysDriving() {
        val t0 = 1_000_000L
        var lat = 51.30
        val lng = -0.12
        var mode = TransitMode.STATIONARY
        // Cruise at 35 mph, then crawl at 6 mph in traffic
        for (i in 0 until 6) {
            lat += 0.0003
            mode = TransitClassifier.observeSample("car", lat, lng, 35.0, "", false, null, "", t0 + i * 10_000L)
        }
        assertEquals(TransitMode.DRIVING, mode)
        for (i in 6 until 10) {
            lat += 0.00004
            mode = TransitClassifier.observeSample("car", lat, lng, 6.0, "", false, null, "", t0 + i * 10_000L)
        }
        assertEquals(TransitMode.DRIVING, mode)
    }

    @Test
    fun trainSlowingIntoStationStaysTrain() {
        val t0 = 2_000_000L
        var mode = TransitMode.STATIONARY
        var lat = 51.40
        for (i in 0 until 4) {
            lat += 0.0005
            mode = TransitClassifier.observeSample("train", lat, -0.10, 60.0, "", true, null, "", t0 + i * 10_000L)
        }
        assertEquals(TransitMode.TRAIN, mode)
        lat += 0.00005
        mode = TransitClassifier.observeSample("train", lat, -0.10, 5.0, "", true, null, "", t0 + 50_000L)
        assertEquals(TransitMode.TRAIN, mode)
    }

    @Test
    fun walkingCadenceKeepsWalkerAsWalking() {
        val t0 = 3_000_000L
        val hint = InertialActivityDetector.InertialSnapshot(InertialActivityDetector.CadenceKind.WALKING_CADENCE, 85, t0)
        var mode = TransitMode.STATIONARY
        var lat = 51.30
        for (i in 0 until 3) {
            lat += 0.00002
            mode = TransitClassifier.observeSample("walker", lat, -0.12, 3.2, "", false, hint, "", t0 + i * 10_000L)
        }
        assertEquals(TransitMode.WALKING, mode)
    }

    @Test
    fun singleTickGpsSpikeDoesNotCreateDriving() {
        val t0 = 4_000_000L
        TransitClassifier.observeSample("spike", 51.3, -0.12, 0.0, "", false, null, "", t0)
        val mode = TransitClassifier.observeSample("spike", 51.30001, -0.12, 18.0, "", false, null, "", t0 + 10_000L)
        assertNotEquals(TransitMode.DRIVING, mode)
    }

    // ── Inertial cadence ───────────────────────────────────────────────────

    @Test
    fun footstepCadenceDetectedAsWalking() {
        // ~1.9 Hz impulses (every ~520 ms), sampled every 40 ms for 2.8 s
        val samples = (0..70).map { i ->
            val t = i * 40L
            val phase = t % 520L
            t to (if (phase < 80L) 4.0 else 0.3)
        }
        val snap = InertialActivityDetector.analyzeLinearAccelerationSamples(samples, 0L)
        assertEquals(InertialActivityDetector.CadenceKind.WALKING_CADENCE, snap.kind)
    }

    @Test
    fun smoothMotionDetectedAsVehicle() {
        val samples = (0..70).map { i -> (i * 40L) to (0.15 + (i % 3) * 0.02) }
        val snap = InertialActivityDetector.analyzeLinearAccelerationSamples(samples, 0L)
        assertEquals(InertialActivityDetector.CadenceKind.VEHICLE_SMOOTH, snap.kind)
    }
}
