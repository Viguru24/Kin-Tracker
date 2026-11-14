package com.example.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Permission-free inertial activity analyzer using short, on-demand bursts from the linear
 * accelerometer to separate footstep cadence (~1.3–2.8 Hz walking/jogging) from smooth
 * vehicle motion when GPS speeds fall in the overlapping 0.6–18 mph range.
 */
object InertialActivityDetector {

    enum class CadenceKind {
        NONE,
        VEHICLE_SMOOTH,
        WALKING_CADENCE
    }

    data class InertialSnapshot(
        val kind: CadenceKind,
        val confidence: Int,
        val timestampMs: Long
    )

    private const val BURST_DURATION_MS = 2800L
    private const val BURST_COOLDOWN_MS = 12_000L
    private const val HINT_TTL_MS = 35_000L

    @Volatile private var lastBurstStartedMs = 0L
    @Volatile private var isBurstRunning = false
    @Volatile private var latestSnapshot: InertialSnapshot? = null

    fun getRecentHint(nowMs: Long = System.currentTimeMillis()): InertialSnapshot? {
        val snap = latestSnapshot ?: return null
        return if ((nowMs - snap.timestampMs) <= HINT_TTL_MS) snap else null
    }

    fun clearForTests() {
        lastBurstStartedMs = 0L
        isBurstRunning = false
        latestSnapshot = null
    }

    /**
     * Pure signal analyzer on (timestampMs, linearAccelerationMagnitude) samples collected over ~2.8s.
     * Periodic footstep peaks in the 1.3–2.9 Hz band distinguish walking/jogging from riding in a
     * slow-moving car stuck in urban traffic.
     */
    fun analyzeLinearAccelerationSamples(
        samples: List<Pair<Long, Double>>,
        nowMs: Long = System.currentTimeMillis()
    ): InertialSnapshot {
        if (samples.size < 8) {
            return InertialSnapshot(CadenceKind.NONE, 0, nowMs)
        }
        val durationMs = samples.last().first - samples.first().first
        if (durationMs < 1200L) {
            return InertialSnapshot(CadenceKind.NONE, 0, nowMs)
        }

        val magnitudes = samples.map { it.second }
        val mean = magnitudes.average()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        // Count rhythmic impulses spaced cleanly inside human step interval bounds (320ms..950ms)
        var peakCount = 0
        var lastPeakTime = -10_000L
        val stepThreshold = maxOf(mean + 1.1, 1.8)
        for (i in 1 until samples.lastIndex) {
            val prev = samples[i - 1].second
            val cur = samples[i].second
            val next = samples[i + 1].second
            val ts = samples[i].first
            if (cur >= stepThreshold && cur >= prev && cur > next) {
                val delta = ts - lastPeakTime
                if (lastPeakTime < 0L || delta in 320L..950L) {
                    peakCount++
                    lastPeakTime = ts
                } else if (delta > 950L) {
                    lastPeakTime = ts
                }
            }
        }

        val stepsPerSec = peakCount / (durationMs / 1000.0)
        return when {
            peakCount >= 3 && stepsPerSec in 1.05..3.3 && stdDev >= 0.75 -> {
                val conf = (65 + (peakCount - 3) * 6).coerceIn(65, 95)
                InertialSnapshot(CadenceKind.WALKING_CADENCE, conf, nowMs)
            }
            stdDev < 0.65 && mean < 1.2 && peakCount <= 1 -> {
                InertialSnapshot(CadenceKind.VEHICLE_SMOOTH, 75, nowMs)
            }
            else -> {
                InertialSnapshot(CadenceKind.NONE, 40, nowMs)
            }
        }
    }

    /**
     * Triggers an asynchronous ~2.8s accelerometer sample burst only when moving at ambiguous speeds
     * (0.6..18.0 mph) so idle phones and highway/rail cruising draw zero extra sensor power.
     */
    fun maybeSampleBurst(context: Context, speedMph: Double) {
        if (speedMph < 0.6 || speedMph > 18.0) return
        val now = System.currentTimeMillis()
        if (isBurstRunning || (now - lastBurstStartedMs) < BURST_COOLDOWN_MS) return

        val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val fallbackAccel = if (linearSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val targetSensor = linearSensor ?: fallbackAccel ?: return
        val usingLinear = linearSensor != null

        isBurstRunning = true
        lastBurstStartedMs = now
        val collected = ArrayList<Pair<Long, Double>>(32)
        val burstStart = android.os.SystemClock.elapsedRealtime()
        var gravity = doubleArrayOf(0.0, 0.0, 9.81)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val tsNow = android.os.SystemClock.elapsedRealtime()
                val mag = if (usingLinear) {
                    val x = event.values[0].toDouble()
                    val y = event.values[1].toDouble()
                    val z = event.values[2].toDouble()
                    sqrt(x * x + y * y + z * z)
                } else {
                    val alpha = 0.8
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                    val lx = event.values[0] - gravity[0]
                    val ly = event.values[1] - gravity[1]
                    val lz = event.values[2] - gravity[2]
                    sqrt(lx * lx + ly * ly + lz * lz)
                }
                collected.add(Pair(tsNow, mag))
                if (tsNow - burstStart >= BURST_DURATION_MS || collected.size >= 40) {
                    try {
                        sensorManager.unregisterListener(this)
                    } catch (_: Exception) {}
                    isBurstRunning = false
                    latestSnapshot = analyzeLinearAccelerationSamples(collected, System.currentTimeMillis())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        try {
            sensorManager.registerListener(listener, targetSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (_: Exception) {
            isBurstRunning = false
        }
    }
}
