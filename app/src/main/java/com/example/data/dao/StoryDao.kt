package com.example.data.dao

import androidx.room.*
import com.example.data.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(story: StoryEntity)

    @Query("SELECT * FROM stories WHERE expiresAt > :now ORDER BY createdAt DESC")
    fun getActiveStoriesFlow(now: Long): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE authorPhone = :phone AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getStoriesByAuthor(phone: String, now: Long): List<StoryEntity>

    @Query("UPDATE stories SET viewedByMe = 1, viewedAt = :at WHERE storyId = :storyId")
    suspend fun markAsViewed(storyId: String, at: Long)

    @Query("DELETE FROM stories WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM stories WHERE storyId = :storyId")
    suspend fun deleteStory(storyId: String)

    @Query("SELECT * FROM stories WHERE storyId = :storyId LIMIT 1")
    suspend fun getStoryById(storyId: String): StoryEntity?
}
