package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.WhatsAppGreenPrimary
import com.example.ui.theme.WhatsAppTeal

@Composable
fun GroupAvatar(
    avatarUri: String?,
    groupName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val shape = RoundedCornerShape(12.dp)
    val preset = remember(avatarUri) {
        if (avatarUri != null && avatarUri.startsWith("preset:")) {
            AvatarPresets.getPreset(avatarUri)
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = preset?.gradient ?: listOf(WhatsAppGreenPrimary, WhatsAppTeal)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            preset != null -> {
                Icon(
                    imageVector = preset.icon,
                    contentDescription = groupName,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
            !avatarUri.isNullOrBlank() -> {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = groupName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            groupName.isNotBlank() -> {
                val initial = groupName.trim().take(1).uppercase()
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = groupName,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}
