package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudGroupPayload(
    val homeLat: Double,
    val homeLng: Double,
    val isHomeCalibrated: Boolean,
    val lastUpdated: Long,
    val members: Map<String, CloudMember> = emptyMap(),
    val creatorId: String = "",
    val pinCode: String = "",
    val shoppingItems: List<CloudShoppingItem> = emptyList(),
    val deletedShoppingItems: Map<String, Long> = emptyMap(),
    val workLat: Double = 0.0,
    val workLng: Double = 0.0,
    val isWorkCalibrated: Boolean = false,
    val homeRadiusMeters: Double = AppConfig.DEFAULT_HOME_RADIUS_METERS,
    val workRadiusMeters: Double = AppConfig.DEFAULT_WORK_RADIUS_METERS
)

@JsonClass(generateAdapter = true)
data class CloudShoppingItem(
    val name: String,
    val isChecked: Boolean = false,
    val addedByMemberId: String = "",
    val addedByMemberName: String = "",
    val timestamp: Long = System.currentTimeMillis()
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
    val lastActive: Long,
    val avatarEmoji: String = "", // Profile picture emoji representation!
    val locationSince: Long = 0L, // Timestamp of arrival at current location
    val localIp: String = "",
    val isAudioTransmitter: Boolean = false,
    val isLocationPaused: Boolean = false
)

// ============================================================================
// Life360 Sovereign VPS REST DTOs
// ============================================================================

@JsonClass(generateAdapter = true)
data class CreateCircleRequest(
    val name: String,
    val creatorId: String,
    val creatorName: String,
    val avatarColorHex: String = "#00FF88",
    val avatarEmoji: String = "👑",
    val homeLat: Double = AppConfig.DEFAULT_HOME_LAT,
    val homeLng: Double = AppConfig.DEFAULT_HOME_LNG,
    val isHomeCalibrated: Boolean = false,
    val workLat: Double = AppConfig.DEFAULT_WORK_LAT,
    val workLng: Double = AppConfig.DEFAULT_WORK_LNG,
    val isWorkCalibrated: Boolean = false,
    val homeRadiusMeters: Double = AppConfig.DEFAULT_HOME_RADIUS_METERS,
    val workRadiusMeters: Double = AppConfig.DEFAULT_WORK_RADIUS_METERS
)

@JsonClass(generateAdapter = true)
data class JoinCircleRequest(
    val inviteCode: String,
    val memberId: String,
    val name: String,
    val avatarColorHex: String = "#00F0FF",
    val avatarEmoji: String = "📱",
    val phone: String = ""
)

@JsonClass(generateAdapter = true)
data class CircleLocationUpdateRequest(
    val memberId: String,
    val name: String,
    val avatarColorHex: String = "#00FF88",
    val avatarEmoji: String = "📱",
    val lat: Double,
    val lng: Double,
    val batteryPct: Int,
    val isCharging: Boolean,
    val speedMph: Double,
    val statusText: String = "Active",
    val isComingHome: Boolean = false,
    val etaMinutes: Int = 0,
    val isLocationPaused: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CircleSyncResponse(
    val success: Boolean = true,
    val circleId: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val creatorId: String = "",
    val homeLat: Double = AppConfig.DEFAULT_HOME_LAT,
    val homeLng: Double = AppConfig.DEFAULT_HOME_LNG,
    val isHomeCalibrated: Boolean = false,
    val workLat: Double = 0.0,
    val workLng: Double = 0.0,
    val isWorkCalibrated: Boolean = false,
    val homeRadiusMeters: Double = AppConfig.DEFAULT_HOME_RADIUS_METERS,
    val workRadiusMeters: Double = AppConfig.DEFAULT_WORK_RADIUS_METERS,
    val lastUpdated: Long = 0L,
    val members: List<CloudMember> = emptyList(),
    val shoppingItems: List<CloudShoppingItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CircleMutationResponse(
    val success: Boolean = true,
    val circle: CircleSyncResponse? = null,
    val inviteCode: String? = null,
    val error: String? = null
)
