package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WhatsAppTeal

@Composable
fun GitHubInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = WhatsAppTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ChatMesh Offline", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Mensajería y Llamadas 100% Desconectadas",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = WhatsAppTeal
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ChatMesh funciona como WhatsApp pero sin necesidad de Internet ni datos móviles ni torres de telefonía.\n\n" +
                            "• Malla P2P con WiFi Direct y WiFi Aware.\n" +
                            "• Store-and-Forward con retransmisión automática.\n" +
                            "• Llamadas de voz y video en tiempo real sobre enlace directo.\n" +
                            "• Soporte para mensajes de voz, fotos y texto.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                modifier = Modifier.testTag("close_github_info_button")
            ) {
                Text("Entendido")
            }
        }
    )
}
