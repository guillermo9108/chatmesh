package com.example.mesh

enum class MeshTransport {
    WIFI_DIRECT,
    WIFI_LAN,
    HOTSPOT,
    NONE
}

data class MeshNetworkInfo(
    val transport: MeshTransport,
    val localIp: String,
    val subnetBroadcast: String,
    val gateway: String?,
    val ssid: String?
)
