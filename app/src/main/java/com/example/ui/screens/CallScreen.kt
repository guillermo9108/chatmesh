package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    contact: ContactEntity,
    engineState: MeshEngineState,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onDeclineWithMessage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showQuickMessagesSheet by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B141B),
                        Color(0xFF102A2B),
                        Color(0xFF081419)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("dedicated_call_management_ui")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: WiFi Direct Connection Tag & Caller Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                // WiFi Direct Link Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = WhatsAppGreenAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WiFi Direct P2P • Enlace Directo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Text(
                    text = contact.displayName.ifBlank { contact.phoneNumber },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = contact.phoneNumber,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                val statusText = when {
                    engineState.isIncomingCall -> {
                        if (engineState.isVideoCall) "📹 Videollamada entrante WiFi Direct" else "📞 Llamada de voz entrante WiFi Direct"
                    }
                    engineState.isCallConnected -> {
                        val mins = engineState.callDurationSeconds / 60
                        val secs = engineState.callDurationSeconds % 60
                        String.format("%02d:%02d • Conectado P2P", mins, secs)
                    }
                    else -> "Conectando enlace P2P..."
                }

                Text(
                    text = statusText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (engineState.isIncomingCall) WhatsAppGreenAccent else Color.White.copy(alpha = 0.85f)
                )
            }

            // Center Section: Animated Radar Pulse & Caller Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .padding(16.dp)
            ) {
                if (engineState.isIncomingCall || !engineState.isCallConnected) {
                    // Outer pulsing wave
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(WhatsAppGreenAccent.copy(alpha = waveAlpha))
                    )
                    // Middle pulsing wave
                    Box(
                        modifier = Modifier
                            .size(175.dp)
                            .scale(pulseScale * 0.92f)
                            .clip(CircleShape)
                            .background(WhatsAppTeal.copy(alpha = 0.35f))
                    )
                }

                // Core Avatar Circle
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(WhatsAppTeal)
                        .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (engineState.isVideoCall) Icons.Default.Videocam else Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(68.dp)
                    )
                }
            }

            // Bottom Section: DEDICATED CALL MANAGEMENT LAYOUT
            if (engineState.isIncomingCall) {
                // INCOMING CALL MANAGEMENT UI: Dedicated Accept & Decline button layout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp)
                ) {
                    // Quick Message Action Pill
                    if (onDeclineWithMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .padding(bottom = 24.dp)
                                .clickable { showQuickMessagesSheet = true }
                                .testTag("quick_decline_message_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Message,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Responder con mensaje",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // ACCEPT AND DECLINE BUTTON LAYOUT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // DECLINE BUTTON (Red)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.testTag("decline_call_container")
                        ) {
                            FloatingActionButton(
                                onClick = onEndCall,
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(76.dp)
                                    .testTag("decline_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline",
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Decline",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // ACCEPT BUTTON (Green with Pulse Animation)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.testTag("accept_call_container")
                        ) {
                            FloatingActionButton(
                                onClick = onAnswerCall,
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(76.dp)
                                    .scale(pulseScale)
                                    .testTag("accept_call_button")
                            ) {
                                Icon(
                                    imageVector = if (engineState.isVideoCall) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Accept",
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Accept",
                                color = WhatsAppGreenAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // ACTIVE / CONNECTED CALL MANAGEMENT UI: Mute, Speaker, Hangup controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute toggle button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onToggleMute,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(if (engineState.isMicMuted) Color.White else Color.White.copy(alpha = 0.18f))
                                    .testTag("toggle_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (engineState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Silenciar",
                                    tint = if (engineState.isMicMuted) Color.Black else Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (engineState.isMicMuted) "Silenciado" else "Silenciar",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }

                        // Hangup button (Red)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onEndCall,
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(72.dp)
                                    .testTag("end_call_button")
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "Finalizar llamada",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Finalizar",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }

                        // Speaker toggle button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onToggleSpeaker,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(if (engineState.isSpeakerOn) WhatsAppGreenAccent else Color.White.copy(alpha = 0.18f))
                                    .testTag("toggle_speaker_button")
                            ) {
                                Icon(
                                    imageVector = if (engineState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                    contentDescription = "Altavoz",
                                    tint = if (engineState.isSpeakerOn) Color.Black else Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (engineState.isSpeakerOn) "Altavoz activado" else "Altavoz",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Quick Decline Message Bottom Sheet
        if (showQuickMessagesSheet && onDeclineWithMessage != null) {
            val quickResponses = listOf(
                "No puedo hablar ahora. ¿Qué pasó?",
                "Te llamo enseguida.",
                "Estoy ocupado, hablemos por el chat P2P.",
                "En camino, hablamos luego."
            )
            ModalBottomSheet(
                onDismissRequest = { showQuickMessagesSheet = false },
                containerColor = Color(0xFF1E2C34),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Rechazar y enviar mensaje P2P:",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    quickResponses.forEach { msg ->
                        Surface(
                            onClick = {
                                showQuickMessagesSheet = false
                                onDeclineWithMessage(msg)
                            },
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = msg,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
