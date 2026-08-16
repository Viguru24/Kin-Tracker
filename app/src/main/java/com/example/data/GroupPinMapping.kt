package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_pin_mappings")
data class GroupPinMapping(
    @PrimaryKey val pinCode: String, // 4-digit PIN e.g. "5729"
    val groupToken: String,
    val groupName: String,
    val creatorId: String,
    val createdTimestamp: Long,
    val isOwner: Boolean,
    val isActive: Boolean = false
)
