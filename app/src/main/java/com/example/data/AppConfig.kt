package com.example.data

object AppConfig {
    // API Sync configuration
    const val BASE_URL = "https://api.cosmowhisper.com/sync/"
    const val HEARTBEAT_URL = "https://api.cosmowhisper.com/sync/heartbeat"
    const val FEEDBACK_URL = "https://api.cosmowhisper.com/sync/feedback"
    const val DEFAULT_GROUP_SYNC_TOKEN = "81e5632c_pin_group"

    // Default landmark coordinates (Croydon area, UK)
    const val DEFAULT_HOME_LAT = 51.332308
    const val DEFAULT_HOME_LNG = -0.117188

    // Geofencing and hysteresis values
    const val GEOFENCE_COOLDOWN_MS = 300000L // 5 minutes
    const val HYSTERESIS_BUFFER_KM = 0.02 // 20 meters buffer
}
