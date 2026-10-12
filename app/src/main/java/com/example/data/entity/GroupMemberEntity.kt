package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_members",
    indices = [Index(value = ["groupId", "phoneNumber"], unique = true)]
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val phoneNumber: String,
    val displayName: String,
    val isAdmin: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis()
)
