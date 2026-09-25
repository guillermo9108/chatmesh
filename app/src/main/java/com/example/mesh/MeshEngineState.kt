package com.example.mesh

import android.net.wifi.p2p.WifiP2pDevice
import com.example.data.entity.ContactEntity

data class MeshEngineState(
    val isWifiDirectActive: Boolean = false,
    val isWifiAwareActive: Boolean = false,
    val isGroupOwner: Boolean = false,
    val ssid: String = "",
    val passphrase: String = "",
    val p2pInterface: String = "p2p0",
    val localIpAddress: String = "",
    val connectedPeersCount: Int = 0,
    val myNodeId: String = "",
    val myPhoneNumber: String = "",
    val packetsSent: Long = 0L,
    val packetsReceived: Long = 0L,
    val packetsRelayed: Long = 0L,
    val isCallActive: Boolean = false,
    val isIncomingCall: Boolean = false,
    val isCallConnected: Boolean = false,
    val isVideoCall: Boolean = false,
    val activeCallPeer: ContactEntity? = null,
    val callDurationSeconds: Int = 0,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val activeTypingContactPhone: String? = null,
    val activeRecordingContactPhone: String? = null,
    val discoveredP2pDevices: List<WifiP2pDevice> = emptyList(),
    val optimalNodeName: String = "",
    val autoConnectStatus: String = "Modo Autónomo Activo"
)
