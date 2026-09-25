package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.MessageEntity
import com.example.ui.theme.*
import com.example.util.ImageMediaUtil
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatBubble(
    message: MessageEntity,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val isOutgoing = message.isOutgoing
    var isPlaying by remember { mutableStateOf(false) }

    val bgColor = if (isOutgoing) {
        if (isDarkTheme) BubbleSentDark else BubbleSentLight
    } else {
        if (isDarkTheme) BubbleReceivedDark else BubbleReceivedLight
    }

    val textColor = if (isDarkTheme) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val timeColor = if (isDarkTheme) Color(0xFF8696A0) else Color(0xFF667781)

    val timeString = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 64.dp, max = 300.dp)
                .clip(bubbleShape)
                .background(bgColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                when (message.mediaType) {
                    "IMAGE" -> {
                        val bitmap = remember(message.mediaBase64) {
                            message.mediaBase64?.let { ImageMediaUtil.base64ToBitmap(it) }
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Imagen",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else if (!message.mediaUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = message.mediaUri,
                                contentDescription = "Imagen",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        if (message.content.isNotBlank() && message.content != "Foto") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.content,
                                color = textColor,
                                fontSize = 15.sp
                            )
                        }
                    }
                    "AUDIO" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = WhatsAppTeal,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { isPlaying = !isPlaying }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = if (isPlaying) 0.5f else 0f,
                                    onValueChange = {},
                                    colors = SliderDefaults.colors(
                                        thumbColor = WhatsAppTeal,
                                        activeTrackColor = WhatsAppTeal
                                    )
                                )
                                Text(
                                    text = "${message.audioDurationSeconds}s",
                                    fontSize = 11.sp,
                                    color = timeColor
                                )
                            }
                        }
                    }
                    "FILE" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Archivo",
                                tint = WhatsAppTeal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.content,
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = message.content,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeString,
                        color = timeColor,
                        fontSize = 10.sp
                    )
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            "READ" -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Leído",
                                    tint = WhatsAppTickBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            "DELIVERED" -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Entregado",
                                    tint = WhatsAppTickGrey,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            "SENT" -> {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Enviado",
                                    tint = WhatsAppTickGrey,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Pendiente",
                                    tint = WhatsAppTickGrey,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
