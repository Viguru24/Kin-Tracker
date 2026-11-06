package com.example.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth & gatekeeper for all departure, arrival, and proximity alerts
 * across local geofencing, journey analysis, background alarms, and server WebSocket broadcasts.
 *
 * Enforces strict family safety invariants:
 * 1. CHILD WORKPLACE IMMUNITY: Schoolchildren (Eloise, Isabel) can NEVER trigger or receive "Work" alerts.
 * 2. STATE MACHINE (NO BACK-TO-BACK DEPARTURES): A member who has "left" cannot emit another departure warning until they arrive somewhere.
 * 3. GLOBAL RATE LIMITING: Enforces minimum cooldown per person per event type across all places.
 */
object AlertArbiter {

    private val lastAlertTimestamp = ConcurrentHashMap<String, Long>()
    private val lastMemberState = ConcurrentHashMap<String, String>() // "DEPARTED" or "ARRIVED"

    fun canEmitAlert(
        title: String,
        message: String,
        memberId: String = "",
        memberName: String = "",
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val fullText = "$title $message $memberName".lowercase()

        // 1. INVARIANT: Children can NEVER trigger Work alerts
        val isEloiseOrIsabel = fullText.contains("eloise") || fullText.contains("isabel")
        val isWork = fullText.contains("work") || fullText.contains("at work") || fullText.contains("left work")
        if (isEloiseOrIsabel && isWork) {
            return false // HARD DROP: Child cannot have a Work alert
        }

        val personKey = when {
            fullText.contains("eloise") -> "canonical_eloise"
            fullText.contains("isabel") -> "canonical_isabel"
            fullText.contains("annette") || fullText.contains("mama") -> "canonical_mama"
            fullText.contains("louis") || fullText.contains("dad") -> "canonical_dad"
            memberName.isNotBlank() -> IdentityUtils.getCanonicalPersonKey(memberName, memberId)
            else -> "unknown"
        }

        val isDeparture = title.contains("Departure", ignoreCase = true) ||
                title.contains("Journey", ignoreCase = true) ||
                title.contains("Trip", ignoreCase = true) ||
                message.contains("left", ignoreCase = true) ||
                message.contains("departed", ignoreCase = true)

        val isArrival = title.contains("Arrival", ignoreCase = true) ||
                message.contains("arrived", ignoreCase = true)

        if (isDeparture) {
            val key = "${personKey}_DEPARTURE"
            val lastTime = lastAlertTimestamp[key] ?: 0L
            if (lastTime > 0L && now - lastTime < AppConfig.GEOFENCE_COOLDOWN_MS) {
                return false // RATE LIMIT: Max 1 departure every 15 min per person
            }

            // STATE MACHINE: Cannot depart twice in a row without arriving somewhere first
            val lastState = lastMemberState[personKey]
            if (lastState == "DEPARTED") {
                return false // Already in transit / departed
            }

            lastAlertTimestamp[key] = now
            lastMemberState[personKey] = "DEPARTED"
            return true
        }

        if (isArrival) {
            val key = "${personKey}_ARRIVAL"
            val lastTime = lastAlertTimestamp[key] ?: 0L
            if (lastTime > 0L && now - lastTime < AppConfig.GEOFENCE_COOLDOWN_MS) {
                return false // RATE LIMIT: Max 1 arrival every 15 min per person
            }

            lastAlertTimestamp[key] = now
            lastMemberState[personKey] = "ARRIVED"
            return true
        }

        return true
    }

    /**
     * Resets internal latch states (for testing and calibration resets).
     */
    fun reset() {
        lastAlertTimestamp.clear()
        lastMemberState.clear()
    }
}
