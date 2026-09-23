package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val phoneNumber: String,
    val nickname: String,
    val avatarUri: String? = null,
    val ssid: String,
    val ipAddress: String = "192.168.49.1",
    val registeredAt: Long = System.currentTimeMillis()
)
