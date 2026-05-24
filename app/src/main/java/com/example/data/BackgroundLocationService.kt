package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class BackgroundLocationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var locationManager: LocationManager? = null
    private lateinit var repository: FamilyRepository

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateUserPositionInDb(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(applicationContext)
        repository = FamilyRepository(database.familyDao())
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        createNotificationChannel()
        startForegroundServiceWithNotification()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        try {
            val hasFine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            val hasCoarse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (!hasFine && !hasCoarse) {
                return
            }

            val isGpsEnabled = try {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
            } catch (e: Exception) {
                false
            }
            
            val isNetworkEnabled = try {
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
            } catch (e: Exception) {
                false
            }

            if (isGpsEnabled) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        3000L, // Every 3 seconds
                        1.0f,  // 1 meter changes
                        locationListener,
                        android.os.Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    // Safe fallback
                }
            }
            if (isNetworkEnabled) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        3000L,
                        1.0f,
                        locationListener,
                        android.os.Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    // Safe fallback
                }
            }

            // Also request immediate location to initialize
            val lastKnownGps = try {
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                null
            }
            val lastKnownNet = try {
                locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: Exception) {
                null
            }
            val bestLocation = if (lastKnownGps != null && lastKnownNet != null) {
                if (lastKnownGps.time > lastKnownNet.time) lastKnownGps else lastKnownNet
            } else {
                lastKnownGps ?: lastKnownNet
            }
            bestLocation?.let { updateUserPositionInDb(it) }

        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun updateUserPositionInDb(location: Location) {
        var batteryPct = 85
        var isCharging = false
        try {
            val batteryStatusIntent = registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (batteryStatusIntent != null) {
                val level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100 / scale)
                }
                val status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {
            // Fallback
        }

        serviceScope.launch {
            try {
                // Read home position from shared preferences
                val prefs = getSharedPreferences("kintracker_prefs", MODE_PRIVATE)
                val homeLat = prefs.getFloat("homeLat", 51.332308f).toDouble()
                val homeLng = prefs.getFloat("homeLng", -0.117188f).toDouble()

                val current = repository.getFamilyMembersOnce()
                val me = current.firstOrNull { it.id == "me" } ?: return@launch

                // Distance calculation
                val latDiff = location.latitude - homeLat
                val lngDiff = location.longitude - homeLng
                val xDistanceKm = lngDiff * 111.0 * Math.cos(Math.toRadians(homeLat))
                val yDistanceKm = latDiff * 111.0
                val distanceTotalKm = Math.hypot(xDistanceKm, yDistanceKm)
                val isAtHome = distanceTotalKm < 0.05 // 50 meters

                val speedMph = Math.round((location.speed * 2.23694f) * 10.0) / 10.0

                val status = if (isAtHome) {
                    "At Home (Live GPS)"
                } else {
                    "Live GPS tracking (${String.format(Locale.US, "%.2f", distanceTotalKm)} km away)"
                }

                // Check approaching home alert conditions
                val triggeredPrefs = getSharedPreferences("triggered_alerts", MODE_PRIVATE)
                val isMeTriggered = triggeredPrefs.getBoolean("me", false)

                if (isAtHome) {
                    triggeredPrefs.edit().putBoolean("me", false).apply()
                } else if (distanceTotalKm <= 0.40) {
                    if (!isMeTriggered && distanceTotalKm > 0.08) {
                        triggeredPrefs.edit().putBoolean("me", true).apply()
                        repository.insertLog(
                            ActivityLog(
                                memberId = "me",
                                memberName = me.name,
                                actionText = "is close to Home (~${String.format(Locale.US, "%.0f", distanceTotalKm * 1000)}m away)",
                                iconName = "home"
                            )
                        )
                    }
                } else {
                    triggeredPrefs.edit().putBoolean("me", false).apply()
                }

                val updatedMe = me.copy(
                    x = location.longitude,
                    y = location.latitude,
                    batteryPercentage = batteryPct,
                    isCharging = isCharging,
                    speedMph = speedMph,
                    statusText = status
                )
                repository.updateMember(updatedMe)
            } catch (e: Exception) {
                // Fallback
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "kintracker_channel",
                "KinTracker GPS Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps tracking accurate during commutes"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            val notification = NotificationCompat.Builder(this, "kintracker_channel")
                .setContentTitle("KinTracker Active Map")
                .setContentText("Listening to live commuter safety loops in background")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } catch (se: SecurityException) {
                    // Fallback to standard foreground without TYPE_LOCATION if permission is temporary or absent in testing
                    startForeground(1, notification)
                }
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // Unavoidable fallback to prevent application crashes under simulated or automated runtimes
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Fallback
        }
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
