package com.aura.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aura.music.data.db.TagEntity
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing

/**
 * Compact tag filtering shared by the unified Library and playlist details:
 * include chips with AND/OR match, excluded chips (excluded always win).
 * A single bar that expands only when needed.
 */
@Composable
fun TagFilterSection(
    tags: List<TagEntity>,
    selected: Set<String>,
    excluded: Set<String>,
    matchAll: Boolean,
    onToggleInclude: (String) -> Unit,
    onToggleExclude: (String) -> Unit,
    onSetMatchAll: (Boolean) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val activeCount = selected.size + excluded.size

    val summary = when {
        activeCount == 0 -> "Filters"
        selected.isNotEmpty() && excluded.isNotEmpty() ->
            "${selected.size} in • ${excluded.size} out"
        selected.isNotEmpty() -> "${selected.size} included"
        else -> "${excluded.size} excluded"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AuraRadius.Md))
                .clickable { expanded = !expanded }
                .padding(horizontal = AuraSpacing.Xxs, vertical = AuraSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = if (activeCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(AuraSpacing.Xs))

            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = if (activeCount > 0) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )

            if (activeCount > 0) {
                TextButton(onClick = onClearAll) {
                    Text("Clear")
                }
            }

            Text(
                text = if (expanded) "–" else "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Include",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(AuraSpacing.Xs))

                    if (selected.isNotEmpty()) {
                        FilterChip(
                            selected = matchAll,
                            onClick = { onSetMatchAll(true) },
                            label = { Text("ALL tags") }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        FilterChip(
                            selected = !matchAll,
                            onClick = { onSetMatchAll(false) },
                            label = { Text("ANY tag") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                ) {
                    items(tags, key = { it.id }) { tag ->
                        TagChip(
                            name = tag.name,
                            colorHex = tag.colorHex,
                            selected = tag.name in selected,
                            onClick = { onToggleInclude(tag.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                Text(
                    text = "Excluded",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)
                ) {
                    items(tags, key = { it.id }) { tag ->
                        TagChip(
                            name = tag.name,
                            colorHex = tag.colorHex,
                            selected = tag.name in excluded,
                            onClick = { onToggleExclude(tag.name) }
                        )
                    }
                }
            }
        }
    }
}
