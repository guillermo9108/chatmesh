package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "story_seen",
    indices = [Index(value = ["storyId", "viewerPhone"], unique = true)]
)
data class StorySeenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val viewerPhone: String,
    val viewerName: String,
    val seenAt: Long = System.currentTimeMillis()
)
