package com.aura.music.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import kotlinx.coroutines.launch

private enum class MockKind { SEARCH_LIST, TAGS, DOWNLOAD, PLAYER_TABS }

private data class GuideStep(
    val title: String,
    val body: String,
    val mock: MockKind,
    /** True = tooltip sits below the mock with arrow pointing up. */
    val tooltipBelow: Boolean = true
)

private val GUIDE_STEPS = listOf(
    GuideStep(
        title = "Search your vault",
        body = "Everything you save lands here. Search by title or artist to jump to a track.",
        mock = MockKind.SEARCH_LIST,
        tooltipBelow = true
    ),
    GuideStep(
        title = "Filter with tags",
        body = "Tap chips to include, toggle AND / OR, swipe a song to queue it.",
        mock = MockKind.TAGS,
        tooltipBelow = true
    ),
    GuideStep(
        title = "Find it two ways",
        body = "Search YouTube for any song, or paste a link — video, Short or playlist. Preview, then save as-is.",
        mock = MockKind.DOWNLOAD,
        tooltipBelow = true
    ),
    GuideStep(
        title = "Play from anywhere",
        body = "Mini-player opens full Now Playing. Bottom tabs switch Library and Add.",
        mock = MockKind.PLAYER_TABS,
        tooltipBelow = false
    )
)

private const val STARTER_PAGE_INDEX = 4

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onDownloadSelected: (List<StarterTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = GUIDE_STEPS.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == STARTER_PAGE_INDEX

    // Starter-track multi-select, hoisted so the bottom CTA can react.
    // Saveable as a List (Sets aren't Bundle-saveable) so rotation never
    // wipes the user's picks.
    val selected = rememberSaveable { mutableStateOf(listOf<String>()) }
    fun toggle(url: String) {
        selected.value = if (url in selected.value) selected.value - url
        else selected.value + url
    }

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, pageCount - 1)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = AuraSpacing.Md)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} of $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!isLastPage) {
                    TextButton(onClick = onFinish) { Text("Skip") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page < GUIDE_STEPS.size) {
                    CoachStepContent(step = GUIDE_STEPS[page])
                } else {
                    StarterSelectPage(
                        selected = selected.value.toSet(),
                        onToggle = ::toggle,
                        onSelectAll = {
                            selected.value = StarterTracks.tracks.map { it.url }
                        },
                        onClear = { selected.value = emptyList() }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val dotSelected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(
                                width = if (dotSelected) 24.dp else 8.dp,
                                height = 8.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (dotSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.Md))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = { goTo(pagerState.currentPage - 1) }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isLastPage) {
                    if (selected.value.isNotEmpty()) {
                        TextButton(onClick = onFinish) { Text("Skip") }
                        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                        Button(
                            onClick = {
                                onDownloadSelected(
                                    StarterTracks.tracks.filter { it.url in selected.value }
                                )
                            }
                        ) {
                            Text("Download ${selected.value.size}")
                        }
                    } else {
                        Button(onClick = onFinish) { Text("Get started") }
                    }
                } else {
                    Button(onClick = { goTo(pagerState.currentPage + 1) }) {
                        Text(if (pagerState.currentPage == STARTER_PAGE_INDEX - 1) "See starters" else "Next")
                    }
                }
            }
        }
    }
}

/**
 * One coach step: a mock of the real feature with a floating tooltip
 * window whose arrow points at the highlighted element.
 */
@Composable
private fun CoachStepContent(step: GuideStep) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (step.tooltipBelow) {
            MockFeature(kind = step.mock)
            CoachTooltip(
                title = step.title,
                body = step.body,
                arrowUp = true
            )
        } else {
            CoachTooltip(
                title = step.title,
                body = step.body,
                arrowUp = false
            )
            MockFeature(kind = step.mock)
        }
    }
}

/** Floating tooltip window with a small arrow tip on top or bottom. */
@Composable
private fun CoachTooltip(
    title: String,
    body: String,
    arrowUp: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (arrowUp) {
            Box(
                modifier = Modifier
                    .offset(y = 8.dp)
                    .size(16.dp)
                    .graphicsLayer(rotationZ = 45f)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(AuraRadius.Lg))
                .clip(RoundedCornerShape(AuraRadius.Lg))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    RoundedCornerShape(AuraRadius.Lg)
                )
                .padding(AuraSpacing.Md)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!arrowUp) {
            Box(
                modifier = Modifier
                    .offset(y = (-8).dp)
                    .size(16.dp)
                    .graphicsLayer(rotationZ = 45f)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}

/** Simplified replica of the real UI so the arrow has a feature to point at. */
@Composable
private fun MockFeature(kind: MockKind) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuraSpacing.Sm)
    ) {
        when (kind) {
            MockKind.SEARCH_LIST -> Column(Modifier.padding(AuraSpacing.Md)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AuraRadius.Md))
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(AuraRadius.Md)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Sm)
                ) {
                    Text(
                        text = "Search songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                repeat(2) { i ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (i == 0) 0.7f else 0.5f)
                                    .height(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                            )
                        }
                    }
                }
            }

            MockKind.TAGS -> Column(Modifier.padding(AuraSpacing.Md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)) {
                    listOf("chill", "workout", "retro").forEachIndexed { i, tag ->
                        val highlighted = i == 0
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (highlighted) Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ) else Modifier
                                )
                                .background(
                                    if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (highlighted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                )
            }

            MockKind.DOWNLOAD -> Column(Modifier.padding(AuraSpacing.Md)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AuraRadius.Md))
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(AuraRadius.Md)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = AuraSpacing.Md, vertical = 14.dp)
                ) {
                    Text(
                        text = "youtube.com/watch?v=…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(AuraRadius.Md))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Fetch",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            MockKind.PLAYER_TABS -> Column(Modifier.padding(AuraSpacing.Md)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AuraRadius.Md))
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(AuraRadius.Md)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(AuraSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(11.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarterSelectPage(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Grab a starter track",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
        Text(
            text = "Tap cards to select, then download.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected.size == StarterTracks.tracks.size) {
                TextButton(onClick = onClear) { Text("Clear") }
            } else {
                TextButton(onClick = onSelectAll) { Text("Select all") }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
        ) {
            items(StarterTracks.tracks, key = { it.url }) { track ->
                val isSelected = track.url in selected
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.border(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(AuraRadius.Lg)
                            ) else Modifier
                        )
                        .clickable { onToggle(track.url) }
                ) {
                    Row(
                        modifier = Modifier.padding(AuraSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggle(track.url) }
                        )
                        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
