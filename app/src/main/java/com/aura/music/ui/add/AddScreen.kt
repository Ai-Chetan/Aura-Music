package com.aura.music.ui.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.music.ui.addsong.AddSongPanel
import com.aura.music.ui.addsong.AddSongViewModel
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraToast
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.collectToast
import com.aura.music.ui.search.SearchPanel
import com.aura.music.ui.search.SearchViewModel
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.YoutubeUrls
import kotlinx.coroutines.delay

/**
 * One bar for everything: type a song/artist for results, or tap the link
 * icon (or paste a URL — auto-detected) for the link/playlist download
 * flow. No tabs, no modes to learn.
 */
@Composable
fun AddScreen(
    initialUrl: String? = null,
    /** 0 = Search, 1 = Paste link, null = auto. */
    initialMode: Int? = null,
    onSongAdded: (Long) -> Unit = {},
    onPlaylistDone: () -> Unit = {},
    onPlayResult: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    addSongViewModel: AddSongViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    // Prefilled links (shared URLs) land straight in link mode.
    var text by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var linkMode by rememberSaveable {
        mutableStateOf(initialMode?.coerceIn(0, 1) == 1 || !initialUrl.isNullOrBlank())
    }

    val toast = collectToast(searchViewModel.messages)

    fun submit() {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // Explicit toggle wins; otherwise a YouTube URL auto-routes to links.
        // Link mode with plain text falls back to search instead of erroring.
        val asLink = (linkMode || YoutubeUrls.isYouTubeUrl(trimmed)) &&
            YoutubeUrls.isYouTubeUrl(trimmed)
        linkMode = asLink
        if (asLink) {
            addSongViewModel.setUrl(trimmed)
            addSongViewModel.preview()
        } else {
            searchViewModel.setQuery(trimmed)
            searchViewModel.search()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            AuraTopBar(
                title = "Search music",
                subtitle = if (linkMode) "Paste a video or playlist link" else "Search songs, stream or save",
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md),
                placeholder = { Text("Song, artist, or paste a link") },
                leadingIcon = {
                    Icon(
                        imageVector = if (linkMode) Icons.Default.Link else Icons.Default.Search,
                        contentDescription = null,
                        tint = if (linkMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                linkMode = !linkMode
                                // Flipping to link mode with a URL ready fetches it at once.
                                if (linkMode && text.trim().isNotEmpty()) submit()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = if (linkMode) "Link mode on" else "Search a link",
                                tint = if (linkMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                shape = RoundedCornerShape(AuraRadius.Md)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Xs))

            Text(
                text = if (linkMode) {
                    "Link mode — videos, Shorts and playlists (up to 50 tracks)."
                } else {
                    "Results stream instantly, save, or download offline."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AuraSpacing.Lg)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            if (linkMode) {
                AddSongPanel(
                    onSongAdded = onSongAdded,
                    onPlaylistDone = onPlaylistDone,
                    viewModel = addSongViewModel,
                    showInput = false,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SearchPanel(
                    onPlayResult = onPlayResult,
                    onOpenSettings = onOpenSettings,
                    viewModel = searchViewModel,
                    showInput = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AuraToast(
            message = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AuraSpacing.Xxl)
        )
    }
}
