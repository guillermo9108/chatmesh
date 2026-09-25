package com.example.mesh

data class PeerMetric(
    val nodeId: String,
    val ipAddress: String,
    val port: Int = 8988,
    val phoneNumber: String,
    val nickname: String,
    val hopDistance: Int = 1,
    val signalDbm: Int = -50,
    var currentLoad: Int = 0,
    var lastHeartbeat: Long = System.currentTimeMillis()
)
