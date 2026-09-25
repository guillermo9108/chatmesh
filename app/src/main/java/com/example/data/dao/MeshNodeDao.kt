package com.example.data.dao

import androidx.room.*
import com.example.data.entity.MeshNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(node: MeshNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<MeshNodeEntity>)

    @Query("SELECT * FROM mesh_nodes ORDER BY lastSeen DESC")
    fun getAllNodesFlow(): Flow<List<MeshNodeEntity>>

    @Query("SELECT * FROM mesh_nodes WHERE isActive = 1")
    suspend fun getActiveNodes(): List<MeshNodeEntity>

    @Query("SELECT * FROM mesh_nodes WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getNodeById(nodeId: String): MeshNodeEntity?

    @Query("SELECT * FROM mesh_nodes WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getNodeByPhone(phone: String): MeshNodeEntity?

    @Query("UPDATE mesh_nodes SET isActive = 0 WHERE nodeId = :nodeId")
    suspend fun setNodeInactive(nodeId: String)

    @Query("DELETE FROM mesh_nodes WHERE lastSeen < :olderThan")
    suspend fun pruneOldNodes(olderThan: Long)
}
