package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.ui.components.UserAvatar
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.entity.ContactEntity
import com.example.data.entity.MessageEntity
import com.example.ui.components.AddContactDialog
import com.example.ui.components.ChatBubble
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.LocalAppDimensions
import com.example.ui.theme.WhatsAppChatBgLight
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal
import com.example.util.ImageMediaUtil
import com.example.util.VoiceMessageHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    contact: ContactEntity,
    messages: List<MessageEntity>,
    activeTypingPhone: String?,
    activeRecordingPhone: String?,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImage: (String, String, String) -> Unit,
    onSendAudio: (String, Int) -> Unit,
    onSendFile: (String) -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onTypingChange: (String) -> Unit,
    onSaveContact: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }
    val dims = LocalAppDimensions.current
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var showAddContactDialog by remember { mutableStateOf(false) }

    val isContactSaved = contact.displayName.isNotBlank() &&
        contact.displayName != contact.phoneNumber &&
        !contact.displayName.startsWith("+")

    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            onTypingChange("RECORDING")
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        } else {
            onTypingChange("STOPPED")
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ImageMediaUtil.uriToBase64(context, uri)
            if (!base64.isNullOrEmpty()) {
                onSendImage(uri.toString(), "Foto adjunta", base64)
            }
        }
    }

    // Document picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "documento.pdf"
            onSendFile(fileName)
        }
    }

    // Audio file picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ImageMediaUtil.uriToBase64(context, uri)
            if (!base64.isNullOrEmpty()) {
                onSendAudio(base64, 15)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserAvatar(
                            avatarUri = contact.avatarUri,
                            displayName = contact.displayName.ifBlank { contact.phoneNumber },
                            size = dims.avatarSmall
                        )
                        Spacer(modifier = Modifier.width(dims.itemSpacing))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.displayName.ifBlank { "Contacto" },
                                color = Color.White,
                                fontSize = dims.bodySize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtext = when {
                                activeTypingPhone == contact.phoneNumber -> "Escribiendo..."
                                activeRecordingPhone == contact.phoneNumber -> "Grabando audio..."
                                contact.isConnected -> "En línea • P2P"
                                else -> "Desconectado"
                            }
                            Text(
                                text = "${contact.phoneNumber} • $subtext",
                                color = if (activeTypingPhone == contact.phoneNumber || activeRecordingPhone == contact.phoneNumber) {
                                    WhatsAppGreenAccent
                                } else {
                                    Color.White.copy(alpha = 0.85f)
                                },
                                fontSize = dims.tinySize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onVideoCallClick,
                        modifier = Modifier.testTag("chat_video_call_button")
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Videollamada",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onAudioCallClick,
                        modifier = Modifier.testTag("chat_audio_call_button")
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Llamada",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Más opciones",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (!isContactSaved) "Guardar en Contactos" else "Editar Nombre") },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = WhatsAppTeal) },
                            onClick = {
                                menuExpanded = false
                                showAddContactDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ver detalles") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = { menuExpanded = false }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WhatsAppTeal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(WhatsAppChatBgLight)
        ) {
            // Background Wallpaper
            Image(
                painter = painterResource(id = R.drawable.chat_wallpaper),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // Banner para números no guardados
                if (!isContactSaved) {
                    Surface(
                        color = Color.White.copy(alpha = 0.95f),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Número no guardado en contactos",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WhatsAppTeal
                                )
                                Text(
                                    text = contact.phoneNumber,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showAddContactDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppTeal),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("add_contact_chat_banner_btn")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Agregar", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(message = msg)
                    }
                }

                // Bottom Input Bar
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Message input pill
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isRecording) {
                                    Icon(
                                        Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val mins = recordingSeconds / 60
                                    val secs = recordingSeconds % 60
                                    Text(
                                        text = String.format("%02d:%02d", mins, secs),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        VoiceMessageHelper.cancelRecording()
                                        isRecording = false
                                    }) {
                                        Text("Cancelar", color = Color.Gray)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { showAttachmentSheet = true },
                                        modifier = Modifier.size(dims.iconButtonSize)
                                    ) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = "Adjuntar",
                                            tint = Color.Gray
                                        )
                                    }
                                    OutlinedTextField(
                                        value = inputText,
                                        onValueChange = {
                                            inputText = it
                                            onTypingChange(if (it.isNotBlank()) "TYPING" else "STOPPED")
                                        },
                                        placeholder = {
                                            Text(
                                                "Mensaje",
                                                color = Color.Gray,
                                                fontSize = dims.chatBubbleSize
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("chat_input_field"),
                                        maxLines = 4,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.size(dims.iconButtonSize)
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "Cámara",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(dims.itemSpacing / 2))

                        // Mic / Send floating circle button
                        FloatingActionButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val textToSend = inputText.trim()
                                    inputText = ""
                                    onSendMessage(textToSend)
                                    onTypingChange("STOPPED")
                                } else if (isRecording) {
                                    val dur = recordingSeconds
                                    val (_, base64) = VoiceMessageHelper.stopRecording()
                                    isRecording = false
                                    if (base64 != null) {
                                        onSendAudio(base64, dur.coerceAtLeast(1))
                                    }
                                } else {
                                    val started = VoiceMessageHelper.startRecording(context)
                                    if (started) {
                                        isRecording = true
                                    }
                                }
                            },
                            containerColor = WhatsAppTeal,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(dims.sendButtonSize)
                                .testTag("send_or_mic_button")
                        ) {
                            if (inputText.isNotBlank()) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar",
                                    modifier = Modifier.size(22.dp)
                                )
                            } else if (isRecording) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Detener grabación",
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Grabar audio",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Attachment Bottom Sheet
            if (showAttachmentSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAttachmentSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Compartir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AttachmentOption(
                                icon = Icons.Default.Photo,
                                label = "Galería",
                                color = Color(0xFF9C27B0),
                                onClick = {
                                    showAttachmentSheet = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            AttachmentOption(
                                icon = Icons.Default.InsertDriveFile,
                                label = "Documento",
                                color = Color(0xFF5E35B1),
                                onClick = {
                                    showAttachmentSheet = false
                                    documentPickerLauncher.launch("*/*")
                                }
                            )
                            AttachmentOption(
                                icon = Icons.Default.Audiotrack,
                                label = "Audio",
                                color = Color(0xFFFF9800),
                                onClick = {
                                    showAttachmentSheet = false
                                    audioPickerLauncher.launch("audio/*")
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        if (showAddContactDialog) {
            AddContactDialog(
                initialName = if (contact.displayName != contact.phoneNumber) contact.displayName else "",
                initialPhone = contact.phoneNumber,
                onAddContact = { name, phone ->
                    onSaveContact(name, phone)
                    showAddContactDialog = false
                },
                onDismiss = { showAddContactDialog = false }
            )
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
