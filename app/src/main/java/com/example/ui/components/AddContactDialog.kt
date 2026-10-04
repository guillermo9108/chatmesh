package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WhatsAppTeal

@Composable
fun AddContactDialog(
    initialName: String = "",
    initialPhone: String = "",
    onAddContact: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = WhatsAppTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuevo Contacto P2P", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_name_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        errorMsg = null
                    },
                    label = { Text("Número de teléfono (ej. +53...)") },
                    singleLine = true,
                    isError = errorMsg != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_phone_input")
                )
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = com.example.mesh.SimDetectionUtil.sanitizePhoneNumber(phone)
                    if (!com.example.mesh.SimDetectionUtil.isValidPhoneNumber(clean)) {
                        errorMsg = "Por favor ingresa un número de teléfono válido (no ceros ni dummy)"
                        return@Button
                    }
                    onAddContact(name.ifBlank { clean }, clean)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                modifier = Modifier.testTag("save_contact_button")
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
