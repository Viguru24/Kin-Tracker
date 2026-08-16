package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

object AlarmHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: android.media.Ringtone? = null
    private var appContext: Context? = null
    private var screenStateReceiver: android.content.BroadcastReceiver? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    fun triggerAlarm(context: Context) {
        try {
            appContext = context.applicationContext
            if (mediaPlayer?.isPlaying == true || fallbackRingtone?.isPlaying == true) return

            // Acquire CPU and screen wakeup lock so the alarm triggers immediately when device is locked/asleep
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
                wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "KinTracker:AlarmWakeLock"
                ).apply {
                    acquire(10000)
                }
            } catch (wlEx: Exception) {
                try {
                    wakeLock = powerManager.newWakeLock(
                        android.os.PowerManager.PARTIAL_WAKE_LOCK,
                        "KinTracker:AlarmWakeLock"
                    ).apply {
                        acquire(10000)
                    }
                } catch (ex: Exception) {}
            }

            // Register screen state receiver to stop alarm when power button is pressed (screen toggled)
            if (screenStateReceiver == null) {
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
                context.applicationContext.registerReceiver(receiver, filter)
            }

            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alarmUri == null) return

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            } catch (volEx: Exception) {
                // Ignore volume adjustment errors if permission is denied
                volEx.printStackTrace()
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                stopAlarm()
            }, 10000)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to RingtoneManager if MediaPlayer fails
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                fallbackRingtone = ringtone
                ringtone?.play()
                Handler(Looper.getMainLooper()).postDelayed({
                    stopAlarm()
                }, 10000)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun stopAlarm() {
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
