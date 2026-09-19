package com.aura.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.ui.player.NowPlayingViewModel
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import kotlinx.coroutines.flow.map

/** Horizontal swipe distance (dp) before it counts as a skip. */
private val SWIPE_THRESHOLD_DP = 48.dp

/**
 * Compact mini player: glass bar with art, single-line title,
 * play/pause + next, and a 2dp progress line. Tapping opens the full player.
 *
 * Collects playback state internally so parents don't recompose on every
 * position tick — only this small subtree redraws while playing.
 */
@Composable
fun MiniPlayer(
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    // Chrome slices for artwork/title/transport; the 2dp progress line reads
    // position in isolation so shell ticks don't recompose parents either.
    val song by remember(viewModel) {
        viewModel.playbackState.map { it.currentSong }
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(viewModel) {
        viewModel.playbackState.map { it.isPlaying }
    }.collectAsStateWithLifecycle(initialValue = false)
    val current = song ?: return
    val progress by remember(viewModel) {
        viewModel.playbackState.map { it.positionMs to it.durationMs }
    }.collectAsStateWithLifecycle(initialValue = 0L to 0L)
    val (positionMs, durationMs) = progress
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xs)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraRadius.Lg))
            .clickable(onClick = onOpenPlayer)
            // Swipe the bar left → next, right → previous. Accumulated drag
            // is checked on release so a tap never triggers a skip.
            .pointerInput(onNext, onPrevious) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragX += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragX < -SWIPE_THRESHOLD_DP.toPx()) onNext()
                        else if (dragX > SWIPE_THRESHOLD_DP.toPx()) onPrevious()
                    },
                    onDragCancel = { dragX = 0f }
                )
            },
        cornerRadius = AuraRadius.Lg
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AuraSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(
                    thumbnailPath = current.thumbnailPath,
                    contentDescription = current.title,
                    size = 44.dp,
                    cornerRadius = AuraRadius.Sm
                )
                Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.artist?.ifBlank { null } ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
