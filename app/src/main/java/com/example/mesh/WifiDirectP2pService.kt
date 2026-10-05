package com.example.mesh

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.net.wifi.p2p.WifiP2pManager.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Service class to handle WiFi Direct peer discovery and connection management
 * using Android's WifiP2pManager.
 *
 * Responsibilities:
 * - Initializes and manages WifiP2pManager and WifiP2pManager.Channel
 * - Registers and dispatches broadcast intents for WiFi P2P state, peers, and connections
 * - Manages peer discovery lifecycle (start, stop, discovery state flow)
 * - Manages peer connections, group creation, group removal, and channel reconnections
 * - Exposes reactive StateFlows for UI and mesh engine consumption
 */
@SuppressLint("MissingPermission")
class WifiDirectP2pService : Service(), ChannelListener, PeerListListener, ConnectionInfoListener, GroupInfoListener {

    companion object {
        private const val TAG = "WifiDirectP2pService"

        const val ACTION_START_DISCOVERY = "com.example.mesh.action.START_P2P_DISCOVERY"
        const val ACTION_STOP_DISCOVERY = "com.example.mesh.action.STOP_P2P_DISCOVERY"
        const val ACTION_CREATE_GROUP = "com.example.mesh.action.CREATE_P2P_GROUP"
        const val ACTION_REMOVE_GROUP = "com.example.mesh.action.REMOVE_P2P_GROUP"

        @Volatile
        var instance: WifiDirectP2pService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, WifiDirectP2pService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    private val _thisDevice = MutableStateFlow<WifiP2pDevice?>(null)
    val thisDevice: StateFlow<WifiP2pDevice?> = _thisDevice.asStateFlow()

    private val _p2pGroup = MutableStateFlow<WifiP2pGroup?>(null)
    val p2pGroup: StateFlow<WifiP2pGroup?> = _p2pGroup.asStateFlow()

    // Listeners for external components (e.g. WiFiMeshEngine)
    private val peerListeners = mutableListOf<(List<WifiP2pDevice>) -> Unit>()
    private val connectionListeners = mutableListOf<(WifiP2pInfo) -> Unit>()
    private val groupListeners = mutableListOf<(WifiP2pGroup?) -> Unit>()

    inner class LocalBinder : Binder() {
        fun getService(): WifiDirectP2pService = this@WifiDirectP2pService
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(EXTRA_WIFI_STATE, -1)
                    val enabled = state == WIFI_P2P_STATE_ENABLED
                    _isWifiP2pEnabled.value = enabled
                    Log.i(TAG, "WiFi P2P State Changed: enabled=$enabled")
                }
                WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d(TAG, "WiFi P2P Peers Changed, requesting peers...")
                    requestPeers()
                }
                WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_NETWORK_INFO)
                    }
                    Log.d(TAG, "WiFi P2P Connection Changed: isConnected=${networkInfo?.isConnected}")
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                        requestGroupInfo()
                    } else {
                        _connectionInfo.value = null
                        _p2pGroup.value = null
                        notifyGroupChanged(null)
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
                    Log.d(TAG, "This Device Changed: name=${device?.deviceName}, addr=${device?.deviceAddress}")
                }
                WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val discoveryState = intent.getIntExtra(EXTRA_DISCOVERY_STATE, -1)
                    val active = discoveryState == WIFI_P2P_DISCOVERY_STARTED
                    _isDiscoveryActive.value = active
                    Log.d(TAG, "Discovery state changed: active=$active")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initP2p()
        registerP2pReceiver()
        Log.i(TAG, "WifiDirectP2pService creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DISCOVERY -> startPeerDiscovery()
            ACTION_STOP_DISCOVERY -> stopPeerDiscovery()
            ACTION_CREATE_GROUP -> createGroup()
            ACTION_REMOVE_GROUP -> removeGroup()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopPeerDiscovery()
        unregisterP2pReceiver()
        serviceScope.cancel()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "WifiDirectP2pService destruido")
    }

    // ============================================================
    //  CHANNEL & INITIALIZATION
    // ============================================================
    private fun initP2p() {
        try {
            wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            wifiP2pChannel = wifiP2pManager?.initialize(this, Looper.getMainLooper(), this)
            if (wifiP2pManager != null && wifiP2pChannel != null) {
                Log.i(TAG, "WifiP2pManager inicializado exitosamente con canal")
            } else {
                Log.w(TAG, "WifiP2pManager o Channel no disponibles en este dispositivo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando WifiP2pManager", e)
        }
    }

    override fun onChannelDisconnected() {
        Log.w(TAG, "Canal WiFi P2P desconectado, re-inicializando...")
        try {
            wifiP2pChannel = wifiP2pManager?.initialize(this, Looper.getMainLooper(), this)
        } catch (e: Exception) {
            Log.e(TAG, "Error al re-inicializar canal", e)
        }
    }

    private fun registerP2pReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
        registerReceiver(p2pReceiver, filter)
        isReceiverRegistered = true
    }

    private fun unregisterP2pReceiver() {
        if (!isReceiverRegistered) return
        try {
            unregisterReceiver(p2pReceiver)
        } catch (_: Exception) {}
        isReceiverRegistered = false
    }

    // ============================================================
    //  PEER DISCOVERY MANAGEMENT
    // ============================================================
    fun startPeerDiscovery(listener: ActionListener? = null) {
        val manager = wifiP2pManager ?: run {
            listener?.onFailure(P2P_UNSUPPORTED)
            return
        }
        val channel = wifiP2pChannel ?: run {
            listener?.onFailure(BUSY)
            return
        }
        try {
            manager.discoverPeers(channel, object : ActionListener {
                override fun onSuccess() {
                    _isDiscoveryActive.value = true
                    Log.i(TAG, "discoverPeers iniciado con éxito")
                    listener?.onSuccess()
                }

                override fun onFailure(reason: Int) {
                    _isDiscoveryActive.value = false
                    Log.w(TAG, "discoverPeers falló, motivo=$reason")
                    listener?.onFailure(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al iniciar discoverPeers", e)
            listener?.onFailure(ERROR)
        }
    }

    fun stopPeerDiscovery(listener: ActionListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            manager.stopPeerDiscovery(channel, object : ActionListener {
                override fun onSuccess() {
                    _isDiscoveryActive.value = false
                    Log.i(TAG, "stopPeerDiscovery éxito")
                    listener?.onSuccess()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "stopPeerDiscovery falló, motivo=$reason")
                    listener?.onFailure(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al detener discovery", e)
            listener?.onFailure(ERROR)
        }
    }

    fun requestPeers(listener: PeerListListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            manager.requestPeers(channel) { peers ->
                onPeersAvailable(peers)
                listener?.onPeersAvailable(peers)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando peers", e)
        }
    }

    override fun onPeersAvailable(peers: WifiP2pDeviceList?) {
        val list = peers?.deviceList?.toList() ?: emptyList()
        _peers.value = list
        Log.d(TAG, "Peers disponibles actualizados: ${list.size} dispositivos")
        notifyPeersChanged(list)
    }

    // ============================================================
    //  CONNECTION MANAGEMENT
    // ============================================================
    fun connectToDevice(
        deviceAddress: String,
        groupOwnerIntent: Int = 0,
        listener: ActionListener? = null
    ) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            this.groupOwnerIntent = groupOwnerIntent
        }
        connect(config, listener)
    }

    fun connect(config: WifiP2pConfig, listener: ActionListener? = null) {
        val manager = wifiP2pManager ?: run {
            listener?.onFailure(P2P_UNSUPPORTED)
            return
        }
        val channel = wifiP2pChannel ?: run {
            listener?.onFailure(BUSY)
            return
        }
        try {
            Log.i(TAG, "Conectando a dispositivo P2P: ${config.deviceAddress}")
            manager.connect(channel, config, object : ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "P2P connect iniciado con éxito hacia ${config.deviceAddress}")
                    listener?.onSuccess()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "P2P connect falló hacia ${config.deviceAddress}, motivo=$reason")
                    listener?.onFailure(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Excepción conectando a dispositivo P2P", e)
            listener?.onFailure(ERROR)
        }
    }

    fun cancelConnect(listener: ActionListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            manager.cancelConnect(channel, object : ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "cancelConnect éxito")
                    listener?.onSuccess()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "cancelConnect falló, motivo=$reason")
                    listener?.onFailure(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en cancelConnect", e)
        }
    }

    fun createGroup(listener: ActionListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            manager.createGroup(channel, object : ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "createGroup exitoso como Group Owner")
                    listener?.onSuccess()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "createGroup falló, motivo=$reason")
                    listener?.onFailure(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Excepción creando grupo P2P", e)
            listener?.onFailure(ERROR)
        }
    }

    fun removeGroup(listener: ActionListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            manager.removeGroup(channel, object : ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "removeGroup exitoso")
                    _connectionInfo.value = null
                    _p2pGroup.value = null
                    listener?.onSuccess()
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "removeGroup falló, motivo=$reason")
                    listener?.onFailure(reason)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Excepción removiendo grupo P2P", e)
            listener?.onFailure(ERROR)
        }
    }

    fun requestConnectionInfo(listener: ConnectionInfoListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            manager.requestConnectionInfo(channel) { info ->
                onConnectionInfoAvailable(info)
                listener?.onConnectionInfoAvailable(info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando connection info", e)
        }
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        _connectionInfo.value = info
        if (info != null) {
            Log.i(TAG, "P2P Connection Info: groupFormed=${info.groupFormed}, isGO=${info.isGroupOwner}, goIp=${info.groupOwnerAddress?.hostAddress}")
            notifyConnectionChanged(info)
        }
    }

    fun requestGroupInfo(listener: GroupInfoListener? = null) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        try {
            manager.requestGroupInfo(channel) { group ->
                onGroupInfoAvailable(group)
                listener?.onGroupInfoAvailable(group)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando group info", e)
        }
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        _p2pGroup.value = group
        Log.d(TAG, "P2P Group Info: networkName=${group?.networkName}, clients=${group?.clientList?.size ?: 0}")
        notifyGroupChanged(group)
    }

    // ============================================================
    //  OBSERVERS
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
        listener(_p2pGroup.value)
    }

    fun removeGroupListener(listener: (WifiP2pGroup?) -> Unit) {
        synchronized(groupListeners) { groupListeners.remove(listener) }
    }

    private fun notifyGroupChanged(group: WifiP2pGroup?) {
        val copy = synchronized(groupListeners) { groupListeners.toList() }
        copy.forEach { it(group) }
    }

    fun getManager(): WifiP2pManager? = wifiP2pManager
    fun getChannel(): Channel? = wifiP2pChannel
}
