package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * RoomAudioStreamManager
 * 
 * Provides a private, one-way audio feed between a Room Transmitter device (baby monitor)
 * and a Parent Listener device.
 * 
 * - Transmitter: Captures 16kHz PCM audio from microphone and streams to connected parent sockets.
 * - Listener: Connects to room transmitter IP and plays audio through the phone's speaker with live decibel metering.
 */
object RoomAudioStreamManager {

    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val DEFAULT_PORT = 18884

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- State Flows for UI ---
    private val _isTransmitterActive = MutableStateFlow(false)
    val isTransmitterActive = _isTransmitterActive.asStateFlow()

    private val _transmitterLocalIp = MutableStateFlow("")
    val transmitterLocalIp = _transmitterLocalIp.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _currentDecibels = MutableStateFlow(0f)
    val currentDecibels = _currentDecibels.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage = _statusMessage.asStateFlow()

    private val _activeConnectionsCount = MutableStateFlow(0)
    val activeConnectionsCount = _activeConnectionsCount.asStateFlow()

    private val _activeListeningMemberId = MutableStateFlow<String?>(null)
    val activeListeningMemberId = _activeListeningMemberId.asStateFlow()

    private val _latestDiscoveredIp = MutableStateFlow("")
    val latestDiscoveredIp = _latestDiscoveredIp.asStateFlow()

    private val _activeTransmittingMembers = MutableStateFlow<Set<String>>(emptySet())
    val activeTransmittingMembers = _activeTransmittingMembers.asStateFlow()

    private val _activeTransport = MutableStateFlow("Idle")
    val activeTransport = _activeTransport.asStateFlow()

    private var activeCircleId: String = AppConfig.DEFAULT_GROUP_SYNC_TOKEN
    private var myDeviceName: String = "KinDevice"
    private var myDeviceId: String = "device_${Build.MODEL.replace(" ", "_")}"

    fun setCircleContext(circleId: String, deviceName: String = "", deviceId: String = "") {
        if (circleId.isNotBlank()) activeCircleId = circleId
        if (deviceName.isNotBlank()) myDeviceName = deviceName
        if (deviceId.isNotBlank()) myDeviceId = deviceId
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val lanClients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private var relayWebSocket: WebSocket? = null
    private var listenerWebSocket: WebSocket? = null

    // ── Diagnostics ──────────────────────────────────────────────
    private val _bytesReceived = MutableStateFlow(0L)
    val bytesReceived = _bytesReceived.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError = _lastError.asStateFlow()

    private val _diagnosticLog = MutableStateFlow<List<String>>(emptyList())
    val diagnosticLog = _diagnosticLog.asStateFlow()

    private val _isToneTestActive = MutableStateFlow(false)
    val isToneTestActive = _isToneTestActive.asStateFlow()

    private fun appendLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.UK)
            .format(java.util.Date())
        val line = "[$ts] $msg"
        _diagnosticLog.value = (_diagnosticLog.value + line).takeLast(20)
    }

    /** Public entry-point so the UI layer can write events (e.g. permission denied) into the same log. */
    fun appendDiagLog(msg: String) = appendLog(msg)

    var onTransmitterToggled: ((Boolean) -> Unit)? = null

    private val memberIpRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Plays a 440 Hz sine wave for 2 seconds through STREAM_MUSIC using the exact same
     * AudioTrack configuration as the live stream. If you hear the beep, the speaker
     * pipeline is working and the problem is network/connection. If silent, the audio
     * routing itself is broken on this device.
     */
    fun playTestTone(context: Context) {
        if (_isToneTestActive.value) return
        _isToneTestActive.value = true
        appendLog("TEST TONE started — 440 Hz for 2s")
        scope.launch(Dispatchers.IO) {
            var audioTrack: AudioTrack? = null
            var audioManager: AudioManager? = null
            var audioFocusRequest: AudioFocusRequest? = null
            try {
                val durationMs = 2000
                val numSamples = SAMPLE_RATE * durationMs / 1000
                val buffer = ShortArray(numSamples)
                val freq = 440.0
                for (i in 0 until numSamples) {
                    buffer[i] = (Short.MAX_VALUE * kotlin.math.sin(2 * Math.PI * i * freq / SAMPLE_RATE)).toInt().toShort()
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager = am
                am?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(audioAttributes)
                            .setOnAudioFocusChangeListener {}
                            .build()
                        audioFocusRequest = req
                        it.requestAudioFocus(req)
                    } else {
                        @Suppress("DEPRECATION")
                        it.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    }
                    val maxVol = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val curVol = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                    appendLog("STREAM_MUSIC vol: $curVol / $maxVol | mode=${it.mode} | speaker=${it.isSpeakerphoneOn}")
                    if (curVol == 0) {
                        it.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.8f).toInt(), 0)
                        appendLog("Volume was 0 — raised to ${(maxVol * 0.8f).toInt()}")
                    }
                }

                appendLog("AudioTrack state=${audioTrack.state} (1=INIT OK)")
                audioTrack.play()
                appendLog("AudioTrack playing — writing ${buffer.size} samples")

                // Write in chunks matching the real stream
                val chunkSize = minBuf / 2
                var offset = 0
                while (offset < buffer.size) {
                    val end = minOf(offset + chunkSize, buffer.size)
                    audioTrack.write(buffer, offset, end - offset)
                    offset = end
                }
                audioTrack.stop()
                appendLog("TEST TONE complete — did you hear it?")
            } catch (e: Exception) {
                appendLog("TEST TONE error: ${e.message}")
                _lastError.value = "Tone test error: ${e.message}"
            } finally {
                try { audioTrack?.release() } catch (_: Exception) {}
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager?.abandonAudioFocus(null)
                    }
                } catch (_: Exception) {}
                _isToneTestActive.value = false
            }
        }
    }

    fun registerMemberIp(memberId: String, ip: String, memberName: String = "") {
        if (ip.isNotBlank() && ip != "0.0.0.0" && ip != "127.0.0.1") {
            memberIpRegistry[memberId] = ip
            _latestDiscoveredIp.value = ip
            if (memberName.isNotBlank()) {
                val clean = memberName.lowercase().replace(Regex("\\s*\\(.*?\\)"), "").trim()
                memberIpRegistry[clean] = ip
            }
        }
    }

    fun registerMemberAudioState(memberId: String, ip: String, isTransmitting: Boolean, memberName: String = "") {
        if (ip.isNotBlank() && ip != "0.0.0.0" && ip != "127.0.0.1") {
            registerMemberIp(memberId, ip, memberName)
        }
        val cleanName = memberName.lowercase().replace(Regex("\\s*\\(.*?\\)"), "").trim()
        val currentSet = _activeTransmittingMembers.value.toMutableSet()
        if (isTransmitting) {
            currentSet.add(memberId)
            if (cleanName.isNotBlank()) currentSet.add(cleanName)
        } else {
            currentSet.remove(memberId)
            if (cleanName.isNotBlank()) currentSet.remove(cleanName)
        }
        _activeTransmittingMembers.value = currentSet
    }

    fun isMemberTransmitting(memberId: String, memberName: String = ""): Boolean {
        val cleanName = memberName.lowercase().replace(Regex("\\s*\\(.*?\\)"), "").trim()
        val set = _activeTransmittingMembers.value
        return set.contains(memberId) || (cleanName.isNotBlank() && set.contains(cleanName))
    }

    fun getMemberIp(memberId: String, memberName: String = ""): String? {
        val clean = memberName.lowercase().replace(Regex("\\s*\\(.*?\\)"), "").trim()
        return memberIpRegistry[memberId] ?: memberIpRegistry[clean] ?: memberIpRegistry[memberName.lowercase().trim()]
    }

    private var serverSocket: ServerSocket? = null
    private var transmitterJob: Job? = null
    private var listenerJob: Job? = null
    private var listenerSocket: Socket? = null

    // ─────────────────────────────────────────────────────────────
    // 1. TRANSMITTER ENGINE (Baby Room Phone / Broadcaster)
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startTransmitter(context: Context? = null, port: Int = DEFAULT_PORT) {
        if (_isTransmitterActive.value) return

        transmitterJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            try {
                serverSocket = ServerSocket(port).apply { reuseAddress = true }
                _isTransmitterActive.value = true

                // Capture and expose the tablet's own local IP so the UI can show it
                val myIp = if (context != null) getLocalIpAddress(context) else "unknown"
                _transmitterLocalIp.value = myIp
                _statusMessage.value = "Transmitter active ($myIp & Cloud Relay)"
                appendLog("TRANSMITTER started | IP=$myIp | Port=$port")
                onTransmitterToggled?.invoke(true)

                // 1. Local Wi-Fi acceptor loop
                launch(Dispatchers.IO) {
                    while (isActive && serverSocket?.isClosed == false) {
                        val clientSocket = try {
                            serverSocket?.accept()
                        } catch (_: Exception) {
                            null
                        }

                        if (clientSocket != null) {
                            val clientIp = clientSocket.inetAddress?.hostAddress ?: "?"
                            lanClients.add(clientSocket)
                            val count = lanClients.size + (if (relayWebSocket != null) 1 else 0)
                            _activeConnectionsCount.value = count
                            appendLog("LAN client connected from $clientIp ✓")
                        }
                    }
                }

                // 2. Connect outgoing WebSocket to VPS Cloud Relay (for cellular listeners)
                launch(Dispatchers.IO) {
                    try {
                        val cleanCircle = activeCircleId.replace(Regex("[^a-zA-Z0-9_]"), "")
                        val wsUrl = "${AppConfig.AUDIO_RELAY_WS_URL}?role=broadcast&circleId=$cleanCircle&memberId=${URLEncoder.encode(myDeviceId, "UTF-8")}&memberName=${URLEncoder.encode(myDeviceName, "UTF-8")}"
                        appendLog("Connecting to VPS Cloud Relay...")
                        val req = Request.Builder().url(wsUrl).build()
                        relayWebSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                appendLog("VPS Cloud Bridge connected (Cellular Relay Ready) ✓")
                            }
                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                appendLog("VPS Cloud Bridge notice: ${t.message ?: "reconnecting"}")
                            }
                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                appendLog("VPS Cloud Bridge disconnected")
                            }
                        })
                    } catch (e: Exception) {
                        appendLog("VPS Cloud Bridge notice: ${e.message}")
                    }
                }

                // 3. Audio capture loop (fans out to LAN listeners & Cloud Relay)
                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
                val bufferSize = (minBufSize * 2).coerceAtLeast(2048)
                val buffer = ByteArray(bufferSize)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    appendLog("AudioRecord FAILED to init — mic permission missing?")
                    _lastError.value = "AudioRecord failed to initialise (mic permission?)"
                    return@launch
                }

                audioRecord.startRecording()
                appendLog("Microphone active — streaming to LAN & Cloud...")
                var totalBytesSent = 0L
                var firstChunkLogged = false

                while (isActive && _isTransmitterActive.value) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        totalBytesSent += bytesRead

                        // (a) Fan-out to all connected local LAN Wi-Fi listeners
                        synchronized(lanClients) {
                            val it = lanClients.iterator()
                            while (it.hasNext()) {
                                val client = it.next()
                                try {
                                    val out = client.getOutputStream()
                                    out.write(buffer, 0, bytesRead)
                                    out.flush()
                                } catch (_: Exception) {
                                    it.remove()
                                    try { client.close() } catch (_: Exception) {}
                                }
                            }
                        }

                        // (b) Fan-out to VPS Cloud Relay WebSocket
                        try {
                            relayWebSocket?.send(buffer.toByteString(0, bytesRead))
                        } catch (_: Exception) {}

                        // (c) RMS decibel level for UI waveform
                        val db = calculateDecibels(buffer, bytesRead)
                        _currentDecibels.value = db
                        _activeConnectionsCount.value = lanClients.size

                        if (!firstChunkLogged) {
                            appendLog("First audio chunk dispatched ($bytesRead bytes) ✓")
                            firstChunkLogged = true
                        }
                    } else if (bytesRead < 0) {
                        appendLog("AudioRecord read returned $bytesRead — stopping")
                        break
                    }
                }
                appendLog("Transmitter stopped. Total streamed: ${totalBytesSent / 1024} KB")
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Unknown"
                appendLog("TRANSMITTER error: $msg")
                _statusMessage.value = "Transmitter error: $msg"
                _lastError.value = msg
            } finally {
                try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
                stopTransmitterInternal()
            }
        }
    }

    fun stopTransmitter() {
        scope.launch(Dispatchers.IO) { stopTransmitterInternal() }
    }

    private fun stopTransmitterInternal() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        // Close all LAN clients
        synchronized(lanClients) {
            lanClients.forEach { try { it.close() } catch (_: Exception) {} }
            lanClients.clear()
        }

        // Close VPS WebSocket
        try {
            relayWebSocket?.close(1000, "Transmitter stopped")
        } catch (_: Exception) {}
        relayWebSocket = null

        transmitterJob?.cancel()
        transmitterJob = null
        _isTransmitterActive.value = false
        _transmitterLocalIp.value = ""
        _activeConnectionsCount.value = 0
        _currentDecibels.value = 0f
        _statusMessage.value = "Transmitter Stopped"
        onTransmitterToggled?.invoke(false)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. LISTENER ENGINE (Parent Phone)
    // ─────────────────────────────────────────────────────────────

    fun startListeningToMember(context: Context? = null, memberId: String, memberName: String, port: Int = DEFAULT_PORT) {
        if (_isListening.value) return
        _activeListeningMemberId.value = memberId

        listenerJob = scope.launch(Dispatchers.IO) {
            var targetIp = getMemberIp(memberId, memberName)

            if (targetIp.isNullOrBlank() || targetIp == "127.0.0.1") {
                _statusMessage.value = "Discovering ${memberName.substringBefore(" ")}'s audio beacon..."
                val autoDiscovered = discoverActiveBeaconIp(context, port)
                if (!autoDiscovered.isNullOrBlank()) {
                    targetIp = autoDiscovered
                    registerMemberIp(memberId, autoDiscovered, memberName)
                }
            }

            val finalIp = if (!targetIp.isNullOrBlank()) targetIp else "127.0.0.1"
            connectAndStream(context, finalIp, port, memberName)
        }
    }

    suspend fun discoverActiveBeaconIp(context: Context?, port: Int = DEFAULT_PORT): String? = withContext(Dispatchers.IO) {
        // Collect ALL candidate local subnets (device may have wlan0, ap0, rndis0 etc.)
        val subnets = mutableSetOf<String>()

        // 1. Try WifiManager (most reliable on wlan0)
        if (context != null) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val wifiInfo = wm?.connectionInfo
                if (wifiInfo != null && wifiInfo.ipAddress != 0) {
                    @Suppress("DEPRECATION")
                    val ip = android.text.format.Formatter.formatIpAddress(wifiInfo.ipAddress)
                    if (ip.isNotBlank() && ip != "0.0.0.0" && ip != "127.0.0.1" && ip.contains(".")) {
                        subnets.add(ip.substringBeforeLast(".") + ".")
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Enumerate ALL network interfaces as fallback / supplement
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces?.hasMoreElements() == true) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val h = addr.hostAddress ?: continue
                        if (h != "127.0.0.1" && h != "0.0.0.0" && h.contains(".")) {
                            subnets.add(h.substringBeforeLast(".") + ".")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (subnets.isEmpty()) return@withContext null

        _statusMessage.value = "Scanning ${subnets.size} subnet(s) for audio beacon..."

        val discoveredIp = CompletableDeferred<String?>()
        val jobs = mutableListOf<Job>()

        for (prefix in subnets) {
            for (i in 1..254) {
                val ip = "$prefix$i"
                val job = launch {
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(ip, port), 400)
                            if (!discoveredIp.isCompleted) {
                                discoveredIp.complete(ip)
                            }
                        }
                    } catch (_: Exception) {}
                }
                jobs.add(job)
            }
        }

        // Give up to 5 seconds — covers slow/congested home routers
        val result = withTimeoutOrNull(5000) {
            discoveredIp.await()
        }
        jobs.forEach { it.cancel() }
        if (!result.isNullOrBlank()) {
            _latestDiscoveredIp.value = result
            appendLog("Auto-discovered active audio beacon at $result ✓")
        }
        result
    }


    private suspend fun connectAndStream(context: Context?, hostIp: String, port: Int, memberName: String) = withContext(Dispatchers.IO) {
        var audioTrack: AudioTrack? = null
        var audioManager: AudioManager? = null
        var audioFocusRequest: AudioFocusRequest? = null
        var previousSpeaker = false
        var previousMode = AudioManager.MODE_NORMAL
        _bytesReceived.value = 0L

        try {
            _isListening.value = true

            val minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
            val bufferSize = (minBufSize * 2).coerceAtLeast(2048)
            val buffer = ByteArray(bufferSize)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_OUT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (context != null) {
                audioManager = context.applicationContext
                    .getSystemService(Context.AUDIO_SERVICE) as? AudioManager

                audioManager?.let { am ->
                    previousMode = am.mode
                    previousSpeaker = am.isSpeakerphoneOn
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(audioAttributes)
                            .setAcceptsDelayedFocusGain(false)
                            .setOnAudioFocusChangeListener {}
                            .build()
                        audioFocusRequest = focusReq
                        am.requestAudioFocus(focusReq)
                    } else {
                        @Suppress("DEPRECATION")
                        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    }

                    am.mode = AudioManager.MODE_NORMAL
                    am.isSpeakerphoneOn = true

                    if (curVol == 0) {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.8f).toInt(), 0)
                    }
                }
            }

            var totalBytes = 0L
            var connectedViaLan = false
            val isLanCandidate = hostIp.isNotBlank() && hostIp != "127.0.0.1" && hostIp != "cloud" &&
                (hostIp.startsWith("192.168.") || hostIp.startsWith("10.") || hostIp.startsWith("172."))

            // STEP 1: Attempt direct LAN Wi-Fi connection if on the same local network
            if (isLanCandidate) {
                try {
                    appendLog("Trying Direct Wi-Fi connection ($hostIp:$port)...")
                    _statusMessage.value = "Connecting to ${memberName.substringBefore(" ")} via Wi-Fi..."
                    val socket = Socket()
                    listenerSocket = socket
                    socket.connect(InetSocketAddress(hostIp, port), 1800) // Fast 1.8s timeout for LAN
                    connectedViaLan = true
                    _activeTransport.value = "Direct Wi-Fi"
                    appendLog("Connected via Direct Wi-Fi ✓")
                    _statusMessage.value = "Listening Live to ${memberName.substringBefore(" ")} (Wi-Fi)"

                    audioTrack.play()
                    val inputStream = socket.getInputStream()
                    var firstChunkLogged = false

                    while (isActive && !socket.isClosed && socket.isConnected) {
                        val bytesRead = inputStream.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            audioTrack.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            _bytesReceived.value = totalBytes
                            _currentDecibels.value = calculateDecibels(buffer, bytesRead)
                            if (!firstChunkLogged) {
                                appendLog("First chunk received via Direct Wi-Fi: $bytesRead bytes ✓")
                                firstChunkLogged = true
                            }
                        } else if (bytesRead < 0) {
                            appendLog("Direct Wi-Fi stream ended")
                            break
                        }
                    }
                } catch (e: Exception) {
                    appendLog("Direct Wi-Fi unreachable (${e.message}) — switching to VPS Cloud Relay...")
                    try { listenerSocket?.close() } catch (_: Exception) {}
                    listenerSocket = null
                }
            }

            // STEP 2: Fallback to VPS Cloud Relay WebSocket (for cellular or remote Wi-Fi)
            if (!connectedViaLan && isActive) {
                _activeTransport.value = "VPS Cloud Relay"
                _statusMessage.value = "Connecting to VPS Cloud Relay..."
                val cleanCircle = activeCircleId.replace(Regex("[^a-zA-Z0-9_]"), "")
                appendLog("Connecting to VPS Cloud Relay (circle: $cleanCircle)...")

                val wsUrl = "${AppConfig.AUDIO_RELAY_WS_URL}?role=listen&circleId=$cleanCircle&memberId=${URLEncoder.encode(myDeviceId, "UTF-8")}&memberName=${URLEncoder.encode(myDeviceName, "UTF-8")}"
                val req = Request.Builder().url(wsUrl).build()

                audioTrack.play()
                val streamFinished = CompletableDeferred<Unit>()

                val ws = httpClient.newWebSocket(req, object : WebSocketListener() {
                    var firstChunk = false

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        appendLog("VPS Cloud Relay connected ✓ (Cellular Stream Live)")
                        _statusMessage.value = "Listening Live to ${memberName.substringBefore(" ")} (Cloud Relay)"
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        val arr = bytes.toByteArray()
                        if (arr.isNotEmpty()) {
                            audioTrack.write(arr, 0, arr.size)
                            totalBytes += arr.size
                            _bytesReceived.value = totalBytes
                            _currentDecibels.value = calculateDecibels(arr, arr.size)
                            if (!firstChunk) {
                                appendLog("First chunk received via Cloud Relay: ${arr.size} bytes ✓")
                                firstChunk = true
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        appendLog("VPS Cloud Relay notice: ${t.message}")
                        _lastError.value = "Cloud Relay: ${t.message}"
                        if (!streamFinished.isCompleted) streamFinished.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        appendLog("VPS Cloud Relay ended ($code: $reason)")
                        if (!streamFinished.isCompleted) streamFinished.complete(Unit)
                    }
                })
                listenerWebSocket = ws

                try {
                    streamFinished.await()
                } catch (_: CancellationException) {}
            }

        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unknown error"
            appendLog("ERROR: $msg")
            _lastError.value = msg
            _statusMessage.value = "Connection ended: $msg"
        } finally {
            appendLog("Cleaning up listener. Total bytes received: ${_bytesReceived.value}")
            try {
                audioManager?.let { am ->
                    am.isSpeakerphoneOn = previousSpeaker
                    am.mode = previousMode
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        am.abandonAudioFocus(null)
                    }
                }
            } catch (_: Exception) {}
            try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
            try { listenerSocket?.close() } catch (_: Exception) {}
            try { listenerWebSocket?.close(1000, "Listener stopped") } catch (_: Exception) {}
            listenerSocket = null
            listenerWebSocket = null
            _isListening.value = false
            _activeListeningMemberId.value = null
            _currentDecibels.value = 0f
            _activeTransport.value = "Idle"
        }
    }

    fun startListening(context: Context? = null, hostIp: String, port: Int = DEFAULT_PORT) {
        if (_isListening.value) return

        listenerJob = scope.launch(Dispatchers.IO) {
            connectAndStream(context, hostIp, port, "Room Audio")
        }
    }

    fun stopListening() {
        scope.launch(Dispatchers.IO) {
            try { listenerSocket?.close() } catch (_: Exception) {}
            try { listenerWebSocket?.close(1000, "Stopped listening") } catch (_: Exception) {}
            listenerSocket = null
            listenerWebSocket = null
            listenerJob?.cancel()
            listenerJob = null
            _isListening.value = false
            _activeListeningMemberId.value = null
            _currentDecibels.value = 0f
            _activeTransport.value = "Idle"
            _statusMessage.value = "Stopped Listening"
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. UTILITIES & IP RESOLUTION
    // ─────────────────────────────────────────────────────────────

    private fun calculateDecibels(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until length - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val signedSample = if (sample >= 32768) sample - 65536 else sample
            sum += (signedSample * signedSample).toDouble()
        }

        val rms = sqrt(sum / sampleCount)
        if (rms <= 0) return 0f

        val db = (20 * log10(rms / 32767.0) + 90.0).toFloat()
        return db.coerceIn(0f, 100f)
    }

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            if (wifiInfo != null && wifiInfo.ipAddress != 0) {
                @Suppress("DEPRECATION")
                val ip = Formatter.formatIpAddress(wifiInfo.ipAddress)
                if (ip != "0.0.0.0" && ip.isNotBlank() && ip != "127.0.0.1") return ip
            }
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidateIps = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: ""
                        if (hostAddress.isNotBlank() && hostAddress != "127.0.0.1" && hostAddress != "0.0.0.0") {
                            val ifaceLower = iface.name.lowercase()
                            // Prioritize Wi-Fi, Ethernet, and Hotspot network interfaces
                            if (ifaceLower.contains("wlan") ||
                                ifaceLower.contains("ap") ||
                                ifaceLower.contains("eth") ||
                                hostAddress.startsWith("192.168.") ||
                                hostAddress.startsWith("10.") ||
                                hostAddress.startsWith("172.")) {
                                return hostAddress
                            }
                            candidateIps.add(hostAddress)
                        }
                    }
                }
            }
            if (candidateIps.isNotEmpty()) {
                return candidateIps.first()
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }
}
