package com.example.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.net.wifi.p2p.WifiP2pManager.*
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * P2PManager: Core manager for Android WiFi Direct (P2P) mesh networking.
 *
 * Utilizes [WifiP2pManager] and [WifiP2pManager.Channel] to discover and connect
 * to nearby devices in an offline environment without Internet connectivity.
 *
 * Features:
 * - Peer discovery lifecycle (start, stop, active state tracking)
 * - Connection handling (connect via PBC/WPS, cancel, disconnect)
 * - Group Owner negotiation and autonomous group creation/removal
 * - DNS-SD local service discovery for zero-config mesh node identification
 * - Connection and Group info queries
 * - Reactive StateFlows for Compose UI and MeshEngine consumption
 * - Battery optimization verification for reliable background mesh operation
 */
@SuppressLint("MissingPermission")
class P2PManager private constructor(private val context: Context) :
    ChannelListener,
    PeerListListener,
    ConnectionInfoListener,
    GroupInfoListener {

    companion object {
        private const val TAG = "P2PManager"

        const val SERVICE_TYPE = "_chatmesh._tcp"
        const val SERVICE_INSTANCE_NAME = "ChatMesh"

        @Volatile
        private var instance: P2PManager? = null

        fun getInstance(context: Context): P2PManager {
            return instance ?: synchronized(this) {
                instance ?: P2PManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * Checks whether battery optimizations are ignored for this app,
         * ensuring background Wi-Fi Direct and BLE mesh connectivity isn't terminated by OS.
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        }

        /**
         * Requests the user to whitelist the app from battery optimizations.
         */
        fun requestIgnoreBatteryOptimizations(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch battery optimization settings", e)
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: Channel? = null
    private var isReceiverRegistered = false

    // State flows
    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _isDiscoveryActive = MutableStateFlow(false)
    val isDiscoveryActive: StateFlow<Boolean> = _isDiscoveryActive.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _groupInfo = MutableStateFlow<WifiP2pGroup?>(null)
    val groupInfo: StateFlow<WifiP2pGroup?> = _groupInfo.asStateFlow()

    private val _thisDevice = MutableStateFlow<WifiP2pDevice?>(null)
    val thisDevice: StateFlow<WifiP2pDevice?> = _thisDevice.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Listo")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    // External listener callbacks
    private val peerListeners = mutableListOf<(List<WifiP2pDevice>) -> Unit>()
    private val connectionListeners = mutableListOf<(WifiP2pInfo) -> Unit>()
    private val groupListeners = mutableListOf<(WifiP2pGroup?) -> Unit>()
    private val disconnectionListeners = mutableListOf<() -> Unit>()

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(EXTRA_WIFI_STATE, -1)
                    val enabled = (state == WIFI_P2P_STATE_ENABLED)
                    _isWifiP2pEnabled.value = enabled
                    Log.i(TAG, "WiFi P2P state changed: enabled=$enabled")
                }

                WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d(TAG, "WiFi P2P peers changed, requesting peers...")
                    requestPeers()
                }

                WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_NETWORK_INFO)
                    }

                    Log.d(TAG, "WiFi P2P connection changed: isConnected=${networkInfo?.isConnected}")
                    if (networkInfo?.isConnected == true) {
                        _connectionStatus.value = "Conectado a grupo WiFi Direct"
                        requestConnectionInfo()
                        requestGroupInfo()
                    } else {
                        _connectionStatus.value = "Desconectado"
                        _connectionInfo.value = null
                        _groupInfo.value = null
                        notifyGroupChanged(null)
                        notifyDisconnection()
                    }
                }

                WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_WIFI_P2P_DEVICE)
                    }
                    _thisDevice.value = device
                    Log.d(TAG, "This device changed: name=${device?.deviceName}, addr=${device?.deviceAddress}")
                }

                WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val discoveryState = intent.getIntExtra(EXTRA_DISCOVERY_STATE, -1)
                    val active = (discoveryState == WIFI_P2P_DISCOVERY_STARTED)
                    _isDiscoveryActive.value = active
                    _connectionStatus.value = if (active) "Buscando dispositivos..." else "Búsqueda inactiva"
                    Log.d(TAG, "Discovery state changed: active=$active")
                }
            }
        }
    }

    init {
        initialize()
    }

    /**
     * Initializes WifiP2pManager and registers BroadcastReceiver.
     */
    fun initialize() {
        try {
            if (wifiP2pManager == null) {
                wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            }
            if (wifiP2pManager != null && wifiP2pChannel == null) {
                wifiP2pChannel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), this)
                Log.i(TAG, "WifiP2pManager initialized successfully with channel")
            }
            registerReceiver()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WifiP2pManager", e)
        }
    }

    override fun onChannelDisconnected() {
        Log.w(TAG, "WiFi P2P channel disconnected, re-initializing...")
        try {
            wifiP2pChannel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-initialize WiFi P2P channel", e)
        }
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(p2pReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(p2pReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(p2pReceiver)
        } catch (_: Exception) {}
        isReceiverRegistered = false
    }

    // ============================================================
    //  PEER DISCOVERY
    // ============================================================

    /**
     * Starts discovering nearby WiFi Direct devices in offline environment.
     */
    fun discoverPeers(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val manager = wifiP2pManager ?: run {
            onFailure?.invoke(P2P_UNSUPPORTED)
            return
        }
        val channel = wifiP2pChannel ?: run {
            onFailure?.invoke(BUSY)
            return
        }

        try {
            _connectionStatus.value = "Iniciando escaneo P2P..."
            manager.discoverPeers(channel, object : ActionListener {
                override fun onSuccess() {
                    _isDiscoveryActive.value = true
                    _connectionStatus.value = "Escaneando dispositivos cercanos..."
                    Log.i(TAG, "discoverPeers started successfully")
                    onSuccess?.invoke()
                }

                override fun onFailure(reason: Int) {
                    _isDiscoveryActive.value = false
                    _connectionStatus.value = "Error al escanear: ${getReasonText(reason)}"
                    Log.w(TAG, "discoverPeers failed: reason=$reason")
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception during discoverPeers", e)
            onFailure?.invoke(ERROR)
        }
    }

    /**
     * Stops discovering peers to conserve battery or prepare for connection.
     */
    fun stopPeerDiscovery(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            manager.stopPeerDiscovery(channel, object : ActionListener {
                override fun onSuccess() {
                    _isDiscoveryActive.value = false
                    _connectionStatus.value = "Escaneo detenido"
                    Log.i(TAG, "stopPeerDiscovery successful")
                    onSuccess?.invoke()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "stopPeerDiscovery failed: reason=$reason")
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping peer discovery", e)
            onFailure?.invoke(ERROR)
        }
    }

    /**
     * Requests the current list of discovered peers from WifiP2pManager.
     */
    fun requestPeers(listener: PeerListListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            manager.requestPeers(channel) { peers ->
                onPeersAvailable(peers)
                listener?.onPeersAvailable(peers)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception requesting peers", e)
        }
    }

    override fun onPeersAvailable(peers: WifiP2pDeviceList?) {
        val list = peers?.deviceList?.toList() ?: emptyList()
        _peers.value = list
        Log.d(TAG, "Discovered peers updated: ${list.size} device(s)")
        notifyPeersChanged(list)
    }

    // ============================================================
    //  CONNECTION MANAGEMENT
    // ============================================================

    /**
     * Connects to a nearby peer by device address.
     */
    fun connectToDevice(
        deviceAddress: String,
        groupOwnerIntent: Int = 0,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        @Suppress("DEPRECATION")
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            this.groupOwnerIntent = groupOwnerIntent
            wps.setup = WpsInfo.PBC
        }
        connect(config, onSuccess, onFailure)
    }

    /**
     * Connects to a nearby peer using [WifiP2pDevice].
     */
    fun connect(
        device: WifiP2pDevice,
        groupOwnerIntent: Int = 0,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        connectToDevice(device.deviceAddress, groupOwnerIntent, onSuccess, onFailure)
    }

    /**
     * Initiates connection using the given [WifiP2pConfig].
     */
    fun connect(
        config: WifiP2pConfig,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val manager = wifiP2pManager ?: run {
            onFailure?.invoke(P2P_UNSUPPORTED)
            return
        }
        val channel = wifiP2pChannel ?: run {
            onFailure?.invoke(BUSY)
            return
        }

        try {
            _connectionStatus.value = "Conectando a ${config.deviceAddress}..."
            Log.i(TAG, "Connecting to P2P device: ${config.deviceAddress}")
            manager.connect(channel, config, object : ActionListener {
                override fun onSuccess() {
                    _connectionStatus.value = "Conexión solicitada con éxito"
                    Log.i(TAG, "P2P connect initiated successfully to ${config.deviceAddress}")
                    onSuccess?.invoke()
                }

                override fun onFailure(reason: Int) {
                    _connectionStatus.value = "Fallo de conexión: ${getReasonText(reason)}"
                    Log.w(TAG, "P2P connect failed to ${config.deviceAddress}, reason=$reason")
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to P2P device", e)
            onFailure?.invoke(ERROR)
        }
    }

    /**
     * Cancels any pending P2P connection request.
     */
    fun cancelConnect(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            manager.cancelConnect(channel, object : ActionListener {
                override fun onSuccess() {
                    _connectionStatus.value = "Conexión cancelada"
                    Log.i(TAG, "cancelConnect succeeded")
                    onSuccess?.invoke()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "cancelConnect failed: reason=$reason")
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception in cancelConnect", e)
            onFailure?.invoke(ERROR)
        }
    }

    /**
     * Fully disconnects from current P2P group or cancels connection.
     */
    fun disconnect(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        removeGroup(
            onSuccess = {
                _connectionInfo.value = null
                _groupInfo.value = null
                _connectionStatus.value = "Desconectado"
                onSuccess?.invoke()
            },
            onFailure = {
                cancelConnect(onSuccess, onFailure)
            }
        )
    }

    // ============================================================
    //  GROUP MANAGEMENT
    // ============================================================

    /**
     * Creates an autonomous WiFi Direct Group with this device as Group Owner (GO).
     */
    fun createGroup(
        config: WifiP2pConfig? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val manager = wifiP2pManager ?: run {
            onFailure?.invoke(P2P_UNSUPPORTED)
            return
        }
        val channel = wifiP2pChannel ?: run {
            onFailure?.invoke(BUSY)
            return
        }

        try {
            _connectionStatus.value = "Creando grupo WiFi Direct..."
            val listener = object : ActionListener {
                override fun onSuccess() {
                    _connectionStatus.value = "Grupo WiFi Direct creado (GO)"
                    Log.i(TAG, "createGroup succeeded as Group Owner")
                    requestGroupInfo()
                    requestConnectionInfo()
                    onSuccess?.invoke()
                }

                override fun onFailure(reason: Int) {
                    _connectionStatus.value = "Error creando grupo: ${getReasonText(reason)}"
                    Log.w(TAG, "createGroup failed: reason=$reason")
                    onFailure?.invoke(reason)
                }
            }

            if (config != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                manager.createGroup(channel, config, listener)
            } else {
                manager.createGroup(channel, listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating P2P group", e)
            onFailure?.invoke(ERROR)
        }
    }

    /**
     * Removes the current WiFi Direct Group.
     */
    fun removeGroup(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            manager.removeGroup(channel, object : ActionListener {
                override fun onSuccess() {
                    _connectionStatus.value = "Grupo P2P cerrado"
                    _connectionInfo.value = null
                    _groupInfo.value = null
                    notifyGroupChanged(null)
                    notifyDisconnection()
                    Log.i(TAG, "removeGroup succeeded")
                    onSuccess?.invoke()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "removeGroup failed: reason=$reason")
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing P2P group", e)
            onFailure?.invoke(ERROR)
        }
    }

    /**
     * Requests latest Connection Info (Group formed, isGroupOwner, GroupOwner IP).
     */
    fun requestConnectionInfo(listener: ConnectionInfoListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            manager.requestConnectionInfo(channel) { info ->
                onConnectionInfoAvailable(info)
                listener?.onConnectionInfoAvailable(info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception requesting connection info", e)
        }
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        _connectionInfo.value = info
        if (info != null && info.groupFormed) {
            val role = if (info.isGroupOwner) "Group Owner (GO)" else "Client"
            val ownerIp = info.groupOwnerAddress?.hostAddress ?: "desconocida"
            _connectionStatus.value = "Conectado ($role, GO IP: $ownerIp)"
            Log.i(TAG, "P2P Connection: groupFormed=true, isGO=${info.isGroupOwner}, ownerIp=$ownerIp")
            notifyConnectionChanged(info)
        }
    }

    /**
     * Requests latest Group Info (SSID, passphrase, clients list).
     */
    fun requestGroupInfo(listener: GroupInfoListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            manager.requestGroupInfo(channel) { group ->
                onGroupInfoAvailable(group)
                listener?.onGroupInfoAvailable(group)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception requesting group info", e)
        }
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        _groupInfo.value = group
        Log.d(TAG, "Group info: networkName=${group?.networkName}, clients=${group?.clientList?.size ?: 0}")
        notifyGroupChanged(group)
    }

    // ============================================================
    //  DEVICE NAME & SERVICE DISCOVERY (DNS-SD)
    // ============================================================

    /**
     * Changes local WiFi Direct device name via hidden reflection API.
     */
    fun setDeviceName(name: String, listener: ActionListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            val method = manager.javaClass.getMethod(
                "setDeviceName",
                Channel::class.java,
                String::class.java,
                ActionListener::class.java
            )
            method.invoke(manager, channel, name, listener)
            Log.i(TAG, "setDeviceName requested: $name")
        } catch (e: Exception) {
            Log.w(TAG, "setDeviceName not supported or failed on this OEM", e)
        }
    }

    /**
     * Sets up DNS-SD local service advertisement for zero-config mesh discovery.
     */
    fun setupDnsSdService(
        record: Map<String, String>,
        serviceInstance: String = SERVICE_INSTANCE_NAME,
        serviceType: String = SERVICE_TYPE
    ) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(serviceInstance, serviceType, record)
            manager.addLocalService(channel, serviceInfo, object : ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "DNS-SD local service added successfully: $serviceInstance")
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Failed to add DNS-SD local service: reason=$reason")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception setting up DNS-SD service", e)
        }
    }

    /**
     * Starts discovering DNS-SD mesh services from nearby nodes.
     */
    fun discoverServices(
        onDeviceFound: (serviceType: String, txtRecord: Map<String, String>, device: WifiP2pDevice) -> Unit
    ) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return

        try {
            manager.setDnsSdResponseListeners(
                channel,
                { instanceName, registrationType, srcDevice ->
                    Log.d(TAG, "DNS-SD service found: $instanceName ($registrationType) on ${srcDevice.deviceName}")
                },
                { fullDomainName, txtRecordMap, srcDevice ->
                    Log.d(TAG, "DNS-SD TXT record received from ${srcDevice.deviceName}: $txtRecordMap")
                    onDeviceFound(fullDomainName, txtRecordMap, srcDevice)
                }
            )

            val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
            manager.addServiceRequest(channel, serviceRequest, object : ActionListener {
                override fun onSuccess() {
                    manager.discoverServices(channel, object : ActionListener {
                        override fun onSuccess() {
                            Log.i(TAG, "DNS-SD discoverServices started successfully")
                        }

                        override fun onFailure(reason: Int) {
                            Log.w(TAG, "DNS-SD discoverServices failed: reason=$reason")
                        }
                    })
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Failed to add DNS-SD service request: reason=$reason")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception during discoverServices", e)
        }
    }

    // ============================================================
    //  LISTENERS
    // ============================================================

    fun addPeerListener(listener: (List<WifiP2pDevice>) -> Unit) {
        synchronized(peerListeners) { peerListeners.add(listener) }
        listener(_peers.value)
    }

    fun removePeerListener(listener: (List<WifiP2pDevice>) -> Unit) {
        synchronized(peerListeners) { peerListeners.remove(listener) }
    }

    private fun notifyPeersChanged(peers: List<WifiP2pDevice>) {
        val copy = synchronized(peerListeners) { peerListeners.toList() }
        copy.forEach { it(peers) }
    }

    fun addConnectionListener(listener: (WifiP2pInfo) -> Unit) {
        synchronized(connectionListeners) { connectionListeners.add(listener) }
        _connectionInfo.value?.let { listener(it) }
    }

    fun removeConnectionListener(listener: (WifiP2pInfo) -> Unit) {
        synchronized(connectionListeners) { connectionListeners.remove(listener) }
    }

    private fun notifyConnectionChanged(info: WifiP2pInfo) {
        val copy = synchronized(connectionListeners) { connectionListeners.toList() }
        copy.forEach { it(info) }
    }

    fun addGroupListener(listener: (WifiP2pGroup?) -> Unit) {
        synchronized(groupListeners) { groupListeners.add(listener) }
        listener(_groupInfo.value)
    }

    fun removeGroupListener(listener: (WifiP2pGroup?) -> Unit) {
        synchronized(groupListeners) { groupListeners.remove(listener) }
    }

    private fun notifyGroupChanged(group: WifiP2pGroup?) {
        val copy = synchronized(groupListeners) { groupListeners.toList() }
        copy.forEach { it(group) }
    }

    fun addDisconnectionListener(listener: () -> Unit) {
        synchronized(disconnectionListeners) { disconnectionListeners.add(listener) }
    }

    fun removeDisconnectionListener(listener: () -> Unit) {
        synchronized(disconnectionListeners) { disconnectionListeners.remove(listener) }
    }

    private fun notifyDisconnection() {
        val copy = synchronized(disconnectionListeners) { disconnectionListeners.toList() }
        copy.forEach { it() }
    }

    // ============================================================
    //  HELPERS
    // ============================================================

    fun getManager(): WifiP2pManager? = wifiP2pManager
    fun getChannel(): Channel? = wifiP2pChannel

    private fun getReasonText(reason: Int): String = when (reason) {
        P2P_UNSUPPORTED -> "No soportado en este dispositivo"
        ERROR -> "Error interno del controlador Wi-Fi"
        BUSY -> "Dispositivo ocupado"
        else -> "Código $reason"
    }

    fun destroy() {
        stopPeerDiscovery()
        unregisterReceiver()
    }
}
