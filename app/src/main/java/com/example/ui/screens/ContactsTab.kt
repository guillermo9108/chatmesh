package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@Composable
fun ContactsTab(
    contacts: List<ContactEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onChatClick: (ContactEntity) -> Unit,
    onInviteClick: (ContactEntity) -> Unit,
    onAudioCallClick: (ContactEntity) -> Unit,
    onVideoCallClick: (ContactEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredContacts = if (searchQuery.isBlank()) {
        contacts
    } else {
        contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumber.contains(searchQuery)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Quick Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar nombre o número móvil...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = WhatsAppTeal
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = WhatsAppTeal,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contacts_search_input")
            )
        }

        // Header summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Contactos en tu dispositivo (${filteredContacts.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredContacts, key = { it.phoneNumber }) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (contact.isRegisteredInMesh) {
                                onChatClick(contact)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("contact_item_${contact.phoneNumber}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Contact Avatar
                    Box {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (contact.isRegisteredInMesh) WhatsAppTeal.copy(alpha = 0.15f)
                                    else Color.LightGray.copy(alpha = 0.4f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.displayName.take(1).uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (contact.isRegisteredInMesh) WhatsAppTeal else Color.DarkGray
                            )
                        }
                        if (contact.isConnected) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreenAccent)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = contact.phoneNumber,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = if (contact.isRegisteredInMesh) "✓ En la malla WiFi Direct" else "No registrado",
                            fontSize = 11.sp,
                            color = if (contact.isRegisteredInMesh) Color(0xFF008069) else Color.Gray,
                            fontWeight = if (contact.isRegisteredInMesh) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Buttons: "Chatear" / Calls or "Invitar"
                    if (contact.isRegisteredInMesh) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onAudioCallClick(contact) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Llamada P2P",
                                    tint = WhatsAppTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onVideoCallClick(contact) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Videollamada P2P",
                                    tint = WhatsAppTeal,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Button(
                                onClick = { onChatClick(contact) },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("chat_button_${contact.phoneNumber}")
                            ) {
                                Text(text = "Chatear", fontSize = 12.sp)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onInviteClick(contact) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("invite_button_${contact.phoneNumber}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = WhatsAppTeal
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Invitar", fontSize = 12.sp, color = WhatsAppTeal)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 78.dp),
                    thickness = 0.5.dp,
                    color = Color.LightGray.copy(alpha = 0.3f)
                )
            }
        }
    }
}
