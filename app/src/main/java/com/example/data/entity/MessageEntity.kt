package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["messageUuid"], unique = true),
        Index(value = ["senderPhone"]),
        Index(value = ["recipientPhone"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val messageUuid: String,
    val senderPhone: String,
    val recipientPhone: String,
    val content: String,
    val mediaType: String = "TEXT", // "TEXT", "IMAGE", "AUDIO", "FILE"
    val mediaUri: String? = null,
    val mediaBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "SENT", "DELIVERED", "READ"
    val hopCount: Int = 0,
    val isOutgoing: Boolean = true,
    val audioDurationSeconds: Int = 0
)
