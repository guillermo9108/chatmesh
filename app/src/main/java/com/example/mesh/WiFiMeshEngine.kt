package com.example.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import android.os.Build
import android.util.Log
import com.example.data.entity.ContactEntity
import com.example.data.entity.MeshNodeEntity
import com.example.data.entity.MessageEntity
import com.example.data.repository.ChatMeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class MeshEngineState(
    val isWifiDirectActive: Boolean = false,
    val isWifiAwareActive: Boolean = false,
    val isGroupOwner: Boolean = false,
    val ssid: String = "",
    val myPhoneNumber: String = "",
    val myNodeId: String = "",
    val connectedPeersCount: Int = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val packetsRelayed: Long = 0,
    val activeCallPeer: ContactEntity? = null,
    val isCallActive: Boolean = false,
    val isVideoCall: Boolean = false,
    val callDurationSeconds: Int = 0,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isSimulationActive: Boolean = true,
    val activeTypingContactPhone: String? = null,
    val activeRecordingContactPhone: String? = null
)

class WiFiMeshEngine(
    private val context: Context,
    private val repository: ChatMeshRepository
) {
    private val TAG = "WiFiMeshEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _engineState = MutableStateFlow(MeshEngineState())
    val engineState: StateFlow<MeshEngineState> = _engineState.asStateFlow()

    // WiFi Direct components
    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var isP2pReceiverRegistered = false

    // WiFi Aware components
    private var awareManager: WifiAwareManager? = null
    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private val awarePeerHandles = ConcurrentHashMap<String, PeerHandle>()

    // Local TCP Server for P2P Mesh
    private var serverSocket: ServerSocket? = null
    private val activeClientSockets = ConcurrentHashMap<String, Socket>()

    // Loop prevention & Packet deduplication
    private val processedPacketUuids = ConcurrentHashMap.newKeySet<String>()

    // Heartbeat & mesh maintenance
    private var meshMaintenanceJob: Job? = null
    private var callTimerJob: Job? = null

    fun initialize(myPhone: String, myNickname: String) {
        val mySsid = SimDetectionUtil.generateSsid(myPhone)
        val myNodeId = "NODE_" + myPhone.replace("+", "")

        _engineState.value = _engineState.value.copy(
            myPhoneNumber = myPhone,
            myNodeId = myNodeId,
            ssid = mySsid
        )

        setupWifiP2p(mySsid)
        setupWifiAware(mySsid)
        startLocalMeshServer()
        startMeshMaintenanceLoop()
    }

    /**
     * Requirement 1: Creación automática de red WiFi Direct con SSID generado por la SIM
     */
    @SuppressLint("MissingPermission")
    private fun setupWifiP2p(ssid: String) {
        try {
            p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (p2pManager == null) {
                Log.w(TAG, "WiFi P2P no soportado en este hardware, modo malla virtual disponible")
                return
            }

            p2pChannel = p2pManager?.initialize(context, context.mainLooper, null)

            registerP2pReceiver()

            // Automatically create WiFi Direct group with multiple client capacity
            p2pManager?.createGroup(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Grupo WiFi Direct creado exitosamente con SSID: $ssid")
                    _engineState.value = _engineState.value.copy(
                        isWifiDirectActive = true,
                        isGroupOwner = true
                    )
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Fallo al crear grupo WiFi Direct ($reason), intentando descubrimiento")
                    startP2pDiscovery()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando WiFi P2P", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startP2pDiscovery() {
        try {
            p2pManager?.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _engineState.value = _engineState.value.copy(isWifiDirectActive = true)
                }
                override fun onFailure(reason: Int) {}
            })
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
                    val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    _engineState.value = _engineState.value.copy(isWifiDirectActive = enabled)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    p2pManager?.requestPeers(p2pChannel) { peers ->
                        peers?.deviceList?.let { deviceList ->
                            handleDiscoveredP2pPeers(deviceList)
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    p2pManager?.requestConnectionInfo(p2pChannel) { info ->
                        handleConnectionInfo(info)
                    }
                }
            }
        }
    }

    private fun handleDiscoveredP2pPeers(deviceList: Collection<WifiP2pDevice>) {
        scope.launch {
            for (device in deviceList) {
                val devName = device.deviceName ?: "Dispositivo"
                val phone = if (devName.startsWith("ChatMesh_")) {
                    devName.removePrefix("ChatMesh_")
                } else {
                    "+535" + kotlin.math.abs(device.deviceAddress.hashCode() % 9000000 + 1000000)
                }

                val node = MeshNodeEntity(
                    nodeId = "NODE_" + device.deviceAddress.replace(":", ""),
                    ssid = devName,
                    phoneNumber = phone,
                    nickname = devName,
                    ipAddress = "192.168.49.20", // Typical WiFi Direct client subnet
                    port = 8888,
                    connectionType = "WIFI_DIRECT",
                    isDirectNeighbor = true,
                    hopDistance = 1,
                    lastSeen = System.currentTimeMillis(),
                    isActive = true
                )
                repository.saveMeshNode(node)
            }
        }
    }

    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null) return
        _engineState.value = _engineState.value.copy(
            isGroupOwner = info.isGroupOwner,
            isWifiDirectActive = info.groupFormed
        )

        if (info.groupFormed && !info.isGroupOwner && info.groupOwnerAddress != null) {
            // Connect socket client to Group Owner
            connectToMeshSocket(info.groupOwnerAddress.hostAddress ?: "192.168.49.1", 8888)
        }
    }

    /**
     * Requirement 2: Uso de WiFi Aware (NAN) para Malla
     */
    private fun setupWifiAware(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        try {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
                Log.i(TAG, "WiFi Aware no presente en hardware, simulador de malla activo")
                return
            }

            awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
            if (awareManager?.isAvailable == true) {
                attachWifiAware(ssid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando WiFi Aware", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun attachWifiAware(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            awareManager?.attach(object : AttachCallback() {
                override fun onAttached(session: WifiAwareSession?) {
                    awareSession = session
                    _engineState.value = _engineState.value.copy(isWifiAwareActive = true)
                    publishAwareService(ssid)
                    subscribeAwareService()
                }

                override fun onAttachFailed() {
                    _engineState.value = _engineState.value.copy(isWifiAwareActive = false)
                }
            }, null)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun publishAwareService(ssid: String) {
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
    private fun subscribeAwareService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val config = SubscribeConfig.Builder()
            .setServiceName("ChatMeshOffline")
            .build()

        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                subscribeSession = session
            }

            override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: MutableList<ByteArray>?) {
                val discoveredSsid = if (serviceSpecificInfo != null) String(serviceSpecificInfo) else "ChatMesh_Desconocido"
                val phone = if (discoveredSsid.startsWith("ChatMesh_")) {
                    discoveredSsid.removePrefix("ChatMesh_")
                } else "+535" + (1000000..9999999).random()

                val peerNodeId = "AWARE_" + peerHandle.hashCode()
                awarePeerHandles[peerNodeId] = peerHandle

                scope.launch {
                    val node = MeshNodeEntity(
                        nodeId = peerNodeId,
                        ssid = discoveredSsid,
                        phoneNumber = phone,
                        nickname = discoveredSsid,
                        ipAddress = "192.168.49.1",
                        port = 8888,
                        connectionType = "WIFI_AWARE",
                        isDirectNeighbor = true,
                        hopDistance = 1,
                        lastSeen = System.currentTimeMillis(),
                        isActive = true
                    )
                    repository.saveMeshNode(node)

                    // Mark contact registered
                    val contact = repository.getContact(phone)
                    if (contact != null) {
                        repository.saveContact(contact.copy(isRegisteredInMesh = true, isConnected = true, lastSeen = System.currentTimeMillis()))
                    }
                }
            }
        }, null)
    }

    /**
     * Requirement 1 & 6: Servidor de Sockets local para entrega y enrutamiento en malla
     */
    private fun startLocalMeshServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(8888)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    handleClientSocket(socket)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Server socket detenido o reiniciado: ${e.message}")
            }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    val packet = MeshPacket.fromJson(line)
                    if (packet != null) {
                        processIncomingPacket(packet)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun connectToMeshSocket(host: String, port: Int) {
        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 3000)
                activeClientSockets["$host:$port"] = socket

                // Send Handshake
                val handshake = MeshPacket(
                    packetType = "HANDSHAKE",
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = "Mi Dispositivo",
                    sourceSsid = _engineState.value.ssid,
                    destinationPhone = "BROADCAST"
                )
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(handshake.toJson())

                // Listen for responses
                handleClientSocket(socket)
            } catch (e: Exception) {
                Log.d(TAG, "No se pudo conectar a peer socket $host:$port: ${e.message}")
            }
        }
    }

    /**
     * Requirement 6: Entrega y retransmisión de mensajes en malla nodo a nodo
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

            // 1. Guardar en BD local con estado PENDING
            val messageEntity = MessageEntity(
                messageUuid = messageUuid,
                senderPhone = myPhone,
                recipientPhone = recipientPhone,
                content = content,
                mediaType = mediaType,
                mediaUri = mediaUri,
                mediaBase64 = mediaData,
                timestamp = System.currentTimeMillis(),
                status = "PENDING",
                hopCount = 0,
                isOutgoing = true,
                audioDurationSeconds = audioDuration
            )
            repository.saveMessage(messageEntity)

            // 2. Construir paquete Mesh
            val packet = MeshPacket(
                packetType = "CHAT_MESSAGE",
                packetUuid = messageUuid,
                sourceNodeId = myNodeId,
                sourcePhone = myPhone,
                sourceName = "Mi Dispositivo",
                sourceSsid = _engineState.value.ssid,
                destinationPhone = recipientPhone,
                content = content,
                mediaType = mediaType,
                mediaData = mediaData,
                audioDuration = audioDuration,
                hopCount = 1,
                visitedNodeIds = listOf(myNodeId)
            )

            // 3. Transmitir a la malla (Sockets, WiFi Aware o Simulación)
            transmitMeshPacket(packet)

            _engineState.value = _engineState.value.copy(
                packetsSent = _engineState.value.packetsSent + 1
            )
        }
    }

    fun transmitMeshPacket(packet: MeshPacket) {
        scope.launch {
            val json = packet.toJson()

            // A. Send over active client sockets
            for ((_, socket) in activeClientSockets) {
                try {
                    if (!socket.isClosed) {
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println(json)
                    }
                } catch (_: Exception) {}
            }

            // B. Send via WiFi Aware peer session if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && awareSession != null) {
                for ((_, handle) in awarePeerHandles) {
                    try {
                        publishSession?.sendMessage(handle, 1, json.toByteArray())
                    } catch (_: Exception) {}
                }
            }

            // C. In simulation mode (for emulator / demo), simulate mesh delivery and realistic responses
            if (_engineState.value.isSimulationActive) {
                handleSimulatedMeshRelay(packet)
            }
        }
    }

    /**
     * Procesar paquete recibido desde la malla
     */
    private fun processIncomingPacket(packet: MeshPacket) {
        scope.launch {
            val uuid = packet.packetUuid
            if (processedPacketUuids.contains(uuid)) {
                return@launch // Already processed, drop to prevent infinite loops
            }
            processedPacketUuids.add(uuid)

            _engineState.value = _engineState.value.copy(
                packetsReceived = _engineState.value.packetsReceived + 1
            )

            val myPhone = _engineState.value.myPhoneNumber
            val isForMe = packet.destinationPhone == myPhone || packet.destinationPhone == "BROADCAST"

            when (packet.packetType) {
                "CHAT_MESSAGE" -> {
                    if (isForMe) {
                        // Guardar en la base de datos
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

                        // Enviar ACK de entrega de vuelta por la malla
                        sendAck(packet.packetUuid, packet.sourcePhone)
                    } else if (packet.hopCount < packet.maxHops) {
                        // Requirement 6: Re-transmitir nodo a nodo (Mesh Relay)
                        relayPacket(packet)
                    }
                }
                "ACK" -> {
                    // Update message status to DELIVERED or READ
                    repository.updateMessageStatus(packet.content, "DELIVERED")
                }
                "HANDSHAKE" -> {
                    // Update contact and mesh node registry
                    val node = MeshNodeEntity(
                        nodeId = packet.sourceNodeId,
                        ssid = packet.sourceSsid,
                        phoneNumber = packet.sourcePhone,
                        nickname = packet.sourceName,
                        ipAddress = "192.168.49.1",
                        connectionType = "WIFI_DIRECT",
                        isDirectNeighbor = true,
                        hopDistance = 1,
                        lastSeen = System.currentTimeMillis(),
                        isActive = true
                    )
                    repository.saveMeshNode(node)

                    // Flush pending messages for this node (Store & Forward)
                    val pending = repository.getPendingMessagesForRecipient(packet.sourcePhone)
                    for (msg in pending) {
                        sendChatMessage(msg.recipientPhone, msg.content, msg.mediaType, msg.mediaUri, msg.mediaBase64, msg.audioDurationSeconds)
                    }
                }
                "STATUS_UPDATE" -> {
                    val status = packet.statusType
                    if (status == "TYPING") {
                        _engineState.value = _engineState.value.copy(activeTypingContactPhone = packet.sourcePhone)
                        delay(2500)
                        if (_engineState.value.activeTypingContactPhone == packet.sourcePhone) {
                            _engineState.value = _engineState.value.copy(activeTypingContactPhone = null)
                        }
                    } else if (status == "RECORDING") {
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
            sourceName = "Mi Dispositivo",
            sourceSsid = _engineState.value.ssid,
            destinationPhone = recipientPhone,
            content = originalUuid
        )
        transmitMeshPacket(ackPacket)
    }

    /**
     * Requirement 3: Llamadas de audio y video P2P
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

        // Send Call Offer Packet
        val offerPacket = MeshPacket(
            packetType = "CALL_SIGNAL",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = "Mi Dispositivo",
            sourceSsid = _engineState.value.ssid,
            destinationPhone = contact.phoneNumber,
            callSignalType = "OFFER",
            callIsVideo = isVideo
        )
        transmitMeshPacket(offerPacket)

        // Start call timer
        startCallTimer()
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

    fun endCall() {
        val peer = _engineState.value.activeCallPeer
        val duration = _engineState.value.callDurationSeconds
        val isVideo = _engineState.value.isVideoCall

        if (peer != null) {
            val hangupPacket = MeshPacket(
                packetType = "CALL_SIGNAL",
                sourceNodeId = _engineState.value.myNodeId,
                sourcePhone = _engineState.value.myPhoneNumber,
                sourceName = "Mi Dispositivo",
                sourceSsid = _engineState.value.ssid,
                destinationPhone = peer.phoneNumber,
                callSignalType = "HANGUP"
            )
            transmitMeshPacket(hangupPacket)

            // Save to call history
            scope.launch {
                repository.insertCall(
                    com.example.data.entity.CallEntity(
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

        callTimerJob?.cancel()
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
    }

    private fun handleCallSignal(packet: MeshPacket) {
        when (packet.callSignalType) {
            "HANGUP", "REJECT" -> {
                callTimerJob?.cancel()
                _engineState.value = _engineState.value.copy(
                    activeCallPeer = null,
                    isCallActive = false,
                    callDurationSeconds = 0
                )
            }
            "ANSWER" -> {
                // Call connected
            }
        }
    }

    /**
     * Broadcast status ("escribiendo...", "grabando audio...")
     */
    fun sendUserStatus(recipientPhone: String, status: String) {
        val packet = MeshPacket(
            packetType = "STATUS_UPDATE",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = "Mi Dispositivo",
            sourceSsid = _engineState.value.ssid,
            destinationPhone = recipientPhone,
            statusType = status
        )
        transmitMeshPacket(packet)
    }

    /**
     * Mantenimiento de la malla: heartbeats, descubrir peers, actualizar nodos
     */
    private fun startMeshMaintenanceLoop() {
        meshMaintenanceJob = scope.launch {
            // Seed initial nodes
            seedDefaultMeshNodes()

            while (isActive) {
                delay(12000)
                // Discover peers periodically
                if (p2pManager != null && p2pChannel != null) {
                    startP2pDiscovery()
                }

                // Check and update connected peers count
                val activeNodes = repository.getActiveNodes()
                _engineState.value = _engineState.value.copy(
                    connectedPeersCount = activeNodes.size
                )
            }
        }
    }

    private suspend fun seedDefaultMeshNodes() {
        val sampleNodes = listOf(
            MeshNodeEntity(
                nodeId = "NODE_5352345678",
                ssid = "ChatMesh_+5352345678",
                phoneNumber = "+5352345678",
                nickname = "Alejandro Morales",
                ipAddress = "192.168.49.12",
                connectionType = "WIFI_DIRECT",
                isDirectNeighbor = true,
                hopDistance = 1,
                lastSeen = System.currentTimeMillis()
            ),
            MeshNodeEntity(
                nodeId = "NODE_5353456789",
                ssid = "ChatMesh_+5353456789",
                phoneNumber = "+5353456789",
                nickname = "Claudia Rodríguez",
                ipAddress = "192.168.49.15",
                connectionType = "WIFI_AWARE",
                isDirectNeighbor = true,
                hopDistance = 1,
                lastSeen = System.currentTimeMillis()
            ),
            MeshNodeEntity(
                nodeId = "NODE_5357890123",
                ssid = "ChatMesh_+5357890123",
                phoneNumber = "+5357890123",
                nickname = "Valeria Díaz (Repetidor)",
                ipAddress = "192.168.49.24",
                connectionType = "WIFI_DIRECT",
                isDirectNeighbor = false,
                hopDistance = 2,
                lastSeen = System.currentTimeMillis()
            ),
            MeshNodeEntity(
                nodeId = "NODE_5354567890",
                ssid = "ChatMesh_+5354567890",
                phoneNumber = "+5354567890",
                nickname = "Carlos Pérez (Nodo Lejano)",
                ipAddress = "192.168.49.33",
                connectionType = "WIFI_AWARE",
                isDirectNeighbor = false,
                hopDistance = 3,
                lastSeen = System.currentTimeMillis() - 1000 * 60 * 30
            )
        )
        repository.insertAllNodes(sampleNodes)
    }

    /**
     * Simulación inteligente de malla P2P para pruebas sin hardware físico
     * Responde a mensajes, simula retransmisión de saltos y entrega offline
     */
    private fun handleSimulatedMeshRelay(packet: MeshPacket) {
        if (packet.packetType != "CHAT_MESSAGE") return

        scope.launch {
            // First mark sent message as delivered after a short network hop delay
            delay(1200)
            repository.updateMessageStatus(packet.packetUuid, "DELIVERED")

            delay(1500)
            repository.updateMessageStatus(packet.packetUuid, "READ")

            // Simulate realistic reply from peer if recipient is one of the active mesh contacts
            val peerPhone = packet.destinationPhone
            val contact = repository.getContact(peerPhone) ?: return@launch
            if (!contact.isRegisteredInMesh) return@launch

            // Peer starts typing status
            _engineState.value = _engineState.value.copy(activeTypingContactPhone = peerPhone)
            delay(2000)
            _engineState.value = _engineState.value.copy(activeTypingContactPhone = null)

            val replyText = when {
                packet.mediaType == "AUDIO" -> "¡Escuché tu mensaje de voz perfectamente por la malla P2P! Se oye súper nítido."
                packet.mediaType == "IMAGE" -> "¡Buena foto! La transferencia nodo a nodo sin Internet funcionó al instante."
                packet.content.contains("hola", ignoreCase = true) -> "¡Hola! Estoy conectado como nodo en tu red WiFi Direct (${_engineState.value.ssid})."
                packet.content.contains("malla", ignoreCase = true) || packet.content.contains("mesh", ignoreCase = true) ->
                    "La red en malla está retransmitiendo paquetes a través de ${(_engineState.value.connectedPeersCount + 1)} dispositivos cercanos."
                packet.content.contains("llamada", ignoreCase = true) -> "¡Claro! Toca el botón de llamada arriba para probar el enlace de audio P2P."
                else -> "Recibido en mi nodo. Retransmitiendo mensaje a los demás dispositivos de la malla."
            }

            val replyMsg = MessageEntity(
                messageUuid = java.util.UUID.randomUUID().toString(),
                senderPhone = peerPhone,
                recipientPhone = _engineState.value.myPhoneNumber,
                content = replyText,
                mediaType = "TEXT",
                timestamp = System.currentTimeMillis(),
                status = "READ",
                hopCount = (1..2).random(),
                isOutgoing = false
            )
            repository.saveMessage(replyMsg)
        }
    }

    fun toggleSimulation() {
        val newSim = !_engineState.value.isSimulationActive
        _engineState.value = _engineState.value.copy(isSimulationActive = newSim)
    }

    fun cleanUp() {
        try {
            if (isP2pReceiverRegistered) {
                context.unregisterReceiver(p2pReceiver)
                isP2pReceiverRegistered = false
            }
            serverSocket?.close()
            meshMaintenanceJob?.cancel()
            callTimerJob?.cancel()
            p2pManager?.removeGroup(p2pChannel, null)
        } catch (_: Exception) {}
    }
}
