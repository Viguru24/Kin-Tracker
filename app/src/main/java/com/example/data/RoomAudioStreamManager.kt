package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.text.format.Formatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
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

    private val memberIpRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun registerMemberIp(memberId: String, ip: String, memberName: String = "") {
        if (ip.isNotBlank() && ip != "0.0.0.0" && ip != "127.0.0.1") {
            memberIpRegistry[memberId] = ip
            if (memberName.isNotBlank()) {
                val clean = memberName.lowercase().replace(Regex("\\s*\\(.*?\\)"), "").trim()
                memberIpRegistry[clean] = ip
            }
        }
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
    // 1. TRANSMITTER ENGINE (Baby Room Phone)
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startTransmitter(port: Int = DEFAULT_PORT) {
        if (_isTransmitterActive.value) return

        transmitterJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                _isTransmitterActive.value = true
                _statusMessage.value = "Room Transmitter Active on port $port"

                while (isActive && serverSocket?.isClosed == false) {
                    val clientSocket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    }

                    if (clientSocket != null) {
                        launch(Dispatchers.IO) {
                            handleClientStream(clientSocket)
                        }
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Transmitter error: ${e.localizedMessage ?: "Unknown"}"
            } finally {
                stopTransmitterInternal()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleClientStream(clientSocket: Socket) = withContext(Dispatchers.IO) {
        _activeConnectionsCount.value += 1
        var audioRecord: AudioRecord? = null

        try {
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
                clientSocket.close()
                return@withContext
            }

            audioRecord.startRecording()
            val outputStream: OutputStream = clientSocket.getOutputStream()

            while (isActive && !clientSocket.isClosed && clientSocket.isConnected) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                    outputStream.flush()

                    // Calculate RMS decibel level
                    val db = calculateDecibels(buffer, bytesRead)
                    _currentDecibels.value = db
                } else if (bytesRead < 0) {
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            try {
                clientSocket.close()
            } catch (_: Exception) {}
            _activeConnectionsCount.value = (_activeConnectionsCount.value - 1).coerceAtLeast(0)
        }
    }

    fun stopTransmitter() {
        scope.launch(Dispatchers.IO) {
            stopTransmitterInternal()
        }
    }

    private fun stopTransmitterInternal() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        transmitterJob?.cancel()
        transmitterJob = null
        _isTransmitterActive.value = false
        _activeConnectionsCount.value = 0
        _currentDecibels.value = 0f
        _statusMessage.value = "Transmitter Stopped"
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
            connectAndStream(finalIp, port, memberName)
        }
    }

    private suspend fun discoverActiveBeaconIp(context: Context?, port: Int): String? = withContext(Dispatchers.IO) {
        val baseIp = if (context != null) getLocalIpAddress(context) else "192.168.1.1"
        if (!baseIp.contains(".")) return@withContext null
        val prefix = baseIp.substringBeforeLast(".") + "."

        val discoveredIp = CompletableDeferred<String?>()
        val jobs = mutableListOf<Job>()

        for (i in 1..254) {
            val ip = "$prefix$i"
            val job = launch {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(ip, port), 250)
                        if (!discoveredIp.isCompleted) {
                            discoveredIp.complete(ip)
                        }
                    }
                } catch (_: Exception) {}
            }
            jobs.add(job)
        }

        val result = withTimeoutOrNull(1800) {
            discoveredIp.await()
        }
        jobs.forEach { it.cancel() }
        result
    }

    private suspend fun connectAndStream(hostIp: String, port: Int, memberName: String) = withContext(Dispatchers.IO) {
        var audioTrack: AudioTrack? = null
        try {
            _statusMessage.value = "Connecting to ${memberName.substringBefore(" ")}..."
            val socket = Socket()
            listenerSocket = socket
            socket.connect(InetSocketAddress(hostIp, port), 5000)

            _isListening.value = true
            _statusMessage.value = "Listening Live to ${memberName.substringBefore(" ")}"

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

            audioTrack.play()
            val inputStream: InputStream = socket.getInputStream()

            while (isActive && !socket.isClosed && socket.isConnected) {
                val bytesRead = inputStream.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    audioTrack.write(buffer, 0, bytesRead)

                    val db = calculateDecibels(buffer, bytesRead)
                    _currentDecibels.value = db
                } else if (bytesRead < 0) {
                    break
                }
            }
        } catch (e: Exception) {
            _statusMessage.value = "Connection ended: ${e.localizedMessage ?: "Disconnected"}"
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            try {
                listenerSocket?.close()
            } catch (_: Exception) {}
            listenerSocket = null
            _isListening.value = false
            _activeListeningMemberId.value = null
            _currentDecibels.value = 0f
        }
    }

    fun startListening(hostIp: String, port: Int = DEFAULT_PORT) {
        if (_isListening.value) return

        listenerJob = scope.launch(Dispatchers.IO) {
            connectAndStream(hostIp, port, "Room Audio")
        }
    }

    fun stopListening() {
        scope.launch(Dispatchers.IO) {
            try {
                listenerSocket?.close()
            } catch (_: Exception) {}
            listenerSocket = null
            listenerJob?.cancel()
            listenerJob = null
            _isListening.value = false
            _activeListeningMemberId.value = null
            _currentDecibels.value = 0f
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
                if (ip != "0.0.0.0" && ip.isNotBlank()) return ip
            }
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: ""
                        if (hostAddress.startsWith("192.168.") || hostAddress.startsWith("10.") || hostAddress.startsWith("172.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }
}
