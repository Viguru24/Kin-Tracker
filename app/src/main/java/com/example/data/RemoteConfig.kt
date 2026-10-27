package com.example.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Server-controlled app settings (served by the VPS at /sync/config/kin-tracker.json).
 *
 * The app applies the last config it successfully downloaded (cached on the phone), and falls back to
 * safe built-in defaults if it has never reached the server. A bad or unreachable config never breaks the app.
 */
object RemoteConfig {
    private const val CONFIG_URL = "https://api.cosmowhisper.com/sync/config/kin-tracker.json"
    private const val PREFS = "kintracker_remote_config"
    private const val KEY_JSON = "config_json"
    private const val KEY_LAST_FETCH = "last_fetch_ms"
    private const val MIN_FETCH_INTERVAL_MS = 30 * 60 * 1000L

    /** "esri_street" (default) or "osm" (classic OpenStreetMap fallback). */
    var mapProvider by mutableStateOf("esri_street")
        private set

    /** Hide a saved Work zone that sits on top of a school zone. */
    var hideWorkNearSchool by mutableStateOf(true)
        private set

    /** Hide the Work zone entirely. */
    var hideWorkZone by mutableStateOf(false)
        private set

    private var loadedFromCache = false

    /** Applies the cached config (if any). Safe to call repeatedly. */
    fun loadCached(context: Context) {
        if (loadedFromCache) return
        loadedFromCache = true
        try {
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            if (!json.isNullOrBlank()) apply(JSONObject(json))
        } catch (_: Exception) {
            // Ignore a corrupt cache and keep defaults
        }
    }

    /** Downloads the latest config from the VPS (at most every 30 minutes unless forced). */
    suspend fun refresh(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!force && now - prefs.getLong(KEY_LAST_FETCH, 0L) < MIN_FETCH_INTERVAL_MS) return@withContext

            val connection = URL(CONFIG_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            try {
                if (connection.responseCode != 200) return@withContext
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body) // validates before caching
                prefs.edit().putString(KEY_JSON, body).putLong(KEY_LAST_FETCH, now).apply()
                apply(json)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            // Offline or server problem: keep using the cached/default config
        }
    }

    private fun apply(json: JSONObject) {
        json.optJSONObject("map")?.optString("provider")?.takeIf { it.isNotBlank() }?.let { mapProvider = it }
        json.optJSONObject("zones")?.let { zones ->
            if (zones.has("hideWorkNearSchool")) hideWorkNearSchool = zones.optBoolean("hideWorkNearSchool", true)
            if (zones.has("hideWorkZone")) hideWorkZone = zones.optBoolean("hideWorkZone", false)
        }
    }
}
