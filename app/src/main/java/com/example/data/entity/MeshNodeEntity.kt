package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_nodes")
data class MeshNodeEntity(
    @PrimaryKey
    val nodeId: String,
    val ssid: String,
    val phoneNumber: String,
    val nickname: String,
    val ipAddress: String,
    val port: Int = 8988,
    val connectionType: String = "WIFI_DIRECT",
    val isDirectNeighbor: Boolean = true,
    val hopDistance: Int = 1,
    val lastSeen: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
