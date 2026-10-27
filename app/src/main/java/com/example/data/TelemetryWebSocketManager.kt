package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Persistent WebSocket client & Server Alert Processor.
 * Maintains a real-time duplex connection to the Ra VPS telemetry hub
 * for sub-second location updates, instant departure/arrival alerts, and trip history.
 */
object TelemetryWebSocketManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var activeToken: String = ""
    private var isConnected = false

    // Deduplication set for server-generated alert IDs
    private val processedAlertIds = ConcurrentHashMap.newKeySet<String>()

    // Reactive stream of completed trips synced from the VPS
    private val _completedTrips = MutableStateFlow<List<CloudCompletedTrip>>(emptyList())
    val completedTrips: StateFlow<List<CloudCompletedTrip>> = _completedTrips

    // Selected completed trip for route playback and inspection on map
    private val _selectedTrip = MutableStateFlow<CloudCompletedTrip?>(null)
    val selectedTrip: StateFlow<CloudCompletedTrip?> = _selectedTrip

    fun selectTrip(trip: CloudCompletedTrip?) {
        _selectedTrip.value = trip
    }

    var onRealtimeMemberUpdated: (() -> Unit)? = null

    fun connect(
        context: Context,
        repository: FamilyRepository,
        token: String,
        myDeviceName: String,
        myDeviceUUID: String,
        onUiEvent: ((String) -> Unit)? = null
    ) {
        if (token.isBlank() || AppConfig.isTestEnvironment) return
        if (isConnected && activeToken == token && webSocket != null) return

        disconnect()
        activeToken = token

        val wsUrl = "wss://api.cosmowhisper.com/sync/$token"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "SNAPSHOT" -> {
                                val tripsArr = json.optJSONArray("completedTrips")
                                if (tripsArr != null) {
                                    updateTripsFromJsonArray(tripsArr)
                                }
                                val alertsArr = json.optJSONArray("recentAlerts")
                                if (alertsArr != null) {
                                    val alerts = parseAlertsFromJsonArray(alertsArr)
                                    // Mark initial snapshot alerts as seen so we don't replay old history on launch
                                    alerts.forEach { if (it.id.isNotBlank()) processedAlertIds.add(it.id) }
                                }
                                onRealtimeMemberUpdated?.invoke()
                            }
                            "MEMBER_UPDATE" -> {
                                onRealtimeMemberUpdated?.invoke()
                            }
                            "ALERT" -> {
                                val alertObj = json.optJSONObject("alert")
                                if (alertObj != null) {
                                    val alert = parseAlertFromJsonObject(alertObj)
                                    processServerAlerts(
                                        context = context,
                                        repository = repository,
                                        alerts = listOf(alert),
                                        myDeviceName = myDeviceName,
                                        myDeviceUUID = myDeviceUUID,
                                        onUiEvent = onUiEvent
                                    )
                                }
                            }
                            "COMPLETED_TRIP" -> {
                                val tripObj = json.optJSONObject("trip")
                                if (tripObj != null) {
                                    val trip = parseTripFromJsonObject(tripObj)
                                    val updated = (_completedTrips.value + trip).distinctBy { it.id }.takeLast(30)
                                    _completedTrips.value = updated
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                scope.launch {
                    delay(8000)
                    if (activeToken.isNotBlank() && !isConnected) {
                        connect(context, repository, activeToken, myDeviceName, myDeviceUUID, onUiEvent)
                    }
                }
            }
        })
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Reconnecting or stopping")
        } catch (_: Exception) {}
        webSocket = null
        isConnected = false
    }

    fun updateCompletedTrips(trips: List<CloudCompletedTrip>) {
        if (trips.isNotEmpty()) {
            _completedTrips.value = trips.distinctBy { it.id }.sortedByDescending { it.endTime ?: it.startTime }
        }
    }

    suspend fun processServerAlerts(
        context: Context,
        repository: FamilyRepository,
        alerts: List<CloudAlertEvent>,
        myDeviceName: String,
        myDeviceUUID: String,
        onUiEvent: ((String) -> Unit)? = null
    ) {
        if (alerts.isEmpty()) return
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        val isDepartureAlertsEnabled = prefs.getBoolean("isDepartureAlertsEnabled", true)

        for (alert in alerts) {
            if (alert.id.isBlank() || processedAlertIds.contains(alert.id)) continue
            processedAlertIds.add(alert.id)

            // Ignore alerts older than 5 minutes
            if (alert.timestamp > 0L && (now - alert.timestamp) > 5 * 60 * 1000L) continue

            // Ignore alerts about self
            val isSelf = alert.memberId == "me" ||
                    (myDeviceUUID.isNotBlank() && alert.memberId.contains(myDeviceUUID, ignoreCase = true)) ||
                    (myDeviceName.isNotBlank() && alert.memberName.equals(myDeviceName, ignoreCase = true))
            if (isSelf) continue

            if (alert.type == "DEPARTURE" && !isDepartureAlertsEnabled) continue

            val title = alert.title.ifBlank { "🛰️ Kin-Tracker Notice" }
            val msg = alert.message

            AlertNotificationHelper.postAlertNotification(context, title, msg)
            onUiEvent?.invoke("$title: $msg")
            VoiceAnnouncementManager.announceIfEnabled(context, "$title: $msg")

            repository.insertLog(
                ActivityLog(
                    memberId = alert.memberId,
                    memberName = alert.memberName,
                    actionText = msg.removePrefix(alert.memberName).trim(),
                    iconName = if (alert.type == "ARRIVAL") "check_in" else "away"
                )
            )
        }
    }

    private fun updateTripsFromJsonArray(arr: JSONArray) {
        val list = mutableListOf<CloudCompletedTrip>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(parseTripFromJsonObject(obj))
        }
        updateCompletedTrips(list)
    }

    private fun parseTripFromJsonObject(obj: JSONObject): CloudCompletedTrip {
        val bcList = mutableListOf<CloudTripBreadcrumb>()
        val bcArr = obj.optJSONArray("breadcrumbs")
        if (bcArr != null) {
            for (i in 0 until bcArr.length()) {
                val b = bcArr.optJSONObject(i) ?: continue
                bcList.add(
                    CloudTripBreadcrumb(
                        lat = b.optDouble("lat", 0.0),
                        lng = b.optDouble("lng", 0.0),
                        speedMph = b.optDouble("speedMph", 0.0),
                        timestamp = b.optLong("timestamp", 0L)
                    )
                )
            }
        }
        return CloudCompletedTrip(
            id = obj.optString("id"),
            memberId = obj.optString("memberId"),
            memberName = obj.optString("memberName"),
            transitMode = obj.optString("transitMode"),
            origin = obj.optString("origin"),
            destination = obj.optString("destination"),
            startLat = obj.optDouble("startLat", 0.0),
            startLng = obj.optDouble("startLng", 0.0),
            endLat = obj.optDouble("endLat", 0.0),
            endLng = obj.optDouble("endLng", 0.0),
            startTime = obj.optLong("startTime", 0L),
            endTime = obj.optLong("endTime", 0L),
            durationMinutes = obj.optInt("durationMinutes", 1),
            topSpeedMph = obj.optDouble("topSpeedMph", 0.0),
            totalDistanceKm = obj.optDouble("totalDistanceKm", 0.0),
            harshBrakingCount = obj.optInt("harshBrakingCount", 0),
            rapidAccelerationCount = obj.optInt("rapidAccelerationCount", 0),
            breadcrumbs = bcList
        )
    }

    private fun parseAlertsFromJsonArray(arr: JSONArray): List<CloudAlertEvent> {
        val list = mutableListOf<CloudAlertEvent>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(parseAlertFromJsonObject(obj))
        }
        return list
    }

    private fun parseAlertFromJsonObject(obj: JSONObject): CloudAlertEvent {
        return CloudAlertEvent(
            id = obj.optString("id"),
            type = obj.optString("type"),
            memberId = obj.optString("memberId"),
            memberName = obj.optString("memberName"),
            title = obj.optString("title"),
            message = obj.optString("message"),
            origin = obj.optString("origin"),
            destination = obj.optString("destination"),
            transitMode = obj.optString("transitMode"),
            speedMph = obj.optDouble("speedMph", 0.0),
            timestamp = obj.optLong("timestamp", 0L)
        )
    }
}
