package com.example.ui.screens

import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MeshNodeEntity
import com.example.mesh.MeshEngineState
import com.example.mesh.MeshTransport
import com.example.ui.theme.*

@Composable
fun MeshNodesTab(
    engineState: MeshEngineState,
    meshNodes: List<MeshNodeEntity>,
    onScanPeers: () -> Unit,
    onConnectDevice: (WifiP2pDevice) -> Unit,
    onDisconnectDevice: () -> Unit = {},
    onStartHotspot: () -> Unit = {},
    onStopHotspot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dims = LocalAppDimensions.current
    val context = LocalContext.current
    val isLanActive = engineState.transport == MeshTransport.WIFI_LAN
    val isHotspotActive = engineState.isHotspotActive || engineState.transport == MeshTransport.HOTSPOT

    val activeModeText = when (engineState.transport) {
        MeshTransport.WIFI_DIRECT -> "WiFi Direct (P2P)"
        MeshTransport.WIFI_LAN -> "WiFi LAN (Router)"
        MeshTransport.HOTSPOT -> "Hotspot Móvil"
        MeshTransport.NONE -> "Sin conexión activa"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(dims.screenPadding)
            .testTag("mesh_nodes_list")
    ) {
        // 1. Tarjeta Resumen Modo Activo
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = WhatsAppTeal.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (engineState.transport) {
                            MeshTransport.WIFI_LAN -> Icons.Default.Router
                            MeshTransport.HOTSPOT -> Icons.Default.WifiTethering
                            MeshTransport.WIFI_DIRECT -> Icons.Default.Wifi
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = WhatsAppTeal,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Transporte de Red Activo:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = activeModeText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WhatsAppTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Tarjeta WiFi LAN (Router)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isLanActive) WhatsAppGreenAccent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Router,
                                contentDescription = null,
                                tint = if (isLanActive) WhatsAppTeal else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Red WiFi LAN (Router)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isLanActive) WhatsAppGreenAccent.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isLanActive) "CONECTADO" else "NO CONECTADO",
                                color = if (isLanActive) WhatsAppTeal else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLanActive) {
                        Text(
                            text = "Router SSID: ${engineState.networkSsid}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "IP en la LAN: ${engineState.networkLocalIp}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = "Conéctate al WiFi de tu hogar, aula o trabajo (funciona sin Internet). Los teléfonos en la misma red se detectan solos.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configurar WiFi del Móvil", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3. Tarjeta Hotspot Móvil
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isHotspotActive) WhatsAppTeal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = if (isHotspotActive) WhatsAppTeal else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Punto de Acceso (Hotspot)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isHotspotActive) WhatsAppGreenAccent.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isHotspotActive) "ACTIVO" else "APAGADO",
                                color = if (isHotspotActive) WhatsAppTeal else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (engineState.isHotspotActive) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = WhatsAppTeal.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "SSID: ${engineState.hotspotSsid}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (engineState.hotspotPassword.isNotBlank()) {
                                    Text(
                                        text = "Contraseña: ${engineState.hotspotPassword}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "Pide a otros que se conecten a esta red WiFi y abran ChatMesh.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onStopHotspot,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Detener Hotspot", fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = "Convierte este móvil en el punto de encuentro WiFi sin gastar datos ni saldo.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onStartHotspot,
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Iniciar Hotspot", fontSize = 12.sp)
                        }
                    }

                    if (!engineState.isHotspotActive && engineState.hotspotErrorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Aviso de Hotspot:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = engineState.hotspotErrorMessage ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent("android.settings.TETHER_SETTINGS").apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            try {
                                                val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(fallback)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir Punto de Acceso del Teléfono", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 4. Tarjeta WiFi Direct Mesh
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = WhatsAppTeal.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = WhatsAppTeal,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Malla WiFi Direct (P2P)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
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
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "SSID: ${engineState.ssid.ifEmpty { "Generando..." }}", fontSize = 12.sp)
                    Text(text = "IP Local: ${engineState.localIpAddress.ifEmpty { "192.168.49.1" }}", fontSize = 12.sp)
                    Text(text = "Rol: ${if (engineState.isGroupOwner) "Dueño de Grupo (GO)" else "Cliente Mesh"}", fontSize = 12.sp)
                    Text(
                        text = "Pares Directos: ${engineState.connectedPeersCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WhatsAppTeal
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Enviados: ${engineState.packetsSent}", fontSize = 11.sp, color = Color.Gray)
                        Text(text = "Recibidos: ${engineState.packetsReceived}", fontSize = 11.sp, color = Color.Gray)
                        Text(text = "Retransmitidos: ${engineState.packetsRelayed}", fontSize = 11.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
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

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Dispositivos WiFi Direct Detectados",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
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
                            .padding(20.dp),
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

                        if (dev.status == WifiP2pDevice.CONNECTED) {
                            OutlinedButton(
                                onClick = onDisconnectDevice,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Desconectar", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = { onConnectDevice(dev) },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Conectar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
