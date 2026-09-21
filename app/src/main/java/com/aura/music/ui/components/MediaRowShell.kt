package com.aura.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.data.db.TagEntity
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.formatDuration

/**
 * Shared glass-row shell for vault lists ([SongRow] / Saved rows): art,
 * title, subtitle, optional tag chips, monospace duration and a trailing
 * slot. One layout so Downloaded and Saved read as a single list language.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRowShell(
    title: String,
    subtitle: String,
    thumbnailPath: String?,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isPlaying: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    tags: List<TagEntity> = emptyList(),
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xxs)
            .clip(RoundedCornerShape(AuraRadius.Md))
            .then(
                if (isPlaying) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(AuraRadius.Md)
                    )
                } else Modifier
            )
            .background(
                if (isPlaying) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = AuraSpacing.Sm, vertical = AuraSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            thumbnailPath = thumbnailPath,
            contentDescription = title,
            size = 56.dp,
            cornerRadius = AuraRadius.Sm
        )

        Spacer(modifier = Modifier.width(AuraSpacing.Sm))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (tags.isNotEmpty()) {
                // 12dp above the chips = 12dp below them (8dp row padding
                // + 4dp outer gap to the next row), so the tags sit evenly
                // between the artist line and the next song.
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // One line, always: each chip may use at most its fair
                    // share of the width (long names ellipsize instead of
                    // wrapping the row taller), the rest collapse into +N.
                    val maxChips = 3
                    val overflow = tags.size - maxChips
                    tags.take(maxChips).forEach { tag ->
                        TagChip(
                            name = tag.name,
                            colorHex = tag.colorHex,
                            selected = false,
                            onClick = {},
                            enabled = false,
                            singleLine = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (overflow > 0) {
                        TagChip(
                            name = "+$overflow",
                            colorHex = null,
                            selected = false,
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(AuraSpacing.Xs))

        Text(
            text = durationMs.formatDuration(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        trailing()
    }
}
