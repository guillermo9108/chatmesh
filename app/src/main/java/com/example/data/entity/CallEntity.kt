package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val contactPhone: String,
    val contactName: String,
    val isVideo: Boolean = false,
    val isOutgoing: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val status: String = "COMPLETED" // "COMPLETED", "MISSED", "REJECTED"
)
