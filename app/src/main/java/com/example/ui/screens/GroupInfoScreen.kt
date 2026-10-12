package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactEntity
import com.example.data.entity.GroupEntity
import com.example.data.entity.GroupMemberEntity
import com.example.ui.components.GroupAvatar
import com.example.ui.components.UserAvatar
import com.example.ui.theme.WhatsAppGreenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    group: GroupEntity,
    members: List<GroupMemberEntity>,
    contacts: List<ContactEntity>,
    myPhoneNumber: String,
    onBack: () -> Unit,
    onAddMember: (groupId: String, phone: String, name: String) -> Unit,
    onRemoveMember: (groupId: String, phone: String) -> Unit,
    onLeaveGroup: (groupId: String) -> Unit
) {
    BackHandler { onBack() }

    val amIAdmin = remember(members, myPhoneNumber, group) {
        group.createdByPhone == myPhoneNumber ||
                members.any { it.phoneNumber == myPhoneNumber && it.isAdmin }
    }

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<GroupMemberEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Info. del grupo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Cabecera del grupo
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GroupAvatar(
                        avatarUri = group.avatarUri,
                        groupName = group.name,
                        size = 96.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Grupo · ${members.size} miembros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (group.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = group.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }

            // Sección miembros
            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${members.size} miembros",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (amIAdmin) {
                        TextButton(onClick = { showAddMemberDialog = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Añadir")
                        }
                    }
                }
            }

            // Lista de miembros
            items(members, key = { it.phoneNumber }) { member ->
                val isMe = member.phoneNumber == myPhoneNumber
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        avatarUri = null,
                        displayName = member.displayName,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isMe) "${member.displayName} (Tú)" else member.displayName,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = member.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (member.isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = WhatsAppGreenPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "Admin",
                                color = WhatsAppGreenPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (amIAdmin && !isMe) {
                        IconButton(onClick = { memberToRemove = member }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Quitar miembro",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Salir del grupo
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLeaveConfirmDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Salir del grupo",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Diálogo confirmar salir del grupo
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("¿Salir del grupo?") },
            text = { Text("¿Deseas salir del grupo \"${group.name}\"? Ya no podrás enviar ni recibir mensajes aquí.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveGroup(group.groupId)
                        onBack()
                    }
                ) {
                    Text("Salir", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo confirmar quitar miembro
    memberToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Quitar a ${target.displayName}") },
            text = { Text("¿Seguro que deseas eliminar a este contacto del grupo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember(group.groupId, target.phoneNumber)
                        memberToRemove = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para añadir miembros
    if (showAddMemberDialog) {
        val existingPhones = members.map { it.phoneNumber }.toSet()
        val availableContacts = contacts.filter { !existingPhones.contains(it.phoneNumber) }

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Añadir al grupo") },
            text = {
                if (availableContacts.isEmpty()) {
                    Text("No hay contactos adicionales para añadir.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(availableContacts) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddMember(group.groupId, c.phoneNumber, c.displayName.ifBlank { c.phoneNumber })
                                        showAddMemberDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(avatarUri = c.avatarUri, displayName = c.displayName, size = 36.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(c.displayName.ifBlank { c.phoneNumber }, fontWeight = FontWeight.Medium)
                                    Text(c.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
