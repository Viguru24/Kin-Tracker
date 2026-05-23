package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey val id: String,
    val name: String,
    val avatarColorHex: String,
    val x: Double, // Position relative to home, centered at (0.0, 0.0)
    val y: Double,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val speedMph: Double,
    val statusText: String,
    val isComingHome: Boolean,
    val etaMinutes: Int
)
