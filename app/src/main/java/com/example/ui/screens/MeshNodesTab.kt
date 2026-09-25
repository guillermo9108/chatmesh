package com.example.ui.screens

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MeshNodeEntity
import com.example.mesh.MeshEngineState
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@Composable
fun MeshNodesTab(
    engineState: MeshEngineState,
    meshNodes: List<MeshNodeEntity>,
    onScanPeers: () -> Unit,
    onConnectDevice: (WifiP2pDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("mesh_nodes_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = WhatsAppTeal.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = WhatsAppTeal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Red WiFi Direct Mesh",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (engineState.isWifiDirectActive) WhatsAppGreenAccent.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (engineState.isWifiDirectActive) "ACTIVO" else "DESCONECTADO",
                                color = if (engineState.isWifiDirectActive) WhatsAppTeal else Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "SSID: ${engineState.ssid.ifEmpty { "Generando..." }}", fontSize = 13.sp)
                    Text(text = "IP Local: ${engineState.localIpAddress.ifEmpty { "192.168.49.1" }}", fontSize = 13.sp)
                    Text(text = "Rol: ${if (engineState.isGroupOwner) "Dueño de Grupo (GO)" else "Cliente Mesh"}", fontSize = 13.sp)
                    Text(
                        text = "Pares Directos: ${engineState.connectedPeersCount}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WhatsAppTeal
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Enviados: ${engineState.packetsSent}", fontSize = 11.sp, color = Color.Gray)
                        Text(text = "Recibidos: ${engineState.packetsReceived}", fontSize = 11.sp, color = Color.Gray)
                        Text(text = "Retransmitidos: ${engineState.packetsRelayed}", fontSize = 11.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = engineState.autoConnectStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WhatsAppTeal,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onScanPeers,
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("scan_mesh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Escanear", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Dispositivos WiFi Direct Detectados",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (engineState.discoveredP2pDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Buscando teléfonos con ChatMesh cerca...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(engineState.discoveredP2pDevices, key = { it.deviceAddress }) { dev ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dev.deviceName ?: "Dispositivo P2P",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "MAC: ${dev.deviceAddress}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val statusText = when (dev.status) {
                                WifiP2pDevice.CONNECTED -> "Conectado"
                                WifiP2pDevice.INVITED -> "Invitado"
                                WifiP2pDevice.FAILED -> "Falló"
                                WifiP2pDevice.AVAILABLE -> "Disponible"
                                WifiP2pDevice.UNAVAILABLE -> "No disponible"
                                else -> "Desconocido"
                            }
                            Text(
                                text = "Estado: $statusText",
                                fontSize = 11.sp,
                                color = if (dev.status == WifiP2pDevice.CONNECTED) WhatsAppGreenAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onConnectDevice(dev) },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            enabled = dev.status != WifiP2pDevice.CONNECTED
                        ) {
                            Text(if (dev.status == WifiP2pDevice.CONNECTED) "Conectado" else "Conectar", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
