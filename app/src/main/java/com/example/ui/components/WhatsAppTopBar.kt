package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WhatsAppTeal
import com.example.ui.theme.WhatsAppGreenAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppTopBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSyncContactsClick: () -> Unit,
    onToggleSimClick: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ChatMesh",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("app_title")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WhatsAppGreenAccent.copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = "Malla P2P",
                                tint = WhatsAppGreenAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "P2P MESH",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
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
                        text = { Text("Mi Perfil & SIM") },
                        onClick = {
                            menuExpanded = false
                            onProfileClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Actualizar Contactos") },
                        onClick = {
                            menuExpanded = false
                            onSyncContactsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Alternar Simulación de Malla") },
                        onClick = {
                            menuExpanded = false
                            onToggleSimClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🚀 GitHub Actions & APK") },
                        onClick = {
                            menuExpanded = false
                            onGitHubClick()
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = WhatsAppTeal
            )
        )

        // WhatsApp-styled tabs
        val tabs = listOf("CHATS", "MALLA P2P", "CONTACTOS", "LLAMADAS")
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
                    modifier = Modifier.testTag("tab_$index"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                color = if (selectedTabIndex == index) Color.White else Color.White.copy(alpha = 0.7f),
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                            if (index == 0 && unreadChatsCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$unreadChatsCount",
                                        color = WhatsAppTeal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (index == 1 && connectedNodesCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(WhatsAppGreenAccent)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$connectedNodesCount",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
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
