package com.example.data.dao

import androidx.room.*
import com.example.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<GroupEntity>)

    @Query("SELECT * FROM groups WHERE isActive = 1 ORDER BY lastMessageTime DESC")
    fun getAllGroupsFlow(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun getGroupFlow(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("UPDATE groups SET lastMessageText = :text, lastMessageTime = :time WHERE groupId = :groupId")
    suspend fun updateLastMessage(groupId: String, text: String, time: Long)

    @Query("UPDATE groups SET unreadCount = unreadCount + 1 WHERE groupId = :groupId")
    suspend fun incrementUnread(groupId: String)

    @Query("UPDATE groups SET unreadCount = 0 WHERE groupId = :groupId")
    suspend fun markAsRead(groupId: String)

    @Query("UPDATE groups SET isActive = 0 WHERE groupId = :groupId")
    suspend fun deactivateGroup(groupId: String)
}
