package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: String,
    val memberName: String,
    val actionText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val iconName: String // "home", "away", "battery", "critical", "check_in"
)
