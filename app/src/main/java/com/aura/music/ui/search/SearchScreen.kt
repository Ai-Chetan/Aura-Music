package com.aura.music.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.domain.repository.YouTubeTrack
import com.aura.music.ui.components.AlbumArt
import com.aura.music.ui.components.AuraEmptyState
import com.aura.music.ui.components.AuraSearchField
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration
import kotlinx.coroutines.delay

@Composable
fun SearchPanel(
    onPlayResult: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
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
                    text = "Tap ▶ to stream instantly, ⤓ to save to your vault.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AuraSpacing.Lg)
                )

                Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                when (val phase = state.phase) {
                    is SearchPhase.Idle -> {
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

                    is SearchPhase.Searching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                                Text(
                                    text = "Searching YouTube…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                                key = { it.url }
                            ) { track ->
                                SearchResultRow(
                                    track = track,
                                    isResolving = state.resolvingUrl == track.url,
                                    isQueued = track.url in state.queuedUrls,
                                    onPlay = {
                                        viewModel.playNow(track, onPlayResult)
                                    },
                                    onDownload = { viewModel.download(track) }
                                )
                            }
                        }
                    }
                }
    }
}

@Composable
private fun SearchResultRow(
    track: YouTubeTrack,
    isResolving: Boolean,
    isQueued: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            thumbnailPath = track.thumbnailUrl,
            contentDescription = track.title,
            size = 56.dp,
            cornerRadius = AuraRadius.Md
        )
        Spacer(modifier = Modifier.width(AuraSpacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    track.artist,
                    if (track.durationMs > 0) track.durationMs.formatDuration() else null
                ).joinToString(" • ").ifEmpty { "YouTube" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isResolving) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
        } else {
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Stream now",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(
            onClick = onDownload,
            enabled = !isQueued
        ) {
            Icon(
                imageVector = if (isQueued) Icons.Default.Done else Icons.Default.CloudDownload,
                contentDescription = if (isQueued) "Download queued" else "Save to vault",
                tint = if (isQueued) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
