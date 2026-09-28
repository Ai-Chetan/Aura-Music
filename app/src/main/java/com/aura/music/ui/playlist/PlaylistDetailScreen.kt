package com.aura.music.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.domain.repository.LibrarySortMode
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraEmptyState
import com.aura.music.ui.components.AuraSearchField
import com.aura.music.ui.components.AuraToast
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.PlaylistPickerDialog
import com.aura.music.ui.components.TagChip
import com.aura.music.ui.components.TagFilterSection
import com.aura.music.ui.components.UnifiedTrackRow
import com.aura.music.ui.components.collectToast
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration

/**
 * One playlist: header with play/shuffle + tag filters, the filtered track
 * list (what plays is exactly what's listed), and an add-tracks picker.
 */
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onPlayStarted: () -> Unit = {},
    onOpenSong: (Long) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTrackUrl by viewModel.currentTrackUrl.collectAsStateWithLifecycle()
    val resolvingUrl by viewModel.resolvingUrl.collectAsStateWithLifecycle()
    val toast = collectToast(viewModel.messages)
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showAddTracks by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.close.collect { onBack() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = AuraSpacing.BottomListPadding)
        ) {
            AuraTopBar(
                title = state.playlist?.name ?: "Playlist",
                subtitle = if (state.loading) "" else listOfNotNull(
                    "${state.allTracks.size} tracks",
                    if (state.offlineCount > 0) "$state.offlineCount offline" else null,
                    if (state.streamCount > 0) "$state.streamCount stream" else null
                ).joinToString(" • "),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete playlist",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            if (!state.loading) {
                // ---- Play / shuffle header ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.filtersActive) {
                            "${state.tracks.size} of ${state.allTracks.size} shown"
                        } else {
                            state.totalDurationMs.formatDuration()
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AuraRadius.Md))
                            .clickable(enabled = state.tracks.isNotEmpty()) {
                                viewModel.shuffleFiltered { onPlayStarted() }
                            }
                            .padding(horizontal = AuraSpacing.Xs, vertical = AuraSpacing.Xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (state.tracks.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.tracks.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))

                // ---- Search + add tracks ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuraSearchField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = "Search in playlist",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                    IconButton(onClick = { showAddTracks = true }) {
                        Icon(
                            imageVector = Icons.Default.LibraryAdd,
                            contentDescription = "Add tracks to playlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort: ${state.sortMode.label}",
                                tint = if (state.sortMode == LibrarySortMode.RECENT) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            LibrarySortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        viewModel.setSortMode(mode)
                                        sortMenuOpen = false
                                    },
                                    leadingIcon = if (mode == state.sortMode) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))

                // ---- Tag filters: filtered list is what plays ----
                if (state.tags.isNotEmpty()) {
                    TagFilterSection(
                        tags = state.tags,
                        selected = state.selectedTagNames,
                        excluded = state.excludedTagNames,
                        matchAll = state.matchAll,
                        onToggleInclude = viewModel::toggleTag,
                        onToggleExclude = viewModel::toggleExcludedTag,
                        onSetMatchAll = viewModel::setMatchAll,
                        onClearAll = viewModel::clearFilters
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                }

                // ---- Play-the-filtered-list hint ----
                if (state.filtersActive && state.tracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                        Text(
                            text = "Tap any track to play the ${state.tracks.size} filtered tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ---- Track list (order preserved: playback follows it) ----
                if (state.tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AuraSpacing.Xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            state.allTracks.isEmpty() -> AuraEmptyState(
                                icon = Icons.Default.QueueMusic,
                                title = "Empty playlist",
                                subtitle = "Add tracks from your library",
                                actionLabel = "Add tracks",
                                onAction = { showAddTracks = true }
                            )
                            else -> AuraEmptyState(
                                icon = Icons.Default.Search,
                                title = "No matches",
                                subtitle = "Tags or search hide every track",
                                actionLabel = "Clear filters",
                                onAction = { viewModel.clearFilters() }
                            )
                        }
                    }
                } else {
                    Column {
                        state.tracks.forEachIndexed { index, track ->
                            UnifiedTrackRow(
                                item = track,
                                isPlaying = currentTrackUrl == track.url,
                                isResolving = resolvingUrl == track.url,
                                onPlay = { viewModel.playFiltered(index) { onPlayStarted() } },
                                onPlayNext = { viewModel.playNext(track) },
                                onQueue = { viewModel.queue(track) },
                                onAddToPlaylist = null,
                                onTags = null,
                                onRemoveFromPlaylist = { viewModel.removeFromPlaylist(track) },
                                onDetails = track.song?.let { song ->
                                    { onOpenSong(song.id) }
                                },
                                onDownload = if (!track.isOffline) {
                                    { viewModel.downloadTrack(track) }
                                } else null,
                                onDeleteDownload = if (track.isOffline) {
                                    { viewModel.deleteTrack(track) }
                                } else null,
                                onUnsave = track.saved?.let {
                                    { viewModel.unsaveTrack(track) }
                                }
                            )
                        }
                    }
                }
            }
        }

        AuraToast(toast)
    }

    if (showRename) {
        val current = state.playlist?.name.orEmpty()
        var name by rememberSaveable { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    shape = RoundedCornerShape(AuraRadius.Md)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRename = false
                        viewModel.rename(name)
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "“${state.playlist?.name}” will be deleted. The songs and " +
                        "bookmarks themselves stay in your library."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            }
        )
    }

    if (showAddTracks) {
        AddTracksDialog(
            state = state,
            onAdd = viewModel::addToPlaylist,
            onDismiss = { showAddTracks = false }
        )
    }
}

/** Library picker: tap a track to add it to this playlist (already-in hints). */
@Composable
private fun AddTracksDialog(
    state: PlaylistDetailUiState,
    onAdd: (com.aura.music.domain.repository.UnifiedTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val memberUrls = remember(state.allTracks) { state.allTracks.map { it.url }.toSet() }
    val candidates = remember(state.libraryTracks, memberUrls) {
        state.libraryTracks.filterNot { it.url in memberUrls }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tracks") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                if (candidates.isEmpty()) {
                    Text(
                        text = "Every library track is already in this playlist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xxs)
                    ) {
                        items(candidates, key = { it.url }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AuraRadius.Md))
                                    .clickable { onAdd(track) }
                                    .padding(horizontal = AuraSpacing.Xs, vertical = AuraSpacing.Xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                            track.artist?.ifBlank { null },
                                            if (track.isOffline) "Offline" else "Saved"
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (track.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(track.tags.take(3), key = { it.id }) { tag ->
                                            TagChip(
                                                name = tag.name,
                                                colorHex = tag.colorHex,
                                                selected = false,
                                                onClick = {},
                                                enabled = false,
                                                singleLine = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
