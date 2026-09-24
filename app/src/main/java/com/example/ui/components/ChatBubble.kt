package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.entity.MessageEntity
import com.example.ui.theme.BubbleReceivedDark
import com.example.ui.theme.BubbleReceivedLight
import com.example.ui.theme.BubbleSentDark
import com.example.ui.theme.BubbleSentLight
import com.example.ui.theme.WhatsAppTickBlue
import com.example.ui.theme.WhatsAppTickGrey
import com.example.util.ImageMediaUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: MessageEntity,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isOutgoing = message.isOutgoing

    val bubbleColor = when {
        isOutgoing && isDarkTheme -> BubbleSentDark
        isOutgoing && !isDarkTheme -> BubbleSentLight
        !isOutgoing && isDarkTheme -> BubbleReceivedDark
        else -> BubbleReceivedLight
    }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = horizontalArrangement
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("chat_bubble_${message.id}")
        ) {
            Column {
                when (message.mediaType) {
                    "IMAGE" -> {
                        val base64Bitmap = remember(message.mediaBase64) {
                            message.mediaBase64?.let { ImageMediaUtil.base64ToBitmap(it) }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                        ) {
                            if (base64Bitmap != null) {
                                Image(
                                    bitmap = base64Bitmap.asImageBitmap(),
                                    contentDescription = "Foto real P2P",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (message.mediaUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(message.mediaUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Imagen",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color(0xFF2B3A42)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "📷 Imagen transmitida por malla P2P",
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        if (message.content.isNotBlank() && message.content != "Foto adjunta") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.content,
                                fontSize = 15.sp,
                                color = if (isDarkTheme) Color(0xFFE9EDEF) else Color(0xFF111B21)
                            )
                        }
                    }

                    "AUDIO" -> {
                        var isPlaying by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A884))
                                    .clickable { isPlaying = !isPlaying },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                // Visual audio waveform
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val bars = listOf(8, 14, 20, 10, 16, 22, 12, 18, 14, 24, 16, 12, 8, 16)
                                    bars.forEach { h ->
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(h.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(if (isPlaying) Color(0xFF00A884) else Color.Gray)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "00:${if (message.audioDurationSeconds < 10) "0" else ""}${message.audioDurationSeconds} • Audio P2P",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    "FILE" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.06f))
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = "Archivo",
                                tint = Color(0xFF008069),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.content,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDarkTheme) Color(0xFFE9EDEF) else Color(0xFF111B21)
                                )
                                Text(
                                    text = "Archivo transferido en malla",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = message.content,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = if (isDarkTheme) Color(0xFFE9EDEF) else Color(0xFF111B21)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Footer with Hop counter & Delivery status
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.hopCount > 0) {
                        Text(
                            text = "⚡${message.hopCount}s ",
                            fontSize = 10.sp,
                            color = Color(0xFF008069),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = if (isDarkTheme) Color(0xFF8696A0) else Color(0xFF667781)
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            "READ" -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Leído",
                                    tint = WhatsAppTickBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            "DELIVERED" -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Entregado",
                                    tint = WhatsAppTickGrey,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Enviado a la malla",
                                    tint = WhatsAppTickGrey,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
