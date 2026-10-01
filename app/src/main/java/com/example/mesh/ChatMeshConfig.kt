package com.example

/**
 * Configuración global de ChatMesh.
 *
 * IMPORTANTE: reemplaza REGISTRATION_API_URL por la URL de tu backend
 * una vez lo despliegues (ver instrucciones del servidor al final).
 */
object ChatMeshConfig {
    /**
     * URL base de la API de registro.
     * NO incluir barra al final.
     * Ejemplo: "https://chatmesh-api.onrender.com"
     */
    const val REGISTRATION_API_URL: String = "https://chatmesh-api.onrender.com"

    /** Versión que se reporta al servidor. */
    const val APP_VERSION: String = "1.0"

    /** Timeout de conexión y lectura en ms. */
    const val HTTP_CONNECT_TIMEOUT_MS: Int = 15000
    const val HTTP_READ_TIMEOUT_MS: Int = 20000
}