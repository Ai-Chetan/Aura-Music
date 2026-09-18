package com.aura.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.aura.music.playback.RepeatMode
import com.aura.music.ui.components.AlbumArt
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AudioReactiveWaveform
import com.aura.music.ui.components.QualityBadge
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val song = state.currentSong
    var showQueue by remember { mutableStateOf(false) }

    // Horizontal swipe on the artwork block changes the track: the art
    // follows the finger, and releasing past the threshold skips.
    // Swipe left → previous, swipe right → next.
    var trackDragX by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(song?.id) { trackDragX = 0f }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground(animate = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = AuraSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { showQueue = true }) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Lg))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = trackDragX }
                .pointerInput(swipeThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { trackDragX = 0f },
                        onDragEnd = {
                            when {
                                trackDragX <= -swipeThresholdPx -> viewModel.skipToPrevious()
                                trackDragX >= swipeThresholdPx -> viewModel.skipToNext()
                            }
                            trackDragX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (song != null) trackDragX += dragAmount
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        AlbumArt(
            thumbnailPath = song?.thumbnailPath,
            contentDescription = song?.title,
            size = 264.dp,
            cornerRadius = AuraRadius.Xl
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Md))

        if (song != null) {
            QualityBadge(codec = song.audioFormat, bitrateKbps = song.bitrateKbps)
            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
        }

        AnimatedContent(
            targetState = song?.title ?: "Nothing playing",
            label = "trackTitle"
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        if (!song?.artist.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
            Text(
                text = song?.artist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Md))

        AudioReactiveWaveform(
            flow = viewModel.waveform,
            isPlaying = state.isPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = AuraSpacing.Xs)
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Sm))

        Slider(
            value = if (state.durationMs > 0) {
                state.positionMs.toFloat() / state.durationMs.toFloat()
            } else 0f,
            onValueChange = { fraction ->
                viewModel.seekTo((fraction * state.durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.positionMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.durationMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
            Text(
                text = "Couldn't play: ${state.errorMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.setShuffleEnabled(!state.shuffleEnabled) }
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            IconButton(onClick = { viewModel.skipToPrevious() }) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary),
                enabled = song != null
            ) {
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "playPause"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            IconButton(onClick = { viewModel.skipToNext() }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) {
                        Icons.Default.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != RepeatMode.OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Xl))
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            // Fixed-height sheet: removing songs must not shrink it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
            ) {
            Text(
                text = "Queue • ${state.queue.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = AuraSpacing.Lg, vertical = AuraSpacing.Xxs)
            )
            Text(
                text = "Tap to play, drag to reorder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AuraSpacing.Lg)
            )
            Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
            if (state.queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue empty — add songs from Library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AuraSpacing.Lg)
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    QueueSheetList(
                        queue = state.queue,
                        currentIndex = state.currentIndexInQueue,
                        onPlayAt = viewModel::playAtIndex,
                        onRemove = viewModel::removeFromQueue,
                        onMove = viewModel::moveQueue
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun QueueSheetList(
    queue: List<com.aura.music.data.db.SongEntity>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val listState = rememberLazyListState()
    var draggedFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dropTarget by remember { mutableStateOf<Int?>(null) }

    fun resetDrag() {
        draggedFrom = null
        dragOffsetY = 0f
        dropTarget = null
    }

    fun dropTargetFor(from: Int, centerY: Float): Int? {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return null
        return visible
            .filter { it.index != from }
            .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - centerY) }
            ?.index
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp)
    ) {
        itemsIndexed(
            items = queue,
            key = { index, item -> "${item.id}_$index" }
        ) { index, item ->
            val isCurrent = index == currentIndex
            val isDragged = index == draggedFrom
            val isTarget = index == dropTarget && dropTarget != draggedFrom
            val latestIndex by rememberUpdatedState(index)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragged) dragOffsetY else 0f
                        shadowElevation = if (isDragged) 12.dp.toPx() else 0f
                        scaleX = if (isDragged) 1.02f else 1f
                        scaleY = if (isDragged) 1.02f else 1f
                    }
                    .clickable { onPlayAt(index) }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedFrom = latestIndex
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val from = draggedFrom ?: return@detectDragGesturesAfterLongPress
                                dragOffsetY += dragAmount.y
                                val fromInfo = listState.layoutInfo.visibleItemsInfo
                                    .find { it.index == from }
                                    ?: return@detectDragGesturesAfterLongPress
                                val center = fromInfo.offset + dragOffsetY + fromInfo.size / 2
                                dropTarget = dropTargetFor(from, center)
                            },
                            onDragEnd = {
                                val from = draggedFrom
                                if (from != null) {
                                    val fromInfo = listState.layoutInfo.visibleItemsInfo
                                        .find { it.index == from }
                                    if (fromInfo != null) {
                                        val center = fromInfo.offset + dragOffsetY + fromInfo.size / 2
                                        val to = dropTargetFor(from, center)
                                        if (to != null && to != from) onMove(from, to)
                                    }
                                }
                                resetDrag()
                            },
                            onDragCancel = { resetDrag() }
                        )
                    }
                    .background(
                        when {
                            isDragged -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            isTarget -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        }
                    )
                    .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.width(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.artist.isNullOrBlank()) {
                        Text(
                            text = item.artist!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = item.durationMs.formatDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}