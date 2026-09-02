package com.example.core

import android.util.Log
import com.example.BuildConfig

/**
 * Enterprise Production Logger.
 * Guarantees zero sensitive data leakage in release builds by strictly gating logs
 * to [BuildConfig.DEBUG] while providing rich structured context in development.
 */
object AppLogger {
    const val DEFAULT_TAG: String = "KinTracker"

    inline fun d(tag: String = DEFAULT_TAG, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message())
        }
    }

    inline fun i(tag: String = DEFAULT_TAG, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message())
        }
    }

    inline fun w(tag: String = DEFAULT_TAG, throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, message(), throwable)
            } else {
                Log.w(tag, message())
            }
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, throwable)
        }
        // In enterprise production, forward non-fatal errors to crash reporting / telemetry here
    }
}
