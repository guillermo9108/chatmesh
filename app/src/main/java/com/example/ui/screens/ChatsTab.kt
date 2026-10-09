package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.ui.components.OnlineUsersRow
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsTab(
    chats: List<ContactEntity>,
    onlineContacts: List<ContactEntity> = emptyList(),
    onChatClick: (ContactEntity) -> Unit,
    filterChip: String = "Todos",
    searchQuery: String = "",
    onOpenContacts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filteredChats = remember(chats, filterChip, searchQuery) {
        var list = chats

        // Filtrado por chip
        list = when (filterChip) {
            "No leídos" -> list.filter { it.unreadCount > 0 }
            "Favoritos" -> list.filter { it.unreadCount > 0 || it.isConnected }
            "Malla P2P" -> list.filter { it.isConnected || it.isRegisteredInMesh }
            else -> list
        }

        // Filtrado por texto de búsqueda
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.displayName.lowercase().contains(q) ||
                it.phoneNumber.lowercase().contains(q) ||
                (it.lastMessageText?.lowercase()?.contains(q) == true)
            }
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("chats_tab_root")
    ) {
        // Fila horizontal de contactos en línea estilo WhatsApp
        OnlineUsersRow(
            onlineContacts = onlineContacts,
            onContactClick = onChatClick
        )

        if (filteredChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = WhatsAppGreenPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = WhatsAppGreenPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No se encontraron chats" else "No tienes conversaciones activas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty())
                            "Intenta con otro nombre o número de teléfono."
                        else
                            "Inicia una conversación directa sin internet con tus amigos cercanos en la red malla.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onOpenContacts,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenPrimary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Iniciar chat", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chats_list"),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredChats, key = { it.phoneNumber }) { contact ->
                    val isUserOnline = contact.isConnected || onlineContacts.any { it.phoneNumber == contact.phoneNumber }
                    ChatItemRow(
                        contact = contact,
                        isOnline = isUserOnline,
                        onClick = { onChatClick(contact) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.6.dp
                    )
                }

                // Banner de cifrado y privacidad de WhatsApp
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tus mensajes personales están encriptados y viajan de extremo a extremo por la malla.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circular auténtico con indicador en línea
        Box(contentAlignment = Alignment.Center) {
            UserAvatar(
                avatarUri = contact.avatarUri,
                displayName = contact.displayName.ifBlank { contact.phoneNumber },
                size = 52.dp
            )
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(WhatsAppGreenPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Contenido del chat
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contact.displayName.ifBlank { contact.phoneNumber },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (contact.lastMessageTime > 0) {
                    val timeStr = formatWhatsAppTime(contact.lastMessageTime)
                    Text(
                        text = timeStr,
                        fontSize = 12.sp,
                        fontWeight = if (contact.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (contact.unreadCount > 0) WhatsAppGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Si el último mensaje es del usuario, mostrar doble palomita azul o gris
                    if (contact.lastMessageText != null) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = if (contact.unreadCount == 0) WhatsAppTickBlue else WhatsAppTickGrey,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = contact.lastMessageText ?: contact.phoneNumber,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (contact.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WhatsAppGreenPrimary)
                            .padding(horizontal = 6.5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.unreadCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatWhatsAppTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.DATE) == msgTime.get(Calendar.DATE) &&
        now.get(Calendar.MONTH) == msgTime.get(Calendar.MONTH) &&
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.DATE) - msgTime.get(Calendar.DATE) == 1 &&
        now.get(Calendar.MONTH) == msgTime.get(Calendar.MONTH) -> {
            "Ayer"
        }
        else -> {
            SimpleDateFormat("d/M/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
