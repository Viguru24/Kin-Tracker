package com.example.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth & gatekeeper for all departure, arrival, and proximity alerts
 * across local geofencing, journey analysis, background alarms, and server WebSocket broadcasts.
 *
 * Enforces strict family safety invariants:
 * 1. CHILD WORKPLACE IMMUNITY: Schoolchildren (Eloise, Isabel) can NEVER trigger or receive "Work" alerts.
 * 2. STATE MACHINE (NO BACK-TO-BACK DEPARTURES): A member who has "left" cannot emit another departure warning
 *    until they arrive somewhere or until the 45-minute journey window expires.
 * 3. GLOBAL RATE LIMITING: Enforces minimum cooldown per person per event type across all places.
 * 4. HOME ARRIVAL ISOLATION: Arriving at Home is never suppressed by earlier transient arrivals.
 * 5. CRITICAL PASS-THROUGH: SOS and Battery alerts bypass transit latches entirely.
 */
object AlertArbiter {

    private val lastAlertTimestamp = ConcurrentHashMap<String, Long>()
    private val lastMemberState = ConcurrentHashMap<String, String>() // "DEPARTED" or "ARRIVED"

    private val WORK_WORD_REGEX = Regex("\\b(work|at work|left work|arrived at work|working)\\b", RegexOption.IGNORE_CASE)
    private val DEPARTURE_WORD_REGEX = Regex("\\b(departure|departed|departing|has left|just left|left the house|left)\\b", RegexOption.IGNORE_CASE)
    private val ARRIVAL_WORD_REGEX = Regex("\\b(arrival|arrived|arriving|has reached|just reached)\\b", RegexOption.IGNORE_CASE)
    private val UNGEOFENCED_TRANSIT_TIMEOUT_MS = 45 * 60 * 1000L // 45 minutes

    fun canEmitAlert(
        title: String,
        message: String,
        memberId: String = "",
        memberName: String = "",
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val fullText = "$title $message $memberName".lowercase()

        // 1. SOS and Battery warnings must ALWAYS pass through immediately and never touch travel latches
        val isCriticalOrStatus = title.contains("SOS", ignoreCase = true) ||
                title.contains("Emergency", ignoreCase = true) ||
                title.contains("Battery", ignoreCase = true) ||
                message.contains("battery", ignoreCase = true) ||
                message.contains("SOS", ignoreCase = true)
        if (isCriticalOrStatus) {
            return true
        }

        // 2. INVARIANT: Children can NEVER trigger Work alerts
        val isEloiseOrIsabel = fullText.contains("eloise") || fullText.contains("isabel")
        val isWork = WORK_WORD_REGEX.containsMatchIn(fullText)
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
                DEPARTURE_WORD_REGEX.containsMatchIn(message)

        val isArrival = title.contains("Arrival", ignoreCase = true) ||
                ARRIVAL_WORD_REGEX.containsMatchIn(message)

        if (isDeparture) {
            val depKey = "${personKey}_DEPARTURE"
            val lastTime = lastAlertTimestamp[depKey] ?: 0L
            if (lastTime > 0L && now - lastTime < AppConfig.GEOFENCE_COOLDOWN_MS) {
                return false // RATE LIMIT: Max 1 departure every 15 min per person
            }

            // STATE MACHINE: Cannot depart twice in a row without arriving somewhere first,
            // UNLESS 45 minutes have elapsed (e.g. member stopped at an un-geofenced location).
            val lastState = lastMemberState[personKey]
            if (lastState == "DEPARTED") {
                val timeSinceDep = if (lastTime > 0L) now - lastTime else 0L
                if (timeSinceDep in 1 until UNGEOFENCED_TRANSIT_TIMEOUT_MS) {
                    return false // Still actively in transit
                }
            }

            lastAlertTimestamp[depKey] = now
            lastMemberState[personKey] = "DEPARTED"
            return true
        }

        if (isArrival) {
            val isHomeArrival = fullText.contains("home")
            val arrKey = if (isHomeArrival) "${personKey}_ARRIVAL_HOME" else "${personKey}_ARRIVAL"
            val lastTime = lastAlertTimestamp[arrKey] ?: 0L
            if (lastTime > 0L && now - lastTime < AppConfig.GEOFENCE_COOLDOWN_MS) {
                return false // RATE LIMIT: Max 1 arrival every 15 min per person for this category
            }

            lastAlertTimestamp[arrKey] = now
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
