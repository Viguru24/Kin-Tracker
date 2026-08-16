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
