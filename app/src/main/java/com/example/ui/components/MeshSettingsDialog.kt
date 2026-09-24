package com.example.ui.components

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ContactEntity
import com.example.data.entity.MeshNodeEntity
import com.example.mesh.MeshEngineState
import com.example.mesh.SimCardInfo
import com.example.ui.screens.MeshNodesTab
import com.example.ui.theme.WhatsAppTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshSettingsDialog(
    engineState: MeshEngineState,
    simInfo: SimCardInfo,
    meshNodes: List<MeshNodeEntity>,
    onDismiss: () -> Unit,
    onNodeChatClick: (ContactEntity) -> Unit,
    onEditSimClick: () -> Unit,
    onReCreateGroup: () -> Unit,
    onScanPeers: () -> Unit,
    onConnectPeer: (WifiP2pDevice) -> Unit,
    onGitHubClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Ajustes de Red Malla P2P",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTeal)
                )

                MeshNodesTab(
                    engineState = engineState,
                    simInfo = simInfo,
                    meshNodes = meshNodes,
                    onNodeChatClick = { contact ->
                        onDismiss()
                        onNodeChatClick(contact)
                    },
                    onEditSimClick = onEditSimClick,
                    onReCreateGroup = onReCreateGroup,
                    onScanPeers = onScanPeers,
                    onConnectPeer = onConnectPeer,
                    onGitHubClick = onGitHubClick
                )
            }
        }
    }
}
