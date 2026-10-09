package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactEntity
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*

@Composable
fun ContactsTab(
    contacts: List<ContactEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onContactClick: (ContactEntity) -> Unit,
    onInviteContact: (ContactEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = LocalAppDimensions.current

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            val q = searchQuery.trim()
            contacts.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                        it.phoneNumber.contains(q, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Cuadro de búsqueda (Search Box)
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = dims.screenPadding, vertical = dims.itemSpacing)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Buscar contactos por nombre o número...",
                            fontSize = dims.subtitleSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = WhatsAppGreenDark
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.testTag("clear_contacts_search")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar búsqueda",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WhatsAppGreenPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contacts_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "${filteredContacts.size} de ${contacts.size} contactos encontrados"
                        } else {
                            "${contacts.size} contactos disponibles"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    val inMeshCount = filteredContacts.count { it.isConnected || it.isRegisteredInMesh }
                    if (inMeshCount > 0) {
                        Text(
                            text = "$inMeshCount en Malla P2P",
                            fontSize = 12.sp,
                            color = WhatsAppGreenDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (filteredContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "No se encontraron contactos para \"$searchQuery\""
                        } else {
                            "No hay contactos sincronizados"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { onSearchQueryChange("") },
                            colors = ButtonDefaults.textButtonColors(contentColor = WhatsAppGreenDark)
                        ) {
                            Text("Limpiar filtro de búsqueda")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("contacts_list")
            ) {
                items(filteredContacts, key = { it.phoneNumber }) { contact ->
                    ContactItemRow(
                        contact = contact,
                        onClick = { onContactClick(contact) },
                        onConnect = { onInviteContact(contact) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun ContactItemRow(
    contact: ContactEntity,
    onClick: () -> Unit,
    onConnect: () -> Unit
) {
    val dims = LocalAppDimensions.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dims.screenPadding, vertical = dims.listItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUri = contact.avatarUri,
            displayName = contact.displayName.ifBlank { contact.phoneNumber },
            size = dims.avatarMedium
        )

        Spacer(modifier = Modifier.width(dims.itemSpacing * 1.5f))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName.ifBlank { "Contacto" },
                fontWeight = FontWeight.SemiBold,
                fontSize = dims.bodySize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = contact.phoneNumber,
                fontSize = dims.smallSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (contact.isConnected || contact.isRegisteredInMesh) {
            Surface(
                shape = CircleShape,
                color = WhatsAppChipSelectedLight
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = WhatsAppGreenDark,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "En Malla",
                        fontSize = dims.tinySize,
                        fontWeight = FontWeight.Bold,
                        color = WhatsAppGreenDark
                    )
                }
            }
        }
    }
}
