package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isChecked: Boolean = false,
    val addedByMemberId: String,
    val addedByMemberName: String,
    val timestamp: Long = System.currentTimeMillis()
)
