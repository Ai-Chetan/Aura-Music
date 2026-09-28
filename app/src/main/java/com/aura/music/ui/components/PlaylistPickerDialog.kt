package com.aura.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.data.db.PlaylistWithCount
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing

/**
 * Adds/removes one track (or song) to/from playlists: existing playlists
 * toggle by tap (selected = member), a name field creates + adds in one go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistPickerDialog(
    trackLabel: String,
    playlists: List<PlaylistWithCount>,
    membership: Set<Long>,
    onToggle: (PlaylistWithCount, Boolean) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
            ) {
                Text(
                    text = trackLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists yet — create the first one below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs),
                        verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                    ) {
                        playlists.forEach { playlist ->
                            TagChip(
                                name = if (playlist.itemCount > 0) {
                                    "${playlist.name} • ${playlist.itemCount}"
                                } else playlist.name,
                                colorHex = null,
                                selected = playlist.id in membership,
                                onClick = {
                                    onToggle(playlist, playlist.id in membership)
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("New playlist…") },
                        singleLine = true,
                        shape = RoundedCornerShape(AuraRadius.Md)
                    )
                    Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreateAndAdd(newName)
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Create")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
