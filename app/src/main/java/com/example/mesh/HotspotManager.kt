package com.example.mesh

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Gestiona el hotspot local (LocalOnlyHotspot) desde Android 8+.
 * No requiere root. La contraseña es generada por el sistema.
 */
class HotspotManager(private val context: Context) {

    private val TAG = "HotspotManager"
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var activeSsid: String = ""
    private var activePassword: String = ""

    val isActive: Boolean get() = reservation != null
    val currentSsid: String get() = activeSsid
    val currentPassword: String get() = activePassword
    val currentGatewayIp: String get() = NetworkInterfaceHelper.getGatewayIp(context) ?: "192.168.43.1"

    var onHotspotStarted: ((ssid: String, password: String) -> Unit)? = null

    fun start(onReady: (ssid: String, password: String) -> Unit, onError: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onError("Hotspot requiere Android 8.0 o superior")
            return
        }
        if (reservation != null) {
            onReady(activeSsid, activePassword)
            return
        }
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null) {
                onError("Servicio Wi-Fi no disponible en el dispositivo")
                return
            }

            // Android exige que la Ubicación (GPS) esté habilitada para iniciar LocalOnlyHotspot
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm?.isLocationEnabled == true
            } else {
                lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
            }
            if (!isLocationEnabled) {
                onError("Debes activar la Ubicación (GPS) en los ajustes del teléfono. Android exige Ubicación encendida para habilitar el Hotspot local.")
                return
            }

            // Si Wi-Fi está apagado en el móvil, LocalOnlyHotspot falla automáticamente con código 2
            if (!wifiManager.isWifiEnabled) {
                try {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                } catch (_: Exception) {}
            }

            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            activeSsid = res.softApConfiguration.ssid ?: "ChatMesh"
                            activePassword = res.softApConfiguration.passphrase ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            activeSsid = res.wifiConfiguration?.SSID ?: "ChatMesh"
                            @Suppress("DEPRECATION")
                            activePassword = res.wifiConfiguration?.preSharedKey ?: ""
                        }
                        Log.i(TAG, "Hotspot iniciado con éxito: SSID=$activeSsid")
                        onHotspotStarted?.invoke(activeSsid, activePassword)
                        onReady(activeSsid, activePassword)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error leyendo credenciales del hotspot", e)
                        onError("Error leyendo datos del hotspot: ${e.message}")
                    }
                }

                override fun onStopped() {
                    Log.i(TAG, "Hotspot detenido por el sistema o por el usuario")
                    reservation = null
                    activeSsid = ""
                    activePassword = ""
                }

                override fun onFailed(reason: Int) {
                    Log.e(TAG, "Hotspot falló con código: $reason")
                    reservation = null
                    activeSsid = ""
                    activePassword = ""

                    val isEmulator = isEmulator()
                    val userMessage = when (reason) {
                        1 -> "No hay canales Wi-Fi disponibles para el punto de acceso (código 1)."
                        2 -> if (isEmulator) {
                            "Estás en un emulador virtual (IP 10.0.2.x). Los emuladores no poseen antena de radio física para emitir un Hotspot Wi-Fi. Debes probar el Hotspot en un teléfono móvil físico real. (Nota: el modo WiFi LAN ya está conectado y funcionando en este emulador con éxito)."
                        } else {
                            "El hardware de este teléfono no admitió el Hotspot automático por software (código 2). Toca 'Abrir Punto de Acceso del Teléfono' abajo para encender la Zona Wi-Fi nativa de Android."
                        }
                        3 -> "Modo incompatible: el chip Wi-Fi ya está en uso por otra conexión (código 3)."
                        4 -> "La política del dispositivo u operador restringe la creación del punto de acceso (código 4)."
                        else -> "No se pudo iniciar el hotspot local (código $reason)."
                    }
                    onError(userMessage)
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permiso denegado para iniciar hotspot", e)
            onError("Permiso denegado por el sistema: ${e.message}. Verifica permisos de Ubicación y Dispositivos Cercanos.")
        } catch (e: Exception) {
            Log.e(TAG, "Excepción iniciando hotspot", e)
            onError("Error iniciando hotspot: ${e.message}")
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    fun stop() {
        try {
            reservation?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando reserva de hotspot", e)
        }
        reservation = null
        activeSsid = ""
        activePassword = ""
    }
}
