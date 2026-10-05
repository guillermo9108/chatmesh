package com.example.mesh

enum class VideoQuality(
    val title: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val maxFrameSize: Int,
    val jpegQuality: Int,
    val frameIntervalMs: Long
) {
    LOW(
        title = "Baja (Ahorro de batería)",
        subtitle = "320x240 • 12 FPS • Mínimo consumo y gran fluidez",
        width = 320,
        height = 240,
        maxFrameSize = 22_000,
        jpegQuality = 60,
        frameIntervalMs = 80L
    ),
    MEDIUM(
        title = "Media (Equilibrada)",
        subtitle = "480x360 • 18 FPS • Buena nitidez y movimiento fluido",
        width = 480,
        height = 360,
        maxFrameSize = 50_000,
        jpegQuality = 75,
        frameIntervalMs = 55L
    ),
    HIGH(
        title = "Alta (HD Nítida)",
        subtitle = "640x480 • 24 FPS • Alta resolución y colores vivos",
        width = 640,
        height = 480,
        maxFrameSize = 95_000,
        jpegQuality = 85,
        frameIntervalMs = 40L
    ),
    ULTRA(
        title = "Ultra (Máxima calidad 720p)",
        subtitle = "1280x720 • 30 FPS • Máxima definición y nitidez total",
        width = 1280,
        height = 720,
        maxFrameSize = 180_000,
        jpegQuality = 90,
        frameIntervalMs = 33L
    );

    companion object {
        fun fromName(name: String?): VideoQuality {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HIGH
        }
    }
}
