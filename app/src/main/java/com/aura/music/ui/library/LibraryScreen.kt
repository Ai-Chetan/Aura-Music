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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.ActiveDownload
import com.aura.music.domain.repository.PlaylistImportState
import com.aura.music.domain.repository.StarterBatchStatus
import com.aura.music.ui.components.AlbumArt
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraEmptyState
import com.aura.music.ui.components.AuraSearchField
import com.aura.music.ui.components.AuraToast
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.components.MediaRowShell
import com.aura.music.ui.components.ShimmerList
import com.aura.music.ui.components.SongRow
import com.aura.music.ui.components.TagChip
import com.aura.music.ui.components.collectToast
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.ui.tour.TourAnchors
import com.aura.music.ui.tour.tourAnchor
import com.aura.music.util.downloadBytesDetail
import com.aura.music.util.downloadStageLabel
import com.aura.music.util.formatDuration

@Composable
fun LibraryScreen(
    onSongClick: (Long) -> Unit = {},
    onAddSongClick: () -> Unit = {},
    onPlaySong: () -> Unit = {},
    onDiscoverClick: (() -> Unit)? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentTrackId.collectAsStateWithLifecycle()
    val downloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val playlistImports by viewModel.playlistImports.collectAsStateWithLifecycle()
    val batch by viewModel.starterBatch.collectAsStateWithLifecycle()
    val savedTracks by viewModel.savedTracks.collectAsStateWithLifecycle()
    val savedResolvingUrl by viewModel.savedResolvingUrl.collectAsStateWithLifecycle()
    val savedFilter by viewModel.savedFilterState.collectAsStateWithLifecycle()
    val savedLoading by viewModel.savedLoading.collectAsStateWithLifecycle()
    val savedSort by viewModel.savedSortMode.collectAsStateWithLifecycle()
    val spiceUpOn by viewModel.spiceUp.collectAsStateWithLifecycle()
    val toast = collectToast(viewModel.messages)
    var sortMenuOpen by remember { mutableStateOf(false) }
    // 0 = Downloaded (offline), 1 = Saved (streams, needs internet).
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Delayed list entrance: lets the splash own the cold-start frames,
    // then the list glides in and is settled before the splash expands.
    // Saveable so rotation doesn't replay the entrance (or collapse paging).
    var listVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        listVisible = true
    }

    val songListState = rememberLazyListState()
    // A new sort order reshuffles everything — jump back to the top so the
    // first song of the new order is visible without manual scrolling.
    // Skips the very first composition (nothing to reposition yet).
    var sortScrolledOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.sortMode) {
        if (!sortScrolledOnce) {
            sortScrolledOnce = true
            return@LaunchedEffect
        }
        if (state.songs.isNotEmpty()) {
            songListState.scrollToItem(0)
        }
    }

    // Completed downloads land at the top of the list (recent-first), but
    // LazyColumn anchors the viewport to the first visible row's key — so
    // new songs would pile up just above the fold, unseen. While the user
    // is at the top (watching downloads land), stay pinned to the newest
    // row; if they scrolled away, never yank them back.
    var lastSongCount by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(state.songs.size) {
        val previous = lastSongCount
        lastSongCount = state.songs.size
        if (previous >= 0 && state.songs.size > previous &&
            songListState.firstVisibleItemIndex <= 1
        ) {
            songListState.scrollToItem(0)
        }
    }

    // The Saved list the shuffle button plays: same filter + search the tab
    // shows, so "shuffle" means the playlist the user is actually looking at.
    val savedShuffleList = remember(savedTracks, state.searchQuery, savedFilter) {
        savedTracks.filter { item ->
            matchesLibraryFilters(
                title = item.track.title,
                artist = item.track.artist,
                tagNames = item.tags.map { it.name }.toSet(),
                query = state.searchQuery.trim(),
                selected = savedFilter.selectedTagNames,
                excluded = savedFilter.excludedTagNames,
                matchAll = savedFilter.matchAll
            )
        }.map { it.track }
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
                subtitle = "${state.songs.size} downloaded • ${savedTracks.size} saved",
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
                        // The sort menu follows the active tab: Downloaded
                        // order vs Saved order are stored separately.
                        val activeSort = if (tab == 0) state.sortMode else savedSort
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort: ${activeSort.label}",
                                tint = if (activeSort == LibrarySortMode.RECENT) {
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
                                        if (tab == 0) viewModel.setSortMode(mode)
                                        else viewModel.setSavedSortMode(mode)
                                        sortMenuOpen = false
                                    },
                                    leadingIcon = if (mode == activeSort) {
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

            LibraryTabs(
                tab = tab,
                onTabChange = { tab = it },
                downloadedCount = state.songs.size,
                savedCount = savedTracks.size,
                modifier = Modifier.tourAnchor(TourAnchors.LIB_TABS)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            AuraSearchField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = if (tab == 0) "Search downloads" else "Search saved",
                modifier = Modifier
                    .tourAnchor(TourAnchors.LIB_SEARCH)
                    .padding(horizontal = AuraSpacing.Md)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Xxs))

            // Playlist controls for the active tab: randomise the order, or
            // keep engine recommendations flowing in behind the list.
            LibraryControlsRow(
                onShuffle = {
                    if (tab == 0) {
                        viewModel.shuffleDownloads(state.songs.map { it.song })
                        onPlaySong()
                    } else {
                        viewModel.shuffleSaved(savedShuffleList) { onPlaySong() }
                    }
                },
                shuffleEnabled = if (tab == 0) state.songs.isNotEmpty() else savedShuffleList.isNotEmpty(),
                spiceUp = spiceUpOn,
                onToggleSpice = { viewModel.toggleSpiceUp() },
                modifier = Modifier.tourAnchor(TourAnchors.LIB_CONTROLS)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Xxs))

            if (tab == 1) {
                val savedSorted = remember(savedTracks, savedSort) {
                    viewModel.sortSaved(savedTracks, savedSort)
                }
                SavedTab(
                    savedTracks = savedSorted,
                    isLoading = savedLoading,
                    searchQuery = state.searchQuery,
                    allTags = state.tags,
    filter = savedFilter,
    sortMode = savedSort,
    onToggleInclude = viewModel::toggleSavedTag,
                    onToggleExclude = viewModel::toggleSavedExcludedTag,
                    onSetMatchAll = viewModel::setSavedMatchAll,
                    onClearFilters = viewModel::clearSavedFilters,
                    downloadedUrls = remember(state.songs) {
                        state.songs.map { it.song.sourceUrl }.toSet()
                    },
                    resolvingUrl = savedResolvingUrl,
                    onPlay = { track, all ->
                        viewModel.playSaved(track, all) { onPlaySong() }
                    },
                    onPlayNext = viewModel::playNextSaved,
                    onQueue = viewModel::queueSaved,
                    onPrefetch = viewModel::prefetchSaved,
                    onDownload = viewModel::downloadSaved,
                    onUnsave = viewModel::unsaveTrack,
                    onToggleTag = viewModel::toggleSavedTagAssignment,
                    onCreateTag = viewModel::createAndAssignSavedTag,
                    onClearSearch = { viewModel.setSearchQuery("") },
                    onDiscover = onDiscoverClick ?: onAddSongClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
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
                if (state.isLoading && !hasFilters) {
                    // First DB emission hasn't landed yet — skeleton, never a
                    // fake "Vault empty" flash on cold start.
                    ShimmerList(rows = 6)
                } else {
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
                        items = state.songs,
                        key = { it.song.id },
                        contentType = { "song" }
                    ) { songWithTags ->
                        // The tour highlights the first row when explaining
                        // tap-to-play / long-press / swipe-to-queue.
                        val rowModifier =
                            if (songWithTags.song.id == state.songs.firstOrNull()?.song?.id) {
                                Modifier.tourAnchor(TourAnchors.LIB_LIST)
                            } else Modifier
                        val density = LocalDensity.current
                        // Queue-on-swipe must be a deliberate, wide gesture:
                        // 40% of the row width, never less than 96dp. The
                        // Material default (~56dp) fired on the slight
                        // horizontal drift of an ordinary vertical scroll.
                        val swipeState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { target ->
                                if (target == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.addToQueue(songWithTags.song)
                                }
                                false
                            },
                            positionalThreshold = { totalWidth ->
                                maxOf(totalWidth * 0.40f, with(density) { 96.dp.toPx() })
                            }
                        )
                        // Swipe right → add to queue (row snaps back, nothing is removed).
                        // Cheap mid-drag hint: driven by the swipe state, not
                        // per-frame coordinate tracking, so scrolling a large
                        // vault stays at 60fps.
                        val showHint = swipeState.dismissDirection !=
                            SwipeToDismissBoxValue.Settled
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
                                    QueueSwipeHint()
                                }
                            },
                            content = {
                                SongRow(
                                    songWithTags = songWithTags,
                                    isPlaying = currentSongId == songWithTags.song.id,
                                    modifier = rowModifier,
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
                                    }
                                )
                            }
                        )
                    }
                }
                }
            }
            } // end Downloaded tab
        }
        }
    }
}

/**
 * Downloaded / Saved tab switch. Downloaded = offline vault, Saved =
 * streaming bookmarks that need internet and start near-instantly.
 */
@Composable
private fun LibraryTabs(
    tab: Int,
    onTabChange: (Int) -> Unit,
    downloadedCount: Int,
    savedCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
    ) {
        FilterChip(
            selected = tab == 0,
            onClick = { onTabChange(0) },
            label = { Text("Downloaded • $downloadedCount") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = tab == 1,
            onClick = { onTabChange(1) },
            label = { Text("Saved • $savedCount") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Playlist controls for whichever library tab is active. Shuffle plays the
 * list in random order; Spice-up keeps recommended picks flowing in behind
 * the queue (the engine's own taste model — skips shrink, replays grow).
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

/**
 * Saved tab: streaming bookmarks with the same tag filtering and the same
 * glass-row look as Downloaded. Each row plays near-instantly (pre-warmed
 * stream URLs + ExoPlayer disk cache) but needs internet.
 */
@Composable
private fun SavedTab(
    savedTracks: List<com.aura.music.data.db.SavedTrackWithTags>,
    isLoading: Boolean,
    searchQuery: String,
    allTags: List<com.aura.music.data.db.TagEntity>,
    filter: SavedFilterUiState,
    sortMode: LibrarySortMode,
    onToggleInclude: (String) -> Unit,
    onToggleExclude: (String) -> Unit,
    onSetMatchAll: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    downloadedUrls: Set<String>,
    resolvingUrl: String?,
    onPlay: (com.aura.music.data.db.SavedTrackEntity, List<com.aura.music.data.db.SavedTrackEntity>) -> Unit,
    onPlayNext: (com.aura.music.data.db.SavedTrackEntity) -> Unit,
    onQueue: (com.aura.music.data.db.SavedTrackEntity) -> Unit,
    onPrefetch: (List<String>) -> Unit,
    onDownload: (com.aura.music.data.db.SavedTrackEntity) -> Unit,
    onUnsave: (com.aura.music.data.db.SavedTrackEntity) -> Unit,
    onToggleTag: (com.aura.music.data.db.SavedTrackWithTags, com.aura.music.data.db.TagEntity) -> Unit,
    onCreateTag: (com.aura.music.data.db.SavedTrackWithTags, String) -> Unit,
    onClearSearch: () -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query = searchQuery.trim()
    val filtered = remember(savedTracks, query, filter) {
        savedTracks.filter { item ->
            matchesLibraryFilters(
                title = item.track.title,
                artist = item.track.artist,
                tagNames = item.tags.map { it.name }.toSet(),
                query = query,
                selected = filter.selectedTagNames,
                excluded = filter.excludedTagNames,
                matchAll = filter.matchAll
            )
        }
    }
    val filteredEntities = remember(filtered) { filtered.map { it.track } }

    LaunchedEffect(savedTracks.size) {
        if (savedTracks.isNotEmpty()) onPrefetch(savedTracks.map { it.track.url })
    }

    // Picker holds only the track id (saveable) — the item itself is looked
    // up fresh so rotation keeps the dialog with live tag state.
    var tagPickerId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (allTags.isNotEmpty()) {
            FilterSection(
                tags = allTags,
                selected = filter.selectedTagNames,
                excluded = filter.excludedTagNames,
                matchAll = filter.matchAll,
                onToggleInclude = onToggleInclude,
                onToggleExclude = onToggleExclude,
                onSetMatchAll = onSetMatchAll,
                onClearAll = onClearFilters
            )
            Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
        }
        Text(
            text = "Streams instantly • needs internet — offline tracks live under Downloaded.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AuraSpacing.Lg, vertical = AuraSpacing.Xs)
        )
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading && savedTracks.isEmpty() && query.isBlank() &&
                    filter.selectedTagNames.isEmpty() && filter.excludedTagNames.isEmpty()
                ) {
                    ShimmerList(rows = 6)
                } else if (savedTracks.isEmpty()) {
                    AuraEmptyState(
                        icon = Icons.Default.BookmarkBorder,
                        title = "No saved tracks",
                        subtitle = "Save from Search — no download needed",
                        actionLabel = "Search",
                        onAction = onDiscover
                    )
                } else {
                    AuraEmptyState(
                        icon = Icons.Default.Search,
                        title = "No matches",
                        subtitle = "Try a different search or tags",
                        actionLabel = "Clear",
                        onAction = {
                            onClearSearch()
                            onClearFilters()
                        }
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(sortMode) {
                if (filtered.isNotEmpty()) {
                    try {
                        listState.scrollToItem(0)
                    } catch (_: Exception) {
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = AuraSpacing.Xs, bottom = AuraSpacing.BottomListPadding)
            ) {
                items(
                    items = filtered,
                    key = { it.track.id },
                    contentType = { "saved" }
                ) { item ->
                    // Same swipe-right-to-queue as Downloaded.
                    val swipeState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { target ->
                            if (target == SwipeToDismissBoxValue.StartToEnd) {
                                onQueue(item.track)
                            }
                            false
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
                            SavedTrackRow(
                                item = item,
                                isResolving = resolvingUrl == item.track.url,
                                isDownloaded = item.track.url in downloadedUrls,
                                onPlay = { onPlay(item.track, filteredEntities) },
                                onPlayNext = { onPlayNext(item.track) },
                                onQueue = { onQueue(item.track) },
                                onDownload = { onDownload(item.track) },
                                onTags = { tagPickerId = item.track.id },
                                onUnsave = { onUnsave(item.track) }
                            )
                        }
                    )
                }
            }
        }
    }

    val pickerItem = savedTracks.firstOrNull { it.track.id == tagPickerId }
    if (pickerItem != null) {
        SavedTagPickerDialog(
            item = pickerItem,
            allTags = allTags,
            onToggle = { tag -> onToggleTag(pickerItem, tag) },
            onCreate = { name -> onCreateTag(pickerItem, name) },
            onDismiss = { tagPickerId = null }
        )
    }
}

/** Mid-swipe hint: icon + label so it's clear the song lands in the queue. */
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
 * Same glass-row language as [SongRow] (both sit on [MediaRowShell]): art,
 * title, tags, duration and a 3-dot menu — so Downloaded and Saved read as
 * one list, not two designs.
 */
@Composable
private fun SavedTrackRow(
    item: com.aura.music.data.db.SavedTrackWithTags,
    isResolving: Boolean,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onTags: () -> Unit,
    onUnsave: () -> Unit
) {
    val track = item.track
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmUnsave by rememberSaveable { mutableStateOf(false) }

    MediaRowShell(
        title = track.title,
        subtitle = listOfNotNull(
            track.artist?.ifBlank { null },
            if (isDownloaded) "Downloaded" else "Online only"
        ).joinToString(" • ").ifEmpty { "YouTube" },
        subtitleColor = if (isDownloaded) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        thumbnailPath = track.thumbnailUrl,
        durationMs = track.durationMs,
        onClick = onPlay,
        tags = item.tags
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
                        contentDescription = "Saved options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onPlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onQueue()
                        }
                    )
                    if (!isDownloaded) {
                        DropdownMenuItem(
                            text = { Text("Download offline") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDownload()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Tags…") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Tag, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onTags()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Saved") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            confirmUnsave = true
                        }
                    )
                }
            }
        }
    }

    if (confirmUnsave) {
        AlertDialog(
            onDismissRequest = { confirmUnsave = false },
            title = { Text("Remove?") },
            text = {
                Text("“${track.title}” will be removed from Saved (downloads stay).")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnsave = false
                        onUnsave()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsave = false }) {
                    Text("Keep")
                }
            }
        )
    }
}

/** Tag assignment for a Saved bookmark: toggle existing or create new. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedTagPickerDialog(
    item: com.aura.music.data.db.SavedTrackWithTags,
    allTags: List<com.aura.music.data.db.TagEntity>,
    onToggle: (com.aura.music.data.db.TagEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
            ) {
                Text(
                    text = item.track.title,
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

private fun downloadDetail(download: ActiveDownload): String =
    downloadBytesDetail(
        percent = download.percent,
        bytesDone = download.bytesDone,
        bytesTotal = download.bytesTotal,
        stageLabel = downloadStageLabel(download.stage)
    )

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
                    items(tags, key = { it.id }) { tag ->
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
                    items(tags, key = { it.id }) { tag ->
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