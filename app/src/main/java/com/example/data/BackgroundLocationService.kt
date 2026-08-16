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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

import android.os.PowerManager
import android.os.Looper
import android.os.Bundle

class BackgroundLocationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var locationManager: LocationManager? = null
    private lateinit var repository: FamilyRepository
    private var wakeLock: PowerManager.WakeLock? = null

    private var isAppInForeground = false

    private val directLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            acquireWakeLock()
            serviceScope.launch {
                BackgroundSyncProcessor.processLocationUpdate(applicationContext, location)
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KinTracker:LocationWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(15 * 60 * 1000L) // 15 min sliding window
        } catch (e: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(applicationContext)
        repository = FamilyRepository(database.familyDao())
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        acquireWakeLock()
        createNotificationChannel()
        startForegroundServiceWithNotification()
        startLocationUpdates()
        scheduleRepeatingSyncAlarm()
        startContinuousSyncLoop()
    }

    private fun startContinuousSyncLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    acquireWakeLock()
                    val hasFine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true
                    val hasCoarse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true

                    if (hasFine || hasCoarse) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasFine) {
                            try {
                                locationManager?.getCurrentLocation(
                                    LocationManager.GPS_PROVIDER,
                                    null,
                                    mainExecutor
                                ) { loc ->
                                    if (loc != null) {
                                        serviceScope.launch {
                                            BackgroundSyncProcessor.processLocationUpdate(applicationContext, loc)
                                        }
                                    }
                                }
                            } catch (e: Exception) {}
                        }

                        val gps = if (hasFine) locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
                        val net = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        val best = if (gps != null && net != null) {
                            if (gps.time > net.time) gps else net
                        } else gps ?: net

                        best?.let { loc ->
                            BackgroundSyncProcessor.processLocationUpdate(applicationContext, loc)
                        }
                    }
                } catch (e: Exception) {}
                delay(20000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "ACTION_FOREGROUND" -> {
                    if (!isAppInForeground) {
                        isAppInForeground = true
                        restartLocationUpdates()
                    }
                }
                "ACTION_BACKGROUND" -> {
                    if (isAppInForeground) {
                        isAppInForeground = false
                        restartLocationUpdates()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun getReceiverPendingIntent(): PendingIntent {
        val intent = Intent(this, LocationUpdateReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun restartLocationUpdates() {
        try {
            locationManager?.removeUpdates(getReceiverPendingIntent())
        } catch (e: Exception) {}
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
                if (hasFine) {
                    locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
            
            val isNetworkEnabled = try {
                if (hasFine || hasCoarse) {
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }

            val interval = if (isAppInForeground) 3000L else 60000L
            val minDistance = if (isAppInForeground) 1.0f else 10.0f
            val pendingIntent = getReceiverPendingIntent()

            if (hasFine && isGpsEnabled) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        interval,
                        minDistance,
                        pendingIntent
                    )
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        interval,
                        minDistance,
                        directLocationListener,
                        Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    // Safe fallback
                }
            }
            if ((hasFine || hasCoarse) && isNetworkEnabled) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        interval,
                        minDistance,
                        pendingIntent
                    )
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        interval,
                        minDistance,
                        directLocationListener,
                        Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    // Safe fallback
                }
            }

            // Also request immediate location to initialize
            val lastKnownGps = try {
                if (hasFine) {
                    locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            val lastKnownNet = try {
                if (hasFine || hasCoarse) {
                    locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            val bestLocation = if (lastKnownGps != null && lastKnownNet != null) {
                if (lastKnownGps.time > lastKnownNet.time) lastKnownGps else lastKnownNet
            } else {
                lastKnownGps ?: lastKnownNet
            }
            bestLocation?.let { loc ->
                serviceScope.launch {
                    BackgroundSyncProcessor.processLocationUpdate(applicationContext, loc)
                }
            }

        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun scheduleRepeatingSyncAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, SyncAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val interval = 5 * 60 * 1000L
        val triggerAt = System.currentTimeMillis() + interval
        
        try {
            alarmManager.setRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerAt,
                interval,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "kintracker_channel",
                "Pulse Tracker GPS Monitor",
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
                .setContentTitle("Pulse Tracker Active Map")
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
            locationManager?.removeUpdates(getReceiverPendingIntent())
            locationManager?.removeUpdates(directLocationListener)
        } catch (e: Exception) {
            // Fallback
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {}
        serviceJob.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent = PendingIntent.getService(
            this,
            1,
            restartServiceIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }
        )
        val alarmService = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
