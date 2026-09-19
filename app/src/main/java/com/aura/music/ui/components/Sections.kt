package com.aura.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing

/**
 * Shared section system for Home / Discover / Library.
 *
 * Hierarchy rules (the actual "redesign"):
 * - One header style everywhere ([SectionHeader]): title + optional
 *   trailing "See all" chevron — no competing header designs per screen.
 * - One streaming row ([StreamRow]): rank slot + art + texts + play +
 *   caller-supplied trailing actions. Discover, Search and Saved all render
 *   through it so lists feel like one product.
 * - Loading is skeleton-first ([ShimmerList]), never a bare spinner page.
 * - Blocked dynamic content renders [GatePlaceholder] with a next step,
 *   never a dead error card.
 */

//region Headers

/** Section header: title, optional subtitle, optional "See all" action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md)
            .clickable(
                enabled = actionLabel != null && onAction != null,
                role = Role.Button
            ) { onAction?.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

//endregion

//region Streaming row

/**
 * The single streaming-row design. Art + texts + instant-play affordance;
 * bookmark/download/unsave buttons are supplied by the caller so every
 * list (Discover, Search, Saved) shares one visual language.
 */
@Composable
fun StreamRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    rankAccent: Boolean = false,
    isResolving: Boolean = false,
    artSize: Dp = 56.dp,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay, role = Role.Button)
            .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                text = "%2d".format(rank),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (rankAccent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp)
            )
        }
        AlbumArt(
            thumbnailPath = thumbnailUrl,
            contentDescription = title,
            size = artSize,
            cornerRadius = AuraRadius.Md
        )
        Spacer(modifier = Modifier.width(AuraSpacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isResolving) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
        } else {
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Stream now",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        trailing()
    }
}

//endregion

//region Hero + rail cards

/**
 * Hero for the #1 top hit: oversized art, rank badge and a committed play
 * button. The single most-significant item on load.
 */
@Composable
fun HeroHitCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    badge: String,
    onPlay: () -> Unit,
    isResolving: Boolean = false,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(AuraSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AlbumArt(
                    thumbnailPath = thumbnailUrl,
                    contentDescription = title,
                    size = 88.dp,
                    cornerRadius = AuraRadius.Md
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(
                                topStart = AuraRadius.Md,
                                bottomEnd = AuraRadius.Md
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(AuraSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play top hit",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact horizontal-rail card (Continue listening / For you).
 */
@Composable
fun RailCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 148.dp
) {
    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(AuraRadius.Md))
            .clickable(onClick = onClick, role = Role.Button)
    ) {
        AlbumArt(
            thumbnailPath = thumbnailUrl,
            contentDescription = title,
            size = cardWidth,
            cornerRadius = AuraRadius.Md
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle.ifBlank { " " },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

//endregion

//region Loading skeletons

@Composable
private fun shimmerAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer-alpha"
    )
    return alpha
}

/** Skeleton rows matching [StreamRow] geometry — loading never blanks the page. */
@Composable
fun ShimmerList(
    rows: Int = 5,
    showRank: Boolean = false,
    modifier: Modifier = Modifier
) {
    val alpha = shimmerAlpha()
    val bar = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRank) Spacer(modifier = Modifier.width(32.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(AuraRadius.Md))
                        .background(bar)
                )
                Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(bar)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bar)
                    )
                }
            }
        }
    }
}

//endregion

//region Gate placeholder

/**
 * Blocked-dynamic-content placeholder: always pairs the reason with a next
 * step (open Settings / load once / retry), never a dead end.
 */
@Composable
fun GatePlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AuraSpacing.Lg,
                vertical = AuraSpacing.Md
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(AuraSpacing.Sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AuraSpacing.Sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onPrimary) { Text(primaryLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

//endregion
