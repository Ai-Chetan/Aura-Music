package com.aura.music.ui.songdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.aura.music.ui.components.AlbumArt
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.QualityBadge
import com.aura.music.ui.components.TagChip
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongDetailScreen(
    onBack: () -> Unit,
    viewModel: SongDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newTagName by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        val song = state.song
        AuraTopBar(
            title = "Details",
            subtitle = song?.let {
                listOfNotNull(
                    it.audioFormat.uppercase(),
                    it.bitrateKbps?.let { b -> "$b kbps" },
                    it.durationMs.formatDuration()
                ).joinToString(" • ")
            },
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Md))

        if (song == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AuraSpacing.Xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                thumbnailPath = song.thumbnailPath,
                contentDescription = song.title,
                size = 76.dp,
                cornerRadius = 16.dp
            )

            Spacer(modifier = Modifier.width(AuraSpacing.Md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!song.artist.isNullOrBlank()) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                QualityBadge(codec = song.audioFormat, bitrateKbps = song.bitrateKbps)
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Xl))

        Text(
            text = "Tags",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = AuraSpacing.Md)
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Xs))

        AnimatedVisibility(
            visible = state.songTags.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
            ) {
                state.songTags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(
                            name = tag.name,
                            colorHex = tag.colorHex,
                            selected = true,
                            onClick = { viewModel.removeTagById(tag.id) }
                        )
                        IconButton(
                            onClick = { viewModel.removeTagById(tag.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove ${tag.name}",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (state.songTags.isEmpty()) {
            Text(
                text = "No tags yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AuraSpacing.Md)
            )
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Md))

        if (state.availableTags.isNotEmpty()) {
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AuraSpacing.Md)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Xs))

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
            ) {
                state.availableTags.forEach { tag ->
                    TagChip(
                        name = tag.name,
                        colorHex = tag.colorHex,
                        selected = false,
                        onClick = { viewModel.addTagById(tag.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.Md))
        }

        Text(
            text = "New tag",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AuraSpacing.Md)
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Xs))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tag name") },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(AuraSpacing.Xs))

            TextButton(
                onClick = {
                    viewModel.createAndAddTag(newTagName)
                    newTagName = ""
                },
                enabled = newTagName.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Xxl))
    }
    }
}