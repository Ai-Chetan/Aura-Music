package com.aura.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.aura.music.ui.theme.Background
import com.aura.music.ui.theme.GradientCyan
import com.aura.music.ui.theme.GradientBlue
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ambient app background.
 *
 * Deep dark base with a slowly drifting blue→cyan wash and a subtle
 * animated wave at the bottom. [tint] lets the Now Playing screen warm the
 * background toward the album-art dominant color (blended over 1000ms).
 *
 * [animate] defaults to false: the drifting wave redraws every frame, so
 * only Now Playing opts into the motion. Everywhere else gets the same
 * look as a static first frame, free.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    animate: Boolean = false
) {
    val base = if (tint != null) {
        val target = Color(
            red = (Background.red + tint.red) / 2f,
            green = (Background.green + tint.green) / 2f,
            blue = (Background.blue + tint.blue) / 2f,
            alpha = 1f
        )
        animateColorAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 1000),
            label = "ambientTint"
        ).value
    } else {
        Background
    }

    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "ambient")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ambientPhase"
        )
        p
    } else {
        0.6f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Signature blue→cyan wash, top-left to bottom-right.
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        GradientBlue.copy(alpha = 0.16f),
                        Color.Transparent,
                        GradientCyan.copy(alpha = 0.10f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // Gentle wave sheet near the bottom.
            val waveH = h * 0.10f
            val centerY = h * 0.86f
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    val progress = x / w
                    val y = centerY +
                        sin(progress * 2f * PI.toFloat() + phase) * waveH * 0.4f +
                        sin(progress * 5f * PI.toFloat() + phase * 1.3f) * waveH * 0.2f
                    lineTo(x, y)
                    x += 8f
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GradientBlue.copy(alpha = 0.14f),
                        GradientCyan.copy(alpha = 0.08f)
                    ),
                    start = Offset(0f, centerY),
                    end = Offset(w, centerY)
                )
            )
        }
    }
}
