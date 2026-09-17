package com.aura.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.aura.music.ui.theme.GradientCyan
import com.aura.music.ui.theme.GradientBlue
import kotlin.math.PI
import kotlin.math.sin

/**
 * Music-reactive waveform: rounded bars driven by live FFT magnitudes while
 * playing, dissolving into a gentle idle wave when paused (or when the
 * device can't provide audio data — the idle path always works).
 */
@Composable
fun AudioReactiveWaveform(
    magnitudes: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 48,
    primaryColor: Color = GradientBlue,
    secondaryColor: Color = GradientCyan
) {
    val transition = rememberInfiniteTransition(label = "reactiveWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idlePhase"
    )
    // Faster flow field — only advances while playing.
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPhase"
    )

    val live = isPlaying && magnitudes.any { it > 0.02f }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val gap = w / barCount
        val barW = (gap * 0.55f).coerceAtLeast(2f)
        val brush = Brush.verticalGradient(
            colors = listOf(secondaryColor, primaryColor, secondaryColor)
        )

        for (i in 0 until barCount) {
            // Linear resample of FFT buckets onto bars.
            val pos = i.toFloat() / (barCount - 1).coerceAtLeast(1) * (magnitudes.size - 1)
            val lo = pos.toInt().coerceIn(0, (magnitudes.size - 1).coerceAtLeast(0))
            val hi = (lo + 1).coerceAtMost((magnitudes.size - 1).coerceAtLeast(0))
            val frac = pos - lo
            val mag = magnitudes[lo] * (1 - frac) + magnitudes[hi] * frac

            val level = if (live) {
                // Music drives the height; a traveling flow field guarantees
                // continuous motion so bars never sit in a static 0/1 pattern.
                val p = i.toFloat() / barCount
                val base = (0.10f + mag * 0.90f).coerceIn(0.06f, 1f)
                val wave = 0.72f + 0.28f * sin(flow * 1.5f + i * 0.45f).toFloat() +
                    0.10f * sin(flow * 2.3f - p * 6f * PI.toFloat()).toFloat()
                (base * wave).coerceIn(0.05f, 1f)
            } else {
                // Breathing idle wave.
                val p = i.toFloat() / barCount
                (0.10f + 0.08f * sin(p * 4f * PI.toFloat() + phase)).coerceAtLeast(0.04f)
            }

            val barH = (h * level).coerceAtLeast(3f)
            val cx = i * gap + gap / 2f
            drawRoundRect(
                brush = brush,
                topLeft = Offset(cx - barW / 2f, (h - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                alpha = if (live) 0.95f else 0.45f
            )
        }
    }
}
