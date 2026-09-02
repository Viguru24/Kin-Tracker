package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_breadcrumbs",
    indices = [
        Index(value = ["memberId"]),
        Index(value = ["timestamp"]),
        Index(value = ["memberId", "timestamp"])
    ]
)
data class LocationBreadcrumb(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: String,
    val latitude: Double,
    val longitude: Double,
    val speedMph: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
