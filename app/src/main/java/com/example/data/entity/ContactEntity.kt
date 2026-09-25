package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val phoneNumber: String,
    val displayName: String,
    val avatarUri: String? = null,
    val isRegisteredInMesh: Boolean = false,
    val isConnected: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val lastMessageText: String? = null,
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val meshNodeId: String? = null,
    val statusText: String = "¡Hola! Estoy usando ChatMesh"
)
