package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactEntity
import com.example.mesh.MeshEngineState

@Composable
fun CallScreen(
    contact: ContactEntity,
    engineState: MeshEngineState,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationSeconds = engineState.callDurationSeconds
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val timerFormatted = String.format("%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F261E), Color(0xFF111B21), Color(0xFF0B141A))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Peer Info & Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(
                    text = contact.displayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contact.phoneNumber,
                    fontSize = 15.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (engineState.isVideoCall) "Videollamada P2P WiFi Direct" else "Llamada de audio P2P",
                    fontSize = 13.sp,
                    color = Color(0xFF00A884),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timerFormatted,
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Center: Big Pulsing Avatar / Video Preview Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 32.dp)
            ) {
                if (engineState.isVideoCall) {
                    // Simulated Video Feed
                    Box(
                        modifier = Modifier
                            .size(260.dp, 340.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1F2C34)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A884).copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.displayName.take(1).uppercase(),
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Transmisión de video P2P",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Enlace directo sin router",
                                color = Color(0xFF25D366),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Pulsing Avatar for Audio Call
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFF00A884).copy(alpha = 0.15f))
                    )

                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF008069)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.displayName.take(1).uppercase(),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom Controls Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speakerphone toggle
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (engineState.isSpeakerOn) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = if (engineState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = "Altavoz",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Mute Microphone toggle
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (engineState.isMicMuted) Color.Red.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = if (engineState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Silenciar micrófono",
                            tint = if (engineState.isMicMuted) Color.Red else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Switch camera for video call
                    if (engineState.isVideoCall) {
                        IconButton(
                            onClick = { /* Switch camera */ },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Cambiar cámara",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // End Call (Red circle button)
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Colgar llamada",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "🔒 Llamada cifrada de nodo a nodo (WiFi Direct)",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}
