package com.aura.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.ui.player.NowPlayingViewModel
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Horizontal swipe distance (dp) before it counts as a skip. */
private val SWIPE_THRESHOLD_DP = 48.dp
/** Off-screen fly-out distance (dp) when a swipe commits. */
private val EXIT_DISTANCE_DP = 460.dp
/** Tilt per pixel of drag, clamped so the exit never spins wildly. */
private const val TILT_PER_PX = 0.02f
private const val MAX_TILT_DEGREES = 8f

/**
 * Compact mini player: glass bar with art, single-line title,
 * play/pause + next, and a 2dp progress line. Tapping opens the full player.
 *
 * Swiping is a real gesture animation: the card follows the finger with a
 * subtle tilt, skip hints fade in on the revealed edges, and a committed
 * swipe flings the card off-screen — left for next, right for previous —
 * before the next track slides back in from the opposite edge like a page
 * turn. A short swipe springs back to rest.
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

    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.toPx() }
    val exitPx = with(density) { EXIT_DISTANCE_DP.toPx() }
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xs)
            .graphicsLayer {
                translationX = dragX.value
                rotationZ = (dragX.value * TILT_PER_PX).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES)
            }
            // The card physically rides the finger; release past the
            // threshold flings it out and skips. Tap still reaches the
            // GlassCard's clickable — drags are consumed here first.
            .pointerInput(onNext, onPrevious) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { dragX.snapTo(dragX.value + amount) }
                    },
                    onDragEnd = {
                        val x = dragX.value
                        when {
                            // Swipe left → next. Fly out, advance, and slide
                            // the incoming card in from the right edge.
                            x <= -thresholdPx -> scope.launch {
                                dragX.animateTo(
                                    -exitPx,
                                    tween(160, easing = FastOutLinearInEasing)
                                )
                                onNext()
                                dragX.snapTo(exitPx)
                                dragX.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                            // Swipe right → previous, mirrored.
                            x >= thresholdPx -> scope.launch {
                                dragX.animateTo(
                                    exitPx,
                                    tween(160, easing = FastOutLinearInEasing)
                                )
                                onPrevious()
                                dragX.snapTo(-exitPx)
                                dragX.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                            // Short swipe: spring back to rest.
                            else -> scope.launch {
                                dragX.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragX.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                )
            }
    ) {
        // Skip hints on the edges the card slides away from: alpha tracks
        // drag progress so they appear exactly as the card reveals them.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    alpha = (dragX.value / thresholdPx).coerceIn(0f, 1f)
                }
                .padding(start = AuraSpacing.Lg)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    alpha = (-dragX.value / thresholdPx).coerceIn(0f, 1f)
                }
                .padding(end = AuraSpacing.Lg)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraRadius.Lg))
                .clickable(onClick = onOpenPlayer),
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
}
