package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.StoryEntity
import com.example.data.entity.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StoryListScreen(
    userProfile: UserProfile?,
    myPhoneNumber: String,
    storiesGrouped: Map<String, List<StoryEntity>>,
    onOpenCreateStory: () -> Unit,
    onViewStory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = LocalAppDimensions.current

    val myStories = storiesGrouped[myPhoneNumber].orEmpty()
    val otherStories = storiesGrouped.filterKeys { it != myPhoneNumber }

    val recentStories = otherStories.filterValues { list -> list.any { !it.viewedByMe } }
    val viewedStories = otherStories.filterValues { list -> list.all { it.viewedByMe } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("stories_list"),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Sección: Mi estado
            item {
                MyStatusRow(
                    userProfile = userProfile,
                    myStories = myStories,
                    onOpenCreate = onOpenCreateStory,
                    onViewMyStories = {
                        if (myStories.isNotEmpty()) onViewStory(myPhoneNumber)
                        else onOpenCreateStory()
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = dims.screenPadding)
                )
            }

            // Sección: Actualizaciones recientes (no vistas)
            if (recentStories.isNotEmpty()) {
                item {
                    Text(
                        text = "Recientes",
                        fontSize = dims.smallSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = dims.screenPadding,
                            vertical = dims.itemSpacing
                        )
                    )
                }

                items(recentStories.entries.toList(), key = { it.key }) { (phone, stories) ->
                    val latest = stories.maxByOrNull { it.createdAt } ?: stories.first()
                    StoryAuthorRow(
                        authorPhone = phone,
                        authorName = latest.authorName,
                        authorAvatarUri = latest.authorAvatarUri,
                        lastUpdated = latest.createdAt,
                        hasUnseen = true,
                        storiesCount = stories.size,
                        onClick = { onViewStory(phone) }
                    )
                }
            }

            // Sección: Actualizaciones vistas
            if (viewedStories.isNotEmpty()) {
                item {
                    Text(
                        text = "Vistos",
                        fontSize = dims.smallSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = dims.screenPadding,
                            vertical = dims.itemSpacing
                        )
                    )
                }

                items(viewedStories.entries.toList(), key = { it.key }) { (phone, stories) ->
                    val latest = stories.maxByOrNull { it.createdAt } ?: stories.first()
                    StoryAuthorRow(
                        authorPhone = phone,
                        authorName = latest.authorName,
                        authorAvatarUri = latest.authorAvatarUri,
                        lastUpdated = latest.createdAt,
                        hasUnseen = false,
                        storiesCount = stories.size,
                        onClick = { onViewStory(phone) }
                    )
                }
            }

            // Estado vacío si nadie ha publicado
            if (otherStories.isEmpty() && myStories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Sin estados disponibles",
                                fontSize = dims.subtitleSize,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sé el primero en compartir un estado con tus nodos vecinos en la red malla.",
                                fontSize = dims.smallSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun MyStatusRow(
    userProfile: UserProfile?,
    myStories: List<StoryEntity>,
    onOpenCreate: () -> Unit,
    onViewMyStories: () -> Unit
) {
    val dims = LocalAppDimensions.current
    val hasStories = myStories.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewMyStories)
            .padding(horizontal = dims.screenPadding, vertical = dims.listItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(dims.avatarLarge),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dims.avatarLarge)
                    .clip(CircleShape)
                    .then(
                        if (hasStories) Modifier.border(2.5.dp, WhatsAppGreenPrimary, CircleShape)
                        else Modifier
                    )
                    .padding(if (hasStories) 3.dp else 0.dp)
            ) {
                UserAvatar(
                    avatarUri = userProfile?.avatarUri,
                    displayName = userProfile?.nickname ?: "Mi Estado",
                    size = if (hasStories) dims.avatarLarge - 6.dp else dims.avatarLarge
                )
            }

            if (!hasStories) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(WhatsAppGreenPrimary)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Añadir estado",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(dims.itemSpacing * 2))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Mi estado",
                fontSize = dims.bodySize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (hasStories) {
                    val latest = myStories.maxByOrNull { it.createdAt }
                    latest?.let { "Hace ${getRelativeTime(it.createdAt)}" } ?: "Toca para ver"
                } else {
                    "Añade una actualización de estado"
                },
                fontSize = dims.smallSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasStories) {
            IconButton(onClick = onOpenCreate) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Nuevo estado",
                    tint = WhatsAppGreenPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryAuthorRow(
    authorPhone: String,
    authorName: String,
    authorAvatarUri: String?,
    lastUpdated: Long,
    hasUnseen: Boolean,
    storiesCount: Int,
    onClick: () -> Unit
) {
    val dims = LocalAppDimensions.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dims.screenPadding, vertical = dims.listItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(dims.avatarLarge),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dims.avatarLarge)
                    .clip(CircleShape)
                    .border(
                        width = 2.5.dp,
                        color = if (hasUnseen) WhatsAppGreenPrimary else Color.Gray.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(3.dp)
            ) {
                UserAvatar(
                    avatarUri = authorAvatarUri,
                    displayName = authorName,
                    size = dims.avatarLarge - 6.dp
                )
            }
        }

        Spacer(modifier = Modifier.width(dims.itemSpacing * 2))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = authorName.ifBlank { authorPhone },
                fontSize = dims.bodySize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Hace ${getRelativeTime(lastUpdated)}",
                fontSize = dims.smallSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = diff / (60 * 1000L)
    val hours = minutes / 60
    return when {
        minutes < 1 -> "un momento"
        minutes < 60 -> "$minutes min"
        hours < 24 -> "$hours h"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
