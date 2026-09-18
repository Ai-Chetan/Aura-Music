package com.aura.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.aura.music.ui.theme.AuraSpacing
import kotlinx.coroutines.launch

/**
 * Cold-start intro: AURA shines (shimmer sweep) and expands
 * (scale 0.75 → 1) then hands off to the library.
 * Auto-dismisses after ~1.5s; tap skips. Cheap tweens only.
 */
@Composable
fun AuraSplash(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.75f) }
    val alpha = remember { Animatable(0f) }
    // Shimmer sweep position, px in an arbitrary wide space.
    val sweep = remember { Animatable(-300f) }

    // One continuous timeline per property (keyframes): appear →
    // hold → expand → fade. No back-to-back animateTo calls, so the
    // zoom never restarts from zero velocity mid-flight (the old
    // expand-stop-lurch). Shine finishes before the zoom, so the zoom
    // is a pure GPU layer transform with no text redraw.
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                2.6f,
                keyframes {
                    durationMillis = 1650
                    0.75f at 0
                    1f at 400 with FastOutSlowInEasing
                    1f at 800 // hold — same value, true plateau
                    2.6f at 1650 with LinearEasing // one smooth zoom out
                }
            )
        }
        launch {
            alpha.animateTo(
                0f,
                keyframes {
                    durationMillis = 1650
                    0f at 0
                    1f at 250
                    1f at 1050 // fully visible through hold + zoom start
                    0f at 1650 with LinearEasing // faded before fully out
                }
            )
        }
        launch {
            kotlinx.coroutines.delay(120)
            sweep.animateTo(1200f, tween(850, easing = LinearEasing))
        }
        launch {
            kotlinx.coroutines.delay(1700)
            onDone()
        }
    }

    val shine = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF5F7FA),
            Color(0xFF38BDF8),
            Color.White,
            Color(0xFF38BDF8),
            Color(0xFFF5F7FA)
        ),
        start = Offset(sweep.value - 260f, 0f),
        end = Offset(sweep.value, 60f)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            ),
        contentAlignment = Alignment.Center
    ) {
        AmbientBackground(animate = false)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
                .padding(horizontal = AuraSpacing.Xl)
        ) {
            Text(
                text = "AURA",
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = shine,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 80.sp,
                    letterSpacing = 8.sp
                )
            )
            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
            Text(
                text = "MUSIC VAULT",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
