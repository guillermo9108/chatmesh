package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*

data class AvatarPresetItem(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val gradient: List<Color>
)

object AvatarPresets {
    val presets = listOf(
        AvatarPresetItem(
            id = "preset:rocket",
            name = "Aventura",
            icon = Icons.Default.RocketLaunch,
            gradient = listOf(Color(0xFF3F51B5), Color(0xFF9C27B0))
        ),
        AvatarPresetItem(
            id = "preset:gamer",
            name = "Gamer",
            icon = Icons.Default.SportsEsports,
            gradient = listOf(Color(0xFF0091EA), Color(0xFF00E676))
        ),
        AvatarPresetItem(
            id = "preset:music",
            name = "Música",
            icon = Icons.Default.Headphones,
            gradient = listOf(Color(0xFFFF4081), Color(0xFF7C4DFF))
        ),
        AvatarPresetItem(
            id = "preset:sparkle",
            name = "Estrella",
            icon = Icons.Default.AutoAwesome,
            gradient = listOf(Color(0xFFFF9100), Color(0xFFFF1744))
        ),
        AvatarPresetItem(
            id = "preset:nature",
            name = "Eco",
            icon = Icons.Default.Eco,
            gradient = listOf(Color(0xFF00B0FF), Color(0xFF00E676))
        ),
        AvatarPresetItem(
            id = "preset:shield",
            name = "Seguro",
            icon = Icons.Default.Shield,
            gradient = listOf(Color(0xFF00897B), Color(0xFF1DE9B6))
        ),
        AvatarPresetItem(
            id = "preset:face",
            name = "Sonrisa",
            icon = Icons.Default.Face,
            gradient = listOf(Color(0xFFFFB300), Color(0xFFFF6D00))
        ),
        AvatarPresetItem(
            id = "preset:person",
            name = "Clásico",
            icon = Icons.Default.Person,
            gradient = listOf(WhatsAppTeal, WhatsAppGreenAccent)
        )
    )

    fun getPreset(id: String): AvatarPresetItem? = presets.firstOrNull { it.id == id }
}

@Composable
fun UserAvatar(
    avatarUri: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = size * 0.58f,
    fontSize: TextUnit = (size.value * 0.42f).sp,
    border: BorderStroke? = null
) {
    val initial = remember(displayName) {
        val trimmed = displayName.trim()
        if (trimmed.isNotEmpty() && trimmed.first().isLetterOrDigit()) {
            trimmed.first().uppercase()
        } else {
            ""
        }
    }

    val preset = remember(avatarUri) {
        if (avatarUri != null && avatarUri.startsWith("preset:")) {
            AvatarPresets.getPreset(avatarUri)
        } else {
            null
        }
    }

    val avatarModifier = modifier
        .size(size)
        .clip(CircleShape)
        .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)

    if (preset != null) {
        Box(
            modifier = avatarModifier.background(Brush.linearGradient(preset.gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = preset.icon,
                contentDescription = displayName,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    } else if (!avatarUri.isNullOrBlank()) {
        Box(
            modifier = avatarModifier.background(WhatsAppTeal.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatarUri,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (initial.isNotEmpty()) {
        val colorIndex = (displayName.hashCode() and 0x7FFFFFFF) % AVATAR_PALETTE.size
        val bgGradient = AVATAR_PALETTE[colorIndex]
        Box(
            modifier = avatarModifier.background(Brush.linearGradient(bgGradient)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Box(
            modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = displayName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

private val AVATAR_PALETTE = listOf(
    listOf(Color(0xFF00796B), Color(0xFF004D40)),
    listOf(Color(0xFF0288D1), Color(0xFF01579B)),
    listOf(Color(0xFF7B1FA2), Color(0xFF4A148C)),
    listOf(Color(0xFFC2185B), Color(0xFF880E4F)),
    listOf(Color(0xFFE64A19), Color(0xFFBF360C)),
    listOf(Color(0xFFF57C00), Color(0xFFE65100)),
    listOf(Color(0xFF388E3C), Color(0xFF1B5E20)),
    listOf(Color(0xFF512DA8), Color(0xFF311B92))
)
