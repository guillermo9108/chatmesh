package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalAppDimensions
import com.example.ui.theme.WhatsAppTeal
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CallsTab(
    calls: List<CallEntity>,
    onStartCall: (ContactEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = LocalAppDimensions.current

    if (calls.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(dims.screenPadding * 2),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = WhatsAppTeal.copy(alpha = 0.5f),
                    modifier = Modifier.size(dims.avatarCall * 0.6f)
                )
                Spacer(modifier = Modifier.height(dims.itemSpacing * 2))
                Text(
                    text = "No hay llamadas recientes",
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.titleSize
                )
                Spacer(modifier = Modifier.height(dims.itemSpacing))
                Text(
                    text = "Para llamar por voz o video a través de WiFi Direct sin internet, abre un chat y toca el ícono de llamada.",
                    fontSize = dims.subtitleSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("calls_list")
        ) {
            items(calls, key = { it.id }) { call ->
                CallItemRow(
                    call = call,
                    onCallClick = {
                        val contact = ContactEntity(
                            phoneNumber = call.contactPhone,
                            displayName = call.contactName
                        )
                        onStartCall(contact, call.isVideo)
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = dims.avatarMedium + dims.screenPadding * 1.5f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun CallItemRow(
    call: CallEntity,
    onCallClick: () -> Unit
) {
    val dims = LocalAppDimensions.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenPadding, vertical = dims.listItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUri = null,
            displayName = call.contactName.ifBlank { call.contactPhone },
            size = dims.avatarMedium
        )

        Spacer(modifier = Modifier.width(dims.itemSpacing * 1.5f))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = call.contactName.ifBlank { call.contactPhone },
                fontWeight = FontWeight.SemiBold,
                fontSize = dims.bodySize
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = if (call.isOutgoing) Icons.Default.CallMade else Icons.Default.CallReceived
                val tint = if (call.status == "MISSED") Color.Red else Color(0xFF25D366)
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val dateStr = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(call.timestamp))
                Text(
                    text = "$dateStr (${call.durationSeconds}s)",
                    fontSize = dims.smallSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onCallClick) {
            Icon(
                imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Llamar",
                tint = WhatsAppTeal
            )
        }
    }
}
