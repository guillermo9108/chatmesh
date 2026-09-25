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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.entity.ContactEntity
import com.example.data.entity.MessageEntity
import com.example.ui.components.ChatBubble
import com.example.ui.theme.WhatsAppChatBgLight
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal
import com.example.util.ImageMediaUtil
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
    onSendAudio: (Int) -> Unit,
    onSendFile: (String) -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onTypingChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.displayName.ifBlank { contact.phoneNumber },
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            val subtext = when {
                                activeTypingPhone == contact.phoneNumber -> "Escribiendo..."
                                activeRecordingPhone == contact.phoneNumber -> "Grabando audio..."
                                contact.isConnected -> "En línea • P2P"
                                else -> "Desconectado"
                            }
                            Text(
                                text = subtext,
                                color = if (activeTypingPhone == contact.phoneNumber || activeRecordingPhone == contact.phoneNumber) {
                                    WhatsAppGreenAccent
                                } else {
                                    Color.White.copy(alpha = 0.8f)
                                },
                                fontSize = 12.sp,
                                maxLines = 1
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
                            text = { Text("Ver contacto") },
                            onClick = { menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Vaciar chat") },
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
                                    TextButton(onClick = { isRecording = false }) {
                                        Text("Cancelar", color = Color.Gray)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { showAttachmentSheet = true },
                                        modifier = Modifier.size(36.dp)
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
                                                fontSize = 15.sp
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
                                        modifier = Modifier.size(36.dp)
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

                        Spacer(modifier = Modifier.width(6.dp))

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
                                    isRecording = false
                                    onSendAudio(dur)
                                } else {
                                    isRecording = true
                                }
                            },
                            containerColor = WhatsAppTeal,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
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
                                    onSendAudio(5)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
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
