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
        var hasAlertedApproachingHomeThisTrip: Boolean = false,
        var hasAlertedApproachingMeThisTrip: Boolean = false,
        var wasConfirmedAtHome: Boolean = false
    )

    private val memberStates = ConcurrentHashMap<String, MemberProximityState>()

    suspend fun evaluateProximity(
        membersList: List<FamilyMember>,
        homeLat: Double,
        homeLng: Double,
        proximityThresholdMeters: Int,
        myDeviceUUID: String = "",
        myDeviceName: String = ""
    ) {
        if (membersList.isEmpty()) return

        val me = membersList.firstOrNull { it.id == "me" || (myDeviceUUID.isNotBlank() && it.id == myDeviceUUID) || (myDeviceName.isNotBlank() && it.name.equals(myDeviceName, ignoreCase = true)) }
        val myLat = me?.y ?: 0.0
        val myLng = me?.x ?: 0.0
        val hasMyGps = myLat != 0.0 && myLng != 0.0

        val isMeAtHome = if (hasMyGps) {
            GeoUtils.isInsideGeofence(myLat, myLng, homeLat, homeLng, 120.0)
        } else true

        val thresholdKm = (proximityThresholdMeters / 1000.0).coerceAtLeast(0.3)
        val now = System.currentTimeMillis()

        membersList.forEach { m ->
            val isSelf = m.id == "me" ||
                    (myDeviceUUID.isNotBlank() && m.id == myDeviceUUID) ||
                    (myDeviceName.isNotBlank() && m.name.equals(myDeviceName, ignoreCase = true)) ||
                    m.name.contains("(You)", ignoreCase = true)
            if (isSelf || (m.x == 0.0 && m.y == 0.0)) return@forEach

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

            // ── Case 1: Member Approaching Home Base (Trigger ONLY ONCE per journey) ──
            if (state.prevDistToHomeKm > 0.0) {
                val deltaHome = distToHomeKm - state.prevDistToHomeKm
                val isGettingCloserToHome = deltaHome < -0.015
                val isAtHome = distToHomeKm <= 0.12

                if (isAtHome) {
                    state.wasConfirmedAtHome = true
                    state.hasAlertedApproachingHomeThisTrip = false
                } else if (distToHomeKm <= thresholdKm && !state.hasAlertedApproachingHomeThisTrip && distToHomeKm > 0.12 && isMemberMoving && isGettingCloserToHome) {
                    // Trigger ONCE per journey with a minimum 20-minute safety latch
                    if (now - state.lastAlertTimestamp > 20 * 60 * 1000L) {
                        state.hasAlertedApproachingHomeThisTrip = true
                        state.wasConfirmedAtHome = false
                        state.lastAlertTimestamp = now

                        // Calculate speed-based ETA to Home
                        val speedMph = m.speedMph
                        val estMinutes = when {
                            m.etaMinutes > 0 -> m.etaMinutes
                            speedMph >= 1.0 -> {
                                val speedKmH = speedMph * 1.60934
                                val hours = distToHomeKm / speedKmH
                                val mins = (hours * 60.0).roundToInt()
                                mins.coerceIn(1, 120)
                            }
                            else -> {
                                // Default walking pace: ~3 mph = ~4.8 km/h -> 12.5 mins per km
                                val mins = (distToHomeKm * 12.5).roundToInt()
                                mins.coerceIn(1, 60)
                            }
                        }

                        val timePhrase = when (estMinutes) {
                            1 -> "about 1 minute"
                            else -> "about $estMinutes minutes"
                        }

                        repository.insertLog(
                            ActivityLog(
                                memberId = m.id,
                                memberName = m.name,
                                actionText = "will be home in $timePhrase",
                                iconName = "home"
                            )
                        )
                        uiEvents.emit("Approaching Alert: $cleanName will be home in $timePhrase")
                    }
                } else if (distToHomeKm > 0.80 && state.wasConfirmedAtHome) {
                    // Reset trip latch ONLY once the member has departed far from home on a new trip
                    state.hasAlertedApproachingHomeThisTrip = false
                    state.wasConfirmedAtHome = false
                }
            }

            // ── Case 2: Member Approaching Current Device (away from Home) ──
            if (!isMeAtHome && distToMeKm > 0.0 && state.prevDistToMeKm > 0.0) {
                val deltaMe = distToMeKm - state.prevDistToMeKm
                val isGettingCloserToMe = deltaMe < -0.015
                val isMetUp = distToMeKm <= 0.08

                if (isMetUp) {
                    if (state.hasAlertedApproachingMeThisTrip) {
                        repository.insertLog(
                            ActivityLog(memberId = m.id, memberName = m.name, actionText = "has met up with you", iconName = "check_in")
                        )
                        uiEvents.emit("👋 $cleanName has met up with you!")
                    }
                    state.hasAlertedApproachingMeThisTrip = false
                } else if (distToMeKm <= thresholdKm && !state.hasAlertedApproachingMeThisTrip && distToMeKm > 0.08 && isMemberMoving && isGettingCloserToMe) {
                    if (now - state.lastAlertTimestamp > 20 * 60 * 1000L) {
                        state.hasAlertedApproachingMeThisTrip = true
                        state.lastAlertTimestamp = now

                        val speedMph = m.speedMph
                        val estMinutes = when {
                            m.etaMinutes > 0 -> m.etaMinutes
                            speedMph >= 1.0 -> {
                                val speedKmH = speedMph * 1.60934
                                val hours = distToMeKm / speedKmH
                                val mins = (hours * 60.0).roundToInt()
                                mins.coerceIn(1, 120)
                            }
                            else -> {
                                val mins = (distToMeKm * 12.5).roundToInt()
                                mins.coerceIn(1, 60)
                            }
                        }

                        val timePhrase = when (estMinutes) {
                            1 -> "about 1 minute"
                            else -> "about $estMinutes minutes"
                        }

                        repository.insertLog(
                            ActivityLog(
                                memberId = m.id,
                                memberName = m.name,
                                actionText = "will be with you in $timePhrase",
                                iconName = "home"
                            )
                        )
                        uiEvents.emit("Approaching Alert: $cleanName will be with you in $timePhrase")
                    }
                } else if (distToMeKm > 0.80) {
                    state.hasAlertedApproachingMeThisTrip = false
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
