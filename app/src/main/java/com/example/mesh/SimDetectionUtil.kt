package com.example.mesh

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object SimDetectionUtil {
    private const val PREFS_NAME = "chatmesh_prefs"
    private const val KEY_USER_PHONE = "sim_phone_number"
    private const val KEY_IS_CONFIRMED = "sim_phone_confirmed"
    private const val KEY_REGISTERED_PHONE = "registered_phone_number"
    private const val KEY_REGISTERED_AT = "registered_at"
    private const val KEY_PHONE_SOURCE = "phone_source" // "SIM" | "API"

    // ============================================================
    //  DETECCIÓN REAL DEL NÚMERO DE TELÉFONO
    // ============================================================

    /**
     * Intenta obtener el número real de la línea del dispositivo.
     * Devuelve null si no se puede obtener ninguno válido.
     */
    fun detectRealPhoneNumber(context: Context): String? {
        val tm = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (_: Exception) { null } ?: return null

        // 1. API 33+ — getPhoneNumber por suscripción
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subs = try { sm?.activeSubscriptionInfoList } catch (_: SecurityException) { null }
                if (!subs.isNullOrEmpty()) {
                    for (sub in subs) {
                        try {
                            val num = sm.getPhoneNumber(sub.subscriptionId)
                            if (isValidPhoneNumber(num)) return sanitizePhoneNumber(num!!)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Legacy: line1Number
        try {
            val line1 = tm.line1Number
            if (isValidPhoneNumber(line1)) return sanitizePhoneNumber(line1!!)
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        // 3. API 24+: createForSubscriptionId por cada sub
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subs = try { sm?.activeSubscriptionInfoList } catch (_: SecurityException) { null }
                if (!subs.isNullOrEmpty()) {
                    for (sub in subs) {
                        try {
                            val perSubTm = tm.createForSubscriptionId(sub.subscriptionId)
                            val num = perSubTm.line1Number
                            if (isValidPhoneNumber(num)) return sanitizePhoneNumber(num!!)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /** Verifica que el número parezca real (no vacío, no todo ceros, etc.). */
    fun isValidPhoneNumber(num: String?): Boolean {
        if (num.isNullOrBlank()) return false
        val digits = num.filter { it.isDigit() }
        if (digits.length < 7) return false
        if (digits.all { it == '0' }) return false
        if (digits.startsWith("00000")) return false
        if (digits.length > 15) return false
        return true
    }

    // ============================================================
    //  PERSISTENCIA
    // ============================================================

    /** Guarda el número obtenido de la SIM. */
    fun saveUserSimPhoneNumber(context: Context, phoneNumber: String) {
        val clean = sanitizePhoneNumber(phoneNumber)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_PHONE, clean)
            .putString(KEY_PHONE_SOURCE, "SIM")
            .putBoolean(KEY_IS_CONFIRMED, true)
            .apply()
    }

    /** Guarda el número obtenido de la API de registro. */
    fun saveRegisteredPhoneNumber(context: Context, phoneNumber: String) {
        val clean = sanitizePhoneNumber(phoneNumber)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_PHONE, clean)
            .putString(KEY_REGISTERED_PHONE, clean)
            .putLong(KEY_REGISTERED_AT, System.currentTimeMillis())
            .putString(KEY_PHONE_SOURCE, "API")
            .putBoolean(KEY_IS_CONFIRMED, true)
            .apply()
    }

    /** Devuelve el número registrado por API (si existe). */
    fun getRegisteredPhoneNumber(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val source = prefs.getString(KEY_PHONE_SOURCE, null)
        if (source != "API") return null
        return prefs.getString(KEY_REGISTERED_PHONE, null)
    }

    /** Devuelve el número actual guardado (venga de SIM o API). */
    fun getCurrentPhoneNumber(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_PHONE, null)
    }

    fun getPhoneSource(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PHONE_SOURCE, null)
    }

    // ============================================================
    //  UTILIDADES
    // ============================================================

    fun sanitizePhoneNumber(raw: String): String {
        val clean = raw.trim().replace(" ", "").replace("-", "")
        return if (clean.startsWith("+")) clean else "+$clean"
    }

    fun generateSsid(phoneNumber: String): String {
        val clean = if (phoneNumber.isNotBlank()) sanitizePhoneNumber(phoneNumber) else "+0000000000"
        return "ChatMesh_$clean"
    }

    // ============================================================
    //  INFORMACIÓN DE SIM (para mostrar en UI)
    // ============================================================

    fun getRealSimDetails(context: Context): SimCardInfo {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val carrier = telephonyManager?.networkOperatorName.orEmpty()
            val country = telephonyManager?.networkCountryIso.orEmpty().uppercase()
            val detectedNumber = detectRealPhoneNumber(context)

            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_PHONE, null)
            val finalPhone = detectedNumber ?: saved

            return SimCardInfo(
                slotIndex = 0,
                carrierName = if (carrier.isNotBlank()) carrier else "Red Móvil",
                countryIso = if (country.isNotBlank()) country else "CU",
                phoneNumber = finalPhone,
                isNumberReadFromSim = detectedNumber != null,
                isSimPresent = telephonyManager?.simState == TelephonyManager.SIM_STATE_READY,
                simStateDescription = when (telephonyManager?.simState) {
                    TelephonyManager.SIM_STATE_READY -> "Lista"
                    TelephonyManager.SIM_STATE_ABSENT -> "Sin SIM"
                    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "Requiere PIN"
                    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "Requiere PUK"
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Bloqueada por red"
                    else -> "Desconocido"
                }
            )
        } catch (e: Exception) {
            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_PHONE, null)
            return SimCardInfo(phoneNumber = saved)
        }
    }

    // Compatibilidad con código antiguo que llamaba a detectSimPhoneNumber()
    @Deprecated("Usa detectRealPhoneNumber() que devuelve null si no hay número")
    fun detectSimPhoneNumber(context: Context): String {
        return detectRealPhoneNumber(context) ?: ""
    }
}