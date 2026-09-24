package com.example.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import com.example.data.entity.CallEntity
import com.example.data.entity.ContactEntity
import com.example.data.entity.MeshNodeEntity
import com.example.data.entity.MessageEntity
import com.example.data.repository.ChatMeshRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class MeshEngineState(
    val isWifiDirectActive: Boolean = false,
    val isWifiAwareActive: Boolean = false,
    val isGroupOwner: Boolean = false,
    val ssid: String = "",
    val passphrase: String = "",
    val p2pInterface: String = "",
    val localIpAddress: String = "192.168.49.1",
    val myPhoneNumber: String = "",
    val myNodeId: String = "",
    val connectedPeersCount: Int = 0,
    val discoveredP2pDevices: List<WifiP2pDevice> = emptyList(),
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val packetsRelayed: Long = 0,
    val activeCallPeer: ContactEntity? = null,
    val isCallActive: Boolean = false,
    val isVideoCall: Boolean = false,
    val callDurationSeconds: Int = 0,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val activeTypingContactPhone: String? = null,
    val activeRecordingContactPhone: String? = null,
    val optimalNodeName: String? = null,
    val autoConnectStatus: String = "Malla P2P activa y autónoma"
)

data class PeerMetric(
    val deviceAddress: String,
    val deviceName: String,
    var status: Int,
    var nodeLoad: Int = 0,
    var lastHeartbeat: Long = System.currentTimeMillis(),
    var isConnected: Boolean = false,
    var estimatedHops: Int = 1
)

data class PendingRetry(
    val packet: MeshPacket,
    val firstSentTime: Long = System.currentTimeMillis(),
    var retryCount: Int = 0,
    var lastAttempt: Long = System.currentTimeMillis()
)

class WiFiMeshEngine(
    private val context: Context,
    private val repository: ChatMeshRepository
) {
    private val TAG = "WiFiMeshEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val notificationHelper = NotificationHelper(context)

    private val _engineState = MutableStateFlow(MeshEngineState())
    val engineState: StateFlow<MeshEngineState> = _engineState.asStateFlow()

    // Real WiFi Direct components
    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var isP2pReceiverRegistered = false

    // Real WiFi Aware components
    private var awareManager: WifiAwareManager? = null
    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private val awarePeerHandles = ConcurrentHashMap<String, PeerHandle>()

    // Real Sockets
    private var serverSocket: ServerSocket? = null
    private var udpDiscoverySocket: DatagramSocket? = null
    private val activeClientSockets = ConcurrentHashMap<String, Socket>()
    private val peerIpByPhone = ConcurrentHashMap<String, String>()

    // Autonomous Network Metrics & QoS
    private val peerMetrics = ConcurrentHashMap<String, PeerMetric>()
    private val pendingRetries = ConcurrentHashMap<String, PendingRetry>()
    private val pendingChunks = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()
    private val chunkTotals = ConcurrentHashMap<String, Int>()
    private val chunkMetas = ConcurrentHashMap<String, MeshPacket>()

    // Loop prevention & Packet deduplication
    private val processedPacketUuids = ConcurrentHashMap.newKeySet<String>()

    // Background jobs
    private var meshMaintenanceJob: Job? = null
    private var udpBeaconJob: Job? = null
    private var callTimerJob: Job? = null
    private var audioRecordJob: Job? = null
    private var audioPlayJob: Job? = null

    // Audio stream port
    private val AUDIO_UDP_PORT = 8890
    private val TCP_MESH_PORT = 8888
    private val UDP_BEACON_PORT = 8889

    fun initialize(myPhone: String, myNickname: String) {
        val mySsid = SimDetectionUtil.generateSsid(myPhone)
        val myNodeId = "NODE_" + (if (myPhone.isNotBlank()) myPhone.replace("+", "") else System.currentTimeMillis().toString())

        _engineState.value = _engineState.value.copy(
            myPhoneNumber = myPhone,
            myNodeId = myNodeId,
            ssid = mySsid
        )

        setupRealWifiP2p(mySsid)
        setupRealWifiAware(mySsid)
        startRealTcpMeshServer()
        startRealUdpBeaconListener()
        startMeshMaintenanceLoop()
    }

    /**
     * Requirement 1: Creación real de red WiFi Direct
     */
    @SuppressLint("MissingPermission")
    private fun setupRealWifiP2p(ssid: String) {
        try {
            p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (p2pManager == null) {
                Log.w(TAG, "WiFi Direct no disponible en este hardware")
                return
            }

            p2pChannel = p2pManager?.initialize(context, context.mainLooper, null)

            registerP2pReceiver()
            setDeviceP2pName(ssid)

            // Setup real DNS-SD Service Discovery
            setupP2pDnsSdService(ssid)

            // Create autonomous group
            createAutonomousP2pGroup(ssid)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando WiFi Direct real", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun reCreateP2pGroup() {
        val currentSsid = _engineState.value.ssid
        p2pManager?.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                createAutonomousP2pGroup(currentSsid)
            }
            override fun onFailure(reason: Int) {
                createAutonomousP2pGroup(currentSsid)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createAutonomousP2pGroup(ssid: String) {
        p2pManager?.createGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Grupo WiFi Direct real creado exitosamente")
                _engineState.value = _engineState.value.copy(
                    isWifiDirectActive = true,
                    isGroupOwner = true
                )
                requestGroupAndConnectionDetails()
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup falló (código $reason), iniciando descubrimiento de pares")
                startP2pDiscovery()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestGroupAndConnectionDetails() {
        p2pManager?.requestGroupInfo(p2pChannel) { group: WifiP2pGroup? ->
            if (group != null) {
                _engineState.value = _engineState.value.copy(
                    ssid = group.networkName ?: _engineState.value.ssid,
                    passphrase = group.passphrase ?: "",
                    p2pInterface = group.`interface` ?: "",
                    isGroupOwner = group.isGroupOwner
                )
                // Register connected clients
                for (client in group.clientList) {
                    handleDiscoveredP2pDevice(client, isConnected = true)
                }
            }
        }

        p2pManager?.requestConnectionInfo(p2pChannel) { info: WifiP2pInfo? ->
            if (info != null && info.groupFormed) {
                val ownerIp = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                _engineState.value = _engineState.value.copy(
                    isWifiDirectActive = true,
                    isGroupOwner = info.isGroupOwner,
                    localIpAddress = if (info.isGroupOwner) ownerIp else _engineState.value.localIpAddress
                )
                if (!info.isGroupOwner) {
                    connectToMeshSocket(ownerIp, TCP_MESH_PORT)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startP2pDiscovery() {
        try {
            p2pManager?.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "discoverPeers iniciado exitosamente")
                    _engineState.value = _engineState.value.copy(isWifiDirectActive = true)
                }
                override fun onFailure(reason: Int) {
                    Log.w(TAG, "discoverPeers falló: $reason")
                }
            })
            p2pManager?.discoverServices(p2pChannel, null)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        p2pManager?.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Conexión iniciada con ${device.deviceName}")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Fallo al conectar con ${device.deviceName}: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun evaluateAndAutoConnectToBestNode() {
        val devices = _engineState.value.discoveredP2pDevices
        if (devices.isEmpty()) return

        // Update peer metrics cache
        for (dev in devices) {
            val existing = peerMetrics[dev.deviceAddress]
            if (existing != null) {
                existing.status = dev.status
                existing.isConnected = (dev.status == WifiP2pDevice.CONNECTED)
            } else {
                peerMetrics[dev.deviceAddress] = PeerMetric(
                    deviceAddress = dev.deviceAddress,
                    deviceName = dev.deviceName ?: "Nodo P2P",
                    status = dev.status,
                    isConnected = (dev.status == WifiP2pDevice.CONNECTED)
                )
            }
        }

        // If currently connected to a healthy, non-saturated peer, maintain connection
        val currentlyConnected = devices.filter { it.status == WifiP2pDevice.CONNECTED }
        if (currentlyConnected.isNotEmpty()) {
            val connectedDev = currentlyConnected.first()
            val metric = peerMetrics[connectedDev.deviceAddress]
            val lastSeenDiff = System.currentTimeMillis() - (metric?.lastHeartbeat ?: System.currentTimeMillis())
            if (metric != null && metric.nodeLoad < 8 && lastSeenDiff < 10000) {
                _engineState.value = _engineState.value.copy(
                    optimalNodeName = connectedDev.deviceName ?: connectedDev.deviceAddress,
                    autoConnectStatus = "Conectado a ${connectedDev.deviceName} (Carga: ${metric.nodeLoad} enlaces, Enlace Óptimo)"
                )
                return
            }
        }

        // Filter available candidate devices (unrestricted auto-connect to best candidate)
        val candidates = devices.filter { it.status == WifiP2pDevice.AVAILABLE }
        if (candidates.isEmpty()) return

        val bestCandidate = candidates.maxByOrNull { device ->
            calculateCandidateScore(device)
        }

        if (bestCandidate != null) {
            val metric = peerMetrics[bestCandidate.deviceAddress]
            val load = metric?.nodeLoad ?: 0
            Log.i(TAG, "⚡ Conexión automática al nodo más óptimo: ${bestCandidate.deviceName} (${bestCandidate.deviceAddress}) Carga: $load")
            _engineState.value = _engineState.value.copy(
                optimalNodeName = bestCandidate.deviceName ?: bestCandidate.deviceAddress,
                autoConnectStatus = "Auto-conectando al nodo óptimo: ${bestCandidate.deviceName} (Carga: $load)"
            )
            connectToPeer(bestCandidate)
        }
    }

    private fun calculateCandidateScore(device: WifiP2pDevice): Double {
        val metric = peerMetrics[device.deviceAddress]
        var score = 100.0

        // Status bonus
        when (device.status) {
            WifiP2pDevice.CONNECTED -> score += 60.0
            WifiP2pDevice.AVAILABLE -> score += 40.0
            WifiP2pDevice.INVITED -> score += 10.0
            else -> score -= 200.0
        }

        // Proximity indicator: ChatMesh device naming
        if (device.deviceName?.startsWith("ChatMesh") == true) {
            score += 35.0
        }

        // Low saturation preference: Lower node load is higher score
        val load = metric?.nodeLoad ?: 0
        score -= (load * 18.0)

        // Lower hops is better
        val hops = metric?.estimatedHops ?: 1
        score -= (hops - 1) * 20.0

        // Heartbeat freshness
        if (metric != null) {
            val ageSec = (System.currentTimeMillis() - metric.lastHeartbeat) / 1000
            if (ageSec < 8) score += 20.0
            else score -= (ageSec.coerceAtMost(60) * 1.5)
        }

        return score
    }

    @SuppressLint("MissingPermission")
    private fun setDeviceP2pName(name: String) {
        try {
            val method = p2pManager?.javaClass?.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method?.invoke(p2pManager, p2pChannel, name, null)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun setupP2pDnsSdService(ssid: String) {
        try {
            val record = mapOf(
                "phone" to _engineState.value.myPhoneNumber,
                "ssid" to ssid,
                "node" to _engineState.value.myNodeId,
                "port" to TCP_MESH_PORT.toString()
            )
            val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("ChatMesh", "_chatmesh._tcp", record)
            p2pManager?.addLocalService(p2pChannel, serviceInfo, null)

            val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
            p2pManager?.setDnsSdResponseListeners(p2pChannel,
                { _, _, device ->
                    handleDiscoveredP2pDevice(device, isConnected = false)
                },
                { _, txtRecordMap, device ->
                    val phone = txtRecordMap["phone"] ?: ""
                    val remoteSsid = txtRecordMap["ssid"] ?: device.deviceName
                    if (phone.isNotBlank()) {
                        registerNodeFromDnsSd(phone, remoteSsid, device.deviceAddress)
                    }
                }
            )
            p2pManager?.addServiceRequest(p2pChannel, serviceRequest, null)
        } catch (_: Exception) {}
    }

    private fun registerP2pReceiver() {
        if (isP2pReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        try {
            context.registerReceiver(p2pReceiver, filter)
            isP2pReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _engineState.value = _engineState.value.copy(
                        isWifiDirectActive = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                    )
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    p2pManager?.requestPeers(p2pChannel) { peers ->
                        peers?.deviceList?.let { deviceList ->
                            _engineState.value = _engineState.value.copy(
                                discoveredP2pDevices = deviceList.toList()
                            )
                            for (device in deviceList) {
                                handleDiscoveredP2pDevice(device, isConnected = (device.status == WifiP2pDevice.CONNECTED))
                            }
                            evaluateAndAutoConnectToBestNode()
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    requestGroupAndConnectionDetails()
                    evaluateAndAutoConnectToBestNode()
                }
            }
        }
    }

    private fun handleDiscoveredP2pDevice(device: WifiP2pDevice, isConnected: Boolean) {
        scope.launch {
            val devName = device.deviceName ?: "Dispositivo WiFi Direct"
            val phone = if (devName.startsWith("ChatMesh_")) {
                devName.removePrefix("ChatMesh_")
            } else {
                ""
            }

            val node = MeshNodeEntity(
                nodeId = "NODE_" + device.deviceAddress.replace(":", ""),
                ssid = devName,
                phoneNumber = phone.ifEmpty { "P2P:" + device.deviceAddress.takeLast(8) },
                nickname = devName,
                ipAddress = if (isConnected) "192.168.49.20" else "",
                port = TCP_MESH_PORT,
                connectionType = "WIFI_DIRECT",
                isDirectNeighbor = true,
                hopDistance = 1,
                lastSeen = System.currentTimeMillis(),
                isActive = isConnected
            )
            repository.saveMeshNode(node)

            // Auto-discovery: Match with contacts list and put online like Messenger
            val allContacts = repository.getAllContactsList()
            val cleanPhone = phone.replace("+", "")
            val matchedContact = allContacts.find { c ->
                val cClean = c.phoneNumber.replace("+", "")
                (cleanPhone.isNotBlank() && (cClean == cleanPhone || cClean.endsWith(cleanPhone) || cleanPhone.endsWith(cClean))) ||
                (devName.isNotBlank() && cClean.isNotBlank() && devName.contains(cClean.takeLast(8))) ||
                (!c.meshNodeId.isNullOrBlank() && c.meshNodeId.equals(device.deviceAddress, ignoreCase = true))
            }

            if (matchedContact != null) {
                repository.saveContact(
                    matchedContact.copy(
                        isRegisteredInMesh = true,
                        isConnected = isConnected || matchedContact.isConnected,
                        meshNodeId = device.deviceAddress,
                        lastSeen = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun registerNodeFromDnsSd(phone: String, ssid: String, macAddress: String) {
        scope.launch {
            val node = MeshNodeEntity(
                nodeId = "NODE_" + macAddress.replace(":", ""),
                ssid = ssid,
                phoneNumber = phone,
                nickname = ssid,
                ipAddress = "192.168.49.20",
                port = TCP_MESH_PORT,
                connectionType = "WIFI_DIRECT_DNS_SD",
                isDirectNeighbor = true,
                hopDistance = 1,
                lastSeen = System.currentTimeMillis(),
                isActive = true
            )
            repository.saveMeshNode(node)

            val contact = repository.getContact(phone)
            if (contact != null) {
                repository.saveContact(contact.copy(isRegisteredInMesh = true, isConnected = true))
            }
        }
    }

    /**
     * Requirement 2: Uso de WiFi Aware real (NAN)
     */
    private fun setupRealWifiAware(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
                Log.i(TAG, "WiFi Aware (NAN) no soportado en este chip")
                return
            }

            awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
            if (awareManager?.isAvailable == true) {
                attachRealWifiAware(ssid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando WiFi Aware", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun attachRealWifiAware(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            awareManager?.attach(object : AttachCallback() {
                override fun onAttached(session: WifiAwareSession?) {
                    awareSession = session
                    _engineState.value = _engineState.value.copy(isWifiAwareActive = true)
                    publishRealAwareService(ssid)
                    subscribeRealAwareService()
                }

                override fun onAttachFailed() {
                    _engineState.value = _engineState.value.copy(isWifiAwareActive = false)
                }
            }, null)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun publishRealAwareService(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val config = PublishConfig.Builder()
            .setServiceName("ChatMeshOffline")
            .setServiceSpecificInfo(ssid.toByteArray())
            .build()

        awareSession?.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                publishSession = session
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                val json = String(message)
                val packet = MeshPacket.fromJson(json)
                if (packet != null) {
                    processIncomingPacket(packet)
                }
            }
        }, null)
    }

    @SuppressLint("MissingPermission")
    private fun subscribeRealAwareService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val config = SubscribeConfig.Builder()
            .setServiceName("ChatMeshOffline")
            .build()

        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                subscribeSession = session
            }

            override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: MutableList<ByteArray>?) {
                val discoveredSsid = if (serviceSpecificInfo != null) String(serviceSpecificInfo) else "ChatMesh_Peer"
                val phone = if (discoveredSsid.startsWith("ChatMesh_")) discoveredSsid.removePrefix("ChatMesh_") else ""
                val peerNodeId = "AWARE_" + peerHandle.hashCode()
                awarePeerHandles[peerNodeId] = peerHandle

                if (phone.isNotBlank()) {
                    awarePeerHandles[phone] = peerHandle
                }

                scope.launch {
                    val node = MeshNodeEntity(
                        nodeId = peerNodeId,
                        ssid = discoveredSsid,
                        phoneNumber = phone.ifEmpty { "AWARE_${peerHandle.hashCode()}" },
                        nickname = discoveredSsid,
                        ipAddress = "192.168.49.1",
                        port = TCP_MESH_PORT,
                        connectionType = "WIFI_AWARE",
                        isDirectNeighbor = true,
                        hopDistance = 1,
                        lastSeen = System.currentTimeMillis(),
                        isActive = true
                    )
                    repository.saveMeshNode(node)

                    if (phone.isNotBlank()) {
                        val contact = repository.getContact(phone)
                        if (contact != null) {
                            repository.saveContact(contact.copy(isRegisteredInMesh = true, isConnected = true, lastSeen = System.currentTimeMillis()))
                        }
                    }
                }
            }
        }, null)
    }

    /**
     * Requirement 1 & 6: Servidor TCP y sockets P2P reales
     */
    private fun startRealTcpMeshServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(TCP_MESH_PORT)
                Log.i(TAG, "Servidor TCP Malla activo en puerto $TCP_MESH_PORT")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    handleClientSocket(socket)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Servidor TCP cerrado: ${e.message}")
            }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        scope.launch {
            val remoteIp = socket.inetAddress.hostAddress ?: ""
            activeClientSockets[remoteIp] = socket
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    val packet = MeshPacket.fromJson(line)
                    if (packet != null) {
                        peerIpByPhone[packet.sourcePhone] = remoteIp
                        processIncomingPacket(packet)
                    }
                }
            } catch (_: Exception) {
            } finally {
                activeClientSockets.remove(remoteIp)
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun connectToMeshSocket(host: String, port: Int) {
        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 4000)
                activeClientSockets[host] = socket

                // Send Handshake
                val handshake = MeshPacket(
                    packetType = "HANDSHAKE",
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = _engineState.value.ssid,
                    sourceSsid = _engineState.value.ssid,
                    destinationPhone = "BROADCAST"
                )
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(handshake.toJson())

                handleClientSocket(socket)
            } catch (e: Exception) {
                Log.d(TAG, "No se pudo conectar a $host:$port: ${e.message}")
            }
        }
    }

    /**
     * Descubrimiento UDP broadcast y recepción en la subred de WiFi Direct (192.168.49.255)
     */
    private fun startRealUdpBeaconListener() {
        scope.launch {
            try {
                udpDiscoverySocket = DatagramSocket(UDP_BEACON_PORT)
                udpDiscoverySocket?.broadcast = true
                val buffer = ByteArray(65536)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpDiscoverySocket?.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue
                    val msg = String(packet.data, 0, packet.length)
                    val meshPacket = MeshPacket.fromJson(msg)

                    if (meshPacket != null && meshPacket.sourcePhone != _engineState.value.myPhoneNumber) {
                        peerIpByPhone[meshPacket.sourcePhone] = senderIp

                        // Register peer in database
                        val node = MeshNodeEntity(
                            nodeId = meshPacket.sourceNodeId,
                            ssid = meshPacket.sourceSsid,
                            phoneNumber = meshPacket.sourcePhone,
                            nickname = meshPacket.sourceName,
                            ipAddress = senderIp,
                            port = TCP_MESH_PORT,
                            connectionType = "WIFI_DIRECT_UDP",
                            isDirectNeighbor = true,
                            hopDistance = 1,
                            lastSeen = System.currentTimeMillis(),
                            isActive = true
                        )
                        repository.saveMeshNode(node)

                        // Update matching contact to Online
                        val allContacts = repository.getAllContactsList()
                        val srcClean = meshPacket.sourcePhone.replace("+", "")
                        val matching = allContacts.find { c ->
                            val cClean = c.phoneNumber.replace("+", "")
                            cClean == srcClean || (cClean.length >= 7 && srcClean.endsWith(cClean.takeLast(8)))
                        }
                        if (matching != null) {
                            repository.saveContact(
                                matching.copy(
                                    isRegisteredInMesh = true,
                                    isConnected = true,
                                    lastSeen = System.currentTimeMillis()
                                )
                            )
                        }

                        // Connect TCP client socket if not already open
                        if (!activeClientSockets.containsKey(senderIp)) {
                            connectToMeshSocket(senderIp, TCP_MESH_PORT)
                        }

                        // Dispatches incoming chat messages, acks, typing events, call signals
                        processIncomingPacket(meshPacket)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "UDP Beacon listener cerrado: ${e.message}")
            }
        }
    }

    private fun startMeshMaintenanceLoop() {
        meshMaintenanceJob = scope.launch {
            while (isActive) {
                delay(3000)
                // 1. Send periodic heartbeat / beacon with current node load
                sendHeartbeatAndBeacon()

                // 2. Discover peers & auto-connect without restriction to the best candidate
                if (p2pManager != null && p2pChannel != null) {
                    startP2pDiscovery()
                    evaluateAndAutoConnectToBestNode()
                }

                // 3. Self-healing check: detect dropped connections and failover
                checkSelfHealingHeartbeats()

                // 4. Retry Store-and-Forward pending messages
                retryStoreAndForwardQueue()

                // 5. Update count of active nodes
                val activeNodes = repository.getActiveNodes()
                _engineState.value = _engineState.value.copy(
                    connectedPeersCount = activeNodes.size
                )
            }
        }
    }

    private fun sendHeartbeatAndBeacon() {
        scope.launch {
            try {
                val currentLoad = activeClientSockets.size
                val beacon = MeshPacket(
                    packetType = "HEARTBEAT",
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = _engineState.value.ssid,
                    sourceSsid = _engineState.value.ssid,
                    destinationPhone = "BROADCAST",
                    nodeLoad = currentLoad,
                    priority = 0
                )
                val data = beacon.toJson().toByteArray()
                val broadcastIps = listOf("192.168.49.255", "255.255.255.255")

                val sock = DatagramSocket()
                sock.broadcast = true
                for (targetIp in broadcastIps) {
                    try {
                        val addr = InetAddress.getByName(targetIp)
                        val pack = DatagramPacket(data, data.size, addr, UDP_BEACON_PORT)
                        sock.send(pack)
                    } catch (_: Exception) {}
                }
                sock.close()
            } catch (_: Exception) {}
        }
    }

    private fun checkSelfHealingHeartbeats() {
        val now = System.currentTimeMillis()
        for ((ip, socket) in activeClientSockets) {
            if (socket.isClosed || !socket.isConnected) {
                try { socket.close() } catch (_: Exception) {}
                activeClientSockets.remove(ip)
                Log.d(TAG, "Socket cerrado detectado por auto-sanado: $ip")
            }
        }

        for ((address, metric) in peerMetrics) {
            if (metric.isConnected && (now - metric.lastHeartbeat) > 9000) {
                Log.w(TAG, "Nodo $address perdió heartbeat (>9s). Iniciando auto-sanado y failover...")
                metric.isConnected = false
                evaluateAndAutoConnectToBestNode()
            }
        }
    }

    private fun retryStoreAndForwardQueue() {
        scope.launch {
            val now = System.currentTimeMillis()
            val iterator = pendingRetries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val retry = entry.value
                if (retry.retryCount > 6) {
                    iterator.remove()
                    continue
                }

                val backoffMs = (retry.retryCount + 1) * 3500L
                if (now - retry.lastAttempt >= backoffMs) {
                    retry.retryCount++
                    retry.lastAttempt = now
                    Log.d(TAG, "Reintentando Store & Forward para ${retry.packet.destinationPhone} (intento ${retry.retryCount})")
                    transmitMeshPacket(retry.packet)
                }
            }

            val pendingDb = repository.getPendingMessages()
            for (msg in pendingDb.take(5)) {
                if (!pendingRetries.containsKey(msg.messageUuid)) {
                    val retryPacket = MeshPacket(
                        packetType = "CHAT_MESSAGE",
                        packetUuid = msg.messageUuid,
                        sourceNodeId = _engineState.value.myNodeId,
                        sourcePhone = _engineState.value.myPhoneNumber,
                        sourceName = _engineState.value.ssid,
                        sourceSsid = _engineState.value.ssid,
                        destinationPhone = msg.recipientPhone,
                        content = msg.content,
                        mediaType = msg.mediaType,
                        mediaData = msg.mediaBase64,
                        audioDuration = msg.audioDurationSeconds,
                        hopCount = 1,
                        visitedNodeIds = listOf(_engineState.value.myNodeId)
                    )
                    pendingRetries[msg.messageUuid] = PendingRetry(retryPacket)
                    transmitMeshPacket(retryPacket)
                }
            }
        }
    }

    /**
     * Requirement 6: Envío real de mensajes a la malla
     */
    fun sendChatMessage(
        recipientPhone: String,
        content: String,
        mediaType: String = "TEXT",
        mediaUri: String? = null,
        mediaData: String? = null,
        audioDuration: Int = 0
    ) {
        scope.launch {
            val messageUuid = java.util.UUID.randomUUID().toString()
            val myPhone = _engineState.value.myPhoneNumber
            val myNodeId = _engineState.value.myNodeId

            val messageEntity = MessageEntity(
                messageUuid = messageUuid,
                senderPhone = myPhone,
                recipientPhone = recipientPhone,
                content = content,
                mediaType = mediaType,
                mediaUri = mediaUri,
                mediaBase64 = mediaData,
                timestamp = System.currentTimeMillis(),
                status = "SENT",
                hopCount = 0,
                isOutgoing = true,
                audioDurationSeconds = audioDuration
            )
            repository.saveMessage(messageEntity)

            if (mediaData != null && mediaData.length > 16384) {
                val chunkSize = 16384
                val totalChunks = (mediaData.length + chunkSize - 1) / chunkSize
                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(start + chunkSize, mediaData.length)
                    val chunkStr = mediaData.substring(start, end)

                    val chunkPacket = MeshPacket(
                        packetType = "CHAT_CHUNK",
                        packetUuid = messageUuid,
                        sourceNodeId = myNodeId,
                        sourcePhone = myPhone,
                        sourceName = _engineState.value.ssid,
                        sourceSsid = _engineState.value.ssid,
                        destinationPhone = recipientPhone,
                        content = content,
                        mediaType = mediaType,
                        mediaData = chunkStr,
                        audioDuration = audioDuration,
                        hopCount = 1,
                        visitedNodeIds = listOf(myNodeId),
                        chunkIndex = i,
                        totalChunks = totalChunks,
                        priority = 2
                    )
                    transmitMeshPacket(chunkPacket)
                }

                val basePacket = MeshPacket(
                    packetType = "CHAT_MESSAGE",
                    packetUuid = messageUuid,
                    sourceNodeId = myNodeId,
                    sourcePhone = myPhone,
                    sourceName = _engineState.value.ssid,
                    sourceSsid = _engineState.value.ssid,
                    destinationPhone = recipientPhone,
                    content = content,
                    mediaType = mediaType,
                    mediaData = mediaData,
                    audioDuration = audioDuration,
                    hopCount = 1,
                    visitedNodeIds = listOf(myNodeId)
                )
                pendingRetries[messageUuid] = PendingRetry(basePacket)
            } else {
                val packet = MeshPacket(
                    packetType = "CHAT_MESSAGE",
                    packetUuid = messageUuid,
                    sourceNodeId = myNodeId,
                    sourcePhone = myPhone,
                    sourceName = _engineState.value.ssid,
                    sourceSsid = _engineState.value.ssid,
                    destinationPhone = recipientPhone,
                    content = content,
                    mediaType = mediaType,
                    mediaData = mediaData,
                    audioDuration = audioDuration,
                    hopCount = 1,
                    visitedNodeIds = listOf(myNodeId),
                    priority = 1
                )
                transmitMeshPacket(packet)
                pendingRetries[messageUuid] = PendingRetry(packet)
            }

            _engineState.value = _engineState.value.copy(
                packetsSent = _engineState.value.packetsSent + 1
            )
        }
    }

    fun transmitMeshPacket(packet: MeshPacket) {
        scope.launch {
            val json = packet.toJson()
            val data = json.toByteArray()

            // 1. Transmit via active TCP sockets
            for ((_, socket) in activeClientSockets) {
                try {
                    if (!socket.isClosed) {
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println(json)
                    }
                } catch (_: Exception) {}
            }

            // 2. Transmit via Direct UDP & Subnet Broadcast
            try {
                val udpSocket = DatagramSocket()
                udpSocket.broadcast = true

                val targets = mutableListOf("192.168.49.255", "192.168.49.1", "255.255.255.255")
                val specificPeerIp = peerIpByPhone[packet.destinationPhone]
                if (specificPeerIp != null && !targets.contains(specificPeerIp)) {
                    targets.add(specificPeerIp)
                }

                for (targetIp in targets) {
                    try {
                        val addr = InetAddress.getByName(targetIp)
                        val pack = DatagramPacket(data, data.size, addr, UDP_BEACON_PORT)
                        udpSocket.send(pack)
                    } catch (_: Exception) {}
                }
                udpSocket.close()
            } catch (_: Exception) {}

            // 3. Transmit via WiFi Aware if peer handle is known
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && awareSession != null) {
                val handle = awarePeerHandles[packet.destinationPhone]
                if (handle != null) {
                    try {
                        publishSession?.sendMessage(handle, 1, json.toByteArray())
                    } catch (_: Exception) {}
                } else {
                    for ((_, h) in awarePeerHandles) {
                        try {
                            publishSession?.sendMessage(h, 1, json.toByteArray())
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun processIncomingPacket(packet: MeshPacket) {
        scope.launch {
            val uuid = packet.packetUuid
            if (processedPacketUuids.contains(uuid)) {
                return@launch
            }
            processedPacketUuids.add(uuid)

            _engineState.value = _engineState.value.copy(
                packetsReceived = _engineState.value.packetsReceived + 1
            )

            val myPhone = _engineState.value.myPhoneNumber
            val isForMe = packet.destinationPhone == myPhone || packet.destinationPhone == "BROADCAST"

            when (packet.packetType) {
                "CHAT_CHUNK" -> {
                    if (isForMe) {
                        val chunksMap = pendingChunks.computeIfAbsent(packet.packetUuid) { ConcurrentHashMap() }
                        packet.mediaData?.let { chunksMap[packet.chunkIndex] = it }
                        chunkTotals[packet.packetUuid] = packet.totalChunks
                        chunkMetas[packet.packetUuid] = packet

                        if (chunksMap.size == packet.totalChunks) {
                            val fullMedia = (0 until packet.totalChunks).joinToString("") { chunksMap[it] ?: "" }
                            pendingChunks.remove(packet.packetUuid)
                            chunkTotals.remove(packet.packetUuid)
                            val meta = chunkMetas.remove(packet.packetUuid) ?: packet

                            val completeMsg = MessageEntity(
                                messageUuid = meta.packetUuid,
                                senderPhone = meta.sourcePhone,
                                recipientPhone = myPhone,
                                content = meta.content,
                                mediaType = meta.mediaType,
                                mediaBase64 = fullMedia,
                                timestamp = meta.timestamp,
                                status = "DELIVERED",
                                hopCount = meta.hopCount,
                                isOutgoing = false,
                                audioDurationSeconds = meta.audioDuration
                            )
                            repository.saveMessage(completeMsg)

                            notificationHelper.showIncomingMessageNotification(
                                senderPhone = meta.sourcePhone,
                                senderName = meta.sourceName,
                                messageText = "📷 Foto recibida vía Malla P2P"
                            )

                            sendAck(meta.packetUuid, meta.sourcePhone)
                        }
                    } else if (packet.hopCount < packet.maxHops) {
                        relayPacket(packet)
                    }
                }
                "CHAT_MESSAGE" -> {
                    if (isForMe) {
                        val msg = MessageEntity(
                            messageUuid = packet.packetUuid,
                            senderPhone = packet.sourcePhone,
                            recipientPhone = myPhone,
                            content = packet.content,
                            mediaType = packet.mediaType,
                            mediaBase64 = packet.mediaData,
                            timestamp = packet.timestamp,
                            status = "DELIVERED",
                            hopCount = packet.hopCount,
                            isOutgoing = false,
                            audioDurationSeconds = packet.audioDuration
                        )
                        repository.saveMessage(msg)

                        // Trigger Android system notification for incoming message
                        val previewText = when (packet.mediaType) {
                            "IMAGE" -> "📷 Foto recibida"
                            "AUDIO" -> "🎙️ Mensaje de voz (${packet.audioDuration}s)"
                            "FILE" -> "📎 Archivo adjunto"
                            else -> packet.content
                        }
                        notificationHelper.showIncomingMessageNotification(
                            senderPhone = packet.sourcePhone,
                            senderName = packet.sourceName,
                            messageText = previewText
                        )

                        // Send real ACK back
                        sendAck(packet.packetUuid, packet.sourcePhone)
                    } else if (packet.hopCount < packet.maxHops) {
                        relayPacket(packet)
                    }
                }
                "ACK" -> {
                    repository.updateMessageStatus(packet.content, "DELIVERED")
                    pendingRetries.remove(packet.content)
                }
                "HEARTBEAT", "BEACON", "HANDSHAKE" -> {
                    // Update peer metric load & heartbeat for optimal node selection
                    val existing = peerMetrics[packet.sourceNodeId]
                    if (existing != null) {
                        existing.nodeLoad = packet.nodeLoad
                        existing.lastHeartbeat = System.currentTimeMillis()
                    } else {
                        peerMetrics[packet.sourceNodeId] = PeerMetric(
                            deviceAddress = packet.sourceNodeId,
                            deviceName = packet.sourceName,
                            status = WifiP2pDevice.CONNECTED,
                            nodeLoad = packet.nodeLoad,
                            lastHeartbeat = System.currentTimeMillis(),
                            isConnected = true
                        )
                    }

                    val node = MeshNodeEntity(
                        nodeId = packet.sourceNodeId,
                        ssid = packet.sourceSsid,
                        phoneNumber = packet.sourcePhone,
                        nickname = packet.sourceName,
                        ipAddress = peerIpByPhone[packet.sourcePhone] ?: "192.168.49.20",
                        connectionType = "WIFI_DIRECT",
                        isDirectNeighbor = true,
                        hopDistance = 1,
                        lastSeen = System.currentTimeMillis(),
                        isActive = true
                    )
                    repository.saveMeshNode(node)

                    val allContacts = repository.getAllContactsList()
                    val srcClean = packet.sourcePhone.replace("+", "")
                    val matching = allContacts.find { c ->
                        val cClean = c.phoneNumber.replace("+", "")
                        cClean == srcClean || (cClean.length >= 7 && srcClean.endsWith(cClean.takeLast(8)))
                    }
                    if (matching != null) {
                        repository.saveContact(
                            matching.copy(
                                isRegisteredInMesh = true,
                                isConnected = true,
                                lastSeen = System.currentTimeMillis()
                            )
                        )
                    }

                    // Flush pending Store & Forward messages
                    val pending = repository.getPendingMessagesForRecipient(packet.sourcePhone)
                    for (msg in pending) {
                        sendChatMessage(msg.recipientPhone, msg.content, msg.mediaType, msg.mediaUri, msg.mediaBase64, msg.audioDurationSeconds)
                    }
                }
                "STATUS_UPDATE" -> {
                    if (packet.statusType == "TYPING") {
                        _engineState.value = _engineState.value.copy(activeTypingContactPhone = packet.sourcePhone)
                        delay(2500)
                        if (_engineState.value.activeTypingContactPhone == packet.sourcePhone) {
                            _engineState.value = _engineState.value.copy(activeTypingContactPhone = null)
                        }
                    } else if (packet.statusType == "RECORDING") {
                        _engineState.value = _engineState.value.copy(activeRecordingContactPhone = packet.sourcePhone)
                        delay(3500)
                        if (_engineState.value.activeRecordingContactPhone == packet.sourcePhone) {
                            _engineState.value = _engineState.value.copy(activeRecordingContactPhone = null)
                        }
                    }
                }
                "CALL_SIGNAL" -> {
                    handleCallSignal(packet)
                }
            }
        }
    }

    private suspend fun relayPacket(packet: MeshPacket) {
        val myNodeId = _engineState.value.myNodeId
        if (packet.visitedNodeIds.contains(myNodeId)) return

        val relayedPacket = packet.copy(
            hopCount = packet.hopCount + 1,
            visitedNodeIds = packet.visitedNodeIds + myNodeId
        )
        transmitMeshPacket(relayedPacket)

        _engineState.value = _engineState.value.copy(
            packetsRelayed = _engineState.value.packetsRelayed + 1
        )
    }

    private fun sendAck(originalUuid: String, recipientPhone: String) {
        val ackPacket = MeshPacket(
            packetType = "ACK",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.ssid,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = recipientPhone,
            content = originalUuid
        )
        transmitMeshPacket(ackPacket)
    }

    /**
     * Requirement 3: Llamadas P2P reales con transmisión de audio
     */
    fun startCall(contact: ContactEntity, isVideo: Boolean) {
        _engineState.value = _engineState.value.copy(
            activeCallPeer = contact,
            isCallActive = true,
            isVideoCall = isVideo,
            callDurationSeconds = 0,
            isMicMuted = false,
            isSpeakerOn = true
        )

        val offerPacket = MeshPacket(
            packetType = "CALL_SIGNAL",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.ssid,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = contact.phoneNumber,
            callSignalType = "OFFER",
            callIsVideo = isVideo
        )
        transmitMeshPacket(offerPacket)

        startCallTimer()
        val peerIp = peerIpByPhone[contact.phoneNumber] ?: "192.168.49.1"
        startRealAudioStreaming(peerIp)
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = scope.launch {
            while (_engineState.value.isCallActive) {
                delay(1000)
                _engineState.value = _engineState.value.copy(
                    callDurationSeconds = _engineState.value.callDurationSeconds + 1
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRealAudioStreaming(peerIp: String) {
        val sampleRate = 16000
        val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
        val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)

        // Real Microphone Capture & UDP Transmission
        audioRecordJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            var udpSocket: DatagramSocket? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfigIn,
                    audioFormat,
                    bufferSize * 2
                )
                udpSocket = DatagramSocket()
                val targetAddr = InetAddress.getByName(peerIp)
                val buffer = ByteArray(bufferSize)

                audioRecord.startRecording()
                while (isActive && _engineState.value.isCallActive) {
                    if (!_engineState.value.isMicMuted) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            val packet = DatagramPacket(buffer, read, targetAddr, AUDIO_UDP_PORT)
                            udpSocket.send(packet)
                        }
                    } else {
                        delay(50)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }

        // Real Audio Playback from incoming UDP packets
        audioPlayJob = scope.launch(Dispatchers.IO) {
            var audioTrack: AudioTrack? = null
            var udpSocket: DatagramSocket? = null
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfigOut)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2)
                    .build()

                udpSocket = DatagramSocket(AUDIO_UDP_PORT)
                val buffer = ByteArray(bufferSize)
                audioTrack.play()

                while (isActive && _engineState.value.isCallActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket.receive(packet)
                    audioTrack.write(packet.data, 0, packet.length)
                }
            } catch (_: Exception) {
            } finally {
                try { audioTrack?.stop() } catch (_: Exception) {}
                try { audioTrack?.release() } catch (_: Exception) {}
                try { udpSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun endCall() {
        val peer = _engineState.value.activeCallPeer
        val duration = _engineState.value.callDurationSeconds
        val isVideo = _engineState.value.isVideoCall

        audioRecordJob?.cancel()
        audioPlayJob?.cancel()
        callTimerJob?.cancel()
        notificationHelper.cancelCallNotification()

        if (peer != null) {
            val hangupPacket = MeshPacket(
                packetType = "CALL_SIGNAL",
                sourceNodeId = _engineState.value.myNodeId,
                sourcePhone = _engineState.value.myPhoneNumber,
                sourceName = _engineState.value.ssid,
                sourceSsid = _engineState.value.ssid,
                destinationPhone = peer.phoneNumber,
                callSignalType = "HANGUP"
            )
            transmitMeshPacket(hangupPacket)

            scope.launch {
                repository.insertCall(
                    CallEntity(
                        contactPhone = peer.phoneNumber,
                        contactName = peer.displayName,
                        isVideo = isVideo,
                        isOutgoing = true,
                        durationSeconds = duration,
                        status = "COMPLETED"
                    )
                )
            }
        }

        _engineState.value = _engineState.value.copy(
            activeCallPeer = null,
            isCallActive = false,
            callDurationSeconds = 0
        )
    }

    fun toggleMute() {
        _engineState.value = _engineState.value.copy(isMicMuted = !_engineState.value.isMicMuted)
    }

    fun toggleSpeaker() {
        _engineState.value = _engineState.value.copy(isSpeakerOn = !_engineState.value.isSpeakerOn)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.isSpeakerphoneOn = _engineState.value.isSpeakerOn
    }

    private fun handleCallSignal(packet: MeshPacket) {
        when (packet.callSignalType) {
            "HANGUP", "REJECT" -> {
                audioRecordJob?.cancel()
                audioPlayJob?.cancel()
                callTimerJob?.cancel()
                notificationHelper.cancelCallNotification()
                _engineState.value = _engineState.value.copy(
                    activeCallPeer = null,
                    isCallActive = false,
                    callDurationSeconds = 0
                )
            }
            "OFFER" -> {
                scope.launch {
                    val contact = repository.getContact(packet.sourcePhone) ?: ContactEntity(
                        phoneNumber = packet.sourcePhone,
                        displayName = packet.sourceName,
                        isRegisteredInMesh = true,
                        isConnected = true
                    )
                    _engineState.value = _engineState.value.copy(
                        activeCallPeer = contact,
                        isCallActive = true,
                        isVideoCall = packet.callIsVideo,
                        callDurationSeconds = 0
                    )

                    // Trigger incoming call notification
                    notificationHelper.showIncomingCallNotification(
                        callerPhone = packet.sourcePhone,
                        callerName = packet.sourceName,
                        isVideo = packet.callIsVideo
                    )

                    startCallTimer()
                    val peerIp = peerIpByPhone[packet.sourcePhone] ?: "192.168.49.1"
                    startRealAudioStreaming(peerIp)
                }
            }
        }
    }

    fun sendUserStatus(recipientPhone: String, status: String) {
        val packet = MeshPacket(
            packetType = "STATUS_UPDATE",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.ssid,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = recipientPhone,
            statusType = status
        )
        transmitMeshPacket(packet)
    }

    /**
     * Requirement: Conexión automática a dispositivos conocidos al entrar al chat
     */
    fun autoConnectToContact(contact: ContactEntity) {
        scope.launch {
            val cleanPhone = contact.phoneNumber.replace("+", "").takeLast(8)
            val dev = _engineState.value.discoveredP2pDevices.find { d ->
                val name = d.deviceName ?: ""
                name.contains(cleanPhone) || d.deviceAddress.equals(contact.meshNodeId, ignoreCase = true)
            }

            if (dev != null && dev.status != WifiP2pDevice.CONNECTED) {
                connectToPeer(dev)
            }

            // If we are a client in a group and not connected to Group Owner socket yet, connect
            if (!_engineState.value.isGroupOwner && !activeClientSockets.containsKey("192.168.49.1")) {
                connectToMeshSocket("192.168.49.1", TCP_MESH_PORT)
            }

            // Send handshake beacon directly and to subnet to sync online status instantly
            val handshake = MeshPacket(
                packetType = "HANDSHAKE",
                sourceNodeId = _engineState.value.myNodeId,
                sourcePhone = _engineState.value.myPhoneNumber,
                sourceName = _engineState.value.ssid,
                sourceSsid = _engineState.value.ssid,
                destinationPhone = contact.phoneNumber
            )
            transmitMeshPacket(handshake)
        }
    }

    fun cleanUp() {
        try {
            audioRecordJob?.cancel()
            audioPlayJob?.cancel()
            meshMaintenanceJob?.cancel()
            callTimerJob?.cancel()
            udpBeaconJob?.cancel()

            if (isP2pReceiverRegistered) {
                context.unregisterReceiver(p2pReceiver)
                isP2pReceiverRegistered = false
            }
            serverSocket?.close()
            udpDiscoverySocket?.close()
            p2pManager?.removeGroup(p2pChannel, null)
        } catch (_: Exception) {}
    }
}
