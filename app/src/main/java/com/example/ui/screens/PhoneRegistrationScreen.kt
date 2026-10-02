package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WhatsAppTeal

@Composable
fun PhoneRegistrationScreen(
    initialPhone: String = "",
    errorMessage: String?,
    onSubmit: (phone: String, nickname: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()

    var phone by remember { mutableStateOf(initialPhone) }
    var nickname by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val cleanDigits = phone.filter { it.isDigit() }
    val isValidPhone = cleanDigits.length in 7..15

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Icon(
            imageVector = Icons.Default.SimCard,
            contentDescription = null,
            tint = WhatsAppTeal,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Registro manual",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "No pudimos detectar tu número de SIM automáticamente. " +
                    "Ingrésalo manualmente para identificarte en la malla.",
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = WhatsAppTeal.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = WhatsAppTeal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "El número se guardará solo en este dispositivo. " +
                            "Sirve para que otros nodos te identifiquen. No se envía a ningún servidor.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = {
                phone = it
                localError = null
            },
            label = { Text("Tu número de teléfono") },
            placeholder = { Text("+5351234567") },
            leadingIcon = {
                Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppTeal)
            },
            isError = localError != null,
            supportingText = {
                if (localError != null) {
                    Text(localError!!, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        "Incluye el código de país (ej. +53 para Cuba)",
                        fontSize = 11.sp
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_phone_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Tu nombre o apodo") },
            placeholder = { Text("Ej. Alex, Carlos...") },
            leadingIcon = {
                Icon(Icons.Default.Badge, contentDescription = null, tint = WhatsAppTeal)
            },
            supportingText = {
                Text(
                    "Así te verán los demás en la malla",
                    fontSize = 11.sp
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_nickname_input")
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                val digits = phone.filter { it.isDigit() }
                when {
                    digits.length < 7 -> localError = "El número debe tener al menos 7 dígitos"
                    digits.length > 15 -> localError = "El número es demasiado largo"
                    digits.all { it == '0' } -> localError = "El número no puede ser todo ceros"
                    nickname.isBlank() -> localError = "Escribe un nombre o apodo"
                    else -> {
                        localError = null
                        val finalNickname = nickname.trim()
                        val finalPhone = if (phone.startsWith("+")) phone.trim()
                        else "+${digits}"
                        onSubmit(finalPhone, finalNickname)
                    }
                }
            },
            enabled = isValidPhone && nickname.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_manual_phone_button")
        ) {
            Text(
                text = "Guardar y entrar",
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}