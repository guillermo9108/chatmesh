package com.example.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.aware.*
import android.net.wifi.p2p.*
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class WiFiMeshEngine(
    private val context: Context,
    private val repository: ChatMeshRepository
) {
    private val TAG = "WiFiMeshEngine"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationHelper = NotificationHelper(context)

    private val TCP_MESH_PORT = 8988
    private val UDP_BEACON_PORT = 8989
    private val AUDIO_UDP_PORT = 8990

    private val _engineState = MutableStateFlow(MeshEngineState())
    val engineState: StateFlow<MeshEngineState> = _engineState.asStateFlow()

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var isP2pReceiverRegistered = false

    private var awareManager: WifiAwareManager? = null
    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private val awarePeerHandles = ConcurrentHashMap<String, PeerHandle>()

    private var serverSocket: ServerSocket? = null
    private var udpDiscoverySocket: DatagramSocket? = null
    private val activeClientSockets = ConcurrentHashMap<String, Socket>()
    private val peerIpByPhone = ConcurrentHashMap<String, String>()
    private val peerMetrics = ConcurrentHashMap<String, PeerMetric>()

    private val storeAndForwardQueue = ConcurrentHashMap<String, PendingRetry>()
    private val receivedPacketUuids = ConcurrentHashMap.newKeySet<String>()

    private val chunkMetas = ConcurrentHashMap<String, MeshPacket>()
    private val chunkTotals = ConcurrentHashMap<String, Int>()
    private val receivedChunks = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()

    private var meshMaintenanceJob: Job? = null
    private var audioRecordJob: Job? = null
    private var audioPlayJob: Job? = null
    private var callTimerJob: Job? = null
    private var udpBeaconJob: Job? = null

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    _engineState.value = _engineState.value.copy(isWifiDirectActive = isEnabled)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    p2pManager?.requestPeers(p2pChannel) { peers ->
                        val deviceList = peers?.deviceList?.toList() ?: emptyList()
                        _engineState.value = _engineState.value.copy(discoveredP2pDevices = deviceList)
                        deviceList.forEach { dev ->
                            handleDiscoveredP2pDevice(dev, dev.status == WifiP2pDevice.CONNECTED)
                        }
                        evaluateAndAutoConnectToBestNode()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    requestGroupAndConnectionDetails()
                    evaluateAndAutoConnectToBestNode()
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val dev = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    if (dev != null && dev.deviceAddress.isNotEmpty()) {
                        _engineState.value = _engineState.value.copy(myNodeId = dev.deviceAddress)
                    }
                }
            }
        }
    }

    fun initialize(myPhone: String, myNickname: String) {
        val nodeId = UUID.randomUUID().toString().take(8)
        val ssid = SimDetectionUtil.generateSsid(myPhone)

        _engineState.value = _engineState.value.copy(
            myNodeId = nodeId,
            myPhoneNumber = myPhone,
            ssid = ssid
        )

        setupP2p(ssid)
        setupRealWifiAware(ssid)
        startRealTcpMeshServer()
        startRealUdpBeaconListener()
        startMeshMaintenanceLoop()
    }

    private fun setupP2p(ssid: String) {
        try {
            p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            p2pChannel = p2pManager?.initialize(context, context.mainLooper, null)

            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            if (!isP2pReceiverRegistered) {
                context.registerReceiver(p2pReceiver, filter)
                isP2pReceiverRegistered = true
            }

            setDeviceP2pName(ssid)
            setupP2pDnsSdService(ssid)
            startP2pDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando WiFi Direct", e)
        }
    }

    private fun setDeviceP2pName(name: String) {
        try {
            val method = p2pManager?.javaClass?.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method?.invoke(p2pManager, p2pChannel, name, null)
        } catch (_: Exception) {
        }
    }

    private fun setupP2pDnsSdService(ssid: String) {
        try {
            val record = mapOf(
                "nodeId" to _engineState.value.myNodeId,
                "phone" to _engineState.value.myPhoneNumber,
                "ssid" to ssid
            )
            val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("ChatMesh", "_chatmesh._tcp", record)
            p2pManager?.addLocalService(p2pChannel, serviceInfo, null)

            p2pManager?.setDnsSdResponseListeners(p2pChannel,
                { _, _, device ->
                    handleDiscoveredP2pDevice(device, device.status == WifiP2pDevice.CONNECTED)
                },
                { _, txtRecord, device ->
                    val phone = txtRecord["phone"]
                    if (!phone.isNullOrBlank()) {
                        registerNodeFromDnsSd(device.deviceName ?: "Nodo", phone, device.deviceAddress)
                    }
                }
            )
            val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
            p2pManager?.addServiceRequest(p2pChannel, serviceRequest, null)
            p2pManager?.discoverServices(p2pChannel, null)
        } catch (_: Exception) {
        }
    }

    private fun registerNodeFromDnsSd(name: String, phone: String, macAddress: String) {
        scope.launch {
            val contact = repository.getContact(phone)
            if (contact == null) {
                repository.insertContact(
                    ContactEntity(
                        phoneNumber = phone,
                        displayName = name,
                        isRegisteredInMesh = true,
                        isConnected = true
                    )
                )
            } else {
                repository.updateConnectionStatus(phone, true, System.currentTimeMillis())
            }
        }
    }

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

    private fun createAutonomousP2pGroup(ssid: String) {
        p2pManager?.createGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Grupo WiFi Direct creado exitosamente")
                _engineState.value = _engineState.value.copy(
                    isWifiDirectActive = true,
                    isGroupOwner = true,
                    localIpAddress = "192.168.49.1"
                )
                requestGroupAndConnectionDetails()
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup falló ($reason), iniciando descubrimiento de pares")
                startP2pDiscovery()
            }
        })
    }

    fun requestGroupAndConnectionDetails() {
        p2pManager?.requestGroupInfo(p2pChannel) { group ->
            if (group != null) {
                val netName = group.networkName ?: _engineState.value.ssid
                val pass = group.passphrase.orEmpty()
                val iface = group.`interface`.orEmpty()
                _engineState.value = _engineState.value.copy(
                    isGroupOwner = group.isGroupOwner,
                    ssid = netName,
                    passphrase = pass,
                    p2pInterface = if (iface.isNotEmpty()) iface else "p2p0",
                    connectedPeersCount = group.clientList.size
                )
                for (client in group.clientList) {
                    handleDiscoveredP2pDevice(client, true)
                }
            }
        }

        p2pManager?.requestConnectionInfo(p2pChannel) { info ->
            if (info != null && info.groupFormed) {
                val ownerIp = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                val myIp = if (info.isGroupOwner) ownerIp else _engineState.value.localIpAddress
                _engineState.value = _engineState.value.copy(
                    isWifiDirectActive = true,
                    isGroupOwner = info.isGroupOwner,
                    localIpAddress = if (info.isGroupOwner) ownerIp else myIp
                )

                if (!info.isGroupOwner) {
                    // Connect to Group Owner with retries
                    connectToMeshSocket(ownerIp, TCP_MESH_PORT)
                }
            }
        }
    }

    fun startP2pDiscovery() {
        try {
            p2pManager?.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _engineState.value = _engineState.value.copy(
                        autoConnectStatus = "Escaneando dispositivos WiFi Direct..."
                    )
                }
                override fun onFailure(reason: Int) {
                    _engineState.value = _engineState.value.copy(
                        autoConnectStatus = "Modo Autónomo Activo"
                    )
                }
            })
        } catch (_: Exception) {
        }
    }

    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0 // Prefer client to join existing GO
        }

        // If we are currently GO of an empty group, remove it first so connect() succeeds
        if (_engineState.value.isGroupOwner && _engineState.value.connectedPeersCount == 0) {
            p2pManager?.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    doConnect(config, device.deviceName)
                }
                override fun onFailure(reason: Int) {
                    doConnect(config, device.deviceName)
                }
            })
        } else {
            doConnect(config, device.deviceName)
        }
    }

    private fun doConnect(config: WifiP2pConfig, deviceName: String?) {
        p2pManager?.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Conexión WiFi Direct iniciada con $deviceName")
                _engineState.value = _engineState.value.copy(
                    autoConnectStatus = "Conectando a ${deviceName ?: "dispositivo"}..."
                )
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Fallo al conectar con $deviceName: $reason")
            }
        })
    }

    fun evaluateAndAutoConnectToBestNode() {
        val devices = _engineState.value.discoveredP2pDevices
        if (devices.isEmpty()) return

        for (dev in devices) {
            val existing = peerMetrics[dev.deviceAddress]
            if (existing != null) {
                existing.lastHeartbeat = System.currentTimeMillis()
            } else {
                peerMetrics[dev.deviceAddress] = PeerMetric(
                    nodeId = dev.deviceAddress,
                    ipAddress = "",
                    port = TCP_MESH_PORT,
                    phoneNumber = "",
                    nickname = dev.deviceName ?: "Nodo",
                    hopDistance = 1,
                    signalDbm = -50
                )
            }
        }

        // If already connected, do not auto-reconnect
        if (_engineState.value.connectedPeersCount > 0 || (!_engineState.value.isGroupOwner && activeClientSockets.isNotEmpty())) {
            return
        }

        val candidates = devices.filter {
            it.status == WifiP2pDevice.AVAILABLE || it.status == WifiP2pDevice.INVITED
        }
        val bestCandidate = candidates.maxByOrNull { calculateCandidateScore(it) }

        if (bestCandidate != null) {
            _engineState.value = _engineState.value.copy(
                optimalNodeName = bestCandidate.deviceName ?: bestCandidate.deviceAddress,
                autoConnectStatus = "Conectando automáticamente a ${bestCandidate.deviceName}..."
            )
            connectToPeer(bestCandidate)
        }
    }

    private fun calculateCandidateScore(device: WifiP2pDevice): Double {
        var score = when (device.status) {
            WifiP2pDevice.CONNECTED -> 200.0
            WifiP2pDevice.INVITED -> 120.0
            WifiP2pDevice.AVAILABLE -> 150.0
            else -> -100.0
        }
        val name = device.deviceName.orEmpty().lowercase()
        // Highly prioritize nodes whose titular is mesh + phone number
        if (name.startsWith("chatmesh_") || name.startsWith("mesh_") || name.contains("mesh")) {
            score += 200.0
        }
        return score
    }

    fun handleDiscoveredP2pDevice(device: WifiP2pDevice, isConnected: Boolean) {
        val devName = device.deviceName.orEmpty()
        val phoneFromSsid = when {
            devName.startsWith("ChatMesh_") -> devName.removePrefix("ChatMesh_")
            devName.startsWith("mesh_") -> devName.removePrefix("mesh_")
            devName.contains("ChatMesh_") -> devName.substringAfter("ChatMesh_")
            devName.contains("mesh_") -> devName.substringAfter("mesh_")
            else -> null
        }

        scope.launch {
            if (phoneFromSsid != null) {
                if (!_engineState.value.isGroupOwner) {
                    peerIpByPhone[phoneFromSsid] = "192.168.49.1"
                }
                val contact = repository.getContact(phoneFromSsid)
                if (contact == null) {
                    repository.insertContact(
                        ContactEntity(
                            phoneNumber = phoneFromSsid,
                            displayName = devName,
                            isRegisteredInMesh = true,
                            isConnected = isConnected
                        )
                    )
                } else {
                    repository.updateConnectionStatus(phoneFromSsid, isConnected, System.currentTimeMillis())
                }
            } else {
                val contact = repository.getContact(device.deviceAddress)
                if (contact == null) {
                    repository.insertContact(
                        ContactEntity(
                            phoneNumber = device.deviceAddress,
                            displayName = devName.ifBlank { "Dispositivo WiFi Direct" },
                            isRegisteredInMesh = true,
                            isConnected = isConnected
                        )
                    )
                } else {
                    repository.updateConnectionStatus(device.deviceAddress, isConnected, System.currentTimeMillis())
                }
            }
        }
    }

    private fun setupRealWifiAware(ssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
                if (awareManager?.isAvailable == true) {
                    attachRealWifiAware(ssid)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun attachRealWifiAware(ssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        }
    }

    fun publishRealAwareService(ssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val config = PublishConfig.Builder()
                .setServiceName("ChatMeshOffline")
                .setServiceSpecificInfo(ssid.toByteArray(Charsets.UTF_8))
                .build()
            awareSession?.publish(config, object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    publishSession = session
                }
                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    val json = String(message, Charsets.UTF_8)
                    MeshPacket.fromJson(json)?.let { processIncomingPacket(it) }
                }
            }, null)
        }
    }

    fun subscribeRealAwareService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val config = SubscribeConfig.Builder()
                .setServiceName("ChatMeshOffline")
                .build()
            awareSession?.subscribe(config, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                    subscribeSession = session
                }
                override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: MutableList<ByteArray>?) {
                    val discoveredSsid = serviceSpecificInfo?.let { String(it, Charsets.UTF_8) }.orEmpty()
                    val phone = if (discoveredSsid.startsWith("ChatMesh_")) discoveredSsid.removePrefix("ChatMesh_") else ""
                    if (phone.isNotEmpty()) {
                        awarePeerHandles[phone] = peerHandle
                        registerNodeFromDnsSd("Nodo Aware", phone, phone)
                    }
                }
                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    val json = String(message, Charsets.UTF_8)
                    MeshPacket.fromJson(json)?.let { processIncomingPacket(it) }
                }
            }, null)
        }
    }

    private fun startRealTcpMeshServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(TCP_MESH_PORT)
                Log.i(TAG, "Servidor TCP Mesh escuchando en puerto $TCP_MESH_PORT")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    handleClientSocket(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en ServerSocket TCP", e)
            }
        }
    }

    fun handleClientSocket(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            val remoteIp = socket.inetAddress?.hostAddress.orEmpty()
            if (remoteIp.isNotEmpty()) {
                activeClientSockets[remoteIp] = socket
            }

            // Immediately send our HANDSHAKE back to the other device so it knows who we are!
            try {
                val handshake = MeshPacket(
                    packetType = "HANDSHAKE",
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = _engineState.value.ssid,
                    sourceSsid = _engineState.value.ssid,
                    destinationPhone = "BROADCAST"
                )
                val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)), true)
                writer.println(handshake.toJson())
            } catch (_: Exception) {
            }

            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    val packet = MeshPacket.fromJson(line)
                    if (packet != null) {
                        if (remoteIp.isNotEmpty()) {
                            peerIpByPhone[packet.sourcePhone] = remoteIp
                        }
                        processIncomingPacket(packet)
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (remoteIp.isNotEmpty()) {
                    activeClientSockets.remove(remoteIp)
                }
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun connectToMeshSocket(host: String, port: Int) {
        scope.launch(Dispatchers.IO) {
            var connected = false
            for (attempt in 1..10) {
                try {
                    delay(500)
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 3000)
                    activeClientSockets[host] = socket

                    // Send initial handshake
                    val handshake = MeshPacket(
                        packetType = "HANDSHAKE",
                        sourceNodeId = _engineState.value.myNodeId,
                        sourcePhone = _engineState.value.myPhoneNumber,
                        sourceName = _engineState.value.ssid,
                        sourceSsid = _engineState.value.ssid,
                        destinationPhone = "BROADCAST"
                    )
                    val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)), true)
                    writer.println(handshake.toJson())

                    connected = true
                    handleClientSocket(socket)
                    break
                } catch (e: Exception) {
                    delay(1200)
                }
            }
            if (!connected) {
                Log.d(TAG, "No se pudo conectar a $host:$port tras reintentos")
            }
        }
    }

    private fun startRealUdpBeaconListener() {
        scope.launch(Dispatchers.IO) {
            try {
                udpDiscoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(UDP_BEACON_PORT))
                    broadcast = true
                }
                val buffer = ByteArray(4096)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpDiscoverySocket?.receive(packet)
                    val senderIp = packet.address?.hostAddress.orEmpty()
                    val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    MeshPacket.fromJson(json)?.let {
                        peerIpByPhone[it.sourcePhone] = senderIp
                        processIncomingPacket(it)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startMeshMaintenanceLoop() {
        meshMaintenanceJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sendHeartbeatAndBeacon()
                checkSelfHealingHeartbeats()
                retryStoreAndForwardQueue()
                delay(8000)
            }
        }
    }

    fun sendHeartbeatAndBeacon() {
        val beacon = MeshPacket(
            packetType = "BEACON",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.ssid,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = "BROADCAST"
        )
        transmitMeshPacket(beacon)
    }

    fun checkSelfHealingHeartbeats() {
        val now = System.currentTimeMillis()
        for ((ip, socket) in activeClientSockets) {
            if (socket.isClosed) {
                activeClientSockets.remove(ip)
            }
        }
    }

    fun retryStoreAndForwardQueue() {
        val now = System.currentTimeMillis()
        for ((uuid, pending) in storeAndForwardQueue) {
            if (now >= pending.nextRetryTime) {
                if (pending.attempts >= 5) {
                    storeAndForwardQueue.remove(uuid)
                } else {
                    pending.attempts++
                    pending.nextRetryTime = now + (pending.attempts * 3000L)
                    transmitMeshPacket(pending.packet)
                }
            }
        }
    }

    fun sendChatMessage(
        recipientPhone: String,
        content: String,
        mediaType: String = "TEXT",
        mediaUri: String? = null,
        mediaData: String? = null,
        audioDuration: Int = 0
    ) {
        scope.launch {
            val messageUuid = UUID.randomUUID().toString()
            val entity = MessageEntity(
                messageUuid = messageUuid,
                senderPhone = _engineState.value.myPhoneNumber,
                recipientPhone = recipientPhone,
                content = content,
                mediaType = mediaType,
                mediaUri = mediaUri,
                mediaBase64 = mediaData,
                timestamp = System.currentTimeMillis(),
                status = "PENDING",
                isOutgoing = true,
                audioDurationSeconds = audioDuration
            )
            repository.saveMessage(entity)

            // If payload is large, chunk it
            val rawData = mediaData.orEmpty()
            if (rawData.length > 2048) {
                val chunkSize = 2048
                val totalChunks = (rawData.length + chunkSize - 1) / chunkSize
                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(start + chunkSize, rawData.length)
                    val chunkData = rawData.substring(start, end)
                    val packet = MeshPacket(
                        packetType = "CHAT_CHUNK",
                        packetUuid = messageUuid,
                        sourceNodeId = _engineState.value.myNodeId,
                        sourcePhone = _engineState.value.myPhoneNumber,
                        sourceName = _engineState.value.ssid,
                        sourceSsid = _engineState.value.ssid,
                        destinationPhone = recipientPhone,
                        content = content,
                        mediaType = mediaType,
                        mediaData = chunkData,
                        audioDuration = audioDuration,
                        chunkIndex = i,
                        totalChunks = totalChunks
                    )
                    transmitMeshPacket(packet)
                    delay(30)
                }
            } else {
                val packet = MeshPacket(
                    packetType = "CHAT_MESSAGE",
                    packetUuid = messageUuid,
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = _engineState.value.ssid,
                    sourceSsid = _engineState.value.ssid,
                    destinationPhone = recipientPhone,
                    content = content,
                    mediaType = mediaType,
                    mediaData = mediaData,
                    audioDuration = audioDuration
                )
                storeAndForwardQueue[messageUuid] = PendingRetry(packet)
                transmitMeshPacket(packet)
            }
        }
    }

    fun transmitMeshPacket(packet: MeshPacket) {
        scope.launch(Dispatchers.IO) {
            val json = packet.toJson()
            val data = json.toByteArray(Charsets.UTF_8)

            // 1. Direct TCP transmission to all active sockets (Most reliable in WiFi Direct!)
            for ((_, socket) in activeClientSockets) {
                try {
                    if (!socket.isClosed) {
                        val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)), true)
                        writer.println(json)
                    }
                } catch (_: Exception) {}
            }

            // 2. If destination has a known IP but socket not in activeClientSockets, connect directly
            val targetIp = peerIpByPhone[packet.destinationPhone]
            if (targetIp != null && !activeClientSockets.containsKey(targetIp)) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(targetIp, TCP_MESH_PORT), 2000)
                    activeClientSockets[targetIp] = s
                    val writer = PrintWriter(BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)), true)
                    writer.println(json)
                    handleClientSocket(s)
                } catch (_: Exception) {}
            }

            // 2b. If we are in a WiFi Direct client role and not yet connected, connect to Group Owner (192.168.49.1)
            if (!_engineState.value.isGroupOwner && !activeClientSockets.containsKey("192.168.49.1") && _engineState.value.isWifiDirectActive) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress("192.168.49.1", TCP_MESH_PORT), 2000)
                    activeClientSockets["192.168.49.1"] = s
                    val writer = PrintWriter(BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)), true)
                    writer.println(json)
                    handleClientSocket(s)
                } catch (_: Exception) {}
            }

            // 3. UDP Broadcast to WiFi Direct subnet (192.168.49.255) and standard subnet
            try {
                val udp = DatagramSocket().apply { broadcast = true }
                val targets = mutableListOf("192.168.49.255", "192.168.49.1", "255.255.255.255")
                if (targetIp != null && !targets.contains(targetIp)) targets.add(targetIp)

                for (tip in targets) {
                    try {
                        val addr = InetAddress.getByName(tip)
                        val dp = DatagramPacket(data, data.size, addr, UDP_BEACON_PORT)
                        udp.send(dp)
                    } catch (_: Exception) {}
                }
                udp.close()
            } catch (_: Exception) {}

            // 4. WiFi Aware
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && awareSession != null) {
                val handle = awarePeerHandles[packet.destinationPhone]
                if (handle != null) {
                    try {
                        publishSession?.sendMessage(handle, 1, data)
                    } catch (_: Exception) {}
                }
            }

            _engineState.value = _engineState.value.copy(
                packetsSent = _engineState.value.packetsSent + 1
            )
        }
    }

    private fun isMatchingPhone(phone1: String, phone2: String): Boolean {
        if (phone1.isBlank() || phone2.isBlank()) return false
        if (phone1 == phone2) return true
        val digits1 = phone1.filter { it.isDigit() }
        val digits2 = phone2.filter { it.isDigit() }
        if (digits1.isNotEmpty() && digits1 == digits2) return true
        if (digits1.length >= 7 && digits2.length >= 7) {
            if (digits1.takeLast(7) == digits2.takeLast(7)) return true
        }
        return false
    }

    fun processIncomingPacket(packet: MeshPacket) {
        scope.launch {
            if (!receivedPacketUuids.add(packet.packetUuid)) {
                return@launch
            }

            _engineState.value = _engineState.value.copy(
                packetsReceived = _engineState.value.packetsReceived + 1
            )

            // Register sender as contact & node
            val myPhone = _engineState.value.myPhoneNumber
            val isForMe = isMatchingPhone(packet.destinationPhone, myPhone) ||
                    packet.destinationPhone == "BROADCAST" ||
                    (activeClientSockets.isNotEmpty() && packet.sourcePhone != myPhone)

            val existingNode = peerMetrics[packet.sourceNodeId]
            if (existingNode != null) {
                existingNode.lastHeartbeat = System.currentTimeMillis()
            } else {
                peerMetrics[packet.sourceNodeId] = PeerMetric(
                    nodeId = packet.sourceNodeId,
                    ipAddress = peerIpByPhone[packet.sourcePhone] ?: "192.168.49.1",
                    port = TCP_MESH_PORT,
                    phoneNumber = packet.sourcePhone,
                    nickname = packet.sourceName
                )
            }

            repository.saveMeshNode(
                MeshNodeEntity(
                    nodeId = packet.sourceNodeId,
                    ssid = packet.sourceSsid,
                    phoneNumber = packet.sourcePhone,
                    nickname = packet.sourceName,
                    ipAddress = peerIpByPhone[packet.sourcePhone] ?: "192.168.49.1",
                    connectionType = "WIFI_DIRECT",
                    isDirectNeighbor = true,
                    hopDistance = packet.hopCount,
                    isActive = true
                )
            )

            when (packet.packetType) {
                "BEACON", "HEARTBEAT", "HANDSHAKE" -> {
                    val contact = repository.getContact(packet.sourcePhone)
                    if (contact == null) {
                        repository.insertContact(
                            ContactEntity(
                                phoneNumber = packet.sourcePhone,
                                displayName = packet.sourceName,
                                isRegisteredInMesh = true,
                                isConnected = true
                            )
                        )
                    } else {
                        repository.updateConnectionStatus(packet.sourcePhone, true, System.currentTimeMillis())
                    }
                }
                "ACK" -> {
                    storeAndForwardQueue.remove(packet.content)
                    repository.updateMessageStatus(packet.content, "DELIVERED")
                }
                "STATUS_UPDATE" -> {
                    if (isForMe) {
                        when (packet.statusType) {
                            "TYPING" -> _engineState.value = _engineState.value.copy(activeTypingContactPhone = packet.sourcePhone)
                            "RECORDING" -> _engineState.value = _engineState.value.copy(activeRecordingContactPhone = packet.sourcePhone)
                            else -> _engineState.value = _engineState.value.copy(activeTypingContactPhone = null, activeRecordingContactPhone = null)
                        }
                    }
                }
                "CALL_SIGNAL" -> {
                    if (isForMe) {
                        handleCallSignal(packet)
                    }
                }
                "CHAT_MESSAGE" -> {
                    if (isForMe) {
                        sendAck(packet.packetUuid, packet.sourcePhone)
                        val entity = MessageEntity(
                            messageUuid = packet.packetUuid,
                            senderPhone = packet.sourcePhone,
                            recipientPhone = myPhone,
                            content = packet.content,
                            mediaType = packet.mediaType,
                            mediaBase64 = packet.mediaData,
                            timestamp = packet.timestamp,
                            status = "DELIVERED",
                            isOutgoing = false,
                            audioDurationSeconds = packet.audioDuration
                        )
                        repository.saveMessage(entity)
                        notificationHelper.showIncomingMessageNotification(
                            packet.sourcePhone,
                            packet.sourceName,
                            packet.content.ifEmpty { "Nuevo mensaje" }
                        )
                    } else {
                        relayPacket(packet)
                    }
                }
                "CHAT_CHUNK" -> {
                    if (isForMe) {
                        val chunks = receivedChunks.computeIfAbsent(packet.packetUuid) { ConcurrentHashMap() }
                        packet.mediaData?.let { chunks[packet.chunkIndex] = it }
                        if (chunks.size == packet.totalChunks) {
                            val sorted = (0 until packet.totalChunks).joinToString("") { chunks[it].orEmpty() }
                            val entity = MessageEntity(
                                messageUuid = packet.packetUuid,
                                senderPhone = packet.sourcePhone,
                                recipientPhone = myPhone,
                                content = packet.content,
                                mediaType = packet.mediaType,
                                mediaBase64 = sorted,
                                timestamp = packet.timestamp,
                                status = "DELIVERED",
                                isOutgoing = false,
                                audioDurationSeconds = packet.audioDuration
                            )
                            repository.saveMessage(entity)
                            sendAck(packet.packetUuid, packet.sourcePhone)
                            receivedChunks.remove(packet.packetUuid)
                        }
                    } else {
                        relayPacket(packet)
                    }
                }
            }
        }
    }

    private suspend fun relayPacket(packet: MeshPacket) {
        val myId = _engineState.value.myNodeId
        if (packet.visitedNodeIds.contains(myId) || packet.hopCount >= packet.maxHops) return
        val relayed = packet.copy(
            hopCount = packet.hopCount + 1,
            visitedNodeIds = packet.visitedNodeIds + myId
        )
        transmitMeshPacket(relayed)
        _engineState.value = _engineState.value.copy(
            packetsRelayed = _engineState.value.packetsRelayed + 1
        )
    }

    fun sendAck(originalUuid: String, recipientPhone: String) {
        val ack = MeshPacket(
            packetType = "ACK",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.ssid,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = recipientPhone,
            content = originalUuid
        )
        transmitMeshPacket(ack)
    }

    // Call functions
    fun startCall(contact: ContactEntity, isVideo: Boolean) {
        _engineState.value = _engineState.value.copy(
            isCallActive = true,
            isIncomingCall = false,
            isCallConnected = false,
            activeCallPeer = contact,
            isVideoCall = isVideo,
            callDurationSeconds = 0
        )

        val offer = MeshPacket(
            packetType = "CALL_SIGNAL",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.ssid,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = contact.phoneNumber,
            callSignalType = "OFFER",
            callIsVideo = isVideo
        )
        transmitMeshPacket(offer)

        val peerIp = peerIpByPhone[contact.phoneNumber] ?: "192.168.49.1"
        startRealAudioStreaming(peerIp)
    }

    fun answerCall() {
        val peer = _engineState.value.activeCallPeer ?: return
        notificationHelper.cancelCallNotification()

        _engineState.value = _engineState.value.copy(
            isIncomingCall = false,
            isCallConnected = true,
            callDurationSeconds = 0
        )

        val answer = MeshPacket(
            packetType = "CALL_SIGNAL",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.ssid,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = peer.phoneNumber,
            callSignalType = "ANSWER",
            callIsVideo = _engineState.value.isVideoCall
        )
        transmitMeshPacket(answer)

        startCallTimer()
        val peerIp = peerIpByPhone[peer.phoneNumber] ?: (if (_engineState.value.isGroupOwner) "192.168.49.2" else "192.168.49.1")
        startRealAudioStreaming(peerIp)
    }

    fun handleCallSignal(packet: MeshPacket) {
        when (packet.callSignalType) {
            "OFFER" -> {
                scope.launch {
                    val contact = repository.getContact(packet.sourcePhone)
                        ?: ContactEntity(
                            phoneNumber = packet.sourcePhone,
                            displayName = if (packet.sourceName.isNotBlank()) packet.sourceName else packet.sourcePhone,
                            isRegisteredInMesh = true,
                            isConnected = true
                        )

                    val callerDisplayName = contact.displayName.ifBlank {
                        if (packet.sourceName.isNotBlank()) packet.sourceName else packet.sourcePhone
                    }

                    // Set call state as INCOMING, ringing!
                    _engineState.value = _engineState.value.copy(
                        isCallActive = true,
                        isIncomingCall = true,
                        isCallConnected = false,
                        activeCallPeer = contact,
                        isVideoCall = packet.callIsVideo,
                        callDurationSeconds = 0
                    )
                    notificationHelper.showIncomingCallNotification(
                        packet.sourcePhone,
                        callerDisplayName,
                        packet.callIsVideo
                    )
                }
            }
            "ANSWER" -> {
                _engineState.value = _engineState.value.copy(
                    isCallConnected = true,
                    isIncomingCall = false
                )
                startCallTimer()
            }
            "HANGUP", "REJECT" -> {
                endCallInternal(saveToHistory = false)
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _engineState.value = _engineState.value.copy(
                    callDurationSeconds = _engineState.value.callDurationSeconds + 1
                )
            }
        }
    }

    private fun startRealAudioStreaming(peerIp: String) {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        audioRecordJob?.cancel()
        audioRecordJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            var socket: DatagramSocket? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                socket = DatagramSocket()
                val targetAddr = InetAddress.getByName(peerIp)
                val buffer = ByteArray(bufferSize)
                recorder.startRecording()

                while (isActive) {
                    if (!_engineState.value.isMicMuted) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            val packet = DatagramPacket(buffer, read, targetAddr, AUDIO_UDP_PORT)
                            socket.send(packet)
                        }
                    } else {
                        delay(50)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        audioPlayJob?.cancel()
        audioPlayJob = scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            var serverSocket: DatagramSocket? = null
            try {
                track = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
                serverSocket = DatagramSocket(AUDIO_UDP_PORT)
                val buffer = ByteArray(bufferSize)
                track.play()

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    serverSocket.receive(packet)
                    track.write(packet.data, 0, packet.length)
                }
            } catch (_: Exception) {
            } finally {
                try { track?.stop(); track?.release() } catch (_: Exception) {}
                try { serverSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun endCall() {
        val peer = _engineState.value.activeCallPeer
        val duration = _engineState.value.callDurationSeconds
        val isVideo = _engineState.value.isVideoCall

        if (peer != null) {
            val signal = if (_engineState.value.isIncomingCall) "REJECT" else "HANGUP"
            val hangup = MeshPacket(
                packetType = "CALL_SIGNAL",
                sourceNodeId = _engineState.value.myNodeId,
                sourcePhone = _engineState.value.myPhoneNumber,
                sourceName = _engineState.value.ssid,
                sourceSsid = _engineState.value.ssid,
                destinationPhone = peer.phoneNumber,
                callSignalType = signal,
                callIsVideo = isVideo
            )
            transmitMeshPacket(hangup)

            scope.launch {
                repository.insertCall(
                    CallEntity(
                        contactPhone = peer.phoneNumber,
                        contactName = peer.displayName,
                        isVideo = isVideo,
                        isOutgoing = !_engineState.value.isIncomingCall,
                        durationSeconds = duration,
                        status = if (_engineState.value.isCallConnected) "COMPLETED" else "MISSED"
                    )
                )
            }
        }

        endCallInternal(saveToHistory = false)
    }

    private fun endCallInternal(saveToHistory: Boolean) {
        audioRecordJob?.cancel()
        audioPlayJob?.cancel()
        callTimerJob?.cancel()
        notificationHelper.cancelCallNotification()

        _engineState.value = _engineState.value.copy(
            isCallActive = false,
            isIncomingCall = false,
            isCallConnected = false,
            activeCallPeer = null,
            callDurationSeconds = 0,
            isMicMuted = false,
            isSpeakerOn = false
        )
    }

    fun toggleMute() {
        _engineState.value = _engineState.value.copy(isMicMuted = !_engineState.value.isMicMuted)
    }

    fun toggleSpeaker() {
        val newSpeaker = !_engineState.value.isSpeakerOn
        _engineState.value = _engineState.value.copy(isSpeakerOn = newSpeaker)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.isSpeakerphoneOn = newSpeaker
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

    fun autoConnectToContact(contact: ContactEntity) {
        scope.launch {
            val cleanPhone = contact.phoneNumber.filter { it.isDigit() }
            val dev = _engineState.value.discoveredP2pDevices.find {
                val name = it.deviceName.orEmpty().lowercase()
                (cleanPhone.isNotEmpty() && name.contains(cleanPhone)) ||
                (cleanPhone.length >= 7 && name.contains(cleanPhone.takeLast(7))) ||
                name.contains(contact.displayName.lowercase())
            }
            if (dev != null) {
                connectToPeer(dev)
            }
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
            activeClientSockets.values.forEach { it.close() }
            p2pManager?.removeGroup(p2pChannel, null)
        } catch (_: Exception) {}
    }
}
