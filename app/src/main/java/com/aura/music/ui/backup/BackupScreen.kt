package com.aura.music.ui.backup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.components.TagChip
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.writeExport(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadImportFile(uri)
    }

    // Fire the system save dialog exactly once per prepared export.
    val pendingName = state.pendingExportName
    val pendingJson = state.pendingExportJson
    LaunchedEffect(pendingName, pendingJson) {
        if (pendingName != null && pendingJson != null) {
            saveLauncher.launch(pendingName)
        }
    }

    // Fire the system share sheet exactly once per staged share file.
    val pendingShare = state.pendingShareUri
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, pendingShare)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share library backup"))
            viewModel.consumeShareUri()
        }
    }

    val excluded = state.excludedTags.map { it.lowercase() }.toSet()
    val exportableCount = if (state.selectiveExport) {
        state.selectedSongIds.size
    } else if (excluded.isEmpty()) {
        songs.size
    } else {
        songs.count { item -> item.tags.none { it.name.lowercase() in excluded } }
    }
    val canExport = songs.isNotEmpty() &&
        (!state.selectiveExport || state.selectedSongIds.isNotEmpty())

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = AuraSpacing.Xxl)
        ) {
            AuraTopBar(
                title = "Backup",
                subtitle = "Save or restore",
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(AuraSpacing.Sm))

            // ---------- Export ----------
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md)
                    .animateContentSize()
            ) {
                Column(modifier = Modifier.padding(AuraSpacing.Md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                        Text(
                            text = "Export",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !state.selectiveExport,
                            onClick = { viewModel.setSelectiveExport(false) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = state.selectiveExport,
                            onClick = { viewModel.setSelectiveExport(true) },
                            label = { Text("Selected") }
                        )
                    }

                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))

                    if (state.selectiveExport) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${state.selectedSongIds.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    viewModel.selectAllSongIds(songs.map { it.song.id }.toSet())
                                }
                            ) {
                                Text("All")
                            }
                            TextButton(onClick = viewModel::clearSongSelection) {
                                Text("None")
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(
                                items = songs,
                                key = { it.song.id }
                            ) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.song.id in state.selectedSongIds,
                                        onCheckedChange = {
                                            viewModel.toggleSongSelected(item.song.id)
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.song.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!item.song.artist.isNullOrBlank()) {
                                            Text(
                                                text = item.song.artist!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                    } else if (tags.isNotEmpty()) {
                        Text(
                            text = "Exclude:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Xs),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(tags) { tag ->
                                TagChip(
                                    name = tag.name,
                                    colorHex = tag.colorHex,
                                    selected = tag.name.lowercase() in excluded ||
                                        tag.name in state.excludedTags,
                                    onClick = { viewModel.toggleExcluded(tag.name) }
                                )
                            }
                        }
                        if (state.excludedTags.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearExcluded) {
                                Text("Clear")
                            }
                        }
                        Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                    }

                    Text(
                        text = if (state.selectiveExport) {
                            "$exportableCount selected"
                        } else {
                            "$exportableCount of ${songs.size}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
                    ) {
                        Button(
                            onClick = viewModel::startExport,
                            enabled = canExport,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(AuraRadius.Md)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = viewModel::shareExport,
                            enabled = canExport,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(AuraRadius.Md),
                            contentPadding = PaddingValues(horizontal = AuraSpacing.Md)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share"
                            )
                            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                            Text("Share")
                        }
                    }

                    AnimatedVisibility(
                        visible = state.exportMessage != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (state.exportMessage != null) {
                            Text(
                                text = if (state.exportMessage == "Library exported.") "Saved." else state.exportMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.exportMessage == "Library exported.") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.padding(top = AuraSpacing.Xs)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.Md))

            // ---------- Import ----------
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Md)
                    .animateContentSize()
            ) {
                Column(modifier = Modifier.padding(AuraSpacing.Md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                        Text(
                            text = "Import",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(AuraSpacing.Xs))

                    Text(
                        text = "Restore from JSON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                    OutlinedButton(
                        onClick = { openLauncher.launch(arrayOf("application/json")) },
                        enabled = !state.importWorking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(AuraRadius.Md)
                    ) {
                        Text(if (state.importFileName != null) "Choose file" else "Choose file")
                    }

                    val preview = state.importPreview
                    if (preview != null) {
                        Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                        Text(
                            text = state.importFileName ?: "Backup",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.Xxs))
                        Text(
                            text = "${preview.importable} new • " +
                                "${preview.duplicates} dupes • " +
                                "${preview.invalid} skipped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        (preview.invalidReasons.take(2)).forEach { reason ->
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                        Button(
                            onClick = viewModel::startImport,
                            enabled = !state.importWorking && preview.importable > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(AuraRadius.Md)
                        ) {
                            Text("Import ${preview.importable}")
                        }
                    }

                    if (state.importWorking) {
                        Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                            Text(
                                text = if (state.importTotal > 0) {
                                    "${state.importDone + 1}/${state.importTotal} • ${state.importCurrent}"
                                } else {
                                    "…"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                        LinearProgressIndicator(
                            progress = {
                                if (state.importTotal > 0) {
                                    (state.importDone.toFloat() / state.importTotal).coerceIn(0f, 1f)
                                } else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(percent = 50))
                        )
                    }

                    val summary = state.importSummary
                    if (summary != null) {
                        Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                            Text(
                                text = "Done: ${summary.imported} new, " +
                                    "${summary.duplicatesMerged} merged, " +
                                    "${summary.failed} failed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        summary.errors.take(3).forEach { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (summary.errors.size > 3) {
                            Text(
                                text = "+ ${summary.errors.size - 3} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = viewModel::clearImport) {
                            Text("Another file")
                        }
                    }

                    if (state.importError != null) {
                        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.importError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
