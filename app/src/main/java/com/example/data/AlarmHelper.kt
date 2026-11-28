package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AlarmHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: android.media.Ringtone? = null
    private var appContext: Context? = null
    private var screenStateReceiver: android.content.BroadcastReceiver? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null

    private val _isRingingFlow = MutableStateFlow(false)
    val isRingingFlow: StateFlow<Boolean> = _isRingingFlow

    val isRinging: Boolean
        get() = (mediaPlayer?.isPlaying == true || fallbackRingtone?.isPlaying == true)

    fun triggerAlarm(context: Context) {
        try {
            appContext = context.applicationContext
            if (isRinging) return

            // Acquire CPU wake lock so the alarm plays reliably when device is locked/asleep
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
                wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "KinTracker:AlarmWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(14000)
                }
            } catch (_: Exception) {}

            // Register screen state receiver to stop alarm when power button is pressed (screen toggled)
            if (screenStateReceiver == null) {
                try {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(c: Context?, intent: android.content.Intent?) {
                            stopAlarm()
                        }
                    }
                    screenStateReceiver = receiver
                    val filter = android.content.IntentFilter().apply {
                        addAction(android.content.Intent.ACTION_SCREEN_OFF)
                        addAction(android.content.Intent.ACTION_SCREEN_ON)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        context.applicationContext.registerReceiver(receiver, filter)
                    }
                } catch (_: Exception) {
                    screenStateReceiver = null
                }
            }

            // Start vibration pattern
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (_: Exception) {}

            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alarmUri == null) return

            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            } catch (volEx: Exception) {
                volEx.printStackTrace()
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(alarmAttributes)
                        .build()
                    audioFocusRequest = req
                    audioManager.requestAudioFocus(req)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                }
            } catch (_: Exception) {}

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, alarmUri)
                setAudioAttributes(alarmAttributes)
                isLooping = true
                prepare()
                start()
            }
            _isRingingFlow.value = true

            // Automatically stop after 12 seconds (rings a few times loudly then shuts off)
            autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
            autoStopRunnable = Runnable { stopAlarm() }.also {
                mainHandler.postDelayed(it, 12000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to RingtoneManager if MediaPlayer fails
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                fallbackRingtone = ringtone
                ringtone?.play()
                _isRingingFlow.value = true
                autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
                autoStopRunnable = Runnable { stopAlarm() }.also {
                    mainHandler.postDelayed(it, 12000)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun stopAlarm() {
        _isRingingFlow.value = false
        autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
        autoStopRunnable = null
        try {
            val ctx = appContext
            if (ctx != null) {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                    audioFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
            }
        } catch (_: Exception) {}
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (fallbackRingtone?.isPlaying == true) {
                fallbackRingtone?.stop()
            }
            fallbackRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        try {
            val receiver = screenStateReceiver
            val ctx = appContext
            if (receiver != null && ctx != null) {
                ctx.unregisterReceiver(receiver)
                screenStateReceiver = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
