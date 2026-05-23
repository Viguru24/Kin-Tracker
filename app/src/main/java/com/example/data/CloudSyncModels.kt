package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudGroupPayload(
    val homeLat: Double,
    val homeLng: Double,
    val isHomeCalibrated: Boolean,
    val lastUpdated: Long,
    val members: Map<String, CloudMember> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class CloudMember(
    val id: String,
    val name: String,
    val avatarColorHex: String,
    val x: Double,
    val y: Double,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val speedMph: Double,
    val statusText: String,
    val isComingHome: Boolean,
    val etaMinutes: Int,
    val lastActive: Long
)
