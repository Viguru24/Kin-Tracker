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
    val shoppingItems: List<CloudShoppingItem> = emptyList()
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
    val locationSince: Long = 0L  // Timestamp of arrival at current location
)
