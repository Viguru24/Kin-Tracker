package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyncAlarmReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        
        scope.launch {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                var bestLocation: Location? = null
                
                try {
                    val hasFine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true
                    val hasCoarse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true
                    
                    if (hasFine) {
                        bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    }
                    if (bestLocation == null && hasCoarse) {
                        bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }
                } catch (e: Exception) {}

                if (bestLocation == null) {
                    val database = AppDatabase.getDatabase(context.applicationContext)
                    val repository = FamilyRepository(database.familyDao())
                    val me = repository.getFamilyMembersOnce().firstOrNull { it.id == "me" }
                    if (me != null && me.y != 0.0 && me.x != 0.0) {
                        bestLocation = Location("db").apply {
                            latitude = me.y
                            longitude = me.x
                        }
                    }
                }

                if (bestLocation != null) {
                    BackgroundSyncProcessor.processLocationUpdate(context, bestLocation)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
