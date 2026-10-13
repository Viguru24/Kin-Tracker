package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlin.math.roundToInt

object AlertNotificationHelper {
    private const val CHANNEL_ID = "kintracker_geofence_alerts"
    private const val CHANNEL_NAME = "KinTracker Departure & Arrival Alerts"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when family members depart or arrive at Home, Work, or Safe Zones"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun getNotificationVolume(context: Context): Int {
        val prefs = context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("notificationVolume")) {
            return prefs.getInt("notificationVolume", 80)
        }
        return getSystemNotificationVolumePercent(context)
    }

    fun getSystemNotificationVolumePercent(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val cur = am?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) ?: 6
            val max = am?.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) ?: 7
            if (max > 0) ((cur.toFloat() / max) * 100).toInt().coerceIn(0, 100) else 80
        } catch (_: Exception) { 80 }
    }

    fun setNotificationVolume(context: Context, volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        val prefs = context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("notificationVolume", clamped).apply()
        applySystemNotificationVolume(context, clamped)
    }

    fun applySystemNotificationVolume(context: Context, volumePercent: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val max = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                val target = ((volumePercent / 100f) * max).roundToInt().coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, target, 0)
            }
        } catch (_: Exception) {
            // Silently handle any platform security/DND restrictions
        }
    }

    fun playNotificationPreview(context: Context, volumePercent: Int = getNotificationVolume(context)) {
        if (volumePercent <= 0) return
        val appCtx = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (alertUri != null) {
                    val player = MediaPlayer().apply {
                        setDataSource(appCtx, alertUri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        val vol = (volumePercent / 100f).coerceIn(0f, 1f)
                        setVolume(vol, vol)
                        setOnCompletionListener { mp ->
                            try { mp.release() } catch (_: Exception) {}
                        }
                        setOnErrorListener { mp, _, _ ->
                            try { mp.release() } catch (_: Exception) {}
                            true
                        }
                        prepare()
                        start()
                    }
                } else {
                    val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volumePercent.coerceIn(1, 100))
                    toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
                    Handler(Looper.getMainLooper()).postDelayed({
                        try { toneG.release() } catch (_: Exception) {}
                    }, 350L)
                }
            } catch (e: Exception) {
                try {
                    val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volumePercent.coerceIn(1, 100))
                    toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
                    Handler(Looper.getMainLooper()).postDelayed({
                        try { toneG.release() } catch (_: Exception) {}
                    }, 350L)
                } catch (_: Exception) {}
            }
        }
    }

    fun postAlertNotification(context: Context, title: String, message: String, notificationId: Int = (System.currentTimeMillis() % 100000).toInt()) {
        try {
            ensureNotificationChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val volume = getNotificationVolume(context)
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))

            if (volume <= 0) {
                notificationBuilder.setSilent(true)
            } else {
                notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
                applySystemNotificationVolume(context, volume)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notificationBuilder.build())
        } catch (e: Exception) {
            // Catch any security or runtime notification exceptions safely
        }
    }
}
