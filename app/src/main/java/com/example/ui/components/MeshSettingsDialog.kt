package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh.MeshEngineState
import com.example.mesh.MeshTransport
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@Composable
fun MeshSettingsDialog(
    engineState: MeshEngineState,
    onReCreateGroup: () -> Unit,
    onScanPeers: () -> Unit,
    onStartHotspot: () -> Unit,
    onStopHotspot: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val isLanActive = engineState.transport == MeshTransport.WIFI_LAN
    val isHotspotActive = engineState.isHotspotActive || engineState.transport == MeshTransport.HOTSPOT

    val activeModeText = when (engineState.transport) {
        MeshTransport.WIFI_DIRECT -> "WiFi Direct (P2P)"
        MeshTransport.WIFI_LAN -> "WiFi LAN (Router)"
        MeshTransport.HOTSPOT -> "Hotspot Móvil"
        MeshTransport.NONE -> "Sin conexión activa"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = WhatsAppTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ajustes de Red y Malla", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Indicador Global de Modo Activo
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = WhatsAppTeal.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
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
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Modo Actual Activo",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = activeModeText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WhatsAppTeal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ==========================================
                // SECCIÓN 1: RED WIFI LAN (ROUTER)
                // ==========================================
                Text(
                    text = "1. Modo WiFi LAN (Router / Sin Internet):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Router,
                                contentDescription = null,
                                tint = if (isLanActive) WhatsAppGreenAccent else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLanActive) "WiFi LAN: CONECTADO" else "WiFi LAN: No detectado",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isLanActive) WhatsAppGreenAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isLanActive) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Router SSID: ${engineState.networkSsid}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "IP en la LAN: ${engineState.networkLocalIp}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Conecta este móvil al router WiFi de tu casa u oficina (no requiere Internet). Todos los dispositivos en la misma red se comunican automáticamente.",
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_wifi_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir Ajustes de WiFi", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // SECCIÓN 2: HOTSPOT (PUNTO DE ACCESO)
                // ==========================================
                Text(
                    text = "2. Modo Hotspot (Punto de Acceso Local):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = if (isHotspotActive) WhatsAppGreenAccent else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHotspotActive) "Hotspot: ACTIVO" else "Hotspot: Apagado",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (engineState.isHotspotActive) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = WhatsAppTeal.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "SSID: ${engineState.hotspotSsid}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (engineState.hotspotPassword.isNotBlank()) {
                                        Text(
                                            text = "Contraseña: ${engineState.hotspotPassword}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Los demás deben conectarse a esta red desde los ajustes de su teléfono y luego abrir ChatMesh.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onStopHotspot,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stop_hotspot_button")
                            ) {
                                Text("Detener Hotspot", fontSize = 13.sp)
                            }
                        } else {
                            Text(
                                text = "Crea un punto de acceso sin saldo ni datos. Los demás se conectan como a cualquier WiFi.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onStartHotspot,
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_hotspot_button")
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Iniciar Hotspot", fontSize = 13.sp)
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

                // ==========================================
                // SECCIÓN 3: WIFI DIRECT (P2P)
                // ==========================================
                Text(
                    text = "3. Modo WiFi Direct (P2P Automático):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (engineState.isWifiDirectActive) WhatsAppTeal else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (engineState.isWifiDirectActive) "WiFi Direct: ACTIVO" else "WiFi Direct: Inactivo",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SSID: ${engineState.ssid.ifEmpty { "Desconocido" }}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (engineState.passphrase.isNotEmpty()) {
                            Text(
                                text = "Contraseña: ${engineState.passphrase}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "IP Local: ${engineState.localIpAddress.ifEmpty { "192.168.49.1" }}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Rol: ${if (engineState.isGroupOwner) "Dueño del Grupo (GO)" else "Cliente Mesh"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pares conectados: ${engineState.connectedPeersCount}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WhatsAppTeal
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onReCreateGroup,
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recreate_group_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reiniciar Grupo P2P", fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onScanPeers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_peers_button")
                        ) {
                            Text("Escanear Dispositivos P2P", fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = WhatsAppTeal)
            }
        }
    )
}
