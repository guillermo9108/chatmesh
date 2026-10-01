package com.example

/**
 * Configuración global de ChatMesh.
 *
 * REGISTRATION_API_URL apunta al backend PHP + MariaDB alojado en el
 * Synology NAS. Para pruebas locales usa la IP del NAS en la LAN.
 *
 * Cuando publiques el servicio por Internet (proxy inverso + HTTPS en el
 * Synology), cambia esta URL por la del dominio público:
 *   "https://tu-nombre.synology.me/chatmesh_api"
 */
object ChatMeshConfig {

    /**
     * URL base de la API de registro.
     * NO incluir barra al final.
     */
    const val REGISTRATION_API_URL: String = "http://192.168.43.101/chatmesh_api"

    /** Versión que se reporta al servidor. */
    const val APP_VERSION: String = "1.0"

    /** Timeout de conexión y lectura en ms. */
    const val HTTP_CONNECT_TIMEOUT_MS: Int = 15000
    const val HTTP_READ_TIMEOUT_MS: Int = 20000
}