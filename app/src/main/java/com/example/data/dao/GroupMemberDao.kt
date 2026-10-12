package com.example.data.dao

import androidx.room.*
import com.example.data.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<GroupMemberEntity>)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY isAdmin DESC, displayName ASC")
    fun getMembersFlow(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembersList(groupId: String): List<GroupMemberEntity>

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND phoneNumber = :phoneNumber")
    suspend fun deleteMember(groupId: String, phoneNumber: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllMembers(groupId: String)
}
