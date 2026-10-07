package com.example.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistema de dimensiones adaptativas.
 * Se calcula UNA VEZ por pantalla según el ancho del dispositivo.
 *
 * Rangos:
 *   COMPACT   < 360dp   → móviles pequeños (Cubot, Itel, Xiaomi Redmi 9A...)
 *   MEDIUM    360..599dp → móviles normales
 *   EXPANDED  >= 600dp  → tablets y plegables
 */
data class AppDimensions(
    val compact: Boolean,
    val medium: Boolean,
    val expanded: Boolean,

    // Tipografía
    val titleSize: TextUnit,
    val subtitleSize: TextUnit,
    val bodySize: TextUnit,
    val smallSize: TextUnit,
    val tinySize: TextUnit,
    val chatBubbleSize: TextUnit,
    val callTitleSize: TextUnit,

    // Avatares
    val avatarSmall: Dp,
    val avatarMedium: Dp,
    val avatarLarge: Dp,
    val avatarCall: Dp,

    // Paddings y spacings
    val screenPadding: Dp,
    val cardPadding: Dp,
    val listItemPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,

    // Top bar
    val topBarHeight: Dp,
    val tabFontSize: TextUnit,
    val tabPadding: PaddingValues,

    // Bottom bar / input
    val inputBarHeight: Dp,
    val sendButtonSize: Dp,
    val iconButtonSize: Dp,

    // Chat
    val chatBubbleMaxWidthFraction: Float,

    // Botones grandes
    val bigButtonHeight: Dp,
    val floatingActionButtonSize: Dp
)

@Composable
fun rememberAppDimensions(sizeClass: WindowSizeClass): AppDimensions {
    val widthClass = sizeClass.widthSizeClass
    return remember(widthClass) {
        when (widthClass) {
            WindowWidthSizeClass.Compact -> AppDimensions(
                compact = true, medium = false, expanded = false,
                titleSize = 16.sp, subtitleSize = 13.sp, bodySize = 13.sp,
                smallSize = 11.sp, tinySize = 9.sp,
                chatBubbleSize = 13.sp, callTitleSize = 20.sp,
                avatarSmall = 34.dp, avatarMedium = 42.dp, avatarLarge = 54.dp, avatarCall = 100.dp,
                screenPadding = 12.dp, cardPadding = 10.dp, listItemPadding = 10.dp,
                itemSpacing = 6.dp, sectionSpacing = 12.dp,
                topBarHeight = 52.dp, tabFontSize = 10.sp,
                tabPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                inputBarHeight = 44.dp, sendButtonSize = 40.dp, iconButtonSize = 36.dp,
                chatBubbleMaxWidthFraction = 0.88f,
                bigButtonHeight = 44.dp, floatingActionButtonSize = 48.dp
            )
            WindowWidthSizeClass.Medium -> AppDimensions(
                compact = false, medium = true, expanded = false,
                titleSize = 20.sp, subtitleSize = 16.sp, bodySize = 15.sp,
                smallSize = 12.sp, tinySize = 10.sp,
                chatBubbleSize = 15.sp, callTitleSize = 28.sp,
                avatarSmall = 38.dp, avatarMedium = 48.dp, avatarLarge = 60.dp, avatarCall = 130.dp,
                screenPadding = 16.dp, cardPadding = 12.dp, listItemPadding = 12.dp,
                itemSpacing = 8.dp, sectionSpacing = 16.dp,
                topBarHeight = 60.dp, tabFontSize = 13.sp,
                tabPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                inputBarHeight = 50.dp, sendButtonSize = 48.dp, iconButtonSize = 40.dp,
                chatBubbleMaxWidthFraction = 0.82f,
                bigButtonHeight = 52.dp, floatingActionButtonSize = 56.dp
            )
            else -> AppDimensions( // Expanded
                compact = false, medium = false, expanded = true,
                titleSize = 24.sp, subtitleSize = 18.sp, bodySize = 16.sp,
                smallSize = 13.sp, tinySize = 11.sp,
                chatBubbleSize = 16.sp, callTitleSize = 32.sp,
                avatarSmall = 44.dp, avatarMedium = 56.dp, avatarLarge = 72.dp, avatarCall = 150.dp,
                screenPadding = 24.dp, cardPadding = 16.dp, listItemPadding = 14.dp,
                itemSpacing = 10.dp, sectionSpacing = 20.dp,
                topBarHeight = 64.dp, tabFontSize = 15.sp,
                tabPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                inputBarHeight = 56.dp, sendButtonSize = 52.dp, iconButtonSize = 44.dp,
                chatBubbleMaxWidthFraction = 0.70f,
                bigButtonHeight = 56.dp, floatingActionButtonSize = 60.dp
            )
        }
    }
}

/**
 * Composable helper que provee AppDimensions vía CompositionLocal.
 */
val LocalAppDimensions = androidx.compose.runtime.staticCompositionLocalOf<AppDimensions> {
    error("AppDimensions no inicializado. Envuelve la UI con ProvideAppDimensions.")
}

@Composable
fun ProvideAppDimensions(sizeClass: WindowSizeClass, content: @Composable () -> Unit) {
    val dims = rememberAppDimensions(sizeClass)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppDimensions provides dims,
        content = content
    )
}
