package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactEntity
import com.example.mesh.MeshEngineState
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@Composable
fun IncomingCallHeadsUpBanner(
    contact: ContactEntity,
    engineState: MeshEngineState,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = engineState.isCallActive && engineState.isIncomingCall,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(onClick = onBannerClick)
                .testTag("incoming_call_heads_up_banner"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E2C34),
            shadowElevation = 8.dp,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(WhatsAppTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (engineState.isVideoCall) Icons.Default.Videocam else Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Caller info & WiFi Direct tag
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.displayName.ifBlank { contact.phoneNumber },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (engineState.isVideoCall) "📹 Videollamada WiFi Direct" else "📞 Llamada WiFi Direct P2P",
                        color = WhatsAppGreenAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action buttons: Decline and Accept
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline button (Red)
                    FilledIconButton(
                        onClick = onDeclineCall,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("heads_up_decline_button")
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Accept button (Green)
                    FilledIconButton(
                        onClick = onAcceptCall,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("heads_up_accept_button")
                    ) {
                        Icon(
                            if (engineState.isVideoCall) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Accept",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
