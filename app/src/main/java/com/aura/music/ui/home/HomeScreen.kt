package com.aura.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraEmptyState
import com.aura.music.ui.components.AuraToast
import com.aura.music.ui.components.GatePlaceholder
import com.aura.music.ui.components.HeroHitCard
import com.aura.music.ui.components.RailCard
import com.aura.music.ui.components.SectionHeader
import com.aura.music.ui.components.ShimmerList
import com.aura.music.ui.components.StreamRow
import com.aura.music.ui.components.collectToast
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration
import kotlinx.coroutines.delay

/**
 * Home hub — the launch screen. Order is deliberate:
 * 1. Your music (continue listening — instant, offline-first),
 * 2. today's top 5 (hero + ranked rows, no deeper chart),
 * 3. For you — recency-driven picks from your current rotation.
 * Dynamic sections render gated placeholders with a next step when the
 * data gate is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayStarted: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gate by viewModel.gateState.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val savedUrls by viewModel.savedUrls.collectAsStateWithLifecycle()
    val queuedDownloads by viewModel.queuedDownloadUrls.collectAsStateWithLifecycle()
    val toast = collectToast(viewModel.messages)

    val dynamicEmpty = state.trending.isEmpty() && state.forYou.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { AuraToast(toast) }
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = state.isLoadingDynamic,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = AuraSpacing.BottomListPadding)
                ) {
                item(key = "header") {
                    HomeHeader(onOpenSettings = onOpenSettings)
                    Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                }

                if (recent.isNotEmpty()) {
                    item(key = "continue-title") {
                        SectionHeader(
                            title = "Continue listening",
                            actionLabel = "Library",
                            onAction = onOpenLibrary
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    }
                    item(key = "continue-rail") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuraSpacing.Md),
                            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                        ) {
                            itemsIndexed(
                                items = recent,
                                key = { _, song -> song.id },
                                contentType = { _, _ -> "rail" }
                            ) { index, song ->
                                RailCard(
                                    title = song.title,
                                    subtitle = song.artist ?: "Unknown",
                                    thumbnailUrl = song.thumbnailPath,
                                    onClick = {
                                        viewModel.playRecent(recent, index, onPlayStarted)
                                    }
                                )
                            }
                        }
                    }
                    item(key = "continue-gap") {
                        Spacer(modifier = Modifier.height(AuraSpacing.Md))
                    }
                }

                if (state.isLoadingDynamic || dynamicEmpty || state.trending.isNotEmpty()) {
                    item(key = "hits-title") {
                        SectionHeader(
                            title = "Top hits today",
                            subtitle = "The most significant drops right now"
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    }
                }

                when {
                    state.isLoadingDynamic -> {
                        item(key = "hits-loading") { ShimmerList(rows = 4, showRank = true) }
                    }
                    dynamicEmpty -> {
                        item(key = "hits-gate") {
                            if (gate.allowed) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AuraEmptyState(
                                        icon = Icons.Default.Whatshot,
                                        title = "Nothing trending right now",
                                        subtitle = "Check back in a bit.",
                                        actionLabel = "Retry",
                                        onAction = viewModel::refresh
                                    )
                                }
                            } else {
                                GatePlaceholder(
                                    icon = Icons.Default.CloudOff,
                                    title = "Top hits need data",
                                    subtitle = gate.reason?.message()
                                        ?: "Connect to see what's hot.",
                                    primaryLabel = "Open settings",
                                    onPrimary = onOpenSettings,
                                    secondaryLabel = "Retry",
                                    onSecondary = viewModel::refresh
                                )
                            }
                        }
                    }
                    else -> {
                        val hero = state.trending.firstOrNull()
                        if (hero != null) {
                            item(key = "hits-hero") {
                                HeroHitCard(
                                    title = hero.title,
                                    subtitle = listOfNotNull(
                                        hero.artist,
                                        if (hero.durationMs > 0) hero.durationMs.formatDuration() else null
                                    ).joinToString(" • "),
                                    thumbnailUrl = hero.thumbnailUrl,
                                    badge = "#1 TODAY",
                                    isResolving = state.resolvingUrl == hero.url,
                                    onPlay = { viewModel.playStream(hero, state.trending, onPlayStarted) }
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                            }
                        }
                        val rest = if (hero != null) state.trending.drop(1).take(4) else emptyList()
                        itemsIndexed(
                            items = rest,
                            key = { _, track -> "home-hit-${track.url}" },
                            contentType = { _, _ -> "hit-row" }
                        ) { index, track ->
                            // Hero owns rank 1 — rows continue at 2.
                            val rank = index + 2
                            val saved = track.url in savedUrls
                            StreamRow(
                                title = track.title,
                                subtitle = listOfNotNull(
                                    track.artist,
                                    if (track.durationMs > 0) track.durationMs.formatDuration() else null
                                ).joinToString(" • ").ifEmpty { "YouTube" },
                                thumbnailUrl = track.thumbnailUrl,
                                onPlay = { viewModel.playStream(track, state.trending, onPlayStarted) },
                                rank = rank,
                                rankAccent = rank <= 3,
                                isResolving = state.resolvingUrl == track.url,
                                trailing = {
                                    HitMenu(
                                        isSaved = saved,
                                        isQueued = track.url in queuedDownloads,
                                        onPlay = { viewModel.playStream(track, state.trending, onPlayStarted) },
                                        onToggleSave = { viewModel.toggleSave(track) },
                                        onDownload = { viewModel.download(track) },
                                        onQueue = { viewModel.queueTrack(track) },
                                        onPlayNext = { viewModel.playNextTrack(track) }
                                    )
                                }
                            )
                        }
                    }
                }

                if (state.forYou.isNotEmpty()) {
                    item(key = "foryou-title") {
                        Spacer(modifier = Modifier.height(AuraSpacing.Md))
                        SectionHeader(
                            title = "For you",
                            subtitle = state.forYouSubtitle.ifBlank { null }
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    }
                    item(key = "foryou-rail") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuraSpacing.Md),
                            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                        ) {
                            itemsIndexed(
                                items = state.forYou,
                                key = { _, track -> "home-foryou-${track.url}" },
                                contentType = { _, _ -> "rail" }
                            ) { _, track ->
                                RailCard(
                                    title = track.title,
                                    subtitle = track.artist ?: "YouTube",
                                    thumbnailUrl = track.thumbnailUrl,
                                    onClick = { viewModel.playStream(track, state.forYou, onPlayStarted) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AuraSpacing.Md))
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun HitMenu(
    isSaved: Boolean,
    isQueued: Boolean,
    onPlay: () -> Unit,
    onToggleSave: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Play") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPlay()
                }
            )
            DropdownMenuItem(
                text = { Text(if (isQueued) "Downloading…" else "Download offline") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null
                    )
                },
                enabled = !isQueued,
                onClick = {
                    expanded = false
                    onDownload()
                }
            )
            DropdownMenuItem(
                text = { Text("Download offline") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onDownload()
                }
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onQueue()
                }
            )
            DropdownMenuItem(
                text = { Text("Play next") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPlayNext()
                }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Md, end = AuraSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "AURA Music",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
