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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.ActiveDownload
import com.aura.music.domain.repository.PlaylistImportState
import com.aura.music.domain.repository.StarterBatchStatus
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraEmptyState
import com.aura.music.ui.components.AuraSearchField
import com.aura.music.ui.components.AuraToast
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.components.SongRow
import com.aura.music.ui.components.TagChip
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing

/** Rows composed in the first window; more load as the user scrolls. */
private const val LIBRARY_PAGE_SIZE = 20
/** Start loading the next window this many rows before the end. */
private const val LOAD_MORE_THRESHOLD = 6

@Composable
fun LibraryScreen(
    onSongClick: (Long) -> Unit = {},
    onAddSongClick: () -> Unit = {},
    onPlaySong: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentTrackId.collectAsStateWithLifecycle()
    val downloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val playlistImports by viewModel.playlistImports.collectAsStateWithLifecycle()
    val batch by viewModel.starterBatch.collectAsStateWithLifecycle()
    var toast by remember { mutableStateOf<String?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            toast = message
            delay(1800)
            toast = null
        }
    }

    // Delayed list entrance: lets the splash own the cold-start frames,
    // then the list glides in and is settled before the splash expands.
    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        listVisible = true
    }

    val songListState = rememberLazyListState()
    // A new sort order reshuffles everything — jump back to the top so the
    // first song of the new order is visible without manual scrolling.
    LaunchedEffect(state.sortMode) {
        songListState.scrollToItem(0)
    }

    // Batched rendering: with hundreds of songs only the first window is
    // composed up front; scrolling near the end grows the window. Any
    // filter/sort/search change restarts from the first window.
    var visibleLimit by remember { mutableIntStateOf(LIBRARY_PAGE_SIZE) }
    LaunchedEffect(
        state.searchQuery,
        state.selectedTagNames,
        state.excludedTagNames,
        state.matchAll,
        state.sortMode
    ) {
        visibleLimit = LIBRARY_PAGE_SIZE
    }
    val visibleSongs = remember(state.songs, visibleLimit) {
        state.songs.take(visibleLimit)
    }
    val nearEnd by remember {
        derivedStateOf {
            val lastVisible =
                songListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= visibleLimit - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.songs.size) {
        if (nearEnd && visibleLimit < state.songs.size) {
            visibleLimit = (visibleLimit + LIBRARY_PAGE_SIZE)
                .coerceAtMost(state.songs.size)
        }
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
                title = "AURA Music",
                subtitle = if (state.songs.isEmpty()) "Vault" else "${state.songs.size} tracks",
                actions = {
                    if (state.songs.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val songs = state.songs.map { it.song }
                                if (songs.isNotEmpty()) {
                                    val shuffled = songs.shuffled()
                                    // Shuffle play: reuse playSong path via first item
                                    val first = state.songs.firstOrNull { it.song.id == shuffled.first().id }
                                        ?: state.songs.first()
                                    viewModel.playSong(first)
                                    onPlaySong()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onBackupClick) {
                        Icon(
                            imageVector = Icons.Default.ImportExport,
                            contentDescription = "Backup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    IconButton(onClick = onGuideClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Getting started",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            AuraSearchField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = "Search songs",
                modifier = Modifier.padding(horizontal = AuraSpacing.Md)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            if (state.tags.isNotEmpty()) {
                FilterSection(
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

            val hasFilters = state.searchQuery.isNotBlank() ||
                state.selectedTagNames.isNotEmpty() || state.excludedTagNames.isNotEmpty()
            if (state.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasFilters) {
                        AuraEmptyState(
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
                    } else {
                        AuraEmptyState(
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
                    state = songListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = AuraSpacing.Xs, bottom = AuraSpacing.BottomListPadding)
                ) {
                    items(
                        items = visibleSongs,
                        key = { it.song.id },
                        contentType = { "song" }
                    ) { songWithTags ->
                        // Swipe right → add to queue (row snaps back, nothing is removed).
                        val swipeState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { target ->
                                if (target == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.addToQueue(songWithTags.song)
                                }
                                false
                            }
                        )
                        // Distance-based hint: track the row's real on-screen
                        // shift (window bounds catch the swipe transform;
                        // layout position does not). Shows past ~64dp of
                        // drag, hides the instant it slides back under it.
                        var baseX by remember { mutableFloatStateOf(Float.NaN) }
                        var dragPx by remember { mutableFloatStateOf(0f) }
                        val hintThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
                        val showHint by remember {
                            derivedStateOf { dragPx > hintThresholdPx }
                        }
                        SwipeToDismissBox(
                            state = swipeState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = false,
                            backgroundContent = {
                                // Only exists mid-drag: at rest nothing is composed
                                // so nothing bleeds through the translucent glass row.
                                AnimatedVisibility(
                                    visible = showHint,
                                    enter = fadeIn(),
                                    exit = fadeOut(animationSpec = tween(100))
                                ) {
                                    // Swipe intent: icon + label so it's clear
                                    // the song lands in the queue.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xxs)
                                            .clip(RoundedCornerShape(AuraRadius.Md))
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
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
                            },
                            content = {
                                SongRow(
                                    songWithTags = songWithTags,
                                    isPlaying = currentSongId == songWithTags.song.id,
                                    onClick = {
                                        viewModel.playSong(songWithTags)
                                        onPlaySong()
                                    },
                                    onLongClick = { onSongClick(songWithTags.song.id) },
                                    onPlayNext = { viewModel.playNext(songWithTags.song) },
                                    onAddToQueue = { viewModel.addToQueue(songWithTags.song) },
                                    onShowDetails = { onSongClick(songWithTags.song.id) },
                                    onDelete = {
                                        viewModel.deleteSong(
                                            songWithTags.song.id,
                                            songWithTags.song.title
                                        )
                                    },
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        val x = coords.boundsInWindow().left
                                        if (baseX.isNaN() || swipeState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                                            baseX = x
                                        }
                                        dragPx = x - baseX
                                    }
                                )
                            }
                        )
                    }
                    if (state.songs.size > visibleLimit) {
                        item(
                            key = "loading-more",
                            contentType = { "loading" }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AuraSpacing.Md),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                                Text(
                                    text = "Showing $visibleLimit of ${state.songs.size}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                }
            }
        }
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
                    text = if (batch.finished) "Starter downloads"
                    else "Getting starters • ${batch.done}/${batch.total}",
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
                    androidx.compose.foundation.lazy.LazyColumn(
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
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = failed.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
 * each item is. Sits above the song list; disappears when idle.
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
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (headline.isNotBlank()) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (playlist.total > 0) {
                                    "${playlist.done} of ${playlist.total}" +
                                        (playlist.current.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "")
                                } else {
                                    "Reading playlist…"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = downloadDetail(download),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

private fun downloadStageLabel(stage: String): String = when (stage) {
    "resolving" -> "Reading…"
    "downloading" -> "Downloading…"
    "saving" -> "Saving…"
    else -> "Working…"
}

private fun downloadDetail(download: ActiveDownload): String {
    val mb = if (download.bytesDone != null && download.bytesTotal != null && download.bytesTotal > 0) {
        val done = download.bytesDone / 1_048_576.0
        val total = download.bytesTotal / 1_048_576.0
        "%.1f / %.1f MB".format(done, total)
    } else if (download.bytesDone != null && download.bytesDone > 0) {
        "%.1f MB".format(download.bytesDone / 1_048_576.0)
    } else {
        null
    }
    return listOfNotNull("${download.percent}%", downloadStageLabel(download.stage), mb)
        .joinToString(" • ")
}

/**
 * Compact tag filtering: a single bar that expands only when needed.
 */
@Composable
private fun FilterSection(
    tags: List<TagEntity>,
    selected: Set<String>,
    excluded: Set<String>,
    matchAll: Boolean,
    onToggleInclude: (String) -> Unit,
    onToggleExclude: (String) -> Unit,
    onSetMatchAll: (Boolean) -> Unit,
    onClearAll: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val activeCount = selected.size + excluded.size

    val summary = when {
        activeCount == 0 -> "Filters"
        selected.isNotEmpty() && excluded.isNotEmpty() ->
            "${selected.size} in • ${excluded.size} out"
        selected.isNotEmpty() -> "${selected.size} included"
        else -> "${excluded.size} excluded"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AuraRadius.Md))
                .clickable { expanded = !expanded }
                .padding(horizontal = AuraSpacing.Xxs, vertical = AuraSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = if (activeCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(AuraSpacing.Xs))

            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = if (activeCount > 0) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )

            if (activeCount > 0) {
                TextButton(onClick = onClearAll) {
                    Text("Clear")
                }
            }

            Text(
                text = if (expanded) "–" else "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Include",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(AuraSpacing.Xs))

                    if (selected.isNotEmpty()) {
                        FilterChip(
                            selected = matchAll,
                            onClick = { onSetMatchAll(true) },
                            label = { Text("AND") }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        FilterChip(
                            selected = !matchAll,
                            onClick = { onSetMatchAll(false) },
                            label = { Text("OR") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                ) {
                    items(tags) { tag ->
                        TagChip(
                            name = tag.name,
                            colorHex = tag.colorHex,
                            selected = tag.name in selected,
                            onClick = { onToggleInclude(tag.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                Text(
                    text = "Excluded",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                ) {
                    items(tags) { tag ->
                        TagChip(
                            name = tag.name,
                            colorHex = tag.colorHex,
                            selected = tag.name in excluded,
                            onClick = { onToggleExclude(tag.name) }
                        )
                    }
                }
            }
        }
    }
}