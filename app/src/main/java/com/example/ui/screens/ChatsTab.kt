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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactEntity
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsTab(
    contacts: List<ContactEntity>,
    activeTypingPhone: String?,
    onContactClick: (ContactEntity) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(modifier = modifier.fillMaxSize()) {
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No tienes chats activos",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pulsa el botón verde para buscar contactos e iniciar un chat en la malla WiFi Direct.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts, key = { it.phoneNumber }) { contact ->
                    val isTyping = activeTypingPhone == contact.phoneNumber

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onContactClick(contact) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("chat_item_${contact.phoneNumber}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with online status badge
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A884).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.displayName.take(1).uppercase(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppTeal
                                )
                            }
                            if (contact.isConnected) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppGreenAccent)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contact.displayName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (contact.lastMessageTime > 0) {
                                    Text(
                                        text = timeFormatter.format(Date(contact.lastMessageTime)),
                                        fontSize = 12.sp,
                                        color = if (contact.unreadCount > 0) WhatsAppTeal else Color.Gray,
                                        fontWeight = if (contact.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isTyping) {
                                    Text(
                                        text = "escribiendo...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF00A884),
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Text(
                                        text = contact.lastMessageText ?: contact.statusText,
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (contact.unreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(WhatsAppGreenAccent)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${contact.unreadCount}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
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

        // WhatsApp Floating Action Button
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = WhatsAppGreenAccent,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_new_chat")
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Nuevo chat"
            )
        }
    }
}
