package com.aura.music.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.ui.components.AuraEmptyState
import com.aura.music.ui.components.AuraSearchField
import com.aura.music.ui.components.GatePlaceholder
import com.aura.music.ui.components.ShimmerList
import com.aura.music.ui.components.StreamRow
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration

@Composable
fun SearchPanel(
    onPlayResult: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    showInput: Boolean = true,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedUrls by viewModel.savedUrls.collectAsStateWithLifecycle()
    val gate by viewModel.gateState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        if (showInput) {
        AuraSearchField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = "Song or artist",
            modifier = Modifier.padding(horizontal = AuraSpacing.Md),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Sm))

        Button(
            onClick = viewModel::search,
            enabled = state.query.isNotBlank() &&
                state.phase !is SearchPhase.Searching,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Md)
                .height(52.dp),
            shape = RoundedCornerShape(AuraRadius.Md)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
            Text("Search YouTube")
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Xs))

        Text(
            text = if (gate.allowed) {
                "Tap ▶ to stream instantly, 🔖 to save, ⤓ to download offline."
            } else {
                gate.reason?.message() ?: "Search needs a data connection."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AuraSpacing.Lg)
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Sm))
        }

        when (val phase = state.phase) {
            is SearchPhase.Idle -> {
                if (!gate.allowed) {
                    GatePlaceholder(
                        icon = Icons.Default.CloudOff,
                        title = "Search needs data",
                        subtitle = gate.reason?.message()
                            ?: "Turn on dynamic content to search YouTube.",
                        primaryLabel = "Open settings",
                        onPrimary = onOpenSettings
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AuraEmptyState(
                            icon = Icons.Default.Search,
                            title = "Find any song",
                            subtitle = "Results stream straight from YouTube"
                        )
                    }
                }
            }

            is SearchPhase.Searching -> {
                ShimmerList(rows = 6)
            }

            is SearchPhase.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AuraEmptyState(
                        icon = Icons.Default.Search,
                        title = "No songs found",
                        subtitle = "Try a different song or artist"
                    )
                }
            }

            is SearchPhase.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AuraEmptyState(
                        icon = Icons.Default.Search,
                        title = "Search failed",
                        subtitle = phase.message,
                        actionLabel = "Retry",
                        onAction = viewModel::search
                    )
                }
            }

            is SearchPhase.Results -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        top = AuraSpacing.Xs,
                        bottom = AuraSpacing.Md
                    ),
                    verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                ) {
                    items(
                        items = phase.tracks,
                        key = { it.url },
                        contentType = { "result" }
                    ) { track ->
                        StreamRow(
                            title = track.title,
                            subtitle = listOfNotNull(
                                track.artist,
                                if (track.durationMs > 0) track.durationMs.formatDuration() else null
                            ).joinToString(" • ").ifEmpty { "YouTube" },
                            thumbnailUrl = track.thumbnailUrl,
                            onPlay = { viewModel.playNow(track, onPlayResult) },
                            isResolving = state.resolvingUrl == track.url,
                            trailing = {
                                val saved = track.url in savedUrls
                                IconButton(onClick = { viewModel.toggleSave(track) }) {
                                    Icon(
                                        imageVector = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = if (saved) "Remove from Saved" else "Save (needs internet)",
                                        tint = if (saved) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.download(track) },
                                    enabled = track.url !in state.queuedUrls
                                ) {
                                    val queued = track.url in state.queuedUrls
                                    Icon(
                                        imageVector = if (queued) Icons.Default.Done else Icons.Default.CloudDownload,
                                        contentDescription = if (queued) "Download queued" else "Save to vault",
                                        tint = if (queued) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
