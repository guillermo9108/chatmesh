package com.example.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
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
import com.example.util.CallRingtonePlayer
import com.example.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Motor nativo de Malla Wi-Fi Direct, WiFi LAN y Hotspot para ChatMesh.
 *
 * Soporta comunicación peer-to-peer sobre:
 * 1. WiFi LAN (Routers domésticos / de oficina sin internet)
 * 2. Hotspot local (Punto de acceso creado por el móvil)
 * 3. WiFi Direct P2P (Malla ad-hoc autónoma)
 */
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
    private val AUDIO_UDP_PORT = 8991
    val MESH_GLOBAL_PASSPHRASE = "12345678"

    // Chunks de medios: más grandes = menos paquetes, más rápidos
    private val MEDIA_CHUNK_SIZE = 4096
    private val CHUNK_DELAY_MS = 8L

    val videoCallManager = P2pVideoCallManager(context) { base64Frame ->
        sendVideoFrame(base64Frame)
    }

    val hotspotManager = HotspotManager(context)
    @Volatile private var currentNetworkInfo: MeshNetworkInfo? = null
    private var networkMonitorJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var bleDiscovery: BleDiscoveryService? = null

    private val _engineState = MutableStateFlow(MeshEngineState())
    val engineState: StateFlow<MeshEngineState> = _engineState.asStateFlow()

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var isP2pReceiverRegistered = false

    @Volatile
    private var isInitialized = false

    private var serverSocket: ServerSocket? = null
    private var udpDiscoverySocket: DatagramSocket? = null
    private val activeClientSockets = ConcurrentHashMap<String, Socket>()
    private val socketWriters = ConcurrentHashMap<Socket, PrintWriter>()
    private val socketMutexes = ConcurrentHashMap<Socket, Mutex>()
    private val peerIpByPhone = ConcurrentHashMap<String, String>()
    private val peerMetrics = ConcurrentHashMap<String, PeerMetric>()

    data class KnownNeighborNode(
        val nodeId: String,
        val phoneNumber: String,
        val displayName: String,
        val lastIp: String? = null,
        val lastSeenTimestamp: Long = System.currentTimeMillis(),
        val isHotspotOwner: Boolean = false,
        val hotspotSsid: String? = null,
        val hotspotPassword: String? = null
    )

    private val knownNeighborPeers = ConcurrentHashMap<String, KnownNeighborNode>()
    private var videoQuality = VideoQuality.MEDIUM

    fun recordKnownNeighbor(
        nodeId: String,
        phone: String,
        name: String,
        ip: String? = null,
        isHotspot: Boolean = false,
        hotspotSsid: String? = null,
        hotspotPassword: String? = null
    ) {
        if (!SimDetectionUtil.isValidPhoneNumber(phone)) return
        val existing = knownNeighborPeers[phone]
        knownNeighborPeers[phone] = KnownNeighborNode(
            nodeId = nodeId.ifBlank { existing?.nodeId ?: "" },
            phoneNumber = phone,
            displayName = if (name.isNotBlank() && name != phone) name else (existing?.displayName ?: phone),
            lastIp = ip ?: existing?.lastIp,
            lastSeenTimestamp = System.currentTimeMillis(),
            isHotspotOwner = isHotspot || (existing?.isHotspotOwner == true),
            hotspotSsid = hotspotSsid ?: existing?.hotspotSsid,
            hotspotPassword = hotspotPassword ?: existing?.hotspotPassword
        )
    }

    fun getKnownNeighborPeers(): List<KnownNeighborNode> = knownNeighborPeers.values.toList()

    fun setVideoQuality(quality: VideoQuality) {
        videoQuality = quality
        videoCallManager.setVideoQuality(quality)
    }

    fun getVideoQuality(): VideoQuality = videoQuality

    private val storeAndForwardQueue = ConcurrentHashMap<String, PendingRetry>()
    private val receivedPacketUuids = ConcurrentHashMap.newKeySet<String>()

    private val receivedChunks = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()

    private var meshMaintenanceJob: Job? = null
    private var audioRecordJob: Job? = null
    private var audioPlayJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioServerSocket: DatagramSocket? = null
    private var audioSendSocket: DatagramSocket? = null
    private var callTimerJob: Job? = null

    // Efectos de audio para evitar eco y ruido
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    // ============================================================
    //  RECEIVER WiFi Direct
    // ============================================================
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
                    if (dev != null) {
                        Log.d(TAG, "P2P device local: ${dev.deviceAddress} - ${dev.deviceName}")
                    }
                }
            }
        }
    }

    // ============================================================
    //  INICIALIZACIÓN (IDEMPOTENTE)
    // ============================================================
    fun initialize(myPhone: String, myNickname: String, myAvatarUri: String? = null) {
        if (isInitialized) {
            updateUserProfile(myPhone, myNickname, myAvatarUri)
            return
        }
        isInitialized = true

        val nodeId = UUID.randomUUID().toString().take(8)
        val ssid = SimDetectionUtil.generateSsid(myPhone)

        _engineState.value = _engineState.value.copy(
            myNodeId = nodeId,
            myPhoneNumber = myPhone,
            myNickname = myNickname.ifBlank { "Usuario" },
            myAvatarUri = myAvatarUri,
            ssid = ssid
        )

        setupP2p(ssid)

        // Adquirir MulticastLock para asegurar recepción de paquetes UDP Broadcast en WiFi LAN y Hotspot
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("ChatMeshMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        startRealTcpMeshServer()
        startRealUdpBeaconListener()
        startMeshMaintenanceLoop()

        // Cada dispositivo decide si debe ser GO autónomo basándose en su número
        // de teléfono. El "mayor" crea grupo autónomo; el "menor" espera para
        // conectarse como cliente.
        maybeCreateAutonomousGroup()

        // Monitor de conectividad y transporte de red (WiFi Direct, Hotspot, WiFi LAN)
        startNetworkMonitorLoop()

        // Iniciar descubrimiento BLE (si los permisos están concedidos)
        try {
            bleDiscovery = BleDiscoveryService(
                context = context,
                myProfileProvider = { buildMyBleProfile() },
                onPeerDiscovered = { peer, rssi ->
                    Log.i(TAG, "BLE peer: ${peer.nickname} score=${peer.score}")
                    val peers = bleDiscovery?.getDiscoveredPeers() ?: emptyList()
                    _engineState.value = _engineState.value.copy(
                        bleEnabled = true,
                        blePeersCount = peers.size,
                        blePeersPhones = peers.map { it.phoneNumber }
                    )
                },
                onPeerLost = { phone ->
                    val peers = bleDiscovery?.getDiscoveredPeers() ?: emptyList()
                    _engineState.value = _engineState.value.copy(
                        blePeersCount = peers.size,
                        blePeersPhones = peers.map { it.phoneNumber }
                    )
                },
                onShouldBecomeGo = {
                    onShouldBecomeGoFromBle()
                }
            )
            bleDiscovery?.start()
            _engineState.value = _engineState.value.copy(
                bleEnabled = bleDiscovery?.isSupported() == true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando BLE discovery", e)
        }
    }

    private fun startNetworkMonitorLoop() {
        networkMonitorJob?.cancel()
        networkMonitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val info = NetworkInterfaceHelper.detectActiveTransport(context, hotspotManager.isActive)
                    if (info != null) {
                        currentNetworkInfo = info
                        if (_engineState.value.transport != info.transport ||
                            _engineState.value.networkLocalIp != info.localIp ||
                            _engineState.value.networkSsid != (info.ssid ?: "")
                        ) {
                            Log.i(TAG, "Transporte de red cambiado a: ${info.transport}, IP=${info.localIp}, SSID=${info.ssid}")
                            _engineState.value = _engineState.value.copy(
                                transport = info.transport,
                                networkSsid = info.ssid ?: "",
                                networkLocalIp = info.localIp
                            )
                            // Enviar beacon de inmediato al cambiar o detectar transporte
                            sendHeartbeatAndBeacon()
                        }
                    } else if (_engineState.value.transport != MeshTransport.NONE) {
                        currentNetworkInfo = null
                        _engineState.value = _engineState.value.copy(
                            transport = MeshTransport.NONE,
                            networkSsid = "",
                            networkLocalIp = ""
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en monitor de red", e)
                }
                delay(3000)
            }
        }
    }

    private fun maybeCreateAutonomousGroup() {
        // Esperar 3 segundos antes de crear el grupo para dar tiempo al descubrimiento
        scope.launch {
            delay(3000)
            try {
                // Si ya estamos conectados activamente por WiFi LAN o Hotspot, no crear grupo P2P para evitar interferencia
                if (currentNetworkInfo?.transport == MeshTransport.WIFI_LAN ||
                    currentNetworkInfo?.transport == MeshTransport.HOTSPOT ||
                    hotspotManager.isActive
                ) {
                    Log.i(TAG, "Conexión ${currentNetworkInfo?.transport} activa. Omitiendo creación de grupo P2P autónomo.")
                    return@launch
                }

                val ch = p2pChannel ?: return@launch
                val myPhone = _engineState.value.myPhoneNumber.filter { it.isDigit() }
                if (myPhone.isEmpty()) return@launch

                // Si NO hay peers descubiertos, creamos grupo autónomo.
                // Cuando aparezca otro dispositivo, si es "menor", se conectará
                // directamente a nuestro grupo sin diálogo.
                val hasPeers = _engineState.value.discoveredP2pDevices.isNotEmpty()
                if (!hasPeers) {
                    createAutonomousP2pGroup(_engineState.value.ssid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creando grupo autónomo inicial", e)
            }
        }
    }

    /**
     * Monitoreo y auto-restablecimiento del canal de malla (WiFi Direct / Hotspot / LAN).
     * Ejecutado continuamente por MeshForegroundService y por el monitor de red.
     */
    fun checkAndRecoverMeshConnection(): String {
        val state = _engineState.value
        val hasActiveTransport = state.transport != MeshTransport.NONE
        val hasActiveSockets = activeClientSockets.isNotEmpty()
        val isHotspotActive = hotspotManager.isActive || state.isHotspotActive
        val hasConnectedPeers = state.connectedPeersCount > 0

        // Si la conexión está viva y saludable
        if (hasActiveTransport && (hasActiveSockets || isHotspotActive || state.isGroupOwner || hasConnectedPeers)) {
            val peerCount = activeClientSockets.size.coerceAtLeast(state.connectedPeersCount)
            val modeName = when (state.transport) {
                MeshTransport.WIFI_DIRECT -> "WiFi Direct"
                MeshTransport.WIFI_LAN -> "WiFi LAN"
                MeshTransport.HOTSPOT -> "Hotspot Móvil"
                else -> "Malla P2P"
            }
            // Enviar beacon de mantenimiento periódico
            sendHeartbeatAndBeacon()
            return "ChatMesh: Activo en $modeName ($peerCount pares conectados)"
        }

        // --- DESCONEXIÓN DETECTADA: RESTABLECIMIENTO AUTOMÁTICO ---
        Log.w(TAG, "Watchdog: Canal de malla desconectado. Iniciando restablecimiento automático con nodos vecinos conocidos...")

        scope.launch {
            // 1. Si teníamos credenciales guardadas de un Hotspot de un peer, intentar reconectar
            val sharedSsid = state.hotspotSharedSsid.ifBlank {
                context.getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
                    .getString("pending_hotspot_ssid", "") ?: ""
            }
            val sharedPass = state.hotspotSharedPassword.ifBlank {
                context.getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
                    .getString("pending_hotspot_password", "") ?: ""
            }
            if (sharedSsid.isNotBlank() && sharedPass.isNotBlank() && HotspotConnector.isSupported()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        HotspotConnector.connectToHotspot(
                            context = context,
                            ssid = sharedSsid,
                            password = sharedPass,
                            onConnected = {
                                Log.i(TAG, "Reconectado exitosamente a hotspot de peer: $sharedSsid")
                            },
                            onFailed = {
                                Log.w(TAG, "Fallo reconectando a hotspot conocido: $it")
                            }
                        )
                    } catch (_: Exception) {}
                }
            }

            // 2. Reiniciar escaneo y descubrimiento de pares WiFi Direct
            startP2pDiscovery()

            // 3. Evaluar y reconectar al mejor nodo si hay pares descubiertos
            evaluateAndAutoConnectToBestNode()

            // 4. Reintentar conexión con nodos vecinos conocidos recientemente registrados
            if (knownNeighborPeers.isNotEmpty()) {
                for ((phone, neighbor) in knownNeighborPeers) {
                    val targetIp = neighbor.lastIp ?: peerIpByPhone[phone]
                    if (targetIp != null && targetIp != "127.0.0.1") {
                        try {
                            val probe = MeshPacket(
                                packetType = "BEACON",
                                sourceNodeId = _engineState.value.myNodeId,
                                sourcePhone = _engineState.value.myPhoneNumber,
                                sourceName = _engineState.value.myNickname,
                                destinationPhone = phone
                            )
                            transmitMeshPacketSync(probe)
                        } catch (_: Exception) {}
                    }
                }
            }

            // 5. Si después de unos segundos seguimos desconectados y sin grupo, evaluar grupo autónomo
            delay(4000)
            if (_engineState.value.transport == MeshTransport.NONE &&
                !_engineState.value.isGroupOwner &&
                _engineState.value.discoveredP2pDevices.isEmpty()
            ) {
                maybeCreateAutonomousGroup()
            }
        }

        return "ChatMesh: Restableciendo canal y reconectando a nodos..."
    }

    fun updateUserProfile(myPhone: String, myNickname: String, myAvatarUri: String?) {
        val ssid = SimDetectionUtil.generateSsid(myPhone)
        _engineState.value = _engineState.value.copy(
            myPhoneNumber = myPhone,
            myNickname = myNickname.ifBlank { "Usuario" },
            myAvatarUri = myAvatarUri,
            ssid = ssid
        )
        sendHeartbeatAndBeacon()
    }

    // ============================================================
    //  WiFi Direct setup
    // ============================================================
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        p2pReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(p2pReceiver, filter)
                }
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
        } catch (_: Exception) {}
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
        } catch (_: Exception) {}
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
        val ch = p2pChannel ?: return
        p2pManager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = createAutonomousP2pGroup(currentSsid)
            override fun onFailure(reason: Int) = createAutonomousP2pGroup(currentSsid)
        })
    }

    fun createAutonomousP2pGroup(ssid: String) {
        val cleanDigits = _engineState.value.myPhoneNumber.filter { it.isDigit() }
        val netName = if (cleanDigits.isNotEmpty()) "DIRECT-ms-Mesh_$cleanDigits" else "DIRECT-ms-Mesh_User"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val config = WifiP2pConfig.Builder()
                    .setNetworkName(netName)
                    .setPassphrase(MESH_GLOBAL_PASSPHRASE)
                    .build()
                val ch = p2pChannel ?: return
                p2pManager?.createGroup(ch, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "Grupo WiFi Direct creado: $netName")
                        _engineState.value = _engineState.value.copy(
                            isWifiDirectActive = true,
                            isGroupOwner = true,
                            ssid = netName,
                            passphrase = MESH_GLOBAL_PASSPHRASE,
                            localIpAddress = "192.168.49.1"
                        )
                        requestGroupAndConnectionDetails()
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "createGroup con config falló ($reason), fallback")
                        fallbackCreateGroup()
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error usando WifiP2pConfig.Builder", e)
            }
        }
        fallbackCreateGroup()
    }

    private fun fallbackCreateGroup() {
        val ch = p2pChannel ?: return
        p2pManager?.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Grupo WiFi Direct estándar creado")
                _engineState.value = _engineState.value.copy(
                    isWifiDirectActive = true,
                    isGroupOwner = true,
                    localIpAddress = "192.168.49.1"
                )
                requestGroupAndConnectionDetails()
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup falló ($reason), iniciando descubrimiento")
                startP2pDiscovery()
            }
        })
    }

    private fun getP2pInterfaceAddress(): String? {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (ifaces.hasMoreElements()) {
                val ni = ifaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name.lowercase()
                if (!name.startsWith("p2p")) continue
                for (addr in ni.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun requestGroupAndConnectionDetails() {
        val ch = p2pChannel ?: return

        p2pManager?.requestGroupInfo(ch) { group ->
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

        p2pManager?.requestConnectionInfo(ch) { info ->
            if (info != null && info.groupFormed) {
                val ownerIp = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"

                if (info.isGroupOwner) {
                    _engineState.value = _engineState.value.copy(
                        isWifiDirectActive = true,
                        isGroupOwner = true,
                        localIpAddress = ownerIp
                    )
                } else {
                    val myLocalIp = getP2pInterfaceAddress() ?: "192.168.49.2"
                    _engineState.value = _engineState.value.copy(
                        isWifiDirectActive = true,
                        isGroupOwner = false,
                        localIpAddress = myLocalIp
                    )
                    connectToMeshSocket(ownerIp, TCP_MESH_PORT)
                }
            }
        }
    }

    fun startP2pDiscovery() {
        val ch = p2pChannel ?: return
        try {
            p2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _engineState.value = _engineState.value.copy(
                        autoConnectStatus = "Escaneando dispositivos WiFi Direct..."
                    )
                }
                override fun onFailure(reason: Int) {
                    _engineState.value = _engineState.value.copy(
                        autoConnectStatus = "Descubrimiento falló ($reason)"
                    )
                }
            })
        } catch (_: Exception) {}
    }

    fun connectToPeer(device: WifiP2pDevice) {
        val ch = p2pChannel ?: return

        @Suppress("DEPRECATION")
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 15
            wps.setup = WpsInfo.PBC
        }

        // Si el otro ya está anunciado como GO autónomo en el discovery, conectar
        // con PBC debería ser silencioso. PBC es el modo que menos diálogos genera.
        if (_engineState.value.isGroupOwner && _engineState.value.connectedPeersCount == 0) {
            p2pManager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = doConnect(config, device.deviceName)
                override fun onFailure(reason: Int) = doConnect(config, device.deviceName)
            })
        } else {
            doConnect(config, device.deviceName)
        }
    }

    private fun doConnect(config: WifiP2pConfig, deviceName: String?) {
        val ch = p2pChannel ?: return
        p2pManager?.connect(ch, config, object : WifiP2pManager.ActionListener {
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
        if (_engineState.value.isWifiDirectActive && _engineState.value.isGroupOwner) return
        if (activeClientSockets.isNotEmpty()) return

        val devices = _engineState.value.discoveredP2pDevices
            .filter { it.status == WifiP2pDevice.AVAILABLE }

        if (devices.isEmpty()) return

        val myPhone = _engineState.value.myPhoneNumber.filter { it.isDigit() }

        val candidates = devices.filter { dev ->
            val theirName = dev.deviceName.orEmpty()
            val theirPhone = theirName
                .substringAfter("ChatMesh_", "")
                .substringAfter("mesh_", "")
                .substringAfter("Mesh_", "")
                .filter { it.isDigit() }
            if (theirPhone.isEmpty() || myPhone.isEmpty()) return@filter true
            myPhone < theirPhone
        }

        val best = candidates.minByOrNull { it.deviceAddress } ?: return

        _engineState.value = _engineState.value.copy(
            optimalNodeName = best.deviceName ?: best.deviceAddress,
            autoConnectStatus = "Conectando automáticamente a ${best.deviceName}..."
        )
        connectToPeer(best)
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
            val cleanPhone = phoneFromSsid?.let { SimDetectionUtil.sanitizePhoneNumber(it) }
            if (cleanPhone != null && SimDetectionUtil.isValidPhoneNumber(cleanPhone)) {
                if (!_engineState.value.isGroupOwner) {
                    peerIpByPhone[cleanPhone] = "192.168.49.1"
                }
                recordKnownNeighbor(
                    nodeId = device.deviceAddress,
                    phone = cleanPhone,
                    name = devName,
                    ip = if (!_engineState.value.isGroupOwner) "192.168.49.1" else null
                )
                val contact = repository.getContact(cleanPhone)
                if (contact == null) {
                    repository.insertContact(
                        ContactEntity(
                            phoneNumber = cleanPhone,
                            displayName = devName,
                            isRegisteredInMesh = true,
                            isConnected = isConnected
                        )
                    )
                } else {
                    repository.updateConnectionStatus(cleanPhone, isConnected, System.currentTimeMillis())
                }
            }
        }
    }

    // ============================================================
    //  TCP Server + Client
    // ============================================================
    private fun startRealTcpMeshServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(TCP_MESH_PORT))
                }
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

    private fun applySocketOptions(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 60_000
            socket.sendBufferSize = 512 * 1024
            socket.receiveBufferSize = 512 * 1024
        } catch (_: Exception) {}
    }

    private fun writerFor(socket: Socket): PrintWriter =
        socketWriters.getOrPut(socket) {
            PrintWriter(
                BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)),
                true
            )
        }

    fun handleClientSocket(socket: Socket) {
        applySocketOptions(socket)

        scope.launch(Dispatchers.IO) {
            val remoteIp = socket.inetAddress?.hostAddress.orEmpty()
            if (remoteIp.isNotEmpty()) {
                activeClientSockets[remoteIp]?.let { old ->
                    if (old !== socket) {
                        try { socketWriters.remove(old)?.close() } catch (_: Exception) {}
                        try { old.close() } catch (_: Exception) {}
                    }
                }
                activeClientSockets[remoteIp] = socket
            }

            try {
                val handshake = MeshPacket(
                    packetType = "HANDSHAKE",
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
                    sourceSsid = _engineState.value.ssid,
                    sourceAvatar = _engineState.value.myAvatarUri,
                    destinationPhone = "BROADCAST"
                )
                val mutex = socketMutexes.getOrPut(socket) { Mutex() }
                mutex.withLock {
                    writerFor(socket).println(handshake.toJson())
                }
            } catch (_: Exception) {}

            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (isActive && !socket.isClosed) {
                    val line = try {
                        reader.readLine()
                    } catch (e: SocketTimeoutException) {
                        continue
                    } ?: break
                    val packet = MeshPacket.fromJson(line)
                    if (packet != null) {
                        if (remoteIp.isNotEmpty()) peerIpByPhone[packet.sourcePhone] = remoteIp
                        processIncomingPacket(packet)
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (remoteIp.isNotEmpty()) activeClientSockets.remove(remoteIp)
                socketWriters.remove(socket)?.close()
                socketMutexes.remove(socket)
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
                    applySocketOptions(socket)

                    activeClientSockets[host] = socket

                    val handshake = MeshPacket(
                        packetType = "HANDSHAKE",
                        sourceNodeId = _engineState.value.myNodeId,
                        sourcePhone = _engineState.value.myPhoneNumber,
                        sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
                        sourceSsid = _engineState.value.ssid,
                        sourceAvatar = _engineState.value.myAvatarUri,
                        destinationPhone = "BROADCAST"
                    )
                    val mutex = socketMutexes.getOrPut(socket) { Mutex() }
                    mutex.withLock {
                        writerFor(socket).println(handshake.toJson())
                    }

                    connected = true
                    handleClientSocket(socket)
                    break
                } catch (_: Exception) {
                    delay(1200)
                }
            }
            if (!connected) Log.d(TAG, "No se pudo conectar a $host:$port tras reintentos")
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
            } catch (_: Exception) {}
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
            sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
            sourceSsid = _engineState.value.ssid,
            sourceAvatar = _engineState.value.myAvatarUri,
            destinationPhone = "BROADCAST"
        )
        transmitMeshPacket(beacon)
    }

    fun checkSelfHealingHeartbeats() {
        for ((ip, socket) in activeClientSockets) {
            if (socket.isClosed || !socket.isConnected) {
                activeClientSockets.remove(ip)
                socketWriters.remove(socket)?.close()
                socketMutexes.remove(socket)
            }
        }
    }

    fun retryStoreAndForwardQueue() {
        val now = System.currentTimeMillis()
        val iterator = storeAndForwardQueue.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, pending) = iterator.next()
            if (now >= pending.nextRetryTime) {
                if (pending.attempts >= 5) {
                    iterator.remove()
                } else {
                    pending.attempts++
                    pending.nextRetryTime = now + (pending.attempts * 3000L)
                    transmitMeshPacket(pending.packet)
                }
            }
        }
    }

    // ============================================================
    //  Envío de mensajes con chunking SERIALIZADO
    // ============================================================
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

            val rawData = mediaData.orEmpty()
            if (rawData.length > MEDIA_CHUNK_SIZE) {
                val totalChunks = (rawData.length + MEDIA_CHUNK_SIZE - 1) / MEDIA_CHUNK_SIZE
                Log.i(TAG, "Enviando $mediaType: $totalChunks chunks, ${rawData.length} chars total")
                for (i in 0 until totalChunks) {
                    val start = i * MEDIA_CHUNK_SIZE
                    val end = minOf(start + MEDIA_CHUNK_SIZE, rawData.length)
                    val chunkData = rawData.substring(start, end)
                    val packet = MeshPacket(
                        packetType = "CHAT_CHUNK",
                        packetUuid = messageUuid,
                        sourceNodeId = _engineState.value.myNodeId,
                        sourcePhone = _engineState.value.myPhoneNumber,
                        sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
                        sourceSsid = _engineState.value.ssid,
                        sourceAvatar = _engineState.value.myAvatarUri,
                        destinationPhone = recipientPhone,
                        content = content,
                        mediaType = mediaType,
                        mediaData = chunkData,
                        audioDuration = audioDuration,
                        chunkIndex = i,
                        totalChunks = totalChunks
                    )
                    // Envío SERIALIZADO: cada chunk espera a escribirse antes del siguiente
                    transmitMeshPacketSync(packet)
                    delay(CHUNK_DELAY_MS)
                }
                val finalPacket = MeshPacket(
                    packetType = "CHAT_CHUNK_END",
                    packetUuid = messageUuid,
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
                    sourceSsid = _engineState.value.ssid,
                    sourceAvatar = _engineState.value.myAvatarUri,
                    destinationPhone = recipientPhone,
                    content = content,
                    mediaType = mediaType,
                    mediaData = null,
                    audioDuration = audioDuration,
                    chunkIndex = totalChunks,
                    totalChunks = totalChunks
                )
                transmitMeshPacketSync(finalPacket)
                Log.i(TAG, "Chunks de ${messageUuid.take(8)} enviados completos")
            } else {
                val packet = MeshPacket(
                    packetType = "CHAT_MESSAGE",
                    packetUuid = messageUuid,
                    sourceNodeId = _engineState.value.myNodeId,
                    sourcePhone = _engineState.value.myPhoneNumber,
                    sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
                    sourceSsid = _engineState.value.ssid,
                    sourceAvatar = _engineState.value.myAvatarUri,
                    destinationPhone = recipientPhone,
                    content = content,
                    mediaType = mediaType,
                    mediaData = mediaData,
                    audioDuration = audioDuration
                )
                storeAndForwardQueue[messageUuid] = PendingRetry(packet)
                transmitMeshPacketSync(packet)
            }
        }
    }

    /**
     * Envía un paquete por todos los canales disponibles de forma SERIALIZADA.
     * Usa un Mutex por socket para evitar que dos paquetes concurrentes
     * intercalen bytes y corrompan el framing JSON.
     */
    private suspend fun transmitMeshPacketSync(packet: MeshPacket) {
        val json = packet.toJson()
        val data = json.toByteArray(Charsets.UTF_8)

        // 1. TCP: envío SERIALIZADO por socket con Mutex
        val socketsSnapshot = activeClientSockets.toMap()
        for ((_, socket) in socketsSnapshot) {
            try {
                if (!socket.isClosed) {
                    val mutex = socketMutexes.getOrPut(socket) { Mutex() }
                    mutex.withLock {
                        try {
                            writerFor(socket).println(json)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error escribiendo a socket: ${e.message}")
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Si destino tiene IP conocida pero no hay socket, conectar
        val targetIp = peerIpByPhone[packet.destinationPhone]
        if (targetIp != null && !activeClientSockets.containsKey(targetIp)) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(targetIp, TCP_MESH_PORT), 2000)
                applySocketOptions(s)
                activeClientSockets[targetIp] = s
                val mutex = socketMutexes.getOrPut(s) { Mutex() }
                mutex.withLock {
                    writerFor(s).println(json)
                }
                handleClientSocket(s)
            } catch (_: Exception) {}
        }

        // 3. Cliente WiFi Direct o Hotspot: asegurar enlace automático
        if (!_engineState.value.isGroupOwner &&
            !activeClientSockets.containsKey("192.168.49.1") &&
            _engineState.value.isWifiDirectActive
        ) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("192.168.49.1", TCP_MESH_PORT), 2000)
                applySocketOptions(s)
                activeClientSockets["192.168.49.1"] = s
                val mutex = socketMutexes.getOrPut(s) { Mutex() }
                mutex.withLock {
                    writerFor(s).println(json)
                }
                handleClientSocket(s)
            } catch (_: Exception) {}
        }

        // Si estamos conectados a un Hotspot (cliente), asegurar enlace TCP con el AP (192.168.43.1)
        val activeInfo = currentNetworkInfo
        if ((activeInfo?.transport == MeshTransport.HOTSPOT || activeInfo?.gateway == "192.168.43.1") &&
            activeInfo?.localIp != "192.168.43.1" &&
            !activeClientSockets.containsKey("192.168.43.1")
        ) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("192.168.43.1", TCP_MESH_PORT), 2000)
                applySocketOptions(s)
                activeClientSockets["192.168.43.1"] = s
                val mutex = socketMutexes.getOrPut(s) { Mutex() }
                mutex.withLock {
                    writerFor(s).println(json)
                }
                handleClientSocket(s)
            } catch (_: Exception) {}
        }

        // 4. UDP broadcast SOLO para paquetes de control (no chunks)
        if (packet.packetType != "CHAT_CHUNK" && packet.packetType != "CHAT_CHUNK_END") {
            try {
                val udp = DatagramSocket().apply { broadcast = true }
                val info = currentNetworkInfo
                val targets = mutableListOf<String>()
                targets.add("255.255.255.255")
                if (info != null && info.subnetBroadcast.isNotBlank()) {
                    targets.add(info.subnetBroadcast)
                }
                // Si estamos en Hotspot (AP o cliente), difundir en toda la subred 192.168.43.x
                if (info?.transport == MeshTransport.HOTSPOT || hotspotManager.isActive || info?.localIp?.startsWith("192.168.43.") == true) {
                    targets.add("192.168.43.255")
                    targets.add("192.168.43.1")
                    // Enviar también a los clientes DHCP comunes del punto de acceso
                    for (i in 2..15) {
                        targets.add("192.168.43.$i")
                    }
                }
                // Si estamos en WiFi LAN
                if (info?.transport == MeshTransport.WIFI_LAN) {
                    if (!info.gateway.isNullOrBlank()) targets.add(info.gateway)
                }
                // Si estamos en WiFi Direct
                if (info?.transport == MeshTransport.WIFI_DIRECT) {
                    targets.add("192.168.49.255")
                    targets.add("192.168.49.1")
                }

                // Detectar si también hay WiFi LAN activa para enviar baliza a esa red
                val lanInfo = NetworkInterfaceHelper.getWifiLanInfo(context)
                if (lanInfo != null && lanInfo.subnetBroadcast.isNotBlank() && !targets.contains(lanInfo.subnetBroadcast)) {
                    targets.add(lanInfo.subnetBroadcast)
                    if (!lanInfo.gateway.isNullOrBlank()) targets.add(lanInfo.gateway)
                }

                // Si destino tiene IP conocida, añadirla también
                if (targetIp != null && !targets.contains(targetIp)) targets.add(targetIp)

                // Enviar también directamente a todos los pares conocidos en la red local
                peerIpByPhone.values.forEach { pip ->
                    if (pip.isNotBlank() && pip != "127.0.0.1" && !targets.contains(pip)) {
                        targets.add(pip)
                    }
                }

                for (tip in targets) {
                    try {
                        val addr = InetAddress.getByName(tip)
                        udp.send(DatagramPacket(data, data.size, addr, UDP_BEACON_PORT))
                    } catch (_: Exception) {}
                }
                udp.close()
            } catch (_: Exception) {}
        }

        _engineState.value = _engineState.value.copy(
            packetsSent = _engineState.value.packetsSent + 1
        )
    }

    /**
     * Versión fire-and-forget para paquetes de control (beacons, status, etc.).
     */
    fun transmitMeshPacket(packet: MeshPacket) {
        scope.launch(Dispatchers.IO) {
            transmitMeshPacketSync(packet)
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
            // Deduplicación: chunks por (uuid, chunkIndex), resto por uuid
            val dedupKey = if (packet.packetType == "CHAT_CHUNK") {
                "${packet.packetUuid}#${packet.chunkIndex}"
            } else {
                packet.packetUuid
            }
            if (!receivedPacketUuids.add(dedupKey)) return@launch

            _engineState.value = _engineState.value.copy(
                packetsReceived = _engineState.value.packetsReceived + 1
            )

            val myPhone = _engineState.value.myPhoneNumber
            val isFromMe = isMatchingPhone(packet.sourcePhone, myPhone) || packet.sourceNodeId == _engineState.value.myNodeId
            if (isFromMe) return@launch

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
                    connectionType = currentNetworkInfo?.transport?.name ?: "WIFI_DIRECT",
                    isDirectNeighbor = true,
                    hopDistance = packet.hopCount,
                    isActive = true
                )
            )

            when (packet.packetType) {
                "BEACON", "HEARTBEAT", "HANDSHAKE" -> {
                    val peerName = if (packet.sourceName.isNotBlank() &&
                        packet.sourceName != "Nodo" &&
                        !packet.sourceName.startsWith("ChatMesh_")
                    ) packet.sourceName else packet.sourcePhone
                    val peerAvatar = packet.sourceAvatar
                    val contact = repository.getContact(packet.sourcePhone)
                    if (contact == null) {
                        repository.insertContact(
                            ContactEntity(
                                phoneNumber = packet.sourcePhone,
                                displayName = peerName,
                                avatarUri = peerAvatar,
                                isRegisteredInMesh = true,
                                isConnected = true
                            )
                        )
                    } else {
                        val updatedName = if (peerName != packet.sourcePhone) peerName else contact.displayName
                        val updatedAvatar = peerAvatar ?: contact.avatarUri
                        repository.insertContact(
                            contact.copy(
                                displayName = updatedName,
                                avatarUri = updatedAvatar,
                                isConnected = true,
                                lastSeen = System.currentTimeMillis()
                            )
                        )
                    }

                    // Respuesta directa e inmediata si es un BEACON de difusión
                    if (packet.packetType == "BEACON" && packet.destinationPhone == "BROADCAST") {
                        val senderIp = peerIpByPhone[packet.sourcePhone]
                        if (!senderIp.isNullOrBlank() && senderIp != "127.0.0.1") {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val handshake = MeshPacket(
                                        packetType = "HANDSHAKE",
                                        sourceNodeId = _engineState.value.myNodeId,
                                        sourcePhone = _engineState.value.myPhoneNumber,
                                        sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
                                        sourceSsid = _engineState.value.ssid,
                                        sourceAvatar = _engineState.value.myAvatarUri,
                                        destinationPhone = packet.sourcePhone
                                    )
                                    val handshakeData = handshake.toJson().toByteArray(Charsets.UTF_8)
                                    val udp = DatagramSocket()
                                    udp.send(DatagramPacket(handshakeData, handshakeData.size, InetAddress.getByName(senderIp), UDP_BEACON_PORT))
                                    udp.close()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
                "ACK" -> {
                    storeAndForwardQueue.remove(packet.content)
                    repository.updateMessageStatus(packet.content, "DELIVERED")
                }
                "STATUS_UPDATE" -> {
                    if (isForMe) {
                        when (packet.statusType) {
                            "TYPING" -> _engineState.value = _engineState.value.copy(
                                activeTypingContactPhone = packet.sourcePhone
                            )
                            "RECORDING" -> _engineState.value = _engineState.value.copy(
                                activeRecordingContactPhone = packet.sourcePhone
                            )
                            else -> _engineState.value = _engineState.value.copy(
                                activeTypingContactPhone = null,
                                activeRecordingContactPhone = null
                            )
                        }
                    }
                }
                "CALL_SIGNAL" -> if (isForMe) handleCallSignal(packet)
                "HOTSPOT_SHARE_REQUEST" -> {
                    if (isForMe) {
                        Log.i(TAG, "Solicitud de hotspot de ${packet.sourcePhone} (${packet.sourceName})")
                        _engineState.value = _engineState.value.copy(
                            isHotspotSharedByPeer = true,
                            hotspotPeerPhone = packet.sourcePhone
                        )
                        notificationHelper.showHotspotRequestNotification(
                            packet.sourcePhone,
                            packet.sourceName.ifBlank { packet.sourcePhone }
                        )
                    }
                }
                "HOTSPOT_SHARE_OFFER" -> {
                    if (isForMe) {
                        val ssid = packet.hotspotSsid
                        val password = packet.hotspotPassword
                        if (!ssid.isNullOrBlank() && !password.isNullOrBlank()) {
                            Log.i(TAG, "Recibidas credenciales de hotspot: SSID=$ssid de ${packet.sourcePhone}")
                            _engineState.value = _engineState.value.copy(
                                hotspotSharedSsid = ssid,
                                hotspotSharedPassword = password,
                                isHotspotSharedByPeer = true,
                                hotspotPeerPhone = packet.sourcePhone
                            )
                            context.getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("pending_hotspot_ssid", ssid)
                                .putString("pending_hotspot_password", password)
                                .putString("pending_hotspot_peer", packet.sourcePhone)
                                .apply()
                        }
                    }
                }
                "VIDEO_FRAME" -> {
                    if (isForMe) {
                        Log.i(TAG, "VIDEO_FRAME recibido de ${packet.sourcePhone}, len=${packet.videoFrameBase64?.length ?: 0}")
                        packet.videoFrameBase64?.let { b64 ->
                            videoCallManager.onRemoteFrameReceived(b64)
                        }
                    }
                }
                "CHAT_MESSAGE" -> {
                    if (isForMe) {
                        sendAck(packet.packetUuid, packet.sourcePhone)
                        persistIncomingMessage(packet, myPhone)
                        notificationHelper.showIncomingMessageNotification(
                            packet.sourcePhone,
                            packet.sourceName.ifBlank { packet.sourcePhone },
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
                        Log.d(TAG, "Chunk ${packet.chunkIndex + 1}/${packet.totalChunks} para ${packet.packetUuid.take(8)} (${chunks.size} recibidos)")
                        if (chunks.size == packet.totalChunks) {
                            val sorted = (0 until packet.totalChunks).joinToString("") { chunks[it].orEmpty() }
                            Log.i(TAG, "Mensaje de medios completo: ${packet.mediaType}, ${sorted.length} chars")
                            persistIncomingMessage(packet.copy(mediaData = sorted), myPhone)
                            sendAck(packet.packetUuid, packet.sourcePhone)
                            receivedChunks.remove(packet.packetUuid)
                            notificationHelper.showIncomingMessageNotification(
                                packet.sourcePhone,
                                packet.sourceName.ifBlank { packet.sourcePhone },
                                when (packet.mediaType) {
                                    "IMAGE" -> "📷 Foto"
                                    "AUDIO" -> "🎤 Mensaje de voz"
                                    "FILE" -> "📎 Archivo"
                                    else -> packet.content.ifEmpty { "Nuevo mensaje" }
                                }
                            )
                        }
                    } else {
                        relayPacket(packet)
                    }
                }
                "CHAT_CHUNK_END" -> {
                    if (isForMe) {
                        val chunks = receivedChunks[packet.packetUuid]
                        if (chunks != null && chunks.size < packet.totalChunks) {
                            Log.w(TAG, "Mensaje ${packet.packetUuid.take(8)} incompleto: ${chunks.size}/${packet.totalChunks}")
                        } else {
                            Log.i(TAG, "Mensaje ${packet.packetUuid.take(8)} confirmado completo")
                        }
                    } else {
                        relayPacket(packet)
                    }
                }
            }
        }
    }

    private suspend fun persistIncomingMessage(packet: MeshPacket, myPhone: String) {
        val peerName = if (packet.sourceName.isNotBlank() &&
            packet.sourceName != "Nodo" &&
            !packet.sourceName.startsWith("ChatMesh_")
        ) packet.sourceName else packet.sourcePhone

        val contact = repository.getContact(packet.sourcePhone)
        if (contact == null) {
            repository.insertContact(
                ContactEntity(
                    phoneNumber = packet.sourcePhone,
                    displayName = peerName,
                    avatarUri = packet.sourceAvatar,
                    isRegisteredInMesh = true,
                    isConnected = true
                )
            )
        } else {
            val updatedName = if (peerName != packet.sourcePhone) peerName else contact.displayName
            val updatedAvatar = packet.sourceAvatar ?: contact.avatarUri
            repository.insertContact(
                contact.copy(
                    displayName = updatedName,
                    avatarUri = updatedAvatar,
                    isConnected = true,
                    lastSeen = System.currentTimeMillis()
                )
            )
        }

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
    }

    private suspend fun relayPacket(packet: MeshPacket) {
        if (packet.packetType == "VIDEO_FRAME") return
        val myId = _engineState.value.myNodeId
        if (packet.visitedNodeIds.contains(myId) || packet.hopCount >= packet.maxHops) return
        val relayed = packet.copy(
            hopCount = packet.hopCount + 1,
            visitedNodeIds = packet.visitedNodeIds + myId
        )
        transmitMeshPacketSync(relayed)
        _engineState.value = _engineState.value.copy(
            packetsRelayed = _engineState.value.packetsRelayed + 1
        )
    }

    fun sendAck(originalUuid: String, recipientPhone: String) {
        val ack = MeshPacket(
            packetType = "ACK",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
            sourceSsid = _engineState.value.ssid,
            sourceAvatar = _engineState.value.myAvatarUri,
            destinationPhone = recipientPhone,
            content = originalUuid
        )
        transmitMeshPacket(ack)
    }

    private fun resolvePeerIp(peerPhone: String): String {
        val cleanDigits = peerPhone.filter { it.isDigit() }
        val direct = peerIpByPhone[peerPhone]
            ?: (if (cleanDigits.isNotEmpty()) peerIpByPhone[cleanDigits] else null)
            ?: peerIpByPhone.entries.firstOrNull { isMatchingPhone(it.key, peerPhone) }?.value
        if (!direct.isNullOrBlank() && direct != "127.0.0.1") return direct

        val clientSocketIp = activeClientSockets.keys.firstOrNull { it != "127.0.0.1" && it.isNotBlank() }
        if (!clientSocketIp.isNullOrBlank()) return clientSocketIp

        val info = currentNetworkInfo
        if (info != null) {
            if (info.transport == MeshTransport.HOTSPOT) {
                // Yo soy el AP (192.168.43.1), los clientes tienen .x
                return if (info.localIp == "192.168.43.1") "192.168.43.2" else "192.168.43.1"
            }
            if (info.transport == MeshTransport.WIFI_LAN) {
                // Todos estamos en la misma subred, el fallback es el gateway
                return info.gateway ?: info.localIp
            }
        }
        return if (_engineState.value.isGroupOwner) "192.168.49.2" else "192.168.49.1"
    }

    // ============================================================
    //  LLAMADAS
    // ============================================================
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
            sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
            sourceSsid = _engineState.value.ssid,
            sourceIp = _engineState.value.localIpAddress,
            sourceAvatar = _engineState.value.myAvatarUri,
            destinationPhone = contact.phoneNumber,
            callSignalType = "OFFER",
            callIsVideo = isVideo
        )
        transmitMeshPacket(offer)
    }

    fun sendVideoFrame(base64Frame: String) {
        val peer = _engineState.value.activeCallPeer ?: return
        if (!_engineState.value.isCallConnected || !_engineState.value.isVideoCall) return
        Log.i(TAG, "Enviando VIDEO_FRAME a ${peer.phoneNumber}, len=${base64Frame.length}")
        val packet = MeshPacket(
            packetType = "VIDEO_FRAME",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
            sourceSsid = _engineState.value.ssid,
            sourceAvatar = _engineState.value.myAvatarUri,
            destinationPhone = peer.phoneNumber,
            videoFrameBase64 = base64Frame
        )
        transmitMeshPacket(packet)
    }

    fun answerCall() {
        CallRingtonePlayer.stop()
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
            sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
            sourceSsid = _engineState.value.ssid,
            sourceIp = _engineState.value.localIpAddress,
            sourceAvatar = _engineState.value.myAvatarUri,
            destinationPhone = peer.phoneNumber,
            callSignalType = "ANSWER",
            callIsVideo = _engineState.value.isVideoCall
        )
        transmitMeshPacket(answer)

        startCallTimer()
        val peerIp = resolvePeerIp(peer.phoneNumber)
        Log.i(TAG, "Llamada aceptada, peerIp=$peerIp, video=${_engineState.value.isVideoCall}")
        startRealAudioStreaming(peerIp)
        if (_engineState.value.isVideoCall) videoCallManager.startVideoStream()
    }

    fun handleCallSignal(packet: MeshPacket) {
        if (packet.sourceIp.isNotBlank() && packet.sourceIp != "127.0.0.1") {
            peerIpByPhone[packet.sourcePhone] = packet.sourceIp
            val digits = packet.sourcePhone.filter { it.isDigit() }
            if (digits.isNotEmpty()) peerIpByPhone[digits] = packet.sourceIp
        }

        when (packet.callSignalType) {
            "OFFER" -> {
                scope.launch {
                    val peerName = if (packet.sourceName.isNotBlank() &&
                        packet.sourceName != "Nodo" &&
                        !packet.sourceName.startsWith("ChatMesh_")
                    ) packet.sourceName else packet.sourcePhone
                    val peerAvatar = packet.sourceAvatar
                    val existing = repository.getContact(packet.sourcePhone)
                    val callerContact = if (existing != null) {
                        val updatedName = if (peerName != packet.sourcePhone) peerName else existing.displayName
                        val updatedAvatar = peerAvatar ?: existing.avatarUri
                        val updated = existing.copy(
                            displayName = updatedName,
                            avatarUri = updatedAvatar,
                            isConnected = true
                        )
                        repository.insertContact(updated)
                        updated
                    } else {
                        val newContact = ContactEntity(
                            phoneNumber = packet.sourcePhone,
                            displayName = peerName,
                            avatarUri = peerAvatar,
                            isRegisteredInMesh = true,
                            isConnected = true
                        )
                        repository.insertContact(newContact)
                        newContact
                    }

                    _engineState.value = _engineState.value.copy(
                        isCallActive = true,
                        isIncomingCall = true,
                        isCallConnected = false,
                        activeCallPeer = callerContact,
                        isVideoCall = packet.callIsVideo,
                        callDurationSeconds = 0
                    )
                    notificationHelper.showIncomingCallNotification(
                        packet.sourcePhone,
                        callerContact.displayName,
                        packet.callIsVideo
                    )
                    CallRingtonePlayer.start(context)
                }
            }
            "ANSWER" -> {
                _engineState.value = _engineState.value.copy(
                    isCallConnected = true,
                    isIncomingCall = false
                )
                startCallTimer()
                val peerIp = resolvePeerIp(packet.sourcePhone)
                Log.i(TAG, "Llamada respondida, peerIp=$peerIp, video=${_engineState.value.isVideoCall}")
                startRealAudioStreaming(peerIp)
                if (_engineState.value.isVideoCall) videoCallManager.startVideoStream()
            }
            "HANGUP", "REJECT" -> endCallInternal()
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

    @SuppressLint("MissingPermission")
    private fun startRealAudioStreaming(peerIp: String) {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = _engineState.value.isSpeakerOn
        } catch (_: Exception) {}

        audioRecordJob?.cancel()
        try { audioSendSocket?.close() } catch (_: Exception) {}
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        audioSendSocket = null
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null

        audioRecordJob = scope.launch(Dispatchers.IO) {
            try {
                var rec = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize * 4
                    )
                } catch (_: Exception) { null }

                if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                    try { rec?.release() } catch (_: Exception) {}
                    rec = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize * 4
                    )
                }
                audioRecord = rec

                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(rec.audioSessionId)
                        echoCanceler?.enabled = true
                        Log.i(TAG, "AEC activado")
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(rec.audioSessionId)
                        noiseSuppressor?.enabled = true
                        Log.i(TAG, "NS activado")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudieron activar efectos de audio", e)
                }

                val sock = DatagramSocket()
                audioSendSocket = sock
                val targetAddr = InetAddress.getByName(peerIp)
                val buffer = ByteArray(bufferSize)
                rec.startRecording()
                Log.i(TAG, "Audio TX iniciado hacia $peerIp")

                while (isActive && _engineState.value.isCallConnected) {
                    if (!_engineState.value.isMicMuted) {
                        val read = rec.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            sock.send(DatagramPacket(buffer, read, targetAddr, AUDIO_UDP_PORT))
                        }
                    } else delay(40)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitiendo audio", e)
            } finally {
                try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
                try { audioSendSocket?.close() } catch (_: Exception) {}
                try { echoCanceler?.release() } catch (_: Exception) {}
                try { noiseSuppressor?.release() } catch (_: Exception) {}
                audioRecord = null
                audioSendSocket = null
                echoCanceler = null
                noiseSuppressor = null
            }
        }

        audioPlayJob?.cancel()
        try { audioServerSocket?.close() } catch (_: Exception) {}
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        audioServerSocket = null
        audioTrack = null

        audioPlayJob = scope.launch(Dispatchers.IO) {
            try {
                val minTrackBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val trackBufSize = maxOf(minTrackBuf, bufferSize * 8)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
                val trk = AudioTrack(
                    attributes, format, trackBufSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                audioTrack = trk

                val srvSock = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(AUDIO_UDP_PORT))
                }
                audioServerSocket = srvSock
                val buffer = ByteArray(bufferSize * 4)
                trk.play()
                Log.i(TAG, "Audio RX escuchando en puerto $AUDIO_UDP_PORT")

                while (isActive && _engineState.value.isCallConnected) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    srvSock.receive(packet)
                    if (packet.length > 0) {
                        trk.write(packet.data, 0, packet.length)
                    }
                }
            } catch (e: Exception) {
                if (_engineState.value.isCallConnected) Log.e(TAG, "Error reproduciendo audio", e)
            } finally {
                try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
                try { audioServerSocket?.close() } catch (_: Exception) {}
                audioTrack = null
                audioServerSocket = null
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
                sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
                sourceSsid = _engineState.value.ssid,
                sourceIp = _engineState.value.localIpAddress,
                sourceAvatar = _engineState.value.myAvatarUri,
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

        endCallInternal()
    }

    private fun endCallInternal() {
        CallRingtonePlayer.stop()
        audioRecordJob?.cancel()
        audioPlayJob?.cancel()
        try { audioServerSocket?.close() } catch (_: Exception) {}
        try { audioSendSocket?.close() } catch (_: Exception) {}
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        audioServerSocket = null
        audioSendSocket = null
        audioRecord = null
        audioTrack = null
        echoCanceler = null
        noiseSuppressor = null

        videoCallManager.stopVideoStream()
        callTimerJob?.cancel()
        notificationHelper.cancelCallNotification()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try { audioManager?.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}

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
        try { audioManager?.isSpeakerphoneOn = newSpeaker } catch (_: Exception) {}
    }

    fun switchCamera() {
        videoCallManager.switchCamera()
    }

    fun sendUserStatus(recipientPhone: String, status: String) {
        val packet = MeshPacket(
            packetType = "STATUS_UPDATE",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.myNickname.ifBlank { "Usuario" },
            sourceSsid = _engineState.value.ssid,
            sourceAvatar = _engineState.value.myAvatarUri,
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
            if (dev != null) connectToPeer(dev)
        }
    }

    fun startHotspot() {
        _engineState.value = _engineState.value.copy(hotspotErrorMessage = null)

        scope.launch {
            // Liberar temporalmente el grupo y detener escaneo P2P para evitar conflicto en el chip de radio Wi-Fi
            try {
                p2pManager?.stopPeerDiscovery(p2pChannel, null)
                p2pManager?.removeGroup(p2pChannel, null)
            } catch (_: Exception) {}

            // Pausa breve para que el controlador de radio de Android libere la interfaz P2P
            delay(500)

            hotspotManager.start(
                onReady = { ssid, password ->
                    _engineState.value = _engineState.value.copy(
                        isHotspotActive = true,
                        hotspotSsid = ssid,
                        hotspotPassword = password,
                        transport = MeshTransport.HOTSPOT,
                        hotspotErrorMessage = null
                    )
                    scope.launch {
                        delay(1500)
                        sendHeartbeatAndBeacon()
                    }
                },
                onError = { msg ->
                    Log.e(TAG, "Error hotspot: $msg")
                    _engineState.value = _engineState.value.copy(
                        isHotspotActive = false,
                        hotspotSsid = "",
                        hotspotPassword = "",
                        hotspotErrorMessage = msg
                    )
                }
            )
        }
    }

    fun stopHotspot() {
        hotspotManager.stop()
        _engineState.value = _engineState.value.copy(
            isHotspotActive = false,
            hotspotSsid = "",
            hotspotPassword = "",
            hotspotErrorMessage = null
        )
        scope.launch {
            delay(500)
            val info = NetworkInterfaceHelper.detectActiveTransport(context, false)
            currentNetworkInfo = info
            _engineState.value = _engineState.value.copy(
                transport = info?.transport ?: MeshTransport.NONE,
                networkSsid = info?.ssid ?: "",
                networkLocalIp = info?.localIp ?: ""
            )
            sendHeartbeatAndBeacon()
        }
    }

    fun clearHotspotError() {
        _engineState.value = _engineState.value.copy(hotspotErrorMessage = null)
    }

    fun requestHotspotFromPeer(peerPhone: String) {
        val packet = MeshPacket(
            packetType = "HOTSPOT_SHARE_REQUEST",
            sourceNodeId = _engineState.value.myNodeId,
            sourcePhone = _engineState.value.myPhoneNumber,
            sourceName = _engineState.value.myNickname,
            sourceSsid = _engineState.value.ssid,
            destinationPhone = peerPhone,
            hotspotRequesterPhone = _engineState.value.myPhoneNumber
        )
        transmitMeshPacket(packet)
        Log.i(TAG, "Solicitando hotspot a $peerPhone")
    }

    fun shareHotspotWithPeer(peerPhone: String) {
        scope.launch {
            if (!hotspotManager.isActive) {
                Log.w(TAG, "No hay hotspot activo para compartir")
                return@launch
            }
            val ssid = hotspotManager.currentSsid
            val password = hotspotManager.currentPassword
            val packet = MeshPacket(
                packetType = "HOTSPOT_SHARE_OFFER",
                sourceNodeId = _engineState.value.myNodeId,
                sourcePhone = _engineState.value.myPhoneNumber,
                sourceName = _engineState.value.myNickname,
                sourceSsid = _engineState.value.ssid,
                destinationPhone = peerPhone,
                hotspotSsid = ssid,
                hotspotPassword = password,
                hotspotRequesterPhone = peerPhone
            )
            transmitMeshPacketSync(packet)
            Log.i(TAG, "Credenciales de hotspot enviadas a $peerPhone (SSID=$ssid)")
        }
    }

    fun getPendingHotspotCredentials(): Triple<String, String, String>? {
        val prefs = context.getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
        val ssid = prefs.getString("pending_hotspot_ssid", null)
        val pass = prefs.getString("pending_hotspot_password", null)
        val peer = prefs.getString("pending_hotspot_peer", null)
        return if (!ssid.isNullOrBlank() && !pass.isNullOrBlank() && !peer.isNullOrBlank()) {
            Triple(ssid, pass, peer)
        } else null
    }

    fun clearPendingHotspotCredentials() {
        context.getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("pending_hotspot_ssid")
            .remove("pending_hotspot_password")
            .remove("pending_hotspot_peer")
            .apply()
        _engineState.value = _engineState.value.copy(
            hotspotSharedSsid = "",
            hotspotSharedPassword = "",
            isHotspotSharedByPeer = false,
            hotspotPeerPhone = ""
        )
    }

    fun getKnownPeerPhones(): List<String> {
        val list = mutableListOf<String>()
        peerIpByPhone.keys.forEach { phone ->
            if (phone.isNotBlank() && phone != _engineState.value.myPhoneNumber && !list.contains(phone)) {
                list.add(phone)
            }
        }
        return list
    }

    private fun buildMyBleProfile(): BleDeviceProfile {
        val battery = getBatteryInfo()
        val hasInternet = hasInternetConnection()
        return BleDeviceProfile(
            nodeId = _engineState.value.myNodeId,
            phoneNumber = _engineState.value.myPhoneNumber,
            nickname = _engineState.value.myNickname,
            batteryPercent = battery.first,
            isCharging = battery.second,
            hasInternet = hasInternet,
            androidSdk = Build.VERSION.SDK_INT,
            score = BleScoreCalculator.calculate(
                batteryPercent = battery.first,
                isCharging = battery.second,
                hasInternet = hasInternet,
                androidSdk = Build.VERSION.SDK_INT
            )
        )
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val status = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS) ?: -1
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == android.os.BatteryManager.BATTERY_STATUS_FULL
            Pair(if (level in 0..100) level else -1, charging)
        } catch (_: Exception) { Pair(-1, false) }
    }

    private fun hasInternetConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { false }
    }

    private fun onShouldBecomeGoFromBle() {
        if (_engineState.value.isGroupOwner) return
        if (activeClientSockets.isNotEmpty()) return

        // Si ya estamos conectados en WiFi LAN o Hotspot, no interferir con la conexión actual
        if (currentNetworkInfo?.transport == MeshTransport.WIFI_LAN ||
            currentNetworkInfo?.transport == MeshTransport.HOTSPOT ||
            hotspotManager.isActive
        ) return

        _engineState.value = _engineState.value.copy(isBleNegotiatingGo = true)

        scope.launch {
            try {
                delay(1500) // Pausa para mitigar condiciones de carrera entre dispositivos

                val myProfile = buildMyBleProfile()
                val peers = bleDiscovery?.getDiscoveredPeers() ?: emptyList()
                val amStillBest = peers.none {
                    it.phoneNumber != myProfile.phoneNumber &&
                    (it.score > myProfile.score ||
                     (it.score == myProfile.score && it.phoneNumber < myProfile.phoneNumber))
                }

                if (!amStillBest) {
                    Log.i(TAG, "Otro nodo tiene mejor score o menor teléfono, esperando rol de cliente")
                    _engineState.value = _engineState.value.copy(isBleNegotiatingGo = false)
                    return@launch
                }

                Log.i(TAG, "Soy el mejor candidato por BLE (score=${myProfile.score}). Autoproclamando Group Owner.")
                val ch = p2pChannel ?: run {
                    _engineState.value = _engineState.value.copy(isBleNegotiatingGo = false)
                    return@launch
                }

                p2pManager?.requestGroupInfo(ch) { group ->
                    if (group != null && group.isGroupOwner) {
                        Log.i(TAG, "Ya somos Group Owner, omitiendo recreación")
                        _engineState.value = _engineState.value.copy(isBleNegotiatingGo = false)
                        return@requestGroupInfo
                    }
                    createAutonomousP2pGroup(_engineState.value.ssid)
                    _engineState.value = _engineState.value.copy(isBleNegotiatingGo = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en negociación BLE → GO", e)
                _engineState.value = _engineState.value.copy(isBleNegotiatingGo = false)
            }
        }
    }

    fun cleanUp() {
        try {
            try { bleDiscovery?.cleanup() } catch (_: Exception) {}
            bleDiscovery = null
            audioRecordJob?.cancel()
            audioPlayJob?.cancel()
            meshMaintenanceJob?.cancel()
            callTimerJob?.cancel()
            networkMonitorJob?.cancel()
            if (isP2pReceiverRegistered) {
                context.unregisterReceiver(p2pReceiver)
                isP2pReceiverRegistered = false
            }
            serverSocket?.close()
            udpDiscoverySocket?.close()
            activeClientSockets.values.forEach { try { it.close() } catch (_: Exception) {} }
            socketWriters.values.forEach { try { it.close() } catch (_: Exception) {} }
            activeClientSockets.clear()
            socketWriters.clear()
            socketMutexes.clear()
            p2pManager?.removeGroup(p2pChannel, null)
            hotspotManager.stop()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock?.release()
                }
            } catch (_: Exception) {}
            multicastLock = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}