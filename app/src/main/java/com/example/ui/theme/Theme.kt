package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppGreenLight,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF6CF8B8),
    secondary = WhatsAppGreenAccent,
    onSecondary = Color(0xFF00391A),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFE1E3E5),
    onSurface = Color(0xFFE1E3E5),
    onSurfaceVariant = Color(0xFFC3C7CE)
)

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8D8),
    onPrimaryContainer = Color(0xFF002113),
    secondary = WhatsAppGreenAccent,
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F2F5),
    onBackground = Color(0xFF111B21),
    onSurface = Color(0xFF111B21),
    onSurfaceVariant = Color(0xFF54656F)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep authentic WhatsApp look
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
