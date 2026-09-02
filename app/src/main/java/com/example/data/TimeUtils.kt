package com.example.data

/**
 * Returns a concise relative-time string for a given unix-ms timestamp.
 * Examples: "just now", "2m ago", "1h ago", "3d ago"
 */
fun formatTimeAgo(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Live"
    val diffMs = System.currentTimeMillis() - timestampMs
    if (diffMs < 0L) return "Live"
    return when {
        diffMs < 20_000L       -> "Live"
        diffMs < 60_000L       -> "${diffMs / 1_000}s ago"
        diffMs < 3_600_000L    -> "${diffMs / 60_000}m ago"
        diffMs < 86_400_000L   -> "${diffMs / 3_600_000}h ago"
        else                   -> "${diffMs / 86_400_000}d ago"
    }
}

/**
 * Returns a human-readable duration for how long someone has been at a place.
 * Examples: "1m", "45m", "2h 10m", "3d"
 */
fun formatDuration(sinceMs: Long): String {
    if (sinceMs <= 0L) return ""
    val diffMs = System.currentTimeMillis() - sinceMs
    if (diffMs < 60_000L) return "< 1m"
    val mins  = (diffMs / 60_000L).toInt()
    val hours = mins / 60
    val days  = hours / 24
    return when {
        days  > 0  -> "${days}d ${hours % 24}h"
        hours > 0  -> "${hours}h ${mins % 60}m"
        else       -> "${mins}m"
    }
}

/**
 * Returns formatted clock time (HH:mm) for a given timestamp.
 */
fun formatExactTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestampMs))
}

/**
 * Activity / Transit mode classification
 */
enum class TransitMode(val icon: String, val label: String) {
    STATIONARY("📍", "Stationary"),
    WALKING("👣", "Walking"),
    CYCLING("🚲", "Cycling"),
    DRIVING("🚗", "Driving"),
    TRAIN("🚆", "Train")
}

/**
 * Accurately determines the transit mode based on speed and explicit status text.
 */
fun classifyTransitMode(speedMph: Double, statusText: String = ""): TransitMode {
    val statusLower = statusText.lowercase()
    return when {
        statusLower.contains("train") || statusLower.contains("transit") || statusLower.contains("rail") || statusLower.contains("metro") || statusLower.contains("subway") || statusLower.contains("tube") -> {
            TransitMode.TRAIN
        }
        statusLower.contains("bike") || statusLower.contains("bicycle") || statusLower.contains("cycling") || statusLower.contains("cycle") -> {
            TransitMode.CYCLING
        }
        statusLower.contains("walk") || statusLower.contains("foot") || statusLower.contains("hiking") || statusLower.contains("steps") || statusLower.contains("run") || statusLower.contains("jog") -> {
            TransitMode.WALKING
        }
        statusLower.contains("drive") || statusLower.contains("driving") || statusLower.contains("car") || statusLower.contains("motorway") || statusLower.contains("highway") -> {
            TransitMode.DRIVING
        }
        speedMph < 0.6 -> {
            TransitMode.STATIONARY
        }
        speedMph in 0.6..4.5 -> {
            TransitMode.WALKING
        }
        speedMph in 4.51..16.0 -> {
            TransitMode.CYCLING
        }
        speedMph in 16.01..80.0 -> {
            TransitMode.DRIVING
        }
        else -> {
            TransitMode.TRAIN
        }
    }
}

/**
 * Formats a live badge string for map markers and member cards.
 * Shows activity emoji & speed when moving, or stationary location duration when stopped.
 */
fun formatTransitBadge(speedMph: Double, statusText: String, locationSince: Long): String {
    val mode = classifyTransitMode(speedMph, statusText)
    return when (mode) {
        TransitMode.WALKING -> {
            if (speedMph >= 0.6) "👣 ${String.format(java.util.Locale.US, "%.1f", speedMph)} mph" else "👣 Walking"
        }
        TransitMode.CYCLING -> {
            if (speedMph >= 1.0) "🚲 ${String.format(java.util.Locale.US, "%.1f", speedMph)} mph" else "🚲 Cycling"
        }
        TransitMode.DRIVING -> {
            if (speedMph >= 1.0) "🚗 ${String.format(java.util.Locale.US, "%.0f", speedMph)} mph" else "🚗 Driving"
        }
        TransitMode.TRAIN -> {
            if (speedMph >= 1.0) "🚆 ${String.format(java.util.Locale.US, "%.0f", speedMph)} mph" else "🚆 Train"
        }
        TransitMode.STATIONARY -> {
            if (locationSince > 0L) {
                val dur = formatDuration(locationSince)
                if (dur.isNotBlank()) "📍 here $dur" else ""
            } else ""
        }
    }
}
