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
        title = "Baja (Ahorro)",
        subtitle = "120x90 • 6 FPS • Muy fluida en conexiones débiles",
        width = 120,
        height = 90,
        maxFrameSize = 900,
        jpegQuality = 20,
        frameIntervalMs = 160L
    ),
    MEDIUM(
        title = "Media (Equilibrada)",
        subtitle = "160x120 • 10 FPS • Calidad estándar recomendada",
        width = 160,
        height = 120,
        maxFrameSize = 1400,
        jpegQuality = 30,
        frameIntervalMs = 100L
    ),
    HIGH(
        title = "Alta (Máxima nitidez)",
        subtitle = "240x180 • 15 FPS • Mayor nitidez y detalle",
        width = 240,
        height = 180,
        maxFrameSize = 2800,
        jpegQuality = 45,
        frameIntervalMs = 66L
    );

    companion object {
        fun fromName(name: String?): VideoQuality {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
        }
    }
}
