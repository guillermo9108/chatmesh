package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.MeshNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshNodeDao {
    @Query("SELECT * FROM mesh_nodes ORDER BY isActive DESC, isDirectNeighbor DESC, lastSeen DESC")
    fun getAllNodesFlow(): Flow<List<MeshNodeEntity>>

    @Query("SELECT * FROM mesh_nodes WHERE isActive = 1")
    suspend fun getActiveNodes(): List<MeshNodeEntity>

    @Query("SELECT * FROM mesh_nodes WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getNodeById(nodeId: String): MeshNodeEntity?

    @Query("SELECT * FROM mesh_nodes WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getNodeByPhone(phone: String): MeshNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(node: MeshNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<MeshNodeEntity>)

    @Query("UPDATE mesh_nodes SET isActive = 0 WHERE nodeId = :nodeId")
    suspend fun setNodeInactive(nodeId: String)

    @Query("DELETE FROM mesh_nodes WHERE isActive = 0 AND lastSeen < :olderThan")
    suspend fun pruneOldNodes(olderThan: Long)
}
