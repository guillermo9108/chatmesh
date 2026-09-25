package com.example.mesh

data class PendingRetry(
    val packet: MeshPacket,
    var attempts: Int = 0,
    var nextRetryTime: Long = System.currentTimeMillis() + 2000L
)
