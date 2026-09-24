package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.entity.ContactEntity
import com.example.data.entity.MessageEntity
import com.example.ui.components.ChatBubble
import com.example.ui.theme.WhatsAppChatBgDark
import com.example.ui.theme.WhatsAppChatBgLight
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTeal
import com.example.util.ImageMediaUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    contact: ContactEntity,
    messages: List<MessageEntity>,
    activeTypingPhone: String?,
    activeRecordingPhone: String?,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImage: (String, String, String?) -> Unit,
    onSendAudio: (Int) -> Unit,
    onSendFile: (String) -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onTypingChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Real Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val file = ImageMediaUtil.saveBitmapToCache(context, bitmap)
            val base64 = ImageMediaUtil.bitmapToBase64(bitmap)
            onSendImage(Uri.fromFile(file).toString(), "Foto de cámara", base64)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    // Real Photo / Gallery Picker (Zero-permission Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val base64 = ImageMediaUtil.uriToBase64(context, uri)
            onSendImage(uri.toString(), "Foto adjunta", base64)
        }
    }

    // Real Document Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "documento.pdf"
            onSendFile(fileName)
        }
    }

    // Voice recording simulation state
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Voice recording timer effect
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    val isContactTyping = activeTypingPhone == contact.phoneNumber
    val isContactRecording = activeRecordingPhone == contact.phoneNumber

    val statusSubtitle = when {
        isContactRecording -> "grabando audio..."
        isContactTyping -> "escribiendo..."
        contact.isConnected -> "● En línea"
        contact.isRegisteredInMesh -> "disponible en malla P2P"
        else -> "desconectado (store & forward)"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // WhatsApp Top Bar
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { /* View contact */ }
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.displayName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = contact.displayName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusSubtitle,
                            color = if (isContactTyping || isContactRecording) WhatsAppGreenAccent else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontStyle = if (isContactTyping || isContactRecording) FontStyle.Italic else FontStyle.Normal
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
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
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Videollamada P2P",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onAudioCallClick,
                    modifier = Modifier.testTag("chat_audio_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Llamada de audio P2P",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menú",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Información del nodo") },
                        onClick = { menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Enviar archivo...") },
                        onClick = {
                            menuExpanded = false
                            showAttachmentSheet = true
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = WhatsAppTeal)
        )

        // Chat Body with Wallpaper background
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(WhatsAppChatBgLight)
        ) {
            // Doodle Wallpaper
            Image(
                painter = painterResource(id = R.drawable.chat_wallpaper),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.15f,
                modifier = Modifier.fillMaxSize()
            )

            // Messages LazyColumn
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // Encryption notice
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFEECD))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔒 Los mensajes y llamadas de audio/video se transmiten de nodo a nodo cifrados por WiFi Direct sin servidor central.",
                                fontSize = 11.sp,
                                color = Color(0xFF5A4400),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                items(messages, key = { it.messageUuid }) { msg ->
                    ChatBubble(message = msg, isDarkTheme = false)
                }
            }
        }

        // WhatsApp Bottom Input Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    // Recording live audio view
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFFF0F2F5))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "Grabando",
                            tint = Color.Red,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "00:${if (recordingSeconds < 10) "0" else ""}$recordingSeconds  Grabando audio P2P...",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Cancelar",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { isRecording = false }
                                .padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Stop and Send Voice Note Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreenAccent)
                            .clickable {
                                isRecording = false
                                onSendAudio(if (recordingSeconds > 0) recordingSeconds else 3)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar audio",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Normal text input field
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFFF0F2F5))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Emoji picker */ }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.SentimentSatisfied,
                                contentDescription = "Emojis",
                                tint = Color.Gray
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                onTypingChange(it)
                            },
                            placeholder = { Text("Escribe un mensaje...", fontSize = 14.sp) },
                            singleLine = false,
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_text")
                        )

                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier.size(36.dp).testTag("attach_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Adjuntar archivo o imagen",
                                tint = Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Cámara real",
                                tint = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Circular Action Button (Mic or Send)
                    val isTextEmpty = inputText.trim().isEmpty()
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(WhatsAppTeal)
                            .clickable {
                                if (isTextEmpty) {
                                    isRecording = true
                                } else {
                                    onSendMessage(inputText)
                                    inputText = ""
                                }
                            }
                            .testTag(if (isTextEmpty) "mic_button" else "send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTextEmpty) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isTextEmpty) "Grabar audio" else "Enviar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    // Attachment Modal Bottom Sheet
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Compartir en la malla P2P",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WhatsAppTeal
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentOption(
                        icon = Icons.Default.Photo,
                        label = "Foto / Galería",
                        color = Color(0xFFAC44CF),
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
                        color = Color(0xFF5F66CD),
                        onClick = {
                            showAttachmentSheet = false
                            docPickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.Mic,
                        label = "Audio",
                        color = Color(0xFFF77926),
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

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
