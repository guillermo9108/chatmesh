package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactEntity
import com.example.mesh.MeshEngineState
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@Composable
fun CallScreen(
    contact: ContactEntity,
    engineState: MeshEngineState,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1B21))
            .testTag("call_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Call Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = contact.displayName.ifBlank { contact.phoneNumber },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = contact.phoneNumber,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                val statusText = when {
                    engineState.isIncomingCall -> {
                        if (engineState.isVideoCall) "📹 Videollamada entrante..." else "📞 Llamada entrante..."
                    }
                    engineState.isCallConnected -> {
                        val mins = engineState.callDurationSeconds / 60
                        val secs = engineState.callDurationSeconds % 60
                        String.format("%02d:%02d • Enlace WiFi Direct", mins, secs)
                    }
                    else -> "Llamando..."
                }

                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (engineState.isIncomingCall) WhatsAppGreenAccent else Color.White.copy(alpha = 0.9f)
                )
            }

            // Center Avatar / Pulsing Effect
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(if (engineState.isIncomingCall || !engineState.isCallConnected) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(WhatsAppTeal.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(WhatsAppTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (engineState.isVideoCall) Icons.Default.Videocam else Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Bottom Actions: DIFFERENT BUTTONS FOR INCOMING CALL VS ACTIVE CALL!
            if (engineState.isIncomingCall) {
                // INCOMING CALL: Prominent ANSWER (Green) and REJECT (Red) buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // REJECT BUTTON (Red)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onEndCall,
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(72.dp)
                                    .testTag("reject_call_button")
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "Rechazar",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Rechazar",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // ANSWER BUTTON (Green) - THIS DIRECTLY SOLVES THE USER ISSUE!
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onAnswerCall,
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(pulseScale)
                                    .testTag("answer_call_button")
                            ) {
                                Icon(
                                    if (engineState.isVideoCall) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Responder",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Responder",
                                color = WhatsAppGreenAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // ACTIVE OR OUTGOING CALL: Mute, Speaker, Hangup buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute button
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (engineState.isMicMuted) Color.White else Color.White.copy(alpha = 0.2f))
                                .testTag("toggle_mute_button")
                        ) {
                            Icon(
                                imageVector = if (engineState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Silenciar",
                                tint = if (engineState.isMicMuted) Color.Black else Color.White
                            )
                        }

                        // Hangup button (Red)
                        FloatingActionButton(
                            onClick = onEndCall,
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("end_call_button")
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Finalizar llamada",
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Speaker button
                        IconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (engineState.isSpeakerOn) WhatsAppGreenAccent else Color.White.copy(alpha = 0.2f))
                                .testTag("toggle_speaker_button")
                        ) {
                            Icon(
                                imageVector = if (engineState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = "Altavoz",
                                tint = if (engineState.isSpeakerOn) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
