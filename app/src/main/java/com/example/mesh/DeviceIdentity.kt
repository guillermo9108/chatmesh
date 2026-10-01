package com.example.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.security.MessageDigest

/**
 * Identidad única del dispositivo.
 *
 * En Android 10+ no se puede leer el IMEI sin ser app de sistema,
 * por lo que combinamos:
 *  - ANDROID_ID (estable, sobrevive reinstalación de app, cambia en reset de fábrica)
 *  - IMEI (si está disponible, previo a Android 10 o con permiso especial)
 *  - Modelo y fabricante (contexto)
 *
 * El resultado es un hash SHA-256 truncado a 32 chars, que se envía a la API.
 */
object DeviceIdentity {

    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun getImei(context: Context): String? {
        // Requiere READ_PHONE_STATE. En Android 10+ solo funciona para apps del sistema.
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val imei = tm?.imei
            if (imei.isNullOrBlank() || imei == "000000000000000") null else imei
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Firma de dispositivo que combina varios identificadores.
     * Estable entre reinstalaciones de la app (mismo ANDROID_ID) pero
     * cambia si se resetea el dispositivo (lo cual es aceptable).
     */
    fun getDeviceFingerprint(context: Context): String {
        val androidId = getAndroidId(context)
        val imei = getImei(context).orEmpty()
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val raw = "$androidId|$imei|$model|$manufacturer"
        return sha256(raw).take(32)
    }

    private fun sha256(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString().padStart(32, '0').take(32)
        }
    }
}