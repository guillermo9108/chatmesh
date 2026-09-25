package com.example.mesh

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object SimDetectionUtil {
    private const val PREFS_NAME = "chatmesh_prefs"
    private const val KEY_USER_PHONE = "sim_phone_number"
    private const val KEY_IS_CONFIRMED = "sim_phone_confirmed"

    fun getRealSimDetails(context: Context): SimCardInfo {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val carrier = telephonyManager?.networkOperatorName.orEmpty()
            val country = telephonyManager?.networkCountryIso.orEmpty().uppercase()
            var detectedNumber: String? = null

            try {
                val line1 = telephonyManager?.line1Number
                if (!line1.isNullOrBlank() && line1.trim().length >= 6) {
                    detectedNumber = sanitizePhoneNumber(line1)
                }
            } catch (_: SecurityException) {
            }

            if (detectedNumber == null) {
                try {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val subList = subManager?.activeSubscriptionInfoList
                    if (!subList.isNullOrEmpty()) {
                        val firstSub = subList[0]
                        val subNumber = firstSub.number
                        if (!subNumber.isNullOrBlank() && subNumber.trim().length >= 6) {
                            detectedNumber = sanitizePhoneNumber(subNumber)
                        }
                    }
                } catch (_: SecurityException) {
                }
            }

            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_USER_PHONE, null)
            val finalPhone = detectedNumber ?: saved

            return SimCardInfo(
                slotIndex = 0,
                carrierName = if (carrier.isNotBlank()) carrier else "Red Móvil",
                countryIso = if (country.isNotBlank()) country else "CU",
                phoneNumber = finalPhone,
                isNumberReadFromSim = detectedNumber != null,
                isSimPresent = telephonyManager?.simState == TelephonyManager.SIM_STATE_READY,
                simStateDescription = if (telephonyManager?.simState == TelephonyManager.SIM_STATE_READY) "Lista" else "Sin SIM"
            )
        } catch (e: Exception) {
            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_USER_PHONE, null)
            return SimCardInfo(phoneNumber = saved)
        }
    }

    fun detectSimPhoneNumber(context: Context): String {
        val details = getRealSimDetails(context)
        if (!details.phoneNumber.isNullOrBlank()) {
            return details.phoneNumber
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_PHONE, "") ?: ""
    }

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

    fun generateSsid(phoneNumber: String): String {
        val clean = if (phoneNumber.isNotBlank()) sanitizePhoneNumber(phoneNumber) else "+0000000000"
        return "ChatMesh_$clean"
    }
}
