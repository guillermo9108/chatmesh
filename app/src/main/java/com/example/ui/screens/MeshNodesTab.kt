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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactEntity
import com.example.data.entity.MeshNodeEntity
import com.example.mesh.MeshEngineState
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@Composable
fun MeshNodesTab(
    engineState: MeshEngineState,
    meshNodes: List<MeshNodeEntity>,
    onNodeChatClick: (ContactEntity) -> Unit,
    onToggleSimulation: () -> Unit,
    onGitHubClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Network Mesh Status
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhatsAppTeal.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mesh_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(WhatsAppTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Red en Malla WiFi Direct & Aware",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sin Internet • P2P Descentralizado",
                                fontSize = 12.sp,
                                color = WhatsAppTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // SIM and SSID info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            tint = WhatsAppTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SSID Auto-Generado por SIM:",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = engineState.ssid.ifEmpty { "ChatMesh_+5351234567" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            tint = WhatsAppTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Número Móvil Activo:",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = engineState.myPhoneNumber.ifEmpty { "+5351234567" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(
                            label = "WiFi Direct",
                            active = engineState.isWifiDirectActive || engineState.isSimulationActive,
                            modifier = Modifier.weight(1f)
                        )
                        StatusChip(
                            label = "WiFi Aware",
                            active = engineState.isWifiAwareActive || engineState.isSimulationActive,
                            modifier = Modifier.weight(1f)
                        )
                        StatusChip(
                            label = "Malla > 4 Nodos",
                            active = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Simulation Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simulación Virtual de Malla",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Permite probar saltos y respuestas automáticas en emulador",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = engineState.isSimulationActive,
                            onCheckedChange = { onToggleSimulation() },
                            colors = SwitchDefaults.colors(checkedThumbColor = WhatsAppTeal, checkedTrackColor = WhatsAppGreenAccent)
                        )
                    }
                }
            }
        }

        // Card 2: Mesh Packet Statistics
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Métricas de Transmisión en Malla",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(
                            label = "Enviados",
                            value = "${engineState.packetsSent}",
                            icon = Icons.Default.SwapHoriz,
                            tint = Color(0xFF008069)
                        )
                        StatItem(
                            label = "Recibidos",
                            value = "${engineState.packetsReceived}",
                            icon = Icons.Default.Hub,
                            tint = Color(0xFF128C7E)
                        )
                        StatItem(
                            label = "Retransmitidos",
                            value = "${engineState.packetsRelayed}",
                            icon = Icons.Default.Router,
                            tint = Color(0xFF25D366)
                        )
                    }
                }
            }
        }

        // Card 3: GitHub Actions & Releases APK
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGitHubClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(WhatsAppTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GitHub Actions & APK",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(WhatsAppGreenAccent.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppTeal
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-compilación y Release en cada push a main",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Ver detalles",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Section: Discovered Mesh Nodes
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Nodos Conectados en la Malla (${meshNodes.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        items(meshNodes, key = { it.nodeId }) { node ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (node.isDirectNeighbor) WhatsAppGreenAccent.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (node.isDirectNeighbor) Icons.Default.Wifi else Icons.Default.Hub,
                            contentDescription = null,
                            tint = if (node.isDirectNeighbor) WhatsAppTeal else Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.nickname,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = node.ssid,
                            fontSize = 12.sp,
                            color = WhatsAppTeal,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (node.isDirectNeighbor) "⚡ Salto Directo (1)" else "⚡ ${node.hopDistance} Saltos (Retransmitido)",
                                fontSize = 11.sp,
                                color = if (node.isDirectNeighbor) Color(0xFF008069) else Color(0xFFD97706),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " • ${node.connectionType}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onNodeChatClick(
                                ContactEntity(
                                    phoneNumber = node.phoneNumber,
                                    displayName = node.nickname,
                                    isRegisteredInMesh = true,
                                    isConnected = true,
                                    statusText = "Conectado vía ${node.connectionType}"
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("chat_node_${node.nodeId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chatear",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Chatear", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) WhatsAppGreenAccent.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) WhatsAppTeal else Color.Gray
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
    }
}
