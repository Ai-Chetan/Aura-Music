package com.aura.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aura.music.ui.theme.GradientCyan
import com.aura.music.ui.theme.GradientBlue
import java.io.File

/**
 * Rounded album art with a blue→cyan gradient placeholder, soft shadow
 * and thin border. Supports both content:// / file paths and remote URLs.
 */
@Composable
fun AlbumArt(
    thumbnailPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val model: Any? = when {
        thumbnailPath.isNullOrBlank() -> null
        thumbnailPath.startsWith("http") -> thumbnailPath
        else -> try {
            val f = File(thumbnailPath)
            if (f.exists()) f else thumbnailPath
        } catch (_: Exception) {
            thumbnailPath
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        GradientBlue.copy(alpha = 0.8f),
                        GradientCyan.copy(alpha = 0.45f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

/** Accent-tinted playing indicator ring used by rows + mini player. */
@Composable
fun PlayingRing(show: Boolean, modifier: Modifier = Modifier) {
    if (!show) return
    Box(
        modifier = modifier
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp)
            )
    )
}
