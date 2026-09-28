package com.aura.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.TagUsage
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.ui.theme.TagColors

/**
 * Lists every tag with its usage and a delete action. Deleting removes the
 * tag from ALL songs and saved bookmarks (the DB cascades the assignments),
 * so each delete asks for confirmation first.
 */
@Composable
fun ManageTagsDialog(
    tags: List<TagEntity>,
    usage: Map<Long, TagUsage>,
    onDelete: (TagEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<TagEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage tags") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xxs)
            ) {
                Text(
                    text = "Deleting a tag removes it from every song and saved track that uses it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tags.isEmpty()) {
                    Text(
                        text = "No tags yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    tags.forEach { tag ->
                        TagDeleteRow(
                            tag = tag,
                            usage = usage[tag.id],
                            onClick = { pendingDelete = tag }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )

    pendingDelete?.let { tag ->
        val counts = usage[tag.id] ?: TagUsage(0, 0)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${tag.name}\"?") },
            text = { Text(deleteMessage(counts)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(tag)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TagDeleteRow(
    tag: TagEntity,
    usage: TagUsage?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parseTagColor(TagColors.displayFor(tag.name, tag.colorHex)))
        )
        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (usage != null) {
                Text(
                    text = usageLabel(usage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete ${tag.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

private fun usageLabel(usage: TagUsage): String {
    val parts = listOfNotNull(
        if (usage.songs > 0) plural(usage.songs, "song") else null,
        if (usage.savedTracks > 0) plural(usage.savedTracks, "saved track") else null
    )
    return if (parts.isEmpty()) "Not used" else parts.joinToString(" • ")
}

private fun deleteMessage(usage: TagUsage): String {
    val parts = listOfNotNull(
        if (usage.songs > 0) plural(usage.songs, "song") else null,
        if (usage.savedTracks > 0) plural(usage.savedTracks, "saved track") else null
    )
    return if (parts.isEmpty()) {
        "This tag isn't used anywhere. It will be deleted permanently."
    } else {
        "It's on ${parts.joinToString(" and ")} and will be removed from all of them. This can't be undone."
    }
}
