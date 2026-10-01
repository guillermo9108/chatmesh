package com.example.mesh

import com.example.ChatMeshConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP para la API de registro de dispositivos sin línea.
 *
 * Endpoint esperado:
 *   POST {REGISTRATION_API_URL}/api/v1/register
 *   Body JSON: { deviceId, androidId, imei, countryIso, carrierName, platform, appVersion }
 *   Respuesta 200: { phoneNumber, token?, issuedAt?, expiresAt? }
 */
object PhoneRegistrationApi {

    data class RegistrationResult(
        val phoneNumber: String,
        val token: String?,
        val issuedAt: Long,
        val expiresAt: Long
    )

    suspend fun register(
        deviceId: String,
        androidId: String,
        imei: String?,
        countryIso: String?,
        carrierName: String?
    ): Result<RegistrationResult> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val baseUrl = ChatMeshConfig.REGISTRATION_API_URL.trimEnd('/')
            val url = URL("$baseUrl/api/v1/register")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ChatMesh-Android/${ChatMeshConfig.APP_VERSION}")
                connectTimeout = ChatMeshConfig.HTTP_CONNECT_TIMEOUT_MS
                readTimeout = ChatMeshConfig.HTTP_READ_TIMEOUT_MS
                doOutput = true
                doInput = true
                useCaches = false
            }

            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("androidId", androidId)
                put("imei", imei ?: JSONObject.NULL)
                put("countryIso", countryIso ?: JSONObject.NULL)
                put("carrierName", carrierName ?: JSONObject.NULL)
                put("platform", "android")
                put("appVersion", ChatMeshConfig.APP_VERSION)
            }

            conn.outputStream.use { out ->
                out.write(body.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                val json = JSONObject(responseText)
                val phone = json.optString("phoneNumber", "")
                if (phone.isBlank()) {
                    Result.failure(IllegalStateException("API respondió sin phoneNumber"))
                } else {
                    Result.success(
                        RegistrationResult(
                            phoneNumber = phone,
                            token = json.optString("token", null),
                            issuedAt = json.optLong("issuedAt", System.currentTimeMillis()),
                            expiresAt = json.optLong("expiresAt", 0L)
                        )
                    )
                }
            } else {
                Result.failure(IllegalStateException("HTTP $code: ${responseText.take(200)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}