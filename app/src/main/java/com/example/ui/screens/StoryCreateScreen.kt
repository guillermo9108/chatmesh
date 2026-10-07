package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.LocalAppDimensions
import com.example.ui.theme.WhatsAppTeal
import com.example.util.ImageMediaUtil

private val StoryColorPalette = listOf(
    Color(0xFF005D4B), // WhatsApp Teal Oscuro
    Color(0xFF1B5E20), // Verde Bosque
    Color(0xFF7B1FA2), // Púrpura
    Color(0xFFC2185B), // Rosa Fuerte
    Color(0xFFD84315), // Naranja / Terracota
    Color(0xFF1565C0), // Azul Real
    Color(0xFF4E342E), // Marrón Oscuro
    Color(0xFF212121)  // Gris Carbón
)

@Composable
fun StoryCreateScreen(
    onPublishText: (String, Int) -> Unit,
    onPublishImage: (String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dims = LocalAppDimensions.current

    var isTextMode by remember { mutableStateOf(true) }
    var storyText by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(StoryColorPalette.first()) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageCaption by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            isTextMode = false
        }
    }

    val canPublish = if (isTextMode) {
        storyText.isNotBlank()
    } else {
        selectedImageUri != null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTextMode) selectedColor else Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra superior de acciones
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenPadding, vertical = dims.itemSpacing),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_story_create_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isTextMode = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isTextMode) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Modo Texto",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            isTextMode = false
                            if (selectedImageUri == null) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (!isTextMode) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Modo Imagen",
                            tint = Color.White
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isTextMode && storyText.isNotBlank()) {
                            onPublishText(storyText.trim(), selectedColor.toArgb())
                            onDismiss()
                        } else if (!isTextMode && selectedImageUri != null) {
                            val base64 = ImageMediaUtil.uriToBase64(
                                context,
                                selectedImageUri!!,
                                maxDim = 512,
                                quality = 50
                            )
                            if (base64 != null) {
                                onPublishImage(base64, imageCaption.trim())
                                onDismiss()
                            }
                        }
                    },
                    enabled = canPublish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppTeal,
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("publish_story_button")
                ) {
                    Text(
                        text = "Publicar",
                        fontSize = dims.smallSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Contenido central según el modo
            if (isTextMode) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = storyText,
                        onValueChange = { if (it.length <= 400) storyText = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = if (dims.compact) 22.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("story_text_input"),
                        decorationBox = { innerTextField ->
                            if (storyText.isEmpty()) {
                                Text(
                                    text = "Escribe un estado...",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = if (dims.compact) 22.sp else 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                // Selector de colores de fondo estilo WhatsApp
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dims.itemSpacing * 2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    items(StoryColorPalette) { color ->
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 36.dp else 28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            } else {
                // Modo Imagen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Imagen de estado",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Toca para elegir una foto",
                                color = Color.White,
                                fontSize = dims.bodySize,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Input de pie de foto (caption) si hay imagen seleccionada
                if (selectedImageUri != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dims.screenPadding, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = imageCaption,
                                onValueChange = { imageCaption = it },
                                placeholder = {
                                    Text(
                                        "Añade un pie de foto...",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = dims.smallSize
                                    )
                                },
                                textStyle = TextStyle(color = Color.White, fontSize = dims.bodySize),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WhatsAppTeal,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("story_image_caption_input")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Cambiar foto",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
