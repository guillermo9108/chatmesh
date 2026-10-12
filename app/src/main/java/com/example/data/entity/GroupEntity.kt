package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val description: String = "",
    val avatarUri: String? = null,
    val createdByPhone: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageText: String? = null,
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isActive: Boolean = true,
    val lastSyncAt: Long = 0L
)
