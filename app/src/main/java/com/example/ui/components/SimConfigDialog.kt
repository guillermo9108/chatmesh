package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh.SimCardInfo
import com.example.ui.theme.WhatsAppTeal

@Composable
fun SimConfigDialog(
    simInfo: SimCardInfo,
    onSaveNumber: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var phoneNumberInput by remember { mutableStateOf(simInfo.phoneNumber.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SimCard, contentDescription = null, tint = WhatsAppTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configuración SIM", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Operador: ${simInfo.carrierName.ifEmpty { "Desconocido" }} (${simInfo.countryIso})",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Estado SIM: ${simInfo.simStateDescription}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phoneNumberInput,
                    onValueChange = { phoneNumberInput = it },
                    label = { Text("Número de teléfono (ej. +53...)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_phone_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tu número se usa para que otros usuarios te identifiquen en la red mesh sin conexión a internet.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phoneNumberInput.isNotBlank()) {
                        onSaveNumber(phoneNumberInput)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                modifier = Modifier.testTag("save_sim_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
