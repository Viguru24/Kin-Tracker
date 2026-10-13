package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object VoiceAnnouncementManager {

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val spokenTimestamps = ConcurrentHashMap<String, Long>()
    private var pendingSpeechText: String? = null
    private var pendingSpeechVolume: Int = 80

    // Minimum cooldown between verbal announcements for the exact same person and action (2 minutes)
    const val DEBOUNCE_COOLDOWN_MS = 2 * 60 * 1000L

    @Synchronized
    fun initTts(context: Context) {
        if (textToSpeech != null) return

        val appCtx = context.applicationContext
        try {
            textToSpeech = TextToSpeech(appCtx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.let { tts ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            try {
                                val audioAttributes = AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                                tts.setAudioAttributes(audioAttributes)
                            } catch (e: Exception) {}
                        }

                        // Configure locale: prioritize default, fallback safely to UK, US, or generic English
                        var langResult = TextToSpeech.LANG_NOT_SUPPORTED
                        try {
                            langResult = tts.setLanguage(Locale.getDefault())
                        } catch (e: Exception) {}

                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            try {
                                langResult = tts.setLanguage(Locale.UK)
                            } catch (e: Exception) {}
                        }

                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            try {
                                langResult = tts.setLanguage(Locale.US)
                            } catch (e: Exception) {}
                        }

                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            try {
                                tts.setLanguage(Locale.ENGLISH)
                            } catch (e: Exception) {}
                        }

                        isTtsReady = true

                        // If an announcement was queued during initialization, speak it now
                        pendingSpeechText?.let { pending ->
                            val v = pendingSpeechVolume
                            pendingSpeechText = null
                            speakInternal(pending, v)
                        }
                    }
                } else {
                    isTtsReady = false
                }
            }
        } catch (e: Exception) {
            isTtsReady = false
        }
    }

    fun isVoiceAnnouncementsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
        // Default must be off (false) unless already configured on the phone
        return prefs.getBoolean("isVoiceAnnouncementsEnabled", false)
    }

    fun formatSpeechText(message: String): Pair<String, String>? {
        val cleanSpeechText = message
            .replace(Regex("^[🚪📍ℹ️🚨🏠👋]\\s*"), "")
            .replace(Regex("^(Departure Warning|Arrival Notice|Approaching Alert):\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father|Other Device)\\)", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleanSpeechText.isBlank()) return null

        val rawPerson = cleanSpeechText.split(Regex("[\\s,]+")).firstOrNull()?.lowercase() ?: "general"
        val personName = rawPerson.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val actionKey = when {
            cleanSpeechText.contains("left", ignoreCase = true) -> "left"
            cleanSpeechText.contains("arrived", ignoreCase = true) -> "arrived"
            cleanSpeechText.contains("home in", ignoreCase = true) || cleanSpeechText.contains("with you in", ignoreCase = true) -> "approaching"
            else -> "alert"
        }

        val spokenKey = "${rawPerson}_$actionKey"

        val formattedSpeech = when {
            cleanSpeechText.contains("has arrived at home", ignoreCase = true) ||
            cleanSpeechText.contains("has arrived home", ignoreCase = true) ||
            cleanSpeechText.equals("$personName has arrived at Home!", ignoreCase = true) ||
            cleanSpeechText.equals("$personName has arrived at Home", ignoreCase = true) -> {
                "$personName has arrived home"
            }
            cleanSpeechText.contains("has left the house", ignoreCase = true) -> {
                "$personName has left the house"
            }
            cleanSpeechText.contains("has left", ignoreCase = true) -> {
                cleanSpeechText.replace("!", "")
            }
            cleanSpeechText.contains("has arrived at", ignoreCase = true) -> {
                cleanSpeechText.replace("!", "")
            }
            else -> cleanSpeechText.replace("!", "")
        }

        return Pair(spokenKey, formattedSpeech)
    }

    fun announceIfEnabled(context: Context, rawMessage: String, force: Boolean = false) {
        if (!force && !isVoiceAnnouncementsEnabled(context)) {
            return
        }

        val volume = AlertNotificationHelper.getNotificationVolume(context)
        if (volume <= 0) {
            return
        }

        val formatted = formatSpeechText(rawMessage) ?: return
        val (spokenKey, formattedSpeech) = formatted

        val now = System.currentTimeMillis()
        val lastSpoken = spokenTimestamps[spokenKey] ?: 0L
        if (now - lastSpoken < DEBOUNCE_COOLDOWN_MS) {
            return
        }
        spokenTimestamps[spokenKey] = now

        initTts(context)

        mainHandler.post {
            try {
                val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume.coerceIn(1, 100))
                toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
                mainHandler.postDelayed({
                    try { toneG.release() } catch (_: Exception) {}
                }, 350L)
            } catch (e: Exception) {}

            mainHandler.postDelayed({
                speakInternal(formattedSpeech, volume)
            }, 300L)
        }
    }

    private fun speakInternal(text: String, volume: Int = 80) {
        if (!isTtsReady || textToSpeech == null) {
            pendingSpeechText = text
            pendingSpeechVolume = volume
            return
        }

        val volFraction = (volume / 100f).coerceIn(0.05f, 1.0f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val params = android.os.Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volFraction)
                }
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "kin_tracker_tts_${System.currentTimeMillis()}")
            } else {
                @Suppress("DEPRECATION")
                val params = java.util.HashMap<String, String>().apply {
                    put(TextToSpeech.Engine.KEY_PARAM_VOLUME, volFraction.toString())
                }
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {}
    }

    fun clearHistory() {
        spokenTimestamps.clear()
        pendingSpeechText = null
        pendingSpeechVolume = 80
    }
}
