package com.aura.music.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.data.db.TagEntity
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.SongRow
import com.aura.music.ui.components.TagChip

@Composable
fun LibraryScreen(
    onSongClick: (Long) -> Unit = {},
    onAddSongClick: () -> Unit = {},
    onPlaySong: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURA Music",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
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
                        contentDescription = "Backup and restore",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = if (state.songs.isEmpty()) {
                    "Your vault"
                } else {
                    "${state.songs.size} tracks"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                placeholder = { Text("Search title or artist") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )

            Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.searchQuery.isNotBlank() || state.selectedTagNames.isNotEmpty() || state.excludedTagNames.isNotEmpty()) {
                                "No songs match the current filters."
                            } else {
                                "Your library is empty.\nTap + to download from YouTube in best quality."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp)
                ) {
                    items(
                        items = state.songs,
                        key = { it.song.id }
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
                        SwipeToDismissBox(
                            state = swipeState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = false,
                            backgroundContent = {
                                // Glass cards already show the motion — a faint wash is enough.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        )
                                )
                            },
                            content = {
                                SongRow(
                                    songWithTags = songWithTags,
                                    isPlaying = playback.currentSong?.id == songWithTags.song.id,
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
        }
    }
}

/**
 * Compact tag filtering: a single bar that expands only when needed.
 * Collapsed it is one row (icon + active summary + clear); expanded it holds
 * the include chips with AND/OR plus the "Don't play" exclusion chips.
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
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 6.dp),
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

            Spacer(modifier = Modifier.width(8.dp))

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

            Icon(
                imageVector = if (expanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (expanded) "Collapse filters" else "Expand filters",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
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

                    Spacer(modifier = Modifier.width(8.dp))

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Don't play",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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