package com.aura.music.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.data.db.PlaylistWithCount
import com.aura.music.domain.repository.ActiveDownload
import com.aura.music.domain.repository.LibrarySegment
import com.aura.music.domain.repository.LibrarySortMode
import com.aura.music.domain.repository.PlaylistImportState
import com.aura.music.domain.repository.StarterBatchStatus
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraEmptyState
import com.aura.music.ui.components.AuraSearchField
import com.aura.music.ui.components.AuraToast
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.components.ManageTagsDialog
import com.aura.music.ui.components.PlaylistPickerDialog
import com.aura.music.ui.components.ShimmerList
import com.aura.music.ui.components.TagChip
import com.aura.music.ui.components.TagFilterSection
import com.aura.music.ui.components.UnifiedTrackRow
import com.aura.music.ui.components.collectToast
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.ui.tour.TourAnchors
import com.aura.music.ui.tour.tourAnchor
import com.aura.music.util.downloadBytesDetail
import com.aura.music.util.downloadStageLabel

@Composable
fun LibraryScreen(
    onSongClick: (Long) -> Unit = {},
    onAddSongClick: () -> Unit = {},
    onPlaySong: () -> Unit = {},
    onDiscoverClick: (() -> Unit)? = null,
    onOpenPlaylist: (Long) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val playlistImports by viewModel.playlistImports.collectAsStateWithLifecycle()
    val batch by viewModel.starterBatch.collectAsStateWithLifecycle()
    val currentTrackUrl by viewModel.currentTrackUrl.collectAsStateWithLifecycle()
    val resolvingUrl by viewModel.resolvingUrl.collectAsStateWithLifecycle()
    val spiceUpOn by viewModel.spiceUp.collectAsStateWithLifecycle()
    val tagUsage by viewModel.tagUsage.collectAsStateWithLifecycle()
    val tagEditorUrl by viewModel.tagEditorUrl.collectAsStateWithLifecycle()
    val tagEditorTrack by viewModel.tagEditorTrack.collectAsStateWithLifecycle()
    val pickerUrl by viewModel.playlistPickerUrl.collectAsStateWithLifecycle()
    val pickerTrack by viewModel.playlistPickerTrack.collectAsStateWithLifecycle()
    val pickerMembership by viewModel.pickerMembership.collectAsStateWithLifecycle()
    val toast = collectToast(viewModel.messages)
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showManageTags by rememberSaveable { mutableStateOf(false) }
    var showNewPlaylist by rememberSaveable { mutableStateOf(false) }

    // Delayed list entrance: lets the splash own the cold-start frames,
    // then the list glides in and is settled before the splash expands.
    var listVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        listVisible = true
    }

    val listState = rememberLazyListState()
    // A new sort order reshuffles everything — jump back to the top so the
    // first track of the new order is visible without manual scrolling.
    var sortScrolledOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.sortMode) {
        if (!sortScrolledOnce) {
            sortScrolledOnce = true
            return@LaunchedEffect
        }
        if (state.tracks.isNotEmpty()) listState.scrollToItem(0)
    }

    // Completed downloads land at the top (recent-first), so while the user
    // is at the top stay pinned to the newest row; never yank them back.
    var lastTrackCount by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(state.tracks.size) {
        val previous = lastTrackCount
        lastTrackCount = state.tracks.size
        if (previous >= 0 && state.tracks.size > previous &&
            listState.firstVisibleItemIndex <= 1
        ) {
            listState.scrollToItem(0)
        }
    }

    // Warm stream URLs for visible bookmark rows as the list settles.
    LaunchedEffect(state.tracks) {
        viewModel.prefetchStreams(
            state.tracks.filterNot { it.isOffline }.map { it.url }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { AuraToast(toast) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AuraTopBar(
                    title = "Your Music Library",
                    subtitle = "${state.totalCount} tracks • ${state.offlineCount} offline • ${state.savedOnlyCount} saved",
                    actions = {
                        IconButton(
                            onClick = onAddSongClick,
                            modifier = Modifier.tourAnchor(TourAnchors.LIB_ADD)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Search music",
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
                )

                Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                PlaylistsRail(
                    playlists = playlists,
                    onOpen = onOpenPlaylist,
                    onCreate = { showNewPlaylist = true },
                    modifier = Modifier.tourAnchor(TourAnchors.LIB_TABS)
                )

                Spacer(modifier = Modifier.height(AuraSpacing.Xs))

                AuraSearchField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = "Search your music",
                    modifier = Modifier
                        .tourAnchor(TourAnchors.LIB_SEARCH)
                        .padding(horizontal = AuraSpacing.Md)
                )

                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                ) {
                    FilterChip(
                        selected = state.segment == LibrarySegment.ALL,
                        onClick = { viewModel.setSegment(LibrarySegment.ALL) },
                        label = { Text("All • ${state.totalCount}") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.segment == LibrarySegment.OFFLINE,
                        onClick = { viewModel.setSegment(LibrarySegment.OFFLINE) },
                        label = { Text("Offline • ${state.offlineCount}") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.segment == LibrarySegment.SAVED,
                        onClick = { viewModel.setSegment(LibrarySegment.SAVED) },
                        label = { Text("Saved • ${state.savedOnlyCount}") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))

                LibraryControlsRow(
                    onShuffle = {
                        viewModel.shuffleUnified(state.tracks) { onPlaySong() }
                    },
                    shuffleEnabled = state.tracks.isNotEmpty(),
                    spiceUp = spiceUpOn,
                    onToggleSpice = { viewModel.toggleSpiceUp() },
                    modifier = Modifier.tourAnchor(TourAnchors.LIB_CONTROLS)
                )

                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))

                if (state.tags.isNotEmpty()) {
                    TagFilterSection(
                        tags = state.tags,
                        selected = state.selectedTagNames,
                        excluded = state.excludedTagNames,
                        matchAll = state.matchAll,
                        onToggleInclude = viewModel::toggleTag,
                        onToggleExclude = viewModel::toggleExcludedTag,
                        onSetMatchAll = viewModel::setMatchAll,
                        onClearAll = {
                            viewModel.clearSelectedTags()
                            viewModel.clearExcludedTags()
                        }
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                }

                val currentBatch = batch
                if (currentBatch != null) {
                    StarterBatchCard(
                        batch = currentBatch,
                        onRetry = viewModel::retryStarterBatch,
                        onDismiss = viewModel::dismissStarterBatch
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                }

                if (downloads.isNotEmpty() || playlistImports.isNotEmpty()) {
                    DownloadsSection(
                        downloads = downloads,
                        playlists = playlistImports,
                        onCancel = viewModel::cancelDownload
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                }

                if (state.tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            state.isLoading && !state.hasActiveFilters -> ShimmerList(rows = 6)
                            state.hasActiveFilters -> AuraEmptyState(
                                icon = Icons.Default.Search,
                                title = "No matches",
                                subtitle = "Try a different search or tags",
                                actionLabel = "Clear",
                                onAction = {
                                    viewModel.setSearchQuery("")
                                    viewModel.clearSelectedTags()
                                    viewModel.clearExcludedTags()
                                }
                            )
                            state.segment == LibrarySegment.OFFLINE && state.offlineCount == 0 ->
                                AuraEmptyState(
                                    icon = Icons.Default.CloudDownload,
                                    title = "Nothing offline yet",
                                    subtitle = "Download from Search or Saved for offline play",
                                    actionLabel = "Find music",
                                    onAction = onDiscoverClick ?: onAddSongClick
                                )
                            state.segment == LibrarySegment.SAVED && state.savedOnlyCount == 0 ->
                                AuraEmptyState(
                                    icon = Icons.Default.BookmarkBorder,
                                    title = "No saved tracks",
                                    subtitle = "Save from Search — no download needed",
                                    actionLabel = "Search",
                                    onAction = onDiscoverClick ?: onAddSongClick
                                )
                            else -> AuraEmptyState(
                                icon = Icons.Default.LibraryMusic,
                                title = "Vault empty",
                                subtitle = "Add your first track",
                                actionLabel = "Download",
                                onAction = onAddSongClick
                            )
                        }
                    }
                } else {
                    AnimatedVisibility(
                        visible = listVisible,
                        modifier = Modifier.weight(1f),
                        enter = fadeIn(animationSpec = tween(450)) +
                            slideInVertically(animationSpec = tween(450)) { it / 12 }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = AuraSpacing.Xs,
                                bottom = AuraSpacing.BottomListPadding
                            )
                        ) {
                            items(
                                items = state.tracks,
                                key = { it.url },
                                contentType = { "unified" }
                            ) { item ->
                                val index = state.tracks.indexOfFirst { it.url == item.url }
                                // The tour highlights the first row when explaining
                                // tap-to-play / long-press / swipe-to-queue.
                                val rowModifier =
                                    if (item.url == state.tracks.firstOrNull()?.url) {
                                        Modifier.tourAnchor(TourAnchors.LIB_LIST)
                                    } else Modifier
                                val density = LocalDensity.current
                                // Queue-on-swipe must be a deliberate, wide gesture:
                                // 40% of the row width, never less than 96dp.
                                val swipeState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { target ->
                                        if (target == SwipeToDismissBoxValue.StartToEnd) {
                                            viewModel.queueUnified(item)
                                        }
                                        false
                                    },
                                    positionalThreshold = { totalWidth ->
                                        maxOf(
                                            totalWidth * 0.40f,
                                            with(density) { 96.dp.toPx() }
                                        )
                                    }
                                )
                                val showHint = swipeState.dismissDirection !=
                                    SwipeToDismissBoxValue.Settled
                                SwipeToDismissBox(
                                    state = swipeState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = false,
                                    backgroundContent = {
                                        AnimatedVisibility(
                                            visible = showHint,
                                            enter = fadeIn(),
                                            exit = fadeOut(animationSpec = tween(100))
                                        ) {
                                            QueueSwipeHint()
                                        }
                                    },
                                    content = {
                                        UnifiedTrackRow(
                                            item = item,
                                            isPlaying = currentTrackUrl == item.url,
                                            isResolving = resolvingUrl == item.url,
                                            onPlay = {
                                                viewModel.playUnified(state.tracks, index) {
                                                    onPlaySong()
                                                }
                                            },
                                            onPlayNext = { viewModel.playNextUnified(item) },
                                            onQueue = { viewModel.queueUnified(item) },
                                            onAddToPlaylist = { viewModel.openPlaylistPicker(item) },
                                            onTags = { viewModel.openTagEditor(item) },
                                            onLongClick = item.song?.let { song ->
                                                { onSongClick(song.id) }
                                            },
                                            onDetails = item.song?.let { song ->
                                                { onSongClick(song.id) }
                                            },
                                            onDownload = if (!item.isOffline) {
                                                { viewModel.downloadUnified(item) }
                                            } else null,
                                            onDeleteDownload = item.song?.let {
                                                { viewModel.deleteUnified(item) }
                                            },
                                            onUnsave = item.saved?.let {
                                                { viewModel.unsaveUnified(item) }
                                            },
                                            modifier = rowModifier
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNewPlaylist) {
            NewPlaylistDialog(
                onConfirm = { name ->
                    showNewPlaylist = false
                    viewModel.createPlaylist(name)
                },
                onDismiss = { showNewPlaylist = false }
            )
        }

        if (showManageTags) {
            ManageTagsDialog(
                tags = state.tags,
                usage = tagUsage,
                onDelete = viewModel::deleteTag,
                onDismiss = { showManageTags = false }
            )
        }

        // Delegated state can't smart-cast — pin to locals before use.
        val editorTrack = tagEditorTrack
        if (tagEditorUrl != null && editorTrack != null) {
            TrackTagEditorDialog(
                item = editorTrack,
                allTags = state.tags,
                onToggle = { tag -> viewModel.toggleTagOnTrack(editorTrack, tag) },
                onCreate = { name -> viewModel.createTagOnTrack(editorTrack, name) },
                onManage = {
                    viewModel.loadTagUsage()
                    showManageTags = true
                },
                onDismiss = viewModel::closeTagEditor
            )
        }

        val pickerItem = pickerTrack
        if (pickerUrl != null && pickerItem != null) {
            PlaylistPickerDialog(
                trackLabel = pickerItem.title,
                playlists = playlists,
                membership = pickerMembership,
                onToggle = { playlist, isMember ->
                    viewModel.togglePlaylistMembership(pickerItem, playlist.id, isMember)
                },
                onCreateAndAdd = { name ->
                    viewModel.createPlaylistAndAdd(pickerItem, name)
                },
                onDismiss = viewModel::closePlaylistPicker
            )
        }
    }
}

/** Horizontal rail of playlists above the library list. */
@Composable
private fun PlaylistsRail(
    playlists: List<PlaylistWithCount>,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AuraRadius.Lg))
                    .clickable { onOpen(playlist.id) }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                Text(
                    text = if (playlist.itemCount > 0) {
                        "${playlist.name} • ${playlist.itemCount}"
                    } else playlist.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item(key = "new-playlist") {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AuraRadius.Lg))
                    .clickable(onClick = onCreate)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                Text(
                    text = "New playlist",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NewPlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
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
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Tag assignment for any track (download, bookmark or both): toggle
 * existing or create new; the ViewModel applies changes to both sides of a
 * downloaded+saved pair.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackTagEditorDialog(
    item: com.aura.music.domain.repository.UnifiedTrack,
    allTags: List<com.aura.music.data.db.TagEntity>,
    onToggle: (com.aura.music.data.db.TagEntity) -> Unit,
    onCreate: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tags",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManage) {
                    Text("Manage")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (allTags.isEmpty()) {
                    Text(
                        text = "No tags yet — create the first one below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs),
                        verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                    ) {
                        allTags.forEach { tag ->
                            TagChip(
                                name = tag.name,
                                colorHex = tag.colorHex,
                                selected = item.tags.any { it.id == tag.id },
                                onClick = { onToggle(tag) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("New tag…") },
                    singleLine = true,
                    shape = RoundedCornerShape(AuraRadius.Md)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newTag.isNotBlank()) {
                        onCreate(newTag)
                        newTag = ""
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (newTag.isNotBlank()) "Add" else "Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Playlist controls for the unified list. Shuffle plays the list the user
 * is looking at (filters and all); Spice-up keeps recommended picks flowing
 * in behind the queue.
 */
@Composable
private fun LibraryControlsRow(
    onShuffle: () -> Unit,
    shuffleEnabled: Boolean,
    spiceUp: Boolean,
    onToggleSpice: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AuraRadius.Md))
                .clickable(enabled = shuffleEnabled, onClick = onShuffle)
                .padding(horizontal = AuraSpacing.Xs, vertical = AuraSpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = null,
                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
            Text(
                text = "Shuffle play",
                style = MaterialTheme.typography.labelLarge,
                color = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Spice up",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
        Switch(
            checked = spiceUp,
            onCheckedChange = onToggleSpice,
            modifier = Modifier.semantics {
                contentDescription = "Spice up: auto-add recommended picks behind the queue"
            }
        )
    }
}

/** Mid-swipe hint: icon + label so it's clear the track lands in the queue. */
@Composable
private fun QueueSwipeHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xxs)
            .clip(RoundedCornerShape(AuraRadius.Md))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = AuraSpacing.Lg),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
            Text(
                text = "Queue",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Starter-batch report: how many of the Getting Started picks landed, and
 * the exact reason printed under every song that did not.
 */
@Composable
private fun StarterBatchCard(
    batch: StarterBatchStatus,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md)
    ) {
        Column(modifier = Modifier.padding(AuraSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                Text(
                    text = if (batch.finished) "Starter songs"
                    else "Getting your starter songs • ${batch.done}/${batch.total}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
            if (!batch.finished) {
                LinearProgressIndicator(
                    progress = {
                        if (batch.total > 0) (batch.done / batch.total.toFloat()).coerceIn(0f, 1f)
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(percent = 50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                Text(
                    text = "Running in the background — keep exploring, new songs land at the top as they finish.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (batch.failed.isEmpty()) "${batch.succeeded} added to your vault"
                    else "${batch.succeeded} added • ${batch.failed.size} failed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (batch.failed.isEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                if (batch.failed.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    // Capped-height scrolling list: a long failure list must
                    // not push the Retry/Dismiss row (and the song list below)
                    // off-screen with no way to reach them.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(
                            items = batch.failed,
                            key = { it.url }
                        ) { failed ->
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = failed.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = failed.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = onRetry) { Text("Retry failed") }
                    }
                } else {
                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    Row {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

/**
 * Live download queue: what is being fetched right now and how far along
 * each item is. Sits above the track list; disappears when idle.
 */
@Composable
private fun DownloadsSection(
    downloads: List<ActiveDownload>,
    playlists: List<PlaylistImportState>,
    onCancel: (java.util.UUID) -> Unit
) {
    // Collapsed by default: one compact overall-progress header. Tap to
    // expand the per-song rows. Finished songs always stream into the
    // list below, ready to play, while this card stays small.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val totalCount = downloads.size + playlists.size
    val overall = remember(downloads, playlists) {
        if (totalCount == 0) 0
        else ((downloads.sumOf { it.percent } + playlists.sumOf { it.percent }) / totalCount)
            .coerceIn(0, 100)
    }
    // Name whatever is closest to landing so the header feels live.
    val headline = remember(downloads, playlists) {
        val topDownload = downloads.maxByOrNull { it.percent }
        val topPlaylist = playlists.maxByOrNull { it.percent }
        val current = when {
            topPlaylist != null &&
                (topDownload == null || topPlaylist.percent >= topDownload.percent) ->
                listOfNotNull(
                    topPlaylist.playlistTitle.ifBlank { null },
                    topPlaylist.current.ifBlank { null }
                ).joinToString(" • ").ifEmpty { "Reading playlist…" }
            topDownload != null ->
                topDownload.title ?: topDownload.url.ifBlank { "Preparing…" }
            else -> ""
        }
        if (totalCount > 1 && current.isNotBlank()) "$current • +${totalCount - 1} more"
        else current
    }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md)
    ) {
        Column(modifier = Modifier.padding(AuraSpacing.Md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AuraRadius.Md))
                    .clickable { expanded = !expanded }
                    .padding(vertical = AuraSpacing.Xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Downloading • $overall%",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (headline.isNotBlank()) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse downloads" else "Expand downloads",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
            LinearProgressIndicator(
                progress = { (overall / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    playlists.forEach { playlist ->
                        Column(modifier = Modifier.padding(vertical = AuraSpacing.Xxs)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.playlistTitle.ifBlank { "Reading playlist…" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (playlist.total > 0) {
                                            "${playlist.done} of ${playlist.total}" +
                                                (playlist.current.takeIf { it.isNotBlank() }
                                                    ?.let { " • $it" } ?: "")
                                        } else {
                                            "Reading playlist…"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onCancel(playlist.workId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel import",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (playlist.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(percent = 50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    downloads.forEach { download ->
                        Column(modifier = Modifier.padding(vertical = AuraSpacing.Xxs)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = download.title ?: download.url.ifBlank { "Preparing…" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = downloadDetail(download),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onCancel(download.workId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel download",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (download.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(percent = 50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun downloadDetail(download: ActiveDownload): String =
    downloadBytesDetail(
        percent = download.percent,
        bytesDone = download.bytesDone,
        bytesTotal = download.bytesTotal,
        stageLabel = downloadStageLabel(download.stage)
    )
