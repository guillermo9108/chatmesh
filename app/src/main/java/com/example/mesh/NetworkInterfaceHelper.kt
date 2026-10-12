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
     * Devuelve MeshNetworkInfo(MeshTransport.HOTSPOT, ...) SOLO si este
     * dispositivo es el punto de acceso.
     *
     * Estrategia:
     * 1. Si hay conexión WiFi activa vía ConnectivityManager, NO somos AP.
     * 2. Solo considerar interfaces con nombres típicos de AP (ap, swlan, softap, tether).
     * 3. NO usar el rango de IP como criterio (192.168.43.x puede ser un router normal).
     */
    fun getHotspotInfo(context: Context? = null): MeshNetworkInfo? {
        try {
            // 1. Si estamos conectados como cliente WiFi, no somos AP
            if (context != null) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
                val isConnectedAsWifiClient = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (isConnectedAsWifiClient) {
                    Log.d(TAG, "Conectado como cliente WiFi: no somos AP")
                    return null
                }
            }

            // 2. Buscar interfaces que sugieran AP
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()

                val isApInterface = name.startsWith("ap") ||
                                    name.startsWith("swlan") ||
                                    name.startsWith("softap") ||
                                    name.startsWith("tether") ||
                                    name.startsWith("wlan1") ||
                                    name.startsWith("wlan2")

                if (!isApInterface) continue

                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        val broadcast = calculateBroadcastAddress(ip, 24)
                        Log.i(TAG, "Hotspot detectado en interfaz ${intf.name}: IP=$ip")
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
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Detecta si estamos conectados a una red WiFi LAN (router).
     * Prioriza ConnectivityManager sobre NetworkInterface para evitar falsos positivos.
     */
    fun getWifiLanInfo(context: Context): MeshNetworkInfo? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val network = cm.activeNetwork ?: return null
            val capabilities = cm.getNetworkCapabilities(network) ?: return null

            // Solo si es WiFi o Ethernet
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                return null
            }

            val linkProperties = cm.getLinkProperties(network)
            val interfaceName = linkProperties?.interfaceName.orEmpty()

            // Si la interfaz es P2P, no es LAN
            if (interfaceName.lowercase().startsWith("p2p")) return null

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

            if (localIp.isNullOrBlank()) return null

            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val rawSsid = wm?.connectionInfo?.ssid?.replace("\"", "")
            val ssid = if (rawSsid != null && rawSsid != "<unknown ssid>") rawSsid else "WiFi LAN"

            val broadcast = calculateBroadcastAddress(localIp!!, prefixLength)

            Log.i(TAG, "WiFi LAN detectado: SSID=$ssid, IP=$localIp, Gateway=$gateway")
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
     * Prioridad: HOTSPOT (si está activo manualmente) > WIFI_LAN > HOTSPOT local > WIFI_DIRECT > NONE
     */
    fun detectActiveTransport(context: Context, isHotspotActive: Boolean = false): MeshNetworkInfo? {
        // 1. Si el usuario activó el Hotspot manualmente, ese es el transporte
        if (isHotspotActive) {
            val hotspot = getHotspotInfo(context)
            if (hotspot != null) return hotspot
            return MeshNetworkInfo(
                transport = MeshTransport.HOTSPOT,
                localIp = "192.168.43.1",
                subnetBroadcast = "192.168.43.255",
                gateway = "192.168.43.1",
                ssid = "Hotspot Local"
            )
        }

        // 2. PRIORIDAD: Si estamos conectados como cliente a una red WiFi, es WiFi LAN
        //    (aunque no tenga internet, sigue siendo LAN)
        val lan = getWifiLanInfo(context)
        if (lan != null) return lan

        // 3. Hotspot local (solo si no hay WiFi cliente activa)
        val hotspot = getHotspotInfo(context)
        if (hotspot != null) return hotspot

        // 4. WiFi Direct P2P
        val p2p = getWifiDirectInfo()
        if (p2p != null) return p2p

        return null
    }

    fun detectActiveTransport(context: Context): MeshNetworkInfo? {
        return detectActiveTransport(context, false)
    }

    /**
     * Obtiene la IP del gateway de una red conectada (útil para saber
     * la IP del dispositivo que creó el hotspot al que estamos conectados).
     * Lee /proc/net/route (default route Destination 00000000 en hex little-endian).
     * Alternativa: DhcpInfo y LinkProperties.
     */
    fun getGatewayIp(context: Context? = null): String? {
        try {
            val file = java.io.File("/proc/net/route")
            if (file.exists() && file.canRead()) {
                val lines = file.readLines()
                for (line in lines) {
                    val tokens = line.trim().split(Regex("\\s+"))
                    if (tokens.size >= 3) {
                        val destination = tokens[1]
                        val gatewayHex = tokens[2]
                        if (destination == "00000000" && gatewayHex != "00000000") {
                            val gwLong = gatewayHex.toLongOrNull(16)
                            if (gwLong != null) {
                                val b1 = (gwLong and 0xFF).toInt()
                                val b2 = ((gwLong shr 8) and 0xFF).toInt()
                                val b3 = ((gwLong shr 16) and 0xFF).toInt()
                                val b4 = ((gwLong shr 24) and 0xFF).toInt()
                                val ip = "$b1.$b2.$b3.$b4"
                                if (ip != "0.0.0.0") return ip
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No se pudo leer /proc/net/route: ${e.message}")
        }

        if (context != null) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val net = cm?.activeNetwork
                val lp = cm?.getLinkProperties(net)
                lp?.routes?.forEach { route ->
                    val gw = route.gateway
                    if (gw is Inet4Address && !gw.isLoopbackAddress && gw.hostAddress != "0.0.0.0") {
                        return gw.hostAddress
                    }
                }
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val dhcp = wm?.dhcpInfo
                if (dhcp != null && dhcp.gateway != 0) {
                    val g = dhcp.gateway
                    return "${g and 0xFF}.${(g shr 8) and 0xFF}.${(g shr 16) and 0xFF}.${(g shr 24) and 0xFF}"
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error obteniendo gateway vía ConnectivityManager: ${e.message}")
            }
        }

        return null
    }
}
