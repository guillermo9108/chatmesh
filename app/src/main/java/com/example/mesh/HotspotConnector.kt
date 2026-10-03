package com.example.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Conecta a una red WiFi específica usando WifiNetworkSpecifier (Android 10+).
 * El sistema muestra un diálogo la primera vez, pero el usuario solo tiene
 * que pulsar "Conectar"; no necesita escribir SSID ni contraseña.
 */
object HotspotConnector {

    private const val TAG = "HotspotConnector"

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToHotspot(
        context: Context,
        ssid: String,
        password: String,
        onConnected: (Network) -> Unit,
        onFailed: (String) -> Unit
    ) {
        try {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Conectado a hotspot $ssid")
                    onConnected(network)
                }
                override fun onUnavailable() {
                    Log.w(TAG, "No se pudo conectar a $ssid")
                    onFailed("El usuario canceló o la conexión falló")
                }
                override fun onLost(network: Network) {
                    Log.i(TAG, "Hotspot perdido: $ssid")
                }
            }, 60000) // timeout de 60s
        } catch (e: Exception) {
            Log.e(TAG, "Error conectando al hotspot", e)
            onFailed("Error: ${e.message}")
        }
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
