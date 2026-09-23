package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UserProfile
import com.example.ui.theme.WhatsAppTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileDialog(
    userProfile: UserProfile?,
    onDismiss: () -> Unit,
    onSaveProfile: (nickname: String, phoneNumber: String) -> Unit
) {
    var nickname by remember(userProfile) { mutableStateOf(userProfile?.nickname ?: "Usuario Mesh") }
    var phoneNumber by remember(userProfile) { mutableStateOf(userProfile?.phoneNumber ?: "+5351234567") }

    val regDate = remember(userProfile?.registeredAt) {
        val date = Date(userProfile?.registeredAt ?: System.currentTimeMillis())
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WhatsAppTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Mi Perfil & Red Malla",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Datos registrados en la base de datos local SQLite (Room) y replicados en la malla:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nombre / Nickname") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = WhatsAppTeal) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Número de la SIM") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppTeal) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                // Network details
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CellTower, contentDescription = null, tint = WhatsAppTeal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SSID Auto-Creado: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = userProfile?.ssid ?: "ChatMesh_$phoneNumber", fontSize = 12.sp, color = WhatsAppTeal)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = WhatsAppTeal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "IP del Nodo: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = userProfile?.ipAddress ?: "192.168.49.1", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = WhatsAppTeal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Registro: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = regDate, fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveProfile(nickname, phoneNumber)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Color.Gray)
            }
        }
    )
}
