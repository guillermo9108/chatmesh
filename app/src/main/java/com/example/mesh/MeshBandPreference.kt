package com.example.mesh

import android.content.Context

/**
 * Almacena y expone la preferencia de banda para WiFi Direct.
 * Por defecto: 2.4GHz (máxima compatibilidad).
 */
object MeshBandPreference {

    private const val PREFS_NAME = "chatmesh_prefs"
    private const val KEY_PREFER_5GHZ = "prefer_5ghz_wifi_direct"

    /** true = preferir 5GHz (menos interferencia, menor alcance).
     *  false = preferir 2.4GHz (por defecto, máxima compatibilidad). */
    fun prefer5Ghz(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFER_5GHZ, false)
    }

    fun setPrefer5Ghz(context: Context, prefer: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFER_5GHZ, prefer)
            .apply()
    }

    /**
     * Devuelve la banda a usar en WifiP2pConfig.
     * En API < 29 devuelve -1 (no soportado, usar default).
     */
    fun getBandConstant(context: Context): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return -1
        return if (prefer5Ghz(context)) {
            android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_BAND_5GHZ
        } else {
            android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_BAND_2GHZ
        }
    }
}
