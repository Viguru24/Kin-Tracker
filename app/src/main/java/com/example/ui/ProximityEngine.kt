package com.example.ui

import com.example.data.ActivityLog
import com.example.data.FamilyMember
import com.example.data.FamilyRepository
import com.example.data.GeoUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Domain engine that tracks circle members' directional movement trends,
 * eliminates false alarms when leaving home, and emits accurate approaching & arrival alerts.
 */
class ProximityEngine(
    private val repository: FamilyRepository,
    private val uiEvents: MutableSharedFlow<String>
) {
    data class MemberProximityState(
        var prevDistToMeKm: Double = -1.0,
        var prevDistToHomeKm: Double = -1.0,
        var prevX: Double = 0.0,
        var prevY: Double = 0.0,
        var lastAlertTimestamp: Long = 0L,
        var isApproachingMeAlerted: Boolean = false,
        var isApproachingHomeAlerted: Boolean = false
    )

    private val memberStates = ConcurrentHashMap<String, MemberProximityState>()

    suspend fun evaluateProximity(
        membersList: List<FamilyMember>,
        homeLat: Double,
        homeLng: Double,
        proximityThresholdMeters: Int
    ) {
        if (membersList.isEmpty()) return

        val me = membersList.firstOrNull { it.id == "me" }
        val myLat = me?.y ?: 0.0
        val myLng = me?.x ?: 0.0
        val hasMyGps = myLat != 0.0 && myLng != 0.0

        val isMeAtHome = if (hasMyGps) {
            GeoUtils.isInsideGeofence(myLat, myLng, homeLat, homeLng, 100.0)
        } else true

        val thresholdKm = proximityThresholdMeters / 1000.0
        val now = System.currentTimeMillis()

        membersList.forEach { m ->
            if (m.id == "me" || (m.x == 0.0 && m.y == 0.0)) return@forEach

            val state = memberStates.getOrPut(m.id) { MemberProximityState() }

            val distToHomeKm = GeoUtils.fastDistanceKm(m.y, m.x, homeLat, homeLng)
            val distToMeKm = if (hasMyGps) GeoUtils.fastDistanceKm(m.y, m.x, myLat, myLng) else -1.0

            val memberMovedKm = if (state.prevX != 0.0 && state.prevY != 0.0) {
                GeoUtils.fastDistanceKm(m.y, m.x, state.prevY, state.prevX)
            } else 0.0

            val isMemberMoving = m.speedMph >= 1.5 || memberMovedKm >= 0.035
            val cleanName = m.name.replace(
                Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter)\\)", RegexOption.IGNORE_CASE),
                ""
            ).trim()

            // ── Case 1: Member Approaching Home Base ──
            if (state.prevDistToHomeKm > 0.0) {
                val deltaHome = distToHomeKm - state.prevDistToHomeKm
                val isGettingCloserToHome = deltaHome < -0.02
                val isAtHome = distToHomeKm < 0.06

                if (isAtHome) {
                    if (state.isApproachingHomeAlerted) {
                        repository.insertLog(
                            ActivityLog(memberId = m.id, memberName = m.name, actionText = "has arrived Home safely", iconName = "home")
                        )
                        uiEvents.emit("🏠 $cleanName has arrived Home!")
                    }
                    state.isApproachingHomeAlerted = false
                } else if (distToHomeKm <= thresholdKm && !state.isApproachingHomeAlerted && distToHomeKm > 0.08 && isMemberMoving && isGettingCloserToHome) {
                    if (now - state.lastAlertTimestamp > 120_000L) {
                        state.isApproachingHomeAlerted = true
                        state.lastAlertTimestamp = now
                        val metersAway = (distToHomeKm * 1000.0).roundToInt()
                        val estMinutes = if (m.etaMinutes > 0) m.etaMinutes else maxOf(1, (metersAway / 90.0).roundToInt())
                        val timeStr = if (estMinutes == 1) "1 min" else "$estMinutes mins"

                        repository.insertLog(
                            ActivityLog(
                                memberId = m.id,
                                memberName = m.name,
                                actionText = "is approaching Home (~${metersAway}m away, ETA $timeStr)",
                                iconName = "home"
                            )
                        )
                        uiEvents.emit("Approaching Alert: $cleanName is approaching Home (~${metersAway}m away)")
                    }
                } else if (distToHomeKm > (thresholdKm + 0.15)) {
                    state.isApproachingHomeAlerted = false
                }
            }

            // ── Case 2: Member Approaching Current Device (away from Home) ──
            if (!isMeAtHome && distToMeKm > 0.0 && state.prevDistToMeKm > 0.0) {
                val deltaMe = distToMeKm - state.prevDistToMeKm
                val isGettingCloserToMe = deltaMe < -0.02
                val isMetUp = distToMeKm < 0.05

                if (isMetUp) {
                    if (state.isApproachingMeAlerted) {
                        repository.insertLog(
                            ActivityLog(memberId = m.id, memberName = m.name, actionText = "has met up with you", iconName = "check_in")
                        )
                        uiEvents.emit("👋 $cleanName has met up with you!")
                    }
                    state.isApproachingMeAlerted = false
                } else if (distToMeKm <= thresholdKm && !state.isApproachingMeAlerted && distToMeKm > 0.08 && isMemberMoving && isGettingCloserToMe) {
                    if (now - state.lastAlertTimestamp > 120_000L) {
                        state.isApproachingMeAlerted = true
                        state.lastAlertTimestamp = now
                        val metersAway = (distToMeKm * 1000.0).roundToInt()
                        val estMinutes = if (m.etaMinutes > 0) m.etaMinutes else maxOf(1, (metersAway / 90.0).roundToInt())
                        val timeStr = if (estMinutes == 1) "1 min" else "$estMinutes mins"

                        repository.insertLog(
                            ActivityLog(
                                memberId = m.id,
                                memberName = m.name,
                                actionText = "is on their way towards you (~${metersAway}m away, ETA $timeStr)",
                                iconName = "home"
                            )
                        )
                        uiEvents.emit("Approaching Alert: $cleanName is on their way towards you (~${metersAway}m away)")
                    }
                } else if (distToMeKm > (thresholdKm + 0.15)) {
                    state.isApproachingMeAlerted = false
                }
            }

            // Update state history
            state.prevDistToHomeKm = distToHomeKm
            if (distToMeKm > 0.0) state.prevDistToMeKm = distToMeKm
            state.prevX = m.x
            state.prevY = m.y
        }
    }
}
