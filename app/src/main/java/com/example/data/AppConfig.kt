package com.example.data

import android.content.Context

object AppConfig {
    const val DEFAULT_SERVER_URL = "https://api.cosmowhisper.com/"

    @Volatile
    private var _cachedServerUrl: String = ""

    fun getServerUrl(context: Context? = null): String {
        if (_cachedServerUrl.isNotBlank()) return _cachedServerUrl
        if (context != null) {
            val prefs = context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("custom_server_url", "") ?: ""
            if (saved.isNotBlank()) {
                _cachedServerUrl = normalizeUrl(saved)
                return _cachedServerUrl
            }
        }
        return DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        val clean = if (url.isBlank()) "" else normalizeUrl(url)
        _cachedServerUrl = clean
        context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_server_url", clean)
            .apply()
    }

    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        if (!u.endsWith("/")) {
            u += "/"
        }
        return u
    }

    fun getWebSocketBaseUrl(context: Context? = null): String {
        val base = getServerUrl(context).trimEnd('/')
        return if (base.startsWith("https://")) {
            "wss://" + base.removePrefix("https://")
        } else if (base.startsWith("http://")) {
            "ws://" + base.removePrefix("http://")
        } else {
            "wss://$base"
        }
    }

    // Dynamic API Sync configuration (supports custom self-hosted VPS)
    val API_BASE_URL: String get() = getServerUrl()
    val BASE_URL: String get() = "${getServerUrl()}sync/"
    val HEARTBEAT_URL: String get() = "${getServerUrl()}sync/heartbeat"
    val FEEDBACK_URL: String get() = "${getServerUrl()}sync/feedback"
    val AUDIO_RELAY_WS_URL: String get() = "${getWebSocketBaseUrl()}/audio"
    const val DEFAULT_CIRCLE_INVITE_CODE = "KT-4666"
    const val DEFAULT_GROUP_SYNC_TOKEN = "81e5632c_pin_group"

    // Test isolation flag: auto-detects unit test environments (JUnit / Robolectric)
    // to guarantee tests NEVER contact external production VPS endpoints or mutate real family circles.
    var isTestEnvironment: Boolean = try {
        Class.forName("org.junit.Test") != null ||
        Class.forName("org.robolectric.Robolectric") != null
    } catch (_: Throwable) {
        false
    }


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
    const val MIN_EXIT_SPEED_MPH = 1.8 // Movement threshold confirming intentional departure (filters out indoor GPS drift)
    const val ARRIVAL_CONFIRMATION_CHECKS = 3 // Consecutive verified readings before triggering an arrival alert
    const val DEPARTURE_CONFIRMATION_CHECKS = 3 // Consecutive verified readings before triggering a departure warning
}
