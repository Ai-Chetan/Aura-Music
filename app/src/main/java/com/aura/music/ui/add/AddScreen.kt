package com.aura.music.ui.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.music.ui.addsong.AddSongPanel
import com.aura.music.ui.addsong.AddSongViewModel
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraToast
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.search.SearchPanel
import com.aura.music.ui.search.SearchViewModel
import com.aura.music.ui.theme.AuraSpacing
import kotlinx.coroutines.delay

/**
 * Combined "add music" section: YouTube search and paste-a-link live side
 * by side under one segmented toggle, sharing the same chrome so both
 * flows look and feel like one place.
 */
@Composable
fun AddScreen(
    initialUrl: String? = null,
    onSongAdded: (Long) -> Unit = {},
    onPlaylistDone: () -> Unit = {},
    onPlayResult: () -> Unit = {},
    addSongViewModel: AddSongViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    // A prefilled link (deep link / shared URL) opens straight on Paste link.
    var mode by rememberSaveable { mutableIntStateOf(if (!initialUrl.isNullOrBlank()) 1 else 0) }

    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchViewModel) {
        searchViewModel.messages.collect { message ->
            toast = message
            delay(1800)
            toast = null
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
                title = "Add music",
                subtitle = if (mode == 0) "Search YouTube" else "Best quality, saved as-is"
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md)
            ) {
                SegmentedButton(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = mode == 0) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text("Search") }
                )
                SegmentedButton(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = mode == 1) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text("Paste link") }
                )
            }

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            if (mode == 0) {
                SearchPanel(
                    onPlayResult = onPlayResult,
                    viewModel = searchViewModel,
                    modifier = Modifier.weight(1f)
                )
            } else {
                AddSongPanel(
                    onSongAdded = onSongAdded,
                    onPlaylistDone = onPlaylistDone,
                    viewModel = addSongViewModel,
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
