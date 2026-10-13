package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey val id: String,
    val name: String,
    val avatarColorHex: String,
    val x: Double = 0.0, // Position relative to home, centered at (0.0, 0.0)
    val y: Double = 0.0,
    val batteryPercentage: Int = 100,
    val isCharging: Boolean = false,
    val speedMph: Double = 0.0,
    val statusText: String = "",
    val isComingHome: Boolean = false,
    val etaMinutes: Int = 0,
    val avatarEmoji: String = "",
    val phoneNumber: String = "",  // International format e.g. +447911123456
    val photoPath: String = "",    // Local file path to profile photo
    val lastActive: Long = 0L,     // Unix ms timestamp of last cloud update
    val locationSince: Long = 0L,  // Unix ms timestamp of when member arrived at current location
    val isLocationPaused: Boolean = false
)
