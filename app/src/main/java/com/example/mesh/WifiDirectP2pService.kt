package com.example.mesh

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.*
import android.net.wifi.p2p.WifiP2pManager.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Android Service class to handle WiFi Direct peer discovery and connection management
 * backed by [P2PManager] and [WifiP2pManager].
 */
@SuppressLint("MissingPermission")
class WifiDirectP2pService : Service() {

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
            try {
                val intent = Intent(context, WifiDirectP2pService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot start WifiDirectP2pService", e)
            }
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val p2pCore: P2PManager by lazy { P2PManager.getInstance(applicationContext) }

    // Expose state flows from P2PManager
    val isWifiP2pEnabled: StateFlow<Boolean> get() = p2pCore.isWifiP2pEnabled
    val isDiscoveryActive: StateFlow<Boolean> get() = p2pCore.isDiscoveryActive
    val peers: StateFlow<List<WifiP2pDevice>> get() = p2pCore.peers
    val connectionInfo: StateFlow<WifiP2pInfo?> get() = p2pCore.connectionInfo
    val thisDevice: StateFlow<WifiP2pDevice?> get() = p2pCore.thisDevice
    val p2pGroup: StateFlow<WifiP2pGroup?> get() = p2pCore.groupInfo

    inner class LocalBinder : Binder() {
        fun getService(): WifiDirectP2pService = this@WifiDirectP2pService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            p2pCore.initialize()
            Log.i(TAG, "WifiDirectP2pService created with P2PManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing p2pCore in WifiDirectP2pService", e)
        }
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
        serviceScope.cancel()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "WifiDirectP2pService destroyed")
    }

    // ============================================================
    //  PEER DISCOVERY MANAGEMENT
    // ============================================================
    fun startPeerDiscovery(listener: ActionListener? = null) {
        p2pCore.discoverPeers(
            onSuccess = { listener?.onSuccess() },
            onFailure = { reason -> listener?.onFailure(reason) }
        )
    }

    fun stopPeerDiscovery(listener: ActionListener? = null) {
        p2pCore.stopPeerDiscovery(
            onSuccess = { listener?.onSuccess() },
            onFailure = { reason -> listener?.onFailure(reason) }
        )
    }

    fun requestPeers(listener: PeerListListener? = null) {
        p2pCore.requestPeers(listener)
    }

    // ============================================================
    //  CONNECTION MANAGEMENT
    // ============================================================
    fun connectToDevice(
        deviceAddress: String,
        groupOwnerIntent: Int = 0,
        listener: ActionListener? = null
    ) {
        p2pCore.connectToDevice(
            deviceAddress = deviceAddress,
            groupOwnerIntent = groupOwnerIntent,
            onSuccess = { listener?.onSuccess() },
            onFailure = { reason -> listener?.onFailure(reason) }
        )
    }

    fun connect(config: WifiP2pConfig, listener: ActionListener? = null) {
        p2pCore.connect(
            config = config,
            onSuccess = { listener?.onSuccess() },
            onFailure = { reason -> listener?.onFailure(reason) }
        )
    }

    fun cancelConnect(listener: ActionListener? = null) {
        p2pCore.cancelConnect(
            onSuccess = { listener?.onSuccess() },
            onFailure = { reason -> listener?.onFailure(reason) }
        )
    }

    fun createGroup(listener: ActionListener? = null) {
        p2pCore.createGroup(
            config = null,
            onSuccess = { listener?.onSuccess() },
            onFailure = { reason -> listener?.onFailure(reason) }
        )
    }

    fun removeGroup(listener: ActionListener? = null) {
        p2pCore.removeGroup(
            onSuccess = { listener?.onSuccess() },
            onFailure = { reason -> listener?.onFailure(reason) }
        )
    }

    fun requestConnectionInfo(listener: ConnectionInfoListener? = null) {
        p2pCore.requestConnectionInfo(listener)
    }

    fun requestGroupInfo(listener: GroupInfoListener? = null) {
        p2pCore.requestGroupInfo(listener)
    }

    // ============================================================
    //  OBSERVERS
    // ============================================================
    fun addPeerListener(listener: (List<WifiP2pDevice>) -> Unit) {
        p2pCore.addPeerListener(listener)
    }

    fun removePeerListener(listener: (List<WifiP2pDevice>) -> Unit) {
        p2pCore.removePeerListener(listener)
    }

    fun addConnectionListener(listener: (WifiP2pInfo) -> Unit) {
        p2pCore.addConnectionListener(listener)
    }

    fun removeConnectionListener(listener: (WifiP2pInfo) -> Unit) {
        p2pCore.removeConnectionListener(listener)
    }

    fun addGroupListener(listener: (WifiP2pGroup?) -> Unit) {
        p2pCore.addGroupListener(listener)
    }

    fun removeGroupListener(listener: (WifiP2pGroup?) -> Unit) {
        p2pCore.removeGroupListener(listener)
    }

    fun getManager(): WifiP2pManager? = p2pCore.getManager()
    fun getChannel(): Channel? = p2pCore.getChannel()
    fun getP2pManager(): P2PManager = p2pCore
}
