package com.example.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInterfaceHelper {
    private const val TAG = "NetworkInterfaceHelper"

    /**
     * Usa NetworkInterface.getNetworkInterfaces() y devuelve la primera IPv4
     * no loopback de la interfaz que coincida con el nombre indicado (ej. "p2p", "wlan", "ap").
     */
    fun getLocalIpForInterface(interfaceName: String): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                if (intf.name.lowercase().contains(interfaceName.lowercase())) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo IP para $interfaceName", e)
            null
        }
    }

    /**
     * Calcula la dirección de broadcast desde una IPv4 y prefijo.
     * Ejemplo: "192.168.1.50" con /24 → "192.168.1.255"
     */
    fun calculateBroadcastAddress(ip: String, prefixLength: Int = 24): String {
        return try {
            val parts = ip.split(".").map { it.toInt() }
            if (parts.size != 4) return "255.255.255.255"

            var ipNum = 0L
            for (p in parts) {
                ipNum = (ipNum shl 8) or (p.toLong() and 0xFF)
            }

            val mask = (-1L shl (32 - prefixLength)) and 0xFFFFFFFFL
            val bcastNum = ipNum or (mask.inv() and 0xFFFFFFFFL)

            val b1 = (bcastNum shr 24) and 0xFF
            val b2 = (bcastNum shr 16) and 0xFF
            val b3 = (bcastNum shr 8) and 0xFF
            val b4 = bcastNum and 0xFF

            "$b1.$b2.$b3.$b4"
        } catch (_: Exception) {
            "255.255.255.255"
        }
    }

    /**
     * Devuelve MeshNetworkInfo(MeshTransport.WIFI_DIRECT, ip, "192.168.49.255", "192.168.49.1", null)
     * si hay una interfaz p2p activa.
     */
    fun getWifiDirectInfo(): MeshNetworkInfo? {
        return try {
            val ip = getLocalIpForInterface("p2p") ?: return null
            Log.i(TAG, "WiFi Direct detectado: IP=$ip")
            MeshNetworkInfo(
                transport = MeshTransport.WIFI_DIRECT,
                localIp = ip,
                subnetBroadcast = "192.168.49.255",
                gateway = "192.168.49.1",
                ssid = null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Devuelve MeshNetworkInfo(MeshTransport.HOTSPOT, "192.168.43.1", "192.168.43.255", "192.168.43.1", null)
     * si el dispositivo es el AP o tiene interfaz hotspot activa.
     */
    fun getHotspotInfo(): MeshNetworkInfo? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (name.startsWith("p2p") || name.startsWith("rmnet") || name.startsWith("dummy")) continue

                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        val isAp = name.startsWith("ap") ||
                                   name.startsWith("swlan") ||
                                   name.startsWith("softap") ||
                                   name.startsWith("tether") ||
                                   (name.startsWith("wlan") && name != "wlan0") ||
                                   ip.startsWith("192.168.43.")

                        if (isAp) {
                            val broadcast = calculateBroadcastAddress(ip, 24)
                            Log.i(TAG, "Hotspot detectado en interfaz ${intf.name}: IP=$ip, Broadcast=$broadcast")
                            return MeshNetworkInfo(
                                transport = MeshTransport.HOTSPOT,
                                localIp = ip,
                                subnetBroadcast = broadcast,
                                gateway = ip,
                                ssid = "Punto de Acceso Móvil"
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Usa ConnectivityManager y WifiManager para obtener IP y SSID actuales.
     * Devuelve MeshNetworkInfo(MeshTransport.WIFI_LAN, ip, broadcast, gateway, ssid)
     * si hay conexión WiFi activa y NO es WiFi Direct.
     */
    fun getWifiLanInfo(context: Context): MeshNetworkInfo? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val network = cm.activeNetwork ?: return null
            val capabilities = cm.getNetworkCapabilities(network) ?: return null

            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null
            }

            val linkProperties = cm.getLinkProperties(network)
            val interfaceName = linkProperties?.interfaceName.orEmpty()
            if (interfaceName.lowercase().startsWith("p2p")) {
                return null
            }

            var localIp: String? = null
            var prefixLength = 24
            var gateway: String? = null

            linkProperties?.linkAddresses?.forEach { linkAddr ->
                val addr = linkAddr.address
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    localIp = addr.hostAddress
                    prefixLength = linkAddr.prefixLength
                }
            }

            linkProperties?.routes?.forEach { route ->
                val gw = route.gateway
                if (gw is Inet4Address && !gw.isLoopbackAddress) {
                    gateway = gw.hostAddress
                }
            }

            if (localIp.isNullOrBlank()) {
                localIp = getLocalIpForInterface("wlan")
            }

            if (localIp.isNullOrBlank()) return null

            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val rawSsid = wm?.connectionInfo?.ssid?.replace("\"", "")
            val ssid = if (rawSsid != null && rawSsid != "<unknown ssid>") rawSsid else "WiFi LAN"

            val broadcast = calculateBroadcastAddress(localIp!!, prefixLength)

            Log.i(TAG, "WiFi LAN detectado: SSID=$ssid, IP=$localIp, Broadcast=$broadcast")
            MeshNetworkInfo(
                transport = MeshTransport.WIFI_LAN,
                localIp = localIp!!,
                subnetBroadcast = broadcast,
                gateway = gateway,
                ssid = ssid
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error detectando WiFi LAN", e)
            null
        }
    }

    /**
     * Prioridad: WIFI_DIRECT > HOTSPOT > WIFI_LAN > NONE
     * Llama a las 3 funciones anteriores en orden y devuelve la primera no nula.
     */
    fun detectActiveTransport(context: Context, isHotspotActive: Boolean = false): MeshNetworkInfo? {
        val hotspot = getHotspotInfo()
        if (hotspot != null) return hotspot

        val p2p = getWifiDirectInfo()
        if (p2p != null) return p2p

        val lan = getWifiLanInfo(context)
        if (lan != null) return lan

        return null
    }

    fun detectActiveTransport(context: Context): MeshNetworkInfo? {
        return detectActiveTransport(context, false)
    }
}
