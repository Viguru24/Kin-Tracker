package com.example.core

import android.util.Log
import com.example.BuildConfig

/**
 * Enterprise Production Logger.
 * Guarantees zero sensitive data leakage in release builds by strictly gating logs
 * to [BuildConfig.DEBUG] while retaining a bounded in-memory ring buffer of warnings/errors.
 */
object AppLogger {
    const val DEFAULT_TAG: String = "KinTracker"
    const val MAX_DIAGNOSTIC_ENTRIES: Int = 100

    data class DiagnosticEntry(
        val timestampMs: Long,
        val level: String,
        val tag: String,
        val message: String,
        val throwableSummary: String? = null
    )

    private val diagnosticsLock = Any()
    private val diagnosticsBuffer = ArrayDeque<DiagnosticEntry>(MAX_DIAGNOSTIC_ENTRIES)

    @PublishedApi
    internal fun recordDiagnostic(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val summary = throwable?.let { "${it.javaClass.simpleName}: ${it.message ?: ""}".trim() }
        val entry = DiagnosticEntry(
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwableSummary = summary
        )
        synchronized(diagnosticsLock) {
            while (diagnosticsBuffer.size >= MAX_DIAGNOSTIC_ENTRIES) {
                diagnosticsBuffer.removeFirst()
            }
            diagnosticsBuffer.addLast(entry)
        }
    }

    fun getRecentDiagnostics(): List<DiagnosticEntry> = synchronized(diagnosticsLock) {
        diagnosticsBuffer.toList()
    }

    fun clearDiagnostics() = synchronized(diagnosticsLock) {
        diagnosticsBuffer.clear()
    }

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
        val msg = message()
        recordDiagnostic("WARN", tag, msg, throwable)
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, msg, throwable)
            } else {
                Log.w(tag, msg)
            }
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        recordDiagnostic("ERROR", tag, message, throwable)
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, throwable)
        }
        // In enterprise production, forward non-fatal errors to crash reporting / telemetry here
    }
}
