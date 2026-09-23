package com.example.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object SimDetectionUtil {

    /**
     * Reads SIM phone number or provides a valid unique mobile number.
     * Generates SSID in the exact required format: "ChatMesh_+5351234567"
     */
    @SuppressLint("HardwareIds")
    fun detectSimPhoneNumber(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                // Try reading line 1 number if permission granted
                val number = try {
                    telephonyManager.line1Number
                } catch (_: SecurityException) {
                    null
                }

                if (!number.isNullOrBlank() && number.length >= 7) {
                    return sanitizePhoneNumber(number)
                }

                // If API 33+, try subscription manager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val subManager = context.getSystemService(SubscriptionManager::class.java)
                        val subId = SubscriptionManager.getDefaultSubscriptionId()
                        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                            val subNumber = subManager.getPhoneNumber(subId)
                            if (!subNumber.isNullOrBlank()) {
                                return sanitizePhoneNumber(subNumber)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            // Fallback for emulator or no SIM
        }

        // Return standard mobile number format (e.g. +5351234567)
        val prefs = context.getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("sim_phone_number", null)
        if (!saved.isNullOrBlank()) {
            return saved
        }

        // Generate a stable default mobile number for this device
        val generated = "+5351" + (100000..999999).random()
        prefs.edit().putString("sim_phone_number", generated).apply()
        return generated
    }

    fun sanitizePhoneNumber(raw: String): String {
        val clean = raw.trim().replace(" ", "").replace("-", "")
        return if (clean.startsWith("+")) clean else "+$clean"
    }

    /**
     * Generates SSID format: "ChatMesh_+5351234567"
     */
    fun generateSsid(phoneNumber: String): String {
        val clean = sanitizePhoneNumber(phoneNumber)
        return "ChatMesh_$clean"
    }
}
