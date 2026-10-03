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

                    val userMessage = when (reason) {
                        1 -> "No hay canales Wi-Fi disponibles para el punto de acceso (código 1)."
                        2 -> "El hardware o sistema no pudo iniciar el Hotspot local automático (código 2). Puede deberse a falta de soporte SoftAP en este dispositivo o Wi-Fi inactivo. Puedes encender el Punto de Acceso normal desde Ajustes del teléfono."
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
