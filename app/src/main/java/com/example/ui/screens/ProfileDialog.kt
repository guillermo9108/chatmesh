package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UserProfile
import com.example.ui.components.AvatarPresetItem
import com.example.ui.components.AvatarPresets
import com.example.ui.components.UserAvatar
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal
import java.io.File
import java.io.FileOutputStream

@Composable
fun ProfileDialog(
    userProfile: UserProfile?,
    onSaveProfile: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var nickname by remember { mutableStateOf(userProfile?.nickname ?: "Usuario") }
    var phone by remember { mutableStateOf(userProfile?.phoneNumber ?: "+53...") }
    var selectedAvatarUri by remember { mutableStateOf(userProfile?.avatarUri) }
    var isError by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Copy selected image to internal storage for permanent access
                val destFile = File(context.filesDir, "user_avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                selectedAvatarUri = destFile.absolutePath
            } catch (e: Exception) {
                // Fallback to Uri string if copy fails
                selectedAvatarUri = uri.toString()
            }
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = WhatsAppTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Editar Perfil y Avatar", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar preview with camera button overlay
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatar(
                        avatarUri = selectedAvatarUri,
                        displayName = nickname.ifBlank { "U" },
                        size = 92.dp,
                        iconSize = 52.dp,
                        border = BorderStroke(2.dp, WhatsAppTeal)
                    )

                    // Edit camera badge button
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(WhatsAppGreenAccent)
                            .border(2.dp, Color.White, CircleShape)
                            .testTag("change_avatar_photo_button")
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Cambiar foto",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Gallery action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galería", fontSize = 12.sp)
                    }

                    if (!selectedAvatarUri.isNullOrEmpty()) {
                        TextButton(
                            onClick = { selectedAvatarUri = null }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quitar", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Presets section
                Text(
                    text = "O elige un avatar prediseñado:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AvatarPresets.presets, key = { it.id }) { preset ->
                        val isSelected = selectedAvatarUri == preset.id
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { selectedAvatarUri = preset.id }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, WhatsAppGreenAccent, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            UserAvatar(
                                avatarUri = preset.id,
                                displayName = preset.name,
                                size = 44.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nickname field ("nik")
                OutlinedTextField(
                    value = nickname,
                    onValueChange = {
                        nickname = it
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text("Nombre / Apodo (Nik)") },
                    placeholder = { Text("Ej. Alex, Carlos...") },
                    leadingIcon = {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = WhatsAppTeal)
                    },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text("El nombre no puede estar vacío", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Este nombre se mostrará en chats y llamadas P2P", fontSize = 11.sp)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_nickname_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone number field
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Número de Teléfono") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppTeal)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_phone_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (userProfile != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = WhatsAppTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SSID en la Malla:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userProfile.ssid,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nickname.isBlank()) {
                        isError = true
                    } else {
                        onSaveProfile(nickname.trim(), phone.trim(), selectedAvatarUri)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Guardar Cambios", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
