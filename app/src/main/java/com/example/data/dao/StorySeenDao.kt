package com.example.data.dao

import androidx.room.*
import com.example.data.entity.StorySeenEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StorySeenDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(seen: StorySeenEntity)

    @Query("SELECT * FROM story_seen WHERE storyId = :storyId ORDER BY seenAt DESC")
    fun getViewersFlow(storyId: String): Flow<List<StorySeenEntity>>

    @Query("DELETE FROM story_seen WHERE storyId = :storyId")
    suspend fun deleteForStory(storyId: String)
}
