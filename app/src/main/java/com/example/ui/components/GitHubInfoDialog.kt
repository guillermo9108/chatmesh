package com.example.ui.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal

@Composable
fun GitHubInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(WhatsAppTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "GitHub Actions & APK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Compilación y Releases",
                        fontSize = 12.sp,
                        color = WhatsAppTeal
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = WhatsAppTeal.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF008069),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Workflow Configurado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF008069)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ".github/workflows/android.yml",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Cómo publicar en tu repositorio de GitHub:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                InstructionStep(
                    number = "1",
                    title = "Hacer 'Push to GitHub'",
                    description = "En Google AI Studio (arriba a la derecha o en ajustes), pulsa el botón 'Push to GitHub' para enviar estos archivos a tu repositorio."
                )
                Spacer(modifier = Modifier.height(6.dp))

                InstructionStep(
                    number = "2",
                    title = "Compilación automática",
                    description = "GitHub Actions detectará el push en 'main', compilará con assembleRelease y firmará la APK automáticamente."
                )
                Spacer(modifier = Modifier.height(6.dp))

                InstructionStep(
                    number = "3",
                    title = "Descarga de la APK",
                    description = "Ve a la sección 'Releases' de tu GitHub para descargar directamente ChatMesh-release.apk."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal)
            ) {
                Text("Entendido")
            }
        }
    )
}

@Composable
private fun InstructionStep(number: String, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(WhatsAppTeal),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = description, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
        }
    }
}
