package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.CallEntity
import com.example.data.entity.ContactEntity
import com.example.ui.theme.WhatsAppTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsTab(
    calls: List<CallEntity>,
    onCallClick: (ContactEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Box(modifier = modifier.fillMaxSize()) {
        if (calls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sin registro de llamadas P2P",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Puedes hacer llamadas de audio y video directas entre dispositivos mediante WiFi Direct sin Internet.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(calls, key = { it.id }) { call ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("call_item_${call.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(WhatsAppTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = call.contactName.take(1).uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = WhatsAppTeal
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = call.contactName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (call.status == "MISSED") Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (call.status) {
                                    "MISSED" -> {
                                        Icon(
                                            imageVector = Icons.Default.PhoneMissed,
                                            contentDescription = "Perdida",
                                            tint = Color.Red,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    else -> {
                                        if (call.isOutgoing) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.CallMade,
                                                contentDescription = "Saliente",
                                                tint = Color(0xFF008069),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.CallReceived,
                                                contentDescription = "Entrante",
                                                tint = Color(0xFF128C7E),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${timeFormatter.format(Date(call.timestamp))} • ${call.durationSeconds}s",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                onCallClick(
                                    ContactEntity(
                                        phoneNumber = call.contactPhone,
                                        displayName = call.contactName,
                                        isRegisteredInMesh = true
                                    ),
                                    call.isVideo
                                )
                            },
                            modifier = Modifier.testTag("redial_button_${call.id}")
                        ) {
                            Icon(
                                imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Rellamar",
                                tint = WhatsAppTeal
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 80.dp),
                        thickness = 0.5.dp,
                        color = Color.LightGray.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
