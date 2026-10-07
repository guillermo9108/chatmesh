package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
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
import coil.compose.AsyncImage
import com.example.data.entity.ContactEntity
import com.example.ui.components.OnlineUsersRow
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalAppDimensions
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppGreenLight
import com.example.ui.theme.WhatsAppTeal
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsTab(
    chats: List<ContactEntity>,
    onlineContacts: List<ContactEntity> = emptyList(),
    onChatClick: (ContactEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = LocalAppDimensions.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("chats_tab_root")
    ) {
        // Fila horizontal de usuarios en línea (estilo Messenger)
        OnlineUsersRow(
            onlineContacts = onlineContacts,
            onContactClick = onChatClick
        )

        if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dims.screenPadding * 2),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = WhatsAppGreenLight.copy(alpha = 0.5f),
                        modifier = Modifier.size(dims.avatarCall * 0.6f)
                    )
                    Spacer(modifier = Modifier.height(dims.itemSpacing * 2))
                    Text(
                        text = "No tienes conversaciones activas",
                        fontWeight = FontWeight.Bold,
                        fontSize = dims.titleSize
                    )
                    Spacer(modifier = Modifier.height(dims.itemSpacing))
                    Text(
                        text = "Selecciona un contacto en la pestaña CONTACTOS o en los usuarios en línea de arriba para iniciar un chat directo sin internet.",
                        fontSize = dims.subtitleSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chats_list")
            ) {
                items(chats, key = { it.phoneNumber }) { contact ->
                    val isUserOnline = contact.isConnected || onlineContacts.any { it.phoneNumber == contact.phoneNumber }
                    ChatItemRow(
                        contact = contact,
                        isOnline = isUserOnline,
                        onClick = { onChatClick(contact) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = dims.avatarMedium + dims.screenPadding * 1.5f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatItemRow(
    contact: ContactEntity,
    isOnline: Boolean = false,
    onClick: () -> Unit
) {
    val dims = LocalAppDimensions.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dims.screenPadding, vertical = dims.listItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            UserAvatar(
                avatarUri = contact.avatarUri,
                displayName = contact.displayName.ifBlank { contact.phoneNumber },
                size = dims.avatarMedium
            )
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .size(dims.avatarMedium * 0.28f)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(1.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(WhatsAppGreenAccent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(dims.itemSpacing * 1.5f))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.displayName.ifBlank { "Contacto" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = dims.bodySize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contact.phoneNumber,
                        fontSize = dims.smallSize,
                        color = WhatsAppTeal,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (contact.lastMessageTime > 0) {
                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(Date(contact.lastMessageTime))
                    Text(
                        text = timeStr,
                        fontSize = dims.tinySize,
                        color = if (contact.unreadCount > 0) WhatsAppGreenAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contact.lastMessageText ?: "Toca para chatear",
                    fontSize = dims.smallSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (contact.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WhatsAppGreenAccent)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = contact.unreadCount.toString(),
                            fontSize = dims.tinySize,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
