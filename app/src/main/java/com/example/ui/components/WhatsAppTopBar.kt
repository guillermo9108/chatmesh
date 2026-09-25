package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppTopBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSyncContactsClick: () -> Unit,
    onSimConfigClick: () -> Unit,
    onMeshSettingsClick: () -> Unit,
    onGitHubClick: () -> Unit,
    unreadChatsCount: Int,
    connectedNodesCount: Int,
    ssidName: String
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhatsAppTeal)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "ChatMesh",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (ssidName.isNotBlank()) {
                        Text(
                            text = ssidName,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = WhatsAppTeal,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            ),
            actions = {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.testTag("search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = Color.White
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Más opciones",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mi Perfil") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onProfileClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sincronizar Contactos") },
                            leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSyncContactsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Configuración SIM") },
                            leadingIcon = { Icon(Icons.Default.SimCard, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSimConfigClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ajustes de Malla P2P") },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMeshSettingsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Acerca de ChatMesh") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onGitHubClick()
                            }
                        )
                    }
                }
            }
        )

        // Tab bar
        val tabs = listOf("CHATS", "CONTACTOS", "NODOS MESH", "LLAMADAS")
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = WhatsAppTeal,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    height = 3.dp,
                    color = Color.White
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                            if (index == 0 && unreadChatsCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(WhatsAppGreenAccent)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = unreadChatsCount.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            } else if (index == 2 && connectedNodesCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = connectedNodesCount.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WhatsAppTeal
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
