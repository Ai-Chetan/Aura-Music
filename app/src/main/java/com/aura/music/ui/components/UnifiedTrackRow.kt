package com.aura.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.music.domain.repository.UnifiedTrack

/**
 * One row for any library track — downloaded or saved bookmark — in the
 * unified Library list and playlist details. Menu items appear per track
 * kind; both destructive actions confirm first.
 */
@Composable
fun UnifiedTrackRow(
    item: UnifiedTrack,
    isPlaying: Boolean,
    isResolving: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onTags: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    onUnsave: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmUnsave by rememberSaveable { mutableStateOf(false) }

    val status = when {
        item.isOffline && item.saved != null -> "Offline • saved"
        item.isOffline -> "Offline"
        else -> "Streams • needs internet"
    }

    MediaRowShell(
        title = item.title,
        subtitle = listOfNotNull(item.artist?.ifBlank { null }, status)
            .joinToString(" • ").ifEmpty { "YouTube" },
        subtitleColor = if (item.isOffline) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        thumbnailPath = item.thumbnail,
        durationMs = item.durationMs,
        onClick = onPlay,
        onLongClick = onLongClick,
        isPlaying = isPlaying,
        tags = item.tags,
        modifier = modifier
    ) {
        if (isResolving) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    MenuEntry("Play", Icons.Default.PlayArrow) {
                        menuExpanded = false; onPlay()
                    }
                    MenuEntry("Play next", Icons.Default.QueueMusic) {
                        menuExpanded = false; onPlayNext()
                    }
                    MenuEntry("Add to queue", Icons.AutoMirrored.Filled.PlaylistAdd) {
                        menuExpanded = false; onQueue()
                    }
                    onAddToPlaylist?.let { handler ->
                        MenuEntry("Add to playlist…", Icons.Default.QueueMusic) {
                            menuExpanded = false; handler()
                        }
                    }
                    onTags?.let { handler ->
                        MenuEntry("Tags…", Icons.Default.Tag) {
                            menuExpanded = false; handler()
                        }
                    }
                    onDetails?.let { handler ->
                        MenuEntry("Details", Icons.Default.Info) {
                            menuExpanded = false; handler()
                        }
                    }
                    onDownload?.let { handler ->
                        MenuEntry("Download offline", Icons.Default.CloudDownload) {
                            menuExpanded = false; handler()
                        }
                    }
                    onRemoveFromPlaylist?.let { handler ->
                        MenuEntry("Remove from playlist", Icons.Default.DeleteOutline) {
                            menuExpanded = false; handler()
                        }
                    }
                    onDeleteDownload?.let {
                        MenuEntry("Remove download", Icons.Default.Delete, danger = true) {
                            menuExpanded = false; confirmDelete = true
                        }
                    }
                    onUnsave?.let {
                        MenuEntry("Remove from Saved", Icons.Default.Delete, danger = true) {
                            menuExpanded = false; confirmUnsave = true
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove download?") },
            text = { Text("“${item.title}” will be deleted from this device (the saved bookmark, if any, stays).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteDownload?.invoke()
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            }
        )
    }

    if (confirmUnsave) {
        AlertDialog(
            onDismissRequest = { confirmUnsave = false },
            title = { Text("Remove?") },
            text = { Text("“${item.title}” will be removed from Saved (downloads stay).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnsave = false
                        onUnsave?.invoke()
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsave = false }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun MenuEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick
    )
}
