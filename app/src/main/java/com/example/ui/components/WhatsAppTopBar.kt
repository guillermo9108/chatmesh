package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh.VideoQuality
import com.example.ui.theme.*

/**
 * Modern WhatsApp TopBar (Latest 2024-2026 Redesign):
 * - Clean surface background (white in light mode, dark surface in dark mode)
 * - Authentic bold "WhatsApp" green wordmark with offline mesh indicator
 * - Header action buttons: Camera, Network Status, Search, and 3-dots Menu
 * - Seamless search bar mode
 * - Modern Filter Chips row: "Todos", "No leídos", "Favoritos", "Malla P2P", "Grupos"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppTopBar(
    currentTabTitle: String = "WhatsApp",
    selectedFilterChip: String = "Todos",
    onFilterChipSelected: (String) -> Unit = {},
    showFilterChips: Boolean = true,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchActiveChange: (Boolean) -> Unit = {},
    onCameraClick: () -> Unit = {},
    onProfileClick: () -> Unit,
    onSyncContactsClick: () -> Unit,
    onSimConfigClick: () -> Unit,
    onMeshSettingsClick: () -> Unit,
    onGitHubClick: () -> Unit,
    onVideoQualityClick: () -> Unit = {},
    onShareAppClick: () -> Unit = {},
    currentVideoQuality: VideoQuality = VideoQuality.MEDIUM,
    connectedNodesCount: Int = 0,
    ssidName: String = "",
    isWifiDirectActive: Boolean = false,
    isHotspotActive: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isSearchActive) {
                // Modo búsqueda integrado con animación limpia
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onSearchActiveChange(false)
                            onSearchQueryChange("")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cerrar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("top_search_input"),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(WhatsAppGreenPrimary),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Buscar chats o mensajes...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar texto",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Barra de herramientas estándar de WhatsApp moderno
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "WhatsApp",
                                color = WhatsAppGreenPrimary,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Chip indicador sutil de red malla offline
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WhatsAppGreenPrimary.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onMeshSettingsClick() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isWifiDirectActive || isHotspotActive || connectedNodesCount > 0)
                                                    WhatsAppGreenPrimary
                                                else
                                                    Color.Gray
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (connectedNodesCount > 0) "$connectedNodesCount P2P" else "Malla",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WhatsAppGreenPrimary
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = WhatsAppGreenPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        // Botón de Cámara (icónico de WhatsApp)
                        IconButton(
                            onClick = onCameraClick,
                            modifier = Modifier.testTag("camera_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Cámara",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Botón de estado de Red Malla / Router
                        IconButton(
                            onClick = onMeshSettingsClick,
                            modifier = Modifier.testTag("network_mode_button")
                        ) {
                            Icon(
                                imageVector = when {
                                    isHotspotActive -> Icons.Default.WifiTethering
                                    isWifiDirectActive -> Icons.Default.Wifi
                                    else -> Icons.Default.Router
                                },
                                contentDescription = "Ajustes de Red",
                                tint = if (isWifiDirectActive || isHotspotActive) WhatsAppGreenPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Botón de Búsqueda
                        IconButton(
                            onClick = { onSearchActiveChange(true) },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Menú de 3 puntos
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Mi Perfil") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = WhatsAppGreenPrimary) },
                                    onClick = {
                                        menuExpanded = false
                                        onProfileClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sincronizar Contactos") },
                                    leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null, tint = WhatsAppGreenPrimary) },
                                    onClick = {
                                        menuExpanded = false
                                        onSyncContactsClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Configuración SIM") },
                                    leadingIcon = { Icon(Icons.Default.SimCard, contentDescription = null, tint = WhatsAppGreenPrimary) },
                                    onClick = {
                                        menuExpanded = false
                                        onSimConfigClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Calidad de Videollamada")
                                            Text(currentVideoQuality.title, fontSize = 11.sp, color = WhatsAppGreenPrimary)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = WhatsAppGreenPrimary) },
                                    onClick = {
                                        menuExpanded = false
                                        onVideoQualityClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Compartir Aplicación") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = WhatsAppGreenPrimary) },
                                    onClick = {
                                        menuExpanded = false
                                        onShareAppClick()
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                DropdownMenuItem(
                                    text = { Text("Ajustes de Red y Malla") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = WhatsAppGreenPrimary) },
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
            }

            // Chips de filtrado rápido estilo WhatsApp moderno (solo cuando no se busca y en vista de chats)
            if (showFilterChips && !isSearchActive) {
                val chips = listOf("Todos", "No leídos", "Favoritos", "Malla P2P", "Contactos")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { chipName ->
                        val isSelected = selectedFilterChip == chipName
                        val chipBg = if (isSelected) {
                            WhatsAppChipSelectedLight
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        }
                        val chipTextColor = if (isSelected) {
                            WhatsAppChipTextSelectedLight
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = chipBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onFilterChipSelected(chipName) }
                        ) {
                            Text(
                                text = chipName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = chipTextColor,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Línea divisoria muy sutil
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.6.dp
            )
        }
    }
}
