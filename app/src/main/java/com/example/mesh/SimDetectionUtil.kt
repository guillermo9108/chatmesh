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
    private const val KEY_PHONE_SOURCE = "phone_source" // "SIM" | "MANUAL" | "API"

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
                if (sm != null) {
                    val subs = try { sm.activeSubscriptionInfoList } catch (_: SecurityException) { null }
                    if (!subs.isNullOrEmpty()) {
                        for (sub in subs) {
                            try {
                                val num = sm.getPhoneNumber(sub.subscriptionId)
                                if (isValidPhoneNumber(num)) return sanitizePhoneNumber(num!!)
                            } catch (_: Exception) {}
                        }
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
                if (sm != null) {
                    val subs = try { sm.activeSubscriptionInfoList } catch (_: SecurityException) { null }
                    if (!subs.isNullOrEmpty()) {
                        for (sub in subs) {
                            try {
                                val perSubTm = tm.createForSubscriptionId(sub.subscriptionId)
                                val num = perSubTm.line1Number
                                if (isValidPhoneNumber(num)) return sanitizePhoneNumber(num!!)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /** Verifica que el número parezca real (no vacío, no todo ceros, no dummy como +530000000, etc.). */
    fun isValidPhoneNumber(num: String?): Boolean {
        if (num.isNullOrBlank()) return false
        val trimmed = num.trim()
        if (trimmed.contains(":") || (trimmed.contains("-") && trimmed.count { it == '-' } >= 3)) {
            // Dirección MAC u otro formato no telefónico
            return false
        }
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 7 || digits.length > 15) return false
        if (digits.all { it == '0' }) return false
        if (digits.startsWith("00000")) return false

        // Rechazo explícito de +530000000 o variantes con ceros como número cubano dummy
        if (digits.startsWith("53")) {
            val national = digits.substring(2)
            if (national.length < 8 || national.all { it == '0' } || national.startsWith("000") || national.startsWith("0000")) {
                return false
            }
        }

        // Rechazar si todos los dígitos después de los 2 o 3 primeros son ceros (ej: +10000000, +3400000000)
        if (digits.length >= 6 && digits.takeLast(6).all { it == '0' }) return false
        if (digits.length > 3 && digits.substring(2).all { it == '0' }) return false
        if (digits.length > 4 && digits.substring(3).all { it == '0' }) return false

        // Rechazar secuencias de prueba conocidas
        if (digits == "1234567" || digits == "12345678" || digits == "0123456789" || digits == "1234567890") {
            return false
        }

        return true
    }

    /** Compara dos números de teléfono considerando código de país y últimos dígitos */
    fun isMatchingPhone(phone1: String?, phone2: String?): Boolean {
        if (phone1.isNullOrBlank() || phone2.isNullOrBlank()) return false
        if (phone1 == phone2) return true
        val digits1 = phone1.filter { it.isDigit() }
        val digits2 = phone2.filter { it.isDigit() }
        if (digits1.isNotEmpty() && digits1 == digits2) return true
        if (digits1.length >= 7 && digits2.length >= 7) {
            if (digits1.takeLast(7) == digits2.takeLast(7)) return true
        }
        return false
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

    /** Guarda el número ingresado manualmente por el usuario. */
    fun saveManualPhoneNumber(context: Context, phoneNumber: String) {
        val clean = sanitizePhoneNumber(phoneNumber)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_PHONE, clean)
            .putString(KEY_PHONE_SOURCE, "MANUAL")
            .putBoolean(KEY_IS_CONFIRMED, true)
            .apply()
    }

    /** Guarda el número obtenido de la API de registro (futuro). */
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

    /** ¿El usuario ya tiene un número guardado (de SIM, manual o API)? */
    fun hasAnyPhoneNumber(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val phone = prefs.getString(KEY_USER_PHONE, null)
        return !phone.isNullOrBlank()
    }

    /** Devuelve el número registrado por API (si existe). */
    fun getRegisteredPhoneNumber(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val source = prefs.getString(KEY_PHONE_SOURCE, null)
        if (source != "API") return null
        return prefs.getString(KEY_REGISTERED_PHONE, null)
    }

    /** Devuelve el número actual guardado (venga de SIM, manual o API). */
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
        val clean = if (isValidPhoneNumber(phoneNumber)) {
            sanitizePhoneNumber(phoneNumber)
        } else {
            "Node_${kotlin.math.abs(phoneNumber.hashCode() % 10000)}"
        }
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