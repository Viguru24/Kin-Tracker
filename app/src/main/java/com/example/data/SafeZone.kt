package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safe_zones")
data class SafeZone(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val iconName: String
)
