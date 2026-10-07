package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.StoryEntity
import com.example.data.entity.StorySeenEntity
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalAppDimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

private const val STORY_DURATION_MS = 5000L

@Composable
fun StoryViewerScreen(
    stories: List<StoryEntity>,
    onStoryViewed: (String) -> Unit,
    onDeleteStory: (String) -> Unit,
    onGetViewersFlow: (String) -> Flow<List<StorySeenEntity>>,
    onDismiss: () -> Unit,
    storyDurationMs: Long = STORY_DURATION_MS,
    modifier: Modifier = Modifier
) {
    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val dims = LocalAppDimensions.current
    var currentIndex by remember { mutableIntStateOf(0) }
    var timerKey by remember { mutableIntStateOf(0) }
    val currentStory = stories.getOrNull(currentIndex) ?: stories.first()

    var imageReady by remember(currentStory.storyId) {
        mutableStateOf(currentStory.mediaType != "IMAGE")
    }

    var showViewersDialog by remember { mutableStateOf(false) }
    val viewers by onGetViewersFlow(currentStory.storyId).collectAsState(initial = emptyList())

    val progress = remember { Animatable(0f) }

    // Marca como vista la historia actual de forma desacoplada
    LaunchedEffect(currentStory.storyId) {
        onStoryViewed(currentStory.storyId)
    }

    // Barra de progreso desacoplada: dura exactamente storyDurationMs
    LaunchedEffect(currentStory.storyId, timerKey, imageReady) {
        progress.snapTo(0f)
        if (!imageReady) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = storyDurationMs.toInt(), easing = LinearEasing)
        )
    }

    // Temporizador de auto-avance desacoplado con timerKey e imageReady
    LaunchedEffect(currentStory.storyId, timerKey, imageReady) {
        if (!imageReady) return@LaunchedEffect
        delay(storyDurationMs)
        if (currentIndex < stories.lastIndex) {
            currentIndex++
            timerKey++
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (currentStory.mediaType == "TEXT" && currentStory.backgroundColor != 0) {
                    Color(currentStory.backgroundColor)
                } else {
                    Color.Black
                }
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("story_viewer_screen")
    ) {
        // Contenido de la historia
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (currentStory.mediaType == "IMAGE") {
                val imageBitmap = remember(currentStory.mediaBase64) {
                    try {
                        val base64 = currentStory.mediaBase64 ?: run {
                            imageReady = true
                            return@remember null
                        }
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        imageReady = true
                        bmp
                    } catch (_: Exception) {
                        imageReady = true
                        null
                    }
                }

                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Estado",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                if (currentStory.content.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = if (currentStory.isMine) 60.dp else 24.dp)
                    ) {
                        Text(
                            text = currentStory.content,
                            color = Color.White,
                            fontSize = dims.bodySize,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                // Estado de Texto
                Text(
                    text = currentStory.content,
                    color = Color.White,
                    fontSize = if (dims.compact) 22.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                )
            }
        }

        // Zonas táctiles invisibles: Izquierda (anterior), Derecha (siguiente)
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentIndex > 0) {
                            currentIndex--
                            timerKey++
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentIndex < stories.lastIndex) {
                            currentIndex++
                            timerKey++
                        } else {
                            onDismiss()
                        }
                    }
            )
        }

        // Capa superior: barras de progreso e información del autor
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.screenPadding, vertical = 8.dp)
        ) {
            // Segmentos de progreso
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { idx, _ ->
                    val segmentProgress = when {
                        idx < currentIndex -> 1f
                        idx == currentIndex -> progress.value
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { segmentProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.35f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Información del autor y botón de cerrar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    avatarUri = currentStory.authorAvatarUri,
                    displayName = currentStory.authorName,
                    size = 36.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentStory.isMine) "Mi estado" else currentStory.authorName,
                        color = Color.White,
                        fontSize = dims.bodySize,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hace ${formatRelativeTime(currentStory.createdAt)}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = dims.tinySize
                    )
                }

                if (currentStory.isMine) {
                    IconButton(
                        onClick = {
                            onDeleteStory(currentStory.storyId)
                            if (stories.size <= 1) {
                                onDismiss()
                            } else if (currentIndex >= stories.lastIndex) {
                                currentIndex--
                                timerKey++
                            } else {
                                timerKey++
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar estado",
                            tint = Color.White
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White
                    )
                }
            }
        }

        // Si soy autor: indicador inferior de "Visto por N"
        if (currentStory.isMine) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clickable { showViewersDialog = true }
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Visto por ${viewers.size}",
                        color = Color.White,
                        fontSize = dims.smallSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Diálogo con lista de visualizadores
        if (showViewersDialog) {
            AlertDialog(
                onDismissRequest = { showViewersDialog = false },
                title = {
                    Text(
                        text = "Visto por ${viewers.size}",
                        fontSize = dims.subtitleSize,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    if (viewers.isEmpty()) {
                        Text(
                            text = "Aún nadie ha visto esta actualización.",
                            fontSize = dims.bodySize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            viewers.forEach { viewer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF25D366))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = viewer.viewerName.ifBlank { viewer.viewerPhone },
                                        fontSize = dims.bodySize,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showViewersDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val mins = diff / (60 * 1000L)
    val hours = mins / 60
    return when {
        mins < 1 -> "un momento"
        mins < 60 -> "$mins min"
        hours < 24 -> "$hours h"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
