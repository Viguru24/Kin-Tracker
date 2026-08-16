package com.example.data

import java.util.concurrent.ConcurrentHashMap

object MemberActivityTracker {
    // Maps memberId to last active timestamp (System.currentTimeMillis())
    val lastActiveMap = ConcurrentHashMap<String, Long>()
    
    // Maps memberId to stationary since timestamp (System.currentTimeMillis())
    val stationarySinceMap = ConcurrentHashMap<String, Long>()
    
    // Maps memberId to last known coordinates (lat, lng) to track movement
    val lastCoordsMap = ConcurrentHashMap<String, Pair<Double, Double>>()
    
    fun updateActivity(memberId: String, lat: Double, lng: Double, speedMph: Double = 0.0) {
        val now = System.currentTimeMillis()
        lastActiveMap[memberId] = now
        
        val lastCoords = lastCoordsMap[memberId]
        if (lastCoords == null) {
            lastCoordsMap[memberId] = Pair(lat, lng)
            stationarySinceMap[memberId] = now
        } else {
            // Distance in meters
            val xDist = (lng - lastCoords.second) * 111000.0 * Math.cos(Math.toRadians(lat))
            val yDist = (lat - lastCoords.first) * 111000.0
            val dist = Math.hypot(xDist, yDist)
            if (dist > 15.0 || speedMph > 1.0) { // If moved more than 15 meters or speed suggests movement
                lastCoordsMap[memberId] = Pair(lat, lng)
                stationarySinceMap[memberId] = now
            }
        }
    }
}
