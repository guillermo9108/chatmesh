package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppGreenPrimary,
    onPrimary = Color(0xFF00382B),
    primaryContainer = WhatsAppGreenPillDark,
    onPrimaryContainer = WhatsAppGreenPrimary,
    secondary = WhatsAppGreenTeal,
    onSecondary = Color.Black,
    background = WhatsAppBackgroundDark,
    onBackground = WhatsAppTextPrimaryDark,
    surface = WhatsAppSurfaceDark,
    onSurface = WhatsAppTextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = WhatsAppTextSecondaryDark,
    outlineVariant = WhatsAppDividerDark
)

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppGreenDark,
    onPrimary = Color.White,
    primaryContainer = WhatsAppGreenLight,
    onPrimaryContainer = Color(0xFF002018),
    secondary = WhatsAppGreenPrimary,
    onSecondary = Color.White,
    background = WhatsAppBackgroundLight,
    onBackground = WhatsAppTextPrimaryLight,
    surface = WhatsAppSurfaceLight,
    onSurface = WhatsAppTextPrimaryLight,
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = WhatsAppTextSecondaryLight,
    outlineVariant = WhatsAppDividerLight
)

@Composable
fun ChatMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ChatMeshTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
