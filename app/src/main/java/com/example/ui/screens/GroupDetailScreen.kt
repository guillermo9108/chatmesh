package com.example.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.entity.GroupEntity
import com.example.data.entity.GroupMemberEntity
import com.example.data.entity.MessageEntity
import com.example.util.VoiceMessageHelper
import com.example.ui.components.ChatBubble
import com.example.ui.components.GroupAvatar
import com.example.ui.theme.*
import com.example.mesh.SimDetectionUtil as SimUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: GroupEntity,
    messages: List<MessageEntity>,
    members: List<GroupMemberEntity>,
    myPhone: String,
    onBackClick: () -> Unit,
    onOpenInfo: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImage: (String, String, String) -> Unit,
    onSendAudio: (String, Int) -> Unit,
    onSendFile: (String) -> Unit,
    onTypingChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val bgWallpaperTint = if (isDark) 0.15f else 0.45f

    // Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val base64 = encodeImageUriToBase64(context, it)
                if (base64.isNotBlank()) {
                    onSendImage(it.toString(), "", base64)
                }
            }
        }
    }

    // File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = queryFileName(context, it) ?: "archivo"
            onSendFile(fileName)
        }
    }

    // Audio recorder timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    // Scroll al final al recibir mensajes nuevos
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenInfo() }
                        ) {
                            GroupAvatar(
                                avatarUri = group.avatarUri,
                                groupName = group.name,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${members.size} miembros",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenInfo) {
                            Icon(Icons.Default.Info, contentDescription = "Información del grupo")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.6.dp
                )
            }
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
                .background(if (isDark) WhatsAppChatBgDark else WhatsAppChatBgLight)
        ) {
            Image(
                painter = painterResource(id = R.drawable.chat_wallpaper),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = bgWallpaperTint
            )

            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF1E2A30) else Color(0xFFFFF9C4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "🔒 Los mensajes de este grupo se transmiten cifrados y descentralizados entre nodos de la malla P2P.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color(0xFFB0BEC5) else Color(0xFF5D4037),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    items(messages) { msg ->
                        val senderName = if (msg.isOutgoing) {
                            null
                        } else {
                            members.firstOrNull { SimUtil.isMatchingPhone(it.phoneNumber, msg.senderPhone) }?.displayName
                                ?: msg.senderPhone
                        }
                        GroupChatBubble(
                            message = msg,
                            senderName = senderName
                        )
                    }
                }

                // Input bar
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
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDark) Color(0xFF1F2C34) else Color.White,
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
                                        modifier = Modifier.size(44.dp)
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
                                            onTypingChange(it)
                                        },
                                        placeholder = {
                                            Text("Mensaje para el grupo", color = Color.Gray)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("group_chat_input_field"),
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
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "Cámara/Foto",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Mic or Send FAB
                        FloatingActionButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText.trim())
                                    inputText = ""
                                } else if (isRecording) {
                                    val dur = recordingSeconds
                                    val (_, base64) = VoiceMessageHelper.stopRecording()
                                    isRecording = false
                                    if (base64 != null) {
                                        onSendAudio(base64, dur.coerceAtLeast(1))
                                    }
                                } else {
                                    val started = VoiceMessageHelper.startRecording(context)
                                    if (started) isRecording = true
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("group_send_fab"),
                            shape = CircleShape,
                            containerColor = WhatsAppGreenPrimary,
                            contentColor = Color.White
                        ) {
                            Icon(
                                imageVector = when {
                                    inputText.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                                    isRecording -> Icons.Default.Stop
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = if (inputText.isNotBlank()) "Enviar" else "Grabar voz"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Compartir en grupo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachmentOption(
                        icon = Icons.Default.Image,
                        label = "Galería",
                        color = Color(0xFF9C27B0)
                    ) {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    AttachmentOption(
                        icon = Icons.Default.Description,
                        label = "Documento",
                        color = Color(0xFF536DFE)
                    ) {
                        showAttachmentSheet = false
                        filePickerLauncher.launch("*/*")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
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
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun encodeImageUriToBase64(context: Context, uri: Uri): String {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
        Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        ""
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}

@Composable
private fun GroupChatBubble(
    message: MessageEntity,
    senderName: String?
) {
    val dims = LocalAppDimensions.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenPadding / 2, vertical = 2.dp)
    ) {
        if (!message.isOutgoing && senderName != null) {
            Text(
                text = senderName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorForName(senderName),
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        ChatBubble(message = message)
    }
}

private fun colorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF1E88E5),
        Color(0xFFD81B60),
        Color(0xFF6D4C41),
        Color(0xFF00897B),
        Color(0xFF7B1FA2),
        Color(0xFFC62828),
        Color(0xFFEF6C00),
        Color(0xFF2E7D32)
    )
    val idx = (name.hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}

