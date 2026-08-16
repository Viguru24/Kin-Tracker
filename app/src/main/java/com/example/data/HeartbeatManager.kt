package com.example.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object HeartbeatManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val PREF_LAST_HEARTBEAT = "last_heartbeat_time"
    private const val PREF_ANONYMOUS_ID = "anonymous_install_id"
    private const val HEARTBEAT_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun sendDailyHeartbeatIfDue(context: Context) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("kintracker_telemetry", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastHeartbeat = prefs.getLong(PREF_LAST_HEARTBEAT, 0L)

            // Only fire once every 24 hours
            if (now - lastHeartbeat < HEARTBEAT_INTERVAL_MS) {
                return@withContext
            }

            var installId = prefs.getString(PREF_ANONYMOUS_ID, null)
            if (installId == null) {
                installId = "inst_" + UUID.randomUUID().toString().take(12)
                prefs.edit().putString(PREF_ANONYMOUS_ID, installId).apply()
            }

            val pInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) { null }
            val versionName = pInfo?.versionName ?: "1.0.0"

            val payload = JSONObject().apply {
                put("install_id", installId)
                put("app_version", versionName)
                put("os_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                put("timestamp", now)
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(AppConfig.HEARTBEAT_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                prefs.edit().putLong(PREF_LAST_HEARTBEAT, now).apply()
            }
            response.close()
        } catch (e: Exception) {
            // Heartbeat failure is silently ignored so user experience is never impacted
        }
    }

    suspend fun submitFeedback(
        context: Context,
        category: String,
        feedbackText: String,
        rating: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("kintracker_telemetry", Context.MODE_PRIVATE)
            val installId = prefs.getString(PREF_ANONYMOUS_ID, "anon_" + UUID.randomUUID().toString().take(8))

            val pInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) { null }
            val versionName = pInfo?.versionName ?: "1.0.0"

            val payload = JSONObject().apply {
                put("install_id", installId)
                put("category", category)
                put("feedback", feedbackText.trim())
                put("rating", rating)
                put("app_version", versionName)
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                put("os_version", "Android ${Build.VERSION.RELEASE}")
                put("timestamp", System.currentTimeMillis())
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(AppConfig.FEEDBACK_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            response.close()
            isSuccess
        } catch (e: Exception) {
            false
        }
    }
}
