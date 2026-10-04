package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Descubrimiento de peers por Bluetooth Low Energy.
 *
 * Cada dispositivo se anuncia con un perfil (BleDeviceProfile) que incluye
 * su número de teléfono, batería, si tiene internet, etc.
 *
 * Al recibir perfiles de otros dispositivos, se calculan los scores y se
 * decide quién debe ser el Group Owner (GO) de WiFi Direct.
 */
class BleDiscoveryService(
    private val context: Context,
    private val myProfileProvider: () -> BleDeviceProfile,
    private val onPeerDiscovered: (BleDeviceProfile, Int) -> Unit, // peer, rssi
    private val onPeerLost: (String) -> Unit, // phoneNumber
    private val onShouldBecomeGo: () -> Unit
) {
    private val TAG = "BleDiscoveryService"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private val discoveredPeers = ConcurrentHashMap<String, BleDeviceProfile>()
    private val lastSeenTimestamps = ConcurrentHashMap<String, Long>()

    @Volatile private var isRunning = false

    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            val adv = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            val conn = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            if (scan != PackageManager.PERMISSION_GRANTED ||
                adv != PackageManager.PERMISSION_GRANTED ||
                conn != PackageManager.PERMISSION_GRANTED) return false
        }
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter ?: return false
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        if (!isSupported()) {
            Log.w(TAG, "BLE no soportado o sin permisos")
            return
        }
        isRunning = true

        val adapter = bluetoothAdapter ?: return
        bleAdvertiser = adapter.bluetoothLeAdvertiser
        bleScanner = adapter.bluetoothLeScanner

        startAdvertising()
        startScanning()
        startCleanupLoop()

        Log.i(TAG, "BLE discovery iniciado")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        isRunning = false
        try { advertiseCallback?.let { bleAdvertiser?.stopAdvertising(it) } } catch (_: Exception) {}
        try { scanCallback?.let { bleScanner?.stopScan(it) } } catch (_: Exception) {}
        advertiseCallback = null
        scanCallback = null
        discoveredPeers.clear()
        lastSeenTimestamps.clear()
        Log.i(TAG, "BLE discovery detenido")
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val advertiser = bleAdvertiser ?: return
        val profile = myProfileProvider()
        val payload = profile.toBytes()

        // Android limita el payload del advertisement a ~31 bytes. Si excede,
        // se envía solo un ID corto y el resto se obtiene al conectar por GATT.
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleRoles.SERVICE_UUID))
            .addServiceData(
                ParcelUuid(BleRoles.SERVICE_UUID),
                payload.copyOf(minOf(payload.size, 27)) // Limitar a 27 bytes
            )
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "BLE advertising activo")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising falló: $errorCode")
            }
        }

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando advertising", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val scanner = bleScanner ?: return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleRoles.SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan falló: $errorCode")
            }
        }

        try {
            scanner.startScan(filters, settings, scanCallback)
            Log.i(TAG, "BLE scanning activo")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando scan", e)
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val myPhone = try { myProfileProvider().phoneNumber } catch (_: Exception) { "" }

        // 1. Intentar leer el perfil desde el service data del advertisement
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(BleRoles.SERVICE_UUID))
        if (serviceData != null) {
            val json = String(serviceData, Charsets.UTF_8)
            val peer = tryParsePartialProfile(json)
            if (peer != null && peer.phoneNumber != myPhone) {
                processPeerProfile(peer, result.rssi)
                return
            }
        }

        // 2. Si no hay service data suficiente, conectar por GATT para obtener el perfil completo
        connectGattToReadFullProfile(result)
    }

    /**
     * Intenta parsear un perfil desde datos parciales de BLE.
     * Si falla, devuelve null para forzar la conexión GATT.
     */
    private fun tryParsePartialProfile(json: String): BleDeviceProfile? {
        return BleDeviceProfile.fromJson(json)
    }

    @SuppressLint("MissingPermission")
    private fun connectGattToReadFullProfile(result: ScanResult) {
        val device = result.device ?: return
        try {
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        try { gatt.close() } catch (_: Exception) {}
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val service = gatt.getService(BleRoles.SERVICE_UUID) ?: run {
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }
                    val characteristic = service.getCharacteristic(BleRoles.PROFILE_CHARACTERISTIC_UUID) ?: run {
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }
                    gatt.readCharacteristic(characteristic)
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    try {
                        @Suppress("DEPRECATION")
                        val bytes = characteristic.value ?: ByteArray(0)
                        val json = String(bytes, Charsets.UTF_8)
                        val peer = BleDeviceProfile.fromJson(json)
                        if (peer != null) {
                            processPeerProfile(peer, result.rssi)
                        }
                    } catch (_: Exception) {}
                    try { gatt.close() } catch (_: Exception) {}
                }
            }
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.w(TAG, "Error conectando GATT a ${device.address}", e)
        }
    }

    private fun processPeerProfile(peer: BleDeviceProfile, rssi: Int) {
        if (peer.phoneNumber.isBlank()) return

        discoveredPeers[peer.phoneNumber] = peer
        lastSeenTimestamps[peer.phoneNumber] = System.currentTimeMillis()

        onPeerDiscovered(peer, rssi)
        Log.d(TAG, "Peer BLE: ${peer.nickname} (${peer.phoneNumber}) score=${peer.score} rssi=$rssi")

        evaluateAndNegotiateRoles()
    }

    /**
     * Decide si este dispositivo debe convertirse en GO.
     * Regla: si mi score es el MÁS ALTO entre los peers conocidos + yo, soy GO.
     * En caso de empate, comparo mi número de teléfono con el del otro.
     */
    private fun evaluateAndNegotiateRoles() {
        val myProfile = try { myProfileProvider() } catch (_: Exception) { return }
        val myPhone = myProfile.phoneNumber
        val myScore = myProfile.score

        val allPeers = discoveredPeers.values.toList()
        if (allPeers.isEmpty()) return

        var amBest = true
        for (peer in allPeers) {
            if (peer.phoneNumber == myPhone) continue
            if (peer.score > myScore) {
                amBest = false
                break
            }
            if (peer.score == myScore && peer.phoneNumber < myPhone) {
                // Empate: gana el número "menor" (orden alfanumérico)
                amBest = false
                break
            }
        }

        if (amBest) {
            Log.i(TAG, "Soy el mejor candidato (score=$myScore), notificando para crear GO")
            onShouldBecomeGo()
        }
    }

    private fun startCleanupLoop() {
        scope.launch {
            while (isActive && isRunning) {
                delay(10_000)
                val now = System.currentTimeMillis()
                val toRemove = lastSeenTimestamps.filter { now - it.value > 30_000 }.keys.toList()
                for (phone in toRemove) {
                    discoveredPeers.remove(phone)
                    lastSeenTimestamps.remove(phone)
                    try { onPeerLost(phone) } catch (_: Exception) {}
                    Log.d(TAG, "Peer BLE perdido: $phone")
                }
            }
        }
    }

    fun getDiscoveredPeers(): List<BleDeviceProfile> = discoveredPeers.values.toList()

    fun cleanup() {
        stop()
        scope.cancel()
    }
}
