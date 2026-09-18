package com.aura.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.GlassBorder
import com.aura.music.ui.theme.GlassFill

/**
 * Frosted-glass card: translucent fill + 1dp white@10% border.
 * (True backdrop-blur needs RenderEffect on API 31+; the translucent
 * fill over [AmbientBackground] gives the same frosted feel on all API 26+.)
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AuraRadius.Lg,
    fill: Color = GlassFill,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(width = 1.dp, color = GlassBorder, shape = shape),
        content = content
    )
}
