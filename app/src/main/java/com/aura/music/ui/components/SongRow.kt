package com.aura.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.data.db.SongWithTags
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    songWithTags: SongWithTags,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onShowDetails: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val song = songWithTags.song
    val titleColor = if (isPlaying) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xxs)
            .clip(RoundedCornerShape(AuraRadius.Md))
            .then(
                if (isPlaying) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(AuraRadius.Md)
                    )
                } else Modifier
            )
            .background(
                if (isPlaying) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            thumbnailPath = song.thumbnailPath,
            contentDescription = song.title,
            size = 56.dp,
            cornerRadius = AuraRadius.Sm
        )

        Spacer(modifier = Modifier.width(AuraSpacing.Sm))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist?.ifBlank { null } ?: "Unknown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (songWithTags.tags.isNotEmpty()) {
                // 12dp above the chips = 12dp below them (8dp row padding
                // + 4dp outer gap to the next row), so the tags sit evenly
                // between the artist line and the next song.
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    songWithTags.tags.take(3).forEach { tag ->
                        TagChip(
                            name = tag.name,
                            colorHex = tag.colorHex,
                            selected = false,
                            onClick = {}
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(AuraSpacing.Xs))

        Text(
            text = song.durationMs.formatDuration(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (onPlayNext != null || onAddToQueue != null || onShowDetails != null || onDelete != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Song options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            }
                        )
                    }
                    if (onShowDetails != null) {
                        DropdownMenuItem(
                            text = { Text("Details & tags") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onShowDetails()
                            }
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete download") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                confirmDelete = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete?") },
            text = {
                Text("“${song.title}” will be removed from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Keep")
                }
            }
        )
    }
}