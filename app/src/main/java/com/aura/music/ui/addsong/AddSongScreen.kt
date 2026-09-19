package com.aura.music.ui.addsong

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import com.aura.music.ui.components.AlbumArt
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.components.QualityBadge
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.util.downloadBytesDetail
import com.aura.music.util.downloadStageLabel
import com.aura.music.util.formatDuration

@Composable
fun AddSongPanel(
    onSongAdded: (Long) -> Unit,
    onPlaylistDone: () -> Unit,
    viewModel: AddSongViewModel = hiltViewModel(),
    showInput: Boolean = true,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
            if (showInput) {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::setUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md),
                placeholder = { Text("YouTube link") },
                singleLine = true,
                enabled = state.phase !is AddSongPhase.Downloading,
                trailingIcon = {
                    if (state.url.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setUrl("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(AuraRadius.Lg)
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))
            }

            val ctaLabel = when (val phase = state.phase) {
                is AddSongPhase.Idle,
                is AddSongPhase.Error,
                is AddSongPhase.Resolving -> "Fetch"
                is AddSongPhase.Preview -> "Download"
                is AddSongPhase.PreviewPlaylist -> "Download ${phase.preview.importable}"
                is AddSongPhase.Downloading -> "Working…"
                is AddSongPhase.Success -> "Done"
                is AddSongPhase.PlaylistSuccess -> "Done"
            }
            Button(
                onClick = viewModel::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md)
                    .height(52.dp),
                enabled = state.url.isNotBlank() &&
                    state.phase !is AddSongPhase.Resolving &&
                    state.phase !is AddSongPhase.Downloading &&
                    state.phase !is AddSongPhase.Success &&
                    state.phase !is AddSongPhase.PlaylistSuccess &&
                    (state.phase !is AddSongPhase.PreviewPlaylist ||
                        (state.phase as AddSongPhase.PreviewPlaylist).preview.importable > 0),
                shape = RoundedCornerShape(AuraRadius.Md)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                Text(ctaLabel)
            }

            // Pinned below the main CTA (never inside the scrolling preview):
            // a video shared from inside a playlist can be upgraded to the
            // whole-list import with one tap. It must not live under the
            // preview card where short screens would cut it off.
            val playlistOptionUrl = (state.phase as? AddSongPhase.Preview)?.playlistOptionUrl
            if (playlistOptionUrl != null) {
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                OutlinedButton(
                    onClick = viewModel::usePlaylistInstead,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md)
                        .height(48.dp),
                    shape = RoundedCornerShape(AuraRadius.Md)
                ) {
                    Text("Get full playlist")
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.Md))

            // Scrollable phase area: on short screens (or with the keyboard
            // open) tall previews must not push buttons like "Get full
            // playlist" off-screen with no way to reach them.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = AuraSpacing.Md)
            ) {
            AnimatedContent(
                targetState = phaseKey(state.phase),
                label = "downloadPhase"
            ) { _ ->
            when (val phase = state.phase) {
                is AddSongPhase.Idle -> {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.Md)
                            .animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(AuraSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                            Text(
                                text = "Video, Short or playlist — up to 50 tracks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is AddSongPhase.Resolving -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AuraSpacing.Xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Reading link…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is AddSongPhase.Preview -> {
                    val info = phase.info
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.Md)
                            .animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(AuraSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (info.thumbnailUrl != null) {
                                AsyncImage(
                                    model = info.thumbnailUrl,
                                    contentDescription = info.title,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(AuraRadius.Md))
                                )
                            } else {
                                AlbumArt(
                                    thumbnailPath = null,
                                    contentDescription = null,
                                    size = 64.dp,
                                    cornerRadius = AuraRadius.Md
                                )
                            }
                            Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        info.uploader,
                                        if (info.durationMs > 0) info.durationMs.formatDuration() else null
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                                Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs)) {
                                    QualityBadge(
                                        codec = info.codec,
                                        bitrateKbps = info.bitrateKbps
                                    )
                                    Text(
                                        text = info.qualityLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                is AddSongPhase.PreviewPlaylist -> {
                    val preview = phase.preview
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.Md)
                            .animateContentSize()
                    ) {
                        Column(modifier = Modifier.padding(AuraSpacing.Md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (preview.thumbnailUrl != null) {
                                    AsyncImage(
                                        model = preview.thumbnailUrl,
                                        contentDescription = preview.title,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(AuraRadius.Md))
                                    )
                                } else {
                                    AlbumArt(
                                        thumbnailPath = null,
                                        contentDescription = null,
                                        size = 64.dp,
                                        cornerRadius = AuraRadius.Md
                                    )
                                }
                                Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preview.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            preview.author,
                                            "${preview.videoUrls.size} tracks" +
                                                if (preview.isCapped) {
                                                    " (first ${preview.videoUrls.size} of ${preview.totalCount})"
                                                } else ""
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (preview.duplicates > 0) {
                                        Text(
                                            text = "${preview.duplicates} dupes skipped",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = AuraSpacing.Xxs)
                            ) {
                                Checkbox(
                                    checked = phase.tagWithPlaylist,
                                    onCheckedChange = { viewModel.togglePlaylistTag() }
                                )
                                Text(
                                    text = "Tag as “${preview.title}”",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                    Text(
                        text = "Skips private or deleted videos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AuraSpacing.Lg)
                    )
                }

                is AddSongPhase.Downloading -> {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.Md)
                    ) {
                        Column(modifier = Modifier.padding(AuraSpacing.Md)) {
                            Text(
                                text = downloadStageLabel(phase.stage),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                            LinearProgressIndicator(
                                progress = { (phase.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(percent = 50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                            Text(
                                text = downloadBytesDetail(phase.percent, phase.bytesDone, phase.bytesTotal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (phase.subtitle != null) {
                                Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                                Text(
                                    text = phase.subtitle!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                            Text(
                                text = "Safe to leave — keeps downloading in Library.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                is AddSongPhase.Success -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AuraSpacing.Xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Saved",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                            ) {
                                TextButton(onClick = { viewModel.reset() }) {
                                    Text("Add more")
                                }
                                Button(onClick = { onSongAdded(phase.songId) }) {
                                    Text("View")
                                }
                            }
                        }
                    }
                }

                is AddSongPhase.PlaylistSuccess -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AuraSpacing.Xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "“${phase.playlistTitle}” saved",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${phase.imported} saved • " +
                                    "${phase.skipped} dupes • " +
                                    "${phase.failed} failed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            phase.errors.take(3).forEach { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                            ) {
                                TextButton(onClick = { viewModel.reset() }) {
                                    Text("More")
                                }
                                Button(onClick = onPlaylistDone) {
                                    Text("Library")
                                }
                            }
                        }
                    }
                }

                is AddSongPhase.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AuraSpacing.Xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = phase.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = viewModel::retry) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private fun phaseKey(phase: AddSongPhase): String = when (phase) {
    is AddSongPhase.Idle -> "idle"
    is AddSongPhase.Resolving -> "loading"
    is AddSongPhase.Preview -> "preview"
    is AddSongPhase.PreviewPlaylist -> "playlist"
    is AddSongPhase.Downloading -> "downloading"
    is AddSongPhase.Success -> "done"
    is AddSongPhase.PlaylistSuccess -> "doneList"
    is AddSongPhase.Error -> "error"
}
