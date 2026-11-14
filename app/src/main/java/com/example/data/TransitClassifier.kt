package com.example.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-signal transit mode classifier solving two chronic GPS failure modes:
 * 1. Cars / trains slowing to 4.5–16 mph in traffic or approaching stations flipping to Cycling/Walking.
 * 2. Walkers / park cyclists getting falsely bumped into Driving or Train due to single-tick GPS spikes
 *    or off-road bike trails.
 */
object TransitClassifier {

    private const val VEHICLE_LATCH_WINDOW_MS = 4 * 60_000L
    private const val STATIONARY_RESET_MS = 45_000L

    data class MemberMotionState(
        val lastLat: Double = 0.0,
        val lastLng: Double = 0.0,
        val lastTimestampMs: Long = 0L,
        val lastMode: TransitMode = TransitMode.STATIONARY,
        val recentPeakMph: Double = 0.0,
        val lastVehicleTimeMs: Long = 0L,
        val stationarySinceMs: Long = 0L,
        val walkingCadenceStreak: Int = 0,
        val highSpeedStreak: Int = 0,
        val cloudTransitMode: String = ""
    )

    private val memberStates = ConcurrentHashMap<String, MemberMotionState>()

    fun clearForTests() {
        memberStates.clear()
    }

    fun getLastClassifiedMode(memberId: String): TransitMode? {
        if (memberId.isBlank()) return null
        return memberStates[memberId.lowercase().trim()]?.lastMode
    }

    /**
     * Observes a new location/motion sample for [memberId], filters single-tick GPS velocity spikes,
     * applies sliding peak-speed & stationary dwell hysteresis, fuses optional inertial cadence, and
     * returns the stabilized [TransitMode].
     */
    fun observeSample(
        memberId: String,
        lat: Double,
        lng: Double,
        reportedSpeedMph: Double,
        statusText: String = "",
        isRailway: Boolean = RailwayTransitDetector.isMemberOnRailway(memberId),
        inertialHint: InertialActivityDetector.InertialSnapshot? = null,
        cloudTransitMode: String = "",
        nowMs: Long = System.currentTimeMillis()
    ): TransitMode {
        if (memberId.isBlank()) {
            return classifyFallback(reportedSpeedMph, statusText, false)
        }
        RailwayTransitDetector.getManualOverride(memberId)?.let { return it }

        val key = memberId.lowercase().trim()
        val prev = memberStates[key] ?: MemberMotionState()

        val statusLower = statusText.lowercase()
        val impliedMph = if (prev.lastLat != 0.0 && prev.lastLng != 0.0 && lat != 0.0 && lng != 0.0 && nowMs > prev.lastTimestampMs) {
            val dtSec = (nowMs - prev.lastTimestampMs) / 1000.0
            if (dtSec in 1.0..180.0) {
                (GeoUtils.distanceMeters(prev.lastLat, prev.lastLng, lat, lng) / dtSec) * 2.23694
            } else -1.0
        } else -1.0

        // Guard against single-tick GPS multipath jump (e.g., phone suddenly reporting 16 mph while staying within 5m)
        val hasWeakDisplacement = impliedMph >= 0.0 && impliedMph < 3.0
        val effectiveSpeedMph = if (
            reportedSpeedMph >= 11.0 &&
            hasWeakDisplacement &&
            prev.highSpeedStreak == 0 &&
            prev.lastMode != TransitMode.DRIVING &&
            prev.lastMode != TransitMode.TRAIN
        ) {
            impliedMph.coerceAtLeast(0.0)
        } else if (reportedSpeedMph > 140.0) {
            prev.recentPeakMph.coerceAtMost(85.0)
        } else {
            reportedSpeedMph.coerceAtLeast(0.0)
        }

        val hasRecentVehicle = prev.lastVehicleTimeMs > 0L && (nowMs - prev.lastVehicleTimeMs) <= VEHICLE_LATCH_WINDOW_MS
        val isStrongWalkingCadence = inertialHint?.kind == InertialActivityDetector.CadenceKind.WALKING_CADENCE && inertialHint.confidence >= 65
        val isSmoothVehicleMotion = inertialHint?.kind == InertialActivityDetector.CadenceKind.VEHICLE_SMOOTH

        val explicitTrain = statusLower.contains("train") || statusLower.contains("rail") || statusLower.contains("subway") || statusLower.contains("tube")
        val explicitBike = statusLower.contains("bike") || statusLower.contains("bicycle") || statusLower.contains("cycling")
        val explicitWalk = statusLower.contains("walk") || statusLower.contains("foot") || statusLower.contains("run") || statusLower.contains("jog")
        val explicitDrive = statusLower.contains("drive") || statusLower.contains("in a car") || statusLower.contains("motorway")

        val nextWalkingStreak = if (isStrongWalkingCadence && effectiveSpeedMph in 0.6..6.5) prev.walkingCadenceStreak + 1 else 0
        val nextHighSpeedStreak = if (effectiveSpeedMph >= 13.5 && !isStrongWalkingCadence) prev.highSpeedStreak + 1 else 0

        val isAtStationaryStop = effectiveSpeedMph < 0.7 && (impliedMph < 0.0 || impliedMph < 0.9)
        val newStationarySince = when {
            !isAtStationaryStop -> 0L
            prev.stationarySinceMs > 0L -> prev.stationarySinceMs
            else -> nowMs
        }
        val settledStationary = isAtStationaryStop &&
            (!hasRecentVehicle || prev.stationarySinceMs == 0L || (nowMs - prev.stationarySinceMs) >= STATIONARY_RESET_MS || statusText.startsWith("At ", ignoreCase = true))

        val resolvedMode: TransitMode = when {
            statusText.startsWith("At Home", ignoreCase = true) || statusText.startsWith("At ", ignoreCase = true) && effectiveSpeedMph < 3.5 -> TransitMode.STATIONARY
            explicitTrain -> TransitMode.TRAIN
            isRailway && effectiveSpeedMph >= 1.5 -> TransitMode.TRAIN
            effectiveSpeedMph >= 78.0 -> TransitMode.TRAIN
            cloudTransitMode.equals("TRAIN", ignoreCase = true) && effectiveSpeedMph >= 5.0 -> TransitMode.TRAIN
            explicitBike -> TransitMode.CYCLING
            explicitWalk -> TransitMode.WALKING
            explicitDrive && !settledStationary -> TransitMode.DRIVING
            settledStationary -> TransitMode.STATIONARY

            // Urban traffic / slowing vehicle latch: never flip a car or train to cycling at traffic lights or approaches
            isStrongWalkingCadence && effectiveSpeedMph in 0.6..6.5 && (nextWalkingStreak >= 2 || !hasRecentVehicle) -> TransitMode.WALKING
            (prev.lastMode == TransitMode.TRAIN || cloudTransitMode.equals("TRAIN", ignoreCase = true)) && hasRecentVehicle && effectiveSpeedMph >= 0.7 -> TransitMode.TRAIN
            (prev.lastMode == TransitMode.DRIVING || (hasRecentVehicle && prev.recentPeakMph >= 18.0)) && effectiveSpeedMph >= 0.7 && !isStrongWalkingCadence -> TransitMode.DRIVING

            effectiveSpeedMph < 0.7 -> TransitMode.STATIONARY
            effectiveSpeedMph < 4.5 -> if (isSmoothVehicleMotion && cloudTransitMode.equals("DRIVING", ignoreCase = true)) TransitMode.DRIVING else TransitMode.WALKING

            // 4.5..16.5 mph ambiguous band: separate jogging/slow cars from true cycling
            effectiveSpeedMph <= 16.5 -> when {
                cloudTransitMode.equals("DRIVING", ignoreCase = true) && effectiveSpeedMph >= 10.0 -> TransitMode.DRIVING
                isSmoothVehicleMotion && effectiveSpeedMph >= 11.5 && nextHighSpeedStreak >= 2 -> TransitMode.DRIVING
                effectiveSpeedMph >= 12.5 && nextHighSpeedStreak >= 3 -> TransitMode.DRIVING
                else -> TransitMode.CYCLING
            }

            else -> TransitMode.DRIVING
        }

        val updatedPeakMph = when {
            resolvedMode == TransitMode.STATIONARY && settledStationary -> 0.0
            isStrongWalkingCadence && nextWalkingStreak >= 2 -> 0.0
            else -> maxOf(if (hasRecentVehicle) prev.recentPeakMph * 0.92 else 0.0, effectiveSpeedMph)
        }
        val updatedVehicleTime = when {
            resolvedMode == TransitMode.DRIVING || resolvedMode == TransitMode.TRAIN || effectiveSpeedMph >= 18.0 -> nowMs
            resolvedMode == TransitMode.STATIONARY && settledStationary -> 0L
            else -> prev.lastVehicleTimeMs
        }

        memberStates[key] = MemberMotionState(
            lastLat = if (lat != 0.0) lat else prev.lastLat,
            lastLng = if (lng != 0.0) lng else prev.lastLng,
            lastTimestampMs = nowMs,
            lastMode = resolvedMode,
            recentPeakMph = updatedPeakMph,
            lastVehicleTimeMs = updatedVehicleTime,
            stationarySinceMs = newStationarySince,
            walkingCadenceStreak = nextWalkingStreak,
            highSpeedStreak = nextHighSpeedStreak,
            cloudTransitMode = cloudTransitMode.ifBlank { prev.cloudTransitMode }
        )

        return resolvedMode
    }

    /**
     * Fast read-only classification used by composables & formatters. Prefers the stabilized
     * [observeSample] decision when available and falls back cleanly when no history exists.
     */
    fun classifyCurrent(speedMph: Double, statusText: String = "", memberId: String = ""): TransitMode {
        if (memberId.isNotBlank()) {
            RailwayTransitDetector.getManualOverride(memberId)?.let { return it }
            val state = memberStates[memberId.lowercase().trim()]
            if (state != null && (System.currentTimeMillis() - state.lastTimestampMs) <= 180_000L) {
                if (state.lastMode == TransitMode.TRAIN && speedMph >= 1.5) return TransitMode.TRAIN
                if (state.lastMode == TransitMode.DRIVING && speedMph in 0.8..16.5 && state.recentPeakMph >= 18.0) {
                    return TransitMode.DRIVING
                }
            }
        }
        val isRail = memberId.isNotBlank() && RailwayTransitDetector.isMemberOnRailway(memberId)
        return classifyFallback(speedMph, statusText, isRail)
    }

    private fun classifyFallback(speedMph: Double, statusText: String, isRail: Boolean): TransitMode {
        val statusLower = statusText.lowercase()
        return when {
            statusLower.contains("train") || statusLower.contains("transit") || statusLower.contains("rail") || statusLower.contains("metro") || statusLower.contains("subway") || statusLower.contains("tube") -> TransitMode.TRAIN
            isRail && speedMph >= 1.0 -> TransitMode.TRAIN
            statusLower.contains("bike") || statusLower.contains("bicycle") || statusLower.contains("cycling") || statusLower.contains("cycle") -> TransitMode.CYCLING
            statusLower.contains("walk") || statusLower.contains("foot") || statusLower.contains("hiking") || statusLower.contains("steps") || statusLower.contains("run") || statusLower.contains("jog") -> TransitMode.WALKING
            statusLower.contains("drive") || statusLower.contains("driving") || statusLower.contains("in a car") || statusLower.contains("motorway") || statusLower.contains("highway") -> TransitMode.DRIVING
            speedMph < 0.7 -> TransitMode.STATIONARY
            speedMph <= 4.5 -> TransitMode.WALKING
            speedMph <= 16.5 -> TransitMode.CYCLING
            speedMph < 78.0 -> TransitMode.DRIVING
            else -> TransitMode.TRAIN
        }
    }
}
