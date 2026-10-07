package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val storyId: String,
    val authorPhone: String,
    val authorName: String,
    val authorAvatarUri: String? = null,
    val mediaType: String,  // "TEXT" | "IMAGE"
    val content: String = "",
    val mediaBase64: String? = null,
    val backgroundColor: Int = 0,  // ARGB
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val viewedByMe: Boolean = false,
    val viewedAt: Long = 0L,
    val isMine: Boolean = false
)
