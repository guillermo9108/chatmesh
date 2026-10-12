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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
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
import com.example.data.entity.GroupEntity
import com.example.ui.components.GroupAvatar
import com.example.ui.components.OnlineUsersRow
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

sealed class ChatListItem {
    abstract val timestamp: Long
    abstract val id: String

    data class DirectChat(val contact: ContactEntity) : ChatListItem() {
        override val timestamp: Long get() = contact.lastMessageTime
        override val id: String get() = "contact_${contact.phoneNumber}"
    }

    data class GroupChat(val group: GroupEntity) : ChatListItem() {
        override val timestamp: Long get() = group.lastMessageTime
        override val id: String get() = "group_${group.groupId}"
    }
}

@Composable
fun ChatsTab(
    chats: List<ContactEntity>,
    groups: List<GroupEntity> = emptyList(),
    onlineContacts: List<ContactEntity> = emptyList(),
    onChatClick: (ContactEntity) -> Unit,
    onGroupClick: (GroupEntity) -> Unit = {},
    filterChip: String = "Todos",
    searchQuery: String = "",
    onOpenContacts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val unifiedItems = remember(chats, groups, filterChip, searchQuery) {
        val directItems = chats.map { ChatListItem.DirectChat(it) }
        val groupItems = groups.map { ChatListItem.GroupChat(it) }

        var list: List<ChatListItem> = when (filterChip) {
            "Grupos" -> groupItems
            "No leídos" -> (directItems.filter { it.contact.unreadCount > 0 } +
                    groupItems.filter { it.group.unreadCount > 0 })
            "Favoritos" -> (directItems.filter { it.contact.unreadCount > 0 || it.contact.isConnected } +
                    groupItems.filter { it.group.unreadCount > 0 })
            "Malla P2P" -> directItems.filter { it.contact.isConnected || it.contact.isRegisteredInMesh }
            else -> directItems + groupItems
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { item ->
                when (item) {
                    is ChatListItem.DirectChat -> {
                        item.contact.displayName.lowercase().contains(q) ||
                                item.contact.phoneNumber.lowercase().contains(q) ||
                                (item.contact.lastMessageText?.lowercase()?.contains(q) == true)
                    }
                    is ChatListItem.GroupChat -> {
                        item.group.name.lowercase().contains(q) ||
                                (item.group.lastMessageText?.lowercase()?.contains(q) == true)
                    }
                }
            }
        }

        list.sortedByDescending { it.timestamp }
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

        if (unifiedItems.isEmpty()) {
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
                        text = if (searchQuery.isNotBlank()) "No se encontraron resultados" else "Sin chats activos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (searchQuery.isNotBlank())
                            "No hay conversaciones que coincidan con \"$searchQuery\"."
                        else
                            "Selecciona un contacto o crea un grupo para comenzar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

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
                items(unifiedItems, key = { it.id }) { item ->
                    when (item) {
                        is ChatListItem.DirectChat -> {
                            val contact = item.contact
                            val isUserOnline = contact.isConnected || onlineContacts.any { it.phoneNumber == contact.phoneNumber }
                            ChatItemRow(
                                contact = contact,
                                isOnline = isUserOnline,
                                onClick = { onChatClick(contact) }
                            )
                        }
                        is ChatListItem.GroupChat -> {
                            val group = item.group
                            GroupItemRow(
                                group = group,
                                onClick = { onGroupClick(group) }
                            )
                        }
                    }
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
                            text = "Tus mensajes personales y grupales están protegidos por la red malla P2P.",
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
fun GroupItemRow(
    group: GroupEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(
            avatarUri = group.avatarUri,
            groupName = group.name,
            size = 52.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (group.lastMessageTime > 0) {
                    val timeStr = formatWhatsAppTime(group.lastMessageTime)
                    Text(
                        text = timeStr,
                        fontSize = 12.sp,
                        fontWeight = if (group.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (group.unreadCount > 0) WhatsAppGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = group.lastMessageText ?: "Grupo creado",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (group.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WhatsAppGreenPrimary)
                            .padding(horizontal = 6.5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = group.unreadCount.toString(),
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
