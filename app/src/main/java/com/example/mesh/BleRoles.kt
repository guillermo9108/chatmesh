package com.example.mesh

/**
 * Constantes BLE usadas por el servicio de descubrimiento.
 */
object BleRoles {
    // UUID del servicio principal de ChatMesh
    val SERVICE_UUID: java.util.UUID = java.util.UUID.fromString(
        "0000c0de-0000-1000-8000-00805f9b34fb"
    )

    // UUID de la característica que publica el perfil del dispositivo
    val PROFILE_CHARACTERISTIC_UUID: java.util.UUID = java.util.UUID.fromString(
        "0000c0df-0000-1000-8000-00805f9b34fb"
    )

    // Identificadores internos
    const val DEVICE_NAME_PREFIX = "ChatMesh_"
}

/**
 * Perfil que cada dispositivo publica por BLE.
 */
data class BleDeviceProfile(
    val nodeId: String,
    val phoneNumber: String,
    val nickname: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val hasInternet: Boolean,
    val androidSdk: Int,
    val score: Int
) {
    fun toJson(): String {
        val obj = org.json.JSONObject()
        obj.put("nodeId", nodeId)
        obj.put("phone", phoneNumber)
        obj.put("name", nickname)
        obj.put("battery", batteryPercent)
        obj.put("charging", isCharging)
        obj.put("internet", hasInternet)
        obj.put("sdk", androidSdk)
        obj.put("score", score)
        return obj.toString()
    }

    fun toBytes(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    companion object {
        fun fromJson(json: String): BleDeviceProfile? {
            return try {
                val o = org.json.JSONObject(json)
                BleDeviceProfile(
                    nodeId = o.getString("nodeId"),
                    phoneNumber = o.getString("phone"),
                    nickname = o.optString("name", "Usuario"),
                    batteryPercent = o.optInt("battery", -1),
                    isCharging = o.optBoolean("charging", false),
                    hasInternet = o.optBoolean("internet", false),
                    androidSdk = o.optInt("sdk", 0),
                    score = o.optInt("score", 0)
                )
            } catch (e: Exception) { null }
        }
    }
}

/**
 * Calcula el score de un dispositivo según los criterios definidos.
 */
object BleScoreCalculator {
    fun calculate(
        batteryPercent: Int,
        isCharging: Boolean,
        hasInternet: Boolean,
        androidSdk: Int
    ): Int {
        var score = 0
        if (hasInternet) score += 100
        if (batteryPercent > 50) score += 50
        if (isCharging) score += 30
        if (batteryPercent in 20..50) score += 10
        if (androidSdk >= 29) score += 5
        return score
    }
}
