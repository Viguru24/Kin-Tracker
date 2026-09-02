package com.example.data

object AppConfig {
    // API Sync configuration
    const val BASE_URL = "https://api.cosmowhisper.com/sync/"
    const val HEARTBEAT_URL = "https://api.cosmowhisper.com/sync/heartbeat"
    const val FEEDBACK_URL = "https://api.cosmowhisper.com/sync/feedback"
    const val DEFAULT_GROUP_SYNC_TOKEN = "81e5632c_pin_group"

    // Default landmark coordinates (Croydon area, UK)
    const val DEFAULT_HOME_LAT = 51.329480
    const val DEFAULT_HOME_LNG = -0.119095
    const val DEFAULT_HOME_RADIUS_METERS = 140.0

    const val DEFAULT_WORK_LAT = 51.375800
    const val DEFAULT_WORK_LNG = -0.098000
    const val DEFAULT_WORK_RADIUS_METERS = 75.0

    // Geofencing and hysteresis values
    const val GEOFENCE_COOLDOWN_MS = 900000L // 15 minutes between duplicate alerts for same person/place
    const val EXIT_HYSTERESIS_METERS = 60.0 // 60m buffer beyond zone radius to confirm building departure and prevent false alarms
    const val MIN_EXIT_SPEED_MPH = 1.2 // Movement threshold confirming intentional departure
    const val ARRIVAL_CONFIRMATION_CHECKS = 3 // Consecutive verified readings before triggering an arrival alert
    const val DEPARTURE_CONFIRMATION_CHECKS = 3 // Consecutive verified readings before triggering a departure warning
}
