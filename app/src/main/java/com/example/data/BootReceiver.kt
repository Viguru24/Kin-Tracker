package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("kintracker_prefs", Context.MODE_PRIVATE)
            val isCloudSyncEnabled = prefs.getBoolean("isCloudSyncEnabled", true)
            val isLocationPaused = prefs.getBoolean("isLocationPaused", false)

            if (isCloudSyncEnabled) {
                val serviceIntent = Intent(context, BackgroundLocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
