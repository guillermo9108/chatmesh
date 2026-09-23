package com.example.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

data class SimCardInfo(
    val slotIndex: Int = 0,
    val carrierName: String = "",
    val countryIso: String = "",
    val phoneNumber: String? = null,
    val isNumberReadFromSim: Boolean = false,
    val isSimPresent: Boolean = false,
    val simStateDescription: String = "Desconocido"
)

object SimDetectionUtil {

    private const val PREFS_NAME = "chatmesh_prefs"
    private const val KEY_USER_PHONE = "sim_phone_number"
    private const val KEY_IS_CONFIRMED = "sim_phone_confirmed"

    /**
     * Reads actual SIM card information from SubscriptionManager and TelephonyManager.
     */
    @SuppressLint("HardwareIds")
    fun getRealSimDetails(context: Context): SimCardInfo {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simState = telephonyManager?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
            val simStateStr = when (simState) {
                TelephonyManager.SIM_STATE_READY -> "Lista y Activa"
                TelephonyManager.SIM_STATE_ABSENT -> "No detectada"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "Bloqueada por PIN"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "Bloqueada por PUK"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Bloqueo de red"
                else -> "Desconocido"
            }

            var carrier = telephonyManager?.simOperatorName.orEmpty()
            var country = telephonyManager?.simCountryIso.orEmpty().uppercase()
            var detectedPhone: String? = null
            var isFromSim = false
            var slot = 0
            var hasActiveSim = (simState == TelephonyManager.SIM_STATE_READY)

            // Try SubscriptionManager
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubs: List<SubscriptionInfo>? = subManager?.activeSubscriptionInfoList

                if (!activeSubs.isNullOrEmpty()) {
                    hasActiveSim = true
                    val primarySub = activeSubs.first()
                    slot = primarySub.simSlotIndex
                    if (primarySub.carrierName != null && primarySub.carrierName.isNotBlank()) {
                        carrier = primarySub.carrierName.toString()
                    }
                    if (!primarySub.countryIso.isNullOrBlank()) {
                        country = primarySub.countryIso.uppercase()
                    }

                    // 1. Try getPhoneNumber (API 33+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            val subNumber = subManager.getPhoneNumber(primarySub.subscriptionId)
                            if (!subNumber.isNullOrBlank() && subNumber.trim().length >= 6) {
                                detectedPhone = sanitizePhoneNumber(subNumber)
                                isFromSim = true
                            }
                        } catch (_: SecurityException) {}
                    }

                    // 2. Try SubscriptionInfo.number (older APIs)
                    if (detectedPhone == null && !primarySub.number.isNullOrBlank() && primarySub.number.trim().length >= 6) {
                        detectedPhone = sanitizePhoneNumber(primarySub.number)
                        isFromSim = true
                    }
                }
            } catch (_: SecurityException) {}

            // 3. Fallback to TelephonyManager line1Number
            if (detectedPhone == null) {
                try {
                    val line1 = telephonyManager?.line1Number
                    if (!line1.isNullOrBlank() && line1.trim().length >= 6) {
                        detectedPhone = sanitizePhoneNumber(line1)
                        isFromSim = true
                    }
                } catch (_: SecurityException) {}
            }

            // 4. If carrier didn't program EF_MSISDN, check user-confirmed real number
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPhone = prefs.getString(KEY_USER_PHONE, null)
            val finalPhone = detectedPhone ?: savedPhone

            return SimCardInfo(
                slotIndex = slot,
                carrierName = if (carrier.isNotBlank()) carrier else (if (hasActiveSim) "Operador Móvil" else "Sin SIM"),
                countryIso = country,
                phoneNumber = finalPhone,
                isNumberReadFromSim = isFromSim,
                isSimPresent = hasActiveSim,
                simStateDescription = simStateStr
            )
        } catch (_: Exception) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPhone = prefs.getString(KEY_USER_PHONE, null)
            return SimCardInfo(
                carrierName = "Operador Móvil",
                phoneNumber = savedPhone,
                isSimPresent = false,
                simStateDescription = "No accesible"
            )
        }
    }

    /**
     * Gets the real phone number (from SIM or user-confirmed).
     * If neither exists, returns a standard initial placeholder for user input.
     */
    fun detectSimPhoneNumber(context: Context): String {
        val details = getRealSimDetails(context)
        if (!details.phoneNumber.isNullOrBlank()) {
            return details.phoneNumber
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_USER_PHONE, null)
        if (!saved.isNullOrBlank()) {
            return saved
        }

        // Return empty so the app prompts the user for their real SIM number
        return ""
    }

    /**
     * Saves user's verified real phone number
     */
    fun saveUserSimPhoneNumber(context: Context, phoneNumber: String) {
        val clean = sanitizePhoneNumber(phoneNumber)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_PHONE, clean)
            .putBoolean(KEY_IS_CONFIRMED, true)
            .apply()
    }

    fun sanitizePhoneNumber(raw: String): String {
        val clean = raw.trim().replace(" ", "").replace("-", "")
        return if (clean.startsWith("+")) clean else "+$clean"
    }

    /**
     * Generates SSID format: "ChatMesh_+5351234567"
     */
    fun generateSsid(phoneNumber: String): String {
        val clean = if (phoneNumber.isNotBlank()) sanitizePhoneNumber(phoneNumber) else "+0000000000"
        return "ChatMesh_$clean"
    }
}
