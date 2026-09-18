package com.aura.music.ui.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.domain.repository.ImportPreview
import com.aura.music.domain.repository.ImportSummary
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class BackupUiState(
    val excludedTags: Set<String> = emptySet(),
    /** True = export only [selectedSongIds]; false = complete export. */
    val selectiveExport: Boolean = false,
    val selectedSongIds: Set<Long> = emptySet(),
    /** Set when the JSON is ready — the screen fires the save dialog once. */
    val pendingExportJson: String? = null,
    val pendingExportName: String? = null,
    val exportMessage: String? = null,
    /** Set when a share file is staged — the screen fires the chooser once. */
    val pendingShareUri: Uri? = null,
    val importFileName: String? = null,
    val importPreview: ImportPreview? = null,
    val importWorking: Boolean = false,
    val importDone: Int = 0,
    val importTotal: Int = 0,
    val importCurrent: String = "",
    val importSummary: ImportSummary? = null,
    val importError: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val songRepository: SongRepository,
    tagRepository: TagRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val allSongs = songRepository.observeAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags = tagRepository.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var lastImportJson: String? = null

    // ---- Export ----

    fun toggleExcluded(tagName: String) {
        _uiState.update {
            val current = it.excludedTags
            it.copy(excludedTags = if (tagName in current) current - tagName else current + tagName)
        }
    }

    fun clearExcluded() {
        _uiState.update { it.copy(excludedTags = emptySet()) }
    }

    fun setSelectiveExport(selective: Boolean) {
        _uiState.update { it.copy(selectiveExport = selective) }
    }

    fun toggleSongSelected(songId: Long) {
        _uiState.update {
            val current = it.selectedSongIds
            it.copy(selectedSongIds = if (songId in current) current - songId else current + songId)
        }
    }

    fun selectAllSongIds(ids: Set<Long>) {
        _uiState.update { it.copy(selectedSongIds = ids) }
    }

    fun clearSongSelection() {
        _uiState.update { it.copy(selectedSongIds = emptySet()) }
    }

    private fun exportScope(): Pair<Set<String>, Set<Long>?> {
        val s = _uiState.value
        return if (s.selectiveExport) {
            emptySet<String>() to s.selectedSongIds.ifEmpty { null }
        } else {
            s.excludedTags to null
        }
    }

    fun startExport() {
        viewModelScope.launch {
            val (excluded, onlyIds) = exportScope()
            // JSON serialization of a big vault must not run on Main.
            val result = withContext(Dispatchers.IO) {
                songRepository.exportLibraryJson(excluded, onlyIds)
            }
            result
                .onSuccess { json ->
                    val name = "aura-library-${LocalDate.now()}.json"
                    _uiState.update {
                        it.copy(
                            pendingExportJson = json,
                            pendingExportName = name,
                            exportMessage = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(exportMessage = e.message ?: "Export failed.")
                    }
                }
        }
    }

    fun writeExport(uri: Uri) {
        val json = _uiState.value.pendingExportJson ?: return
        viewModelScope.launch {
            // File I/O off Main; state updates are thread-safe.
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("Couldn't open that location for writing.")
                }
            }
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            pendingExportJson = null,
                            pendingExportName = null,
                            exportMessage = "Library exported."
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            pendingExportJson = null,
                            pendingExportName = null,
                            exportMessage = "Export failed: ${e.message}"
                        )
                    }
                }
        }
    }

    fun consumeExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    /** Stage the current export scope as a cache file and expose a share Uri. */
    fun shareExport() {
        viewModelScope.launch {
            val (excluded, onlyIds) = exportScope()
            val result = withContext(Dispatchers.IO) {
                songRepository.exportLibraryJson(excluded, onlyIds)
            }
            result
                .onSuccess { json ->
                    val staged = withContext(Dispatchers.IO) {
                        runCatching {
                            val dir = File(appContext.cacheDir, "backup").apply { mkdirs() }
                            val file = File(dir, "aura-library-${LocalDate.now()}.json")
                            file.writeText(json, Charsets.UTF_8)
                            FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.fileprovider",
                                file
                            )
                        }
                    }
                    staged
                        .onSuccess { uri ->
                            _uiState.update { it.copy(pendingShareUri = uri, exportMessage = null) }
                        }
                        .onFailure { e ->
                            _uiState.update {
                                it.copy(exportMessage = "Share failed: ${e.message}")
                            }
                        }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(exportMessage = e.message ?: "Export failed.")
                    }
                }
        }
    }

    fun consumeShareUri() {
        _uiState.update { it.copy(pendingShareUri = null) }
    }

    // ---- Import ----

    fun loadImportFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    importFileName = null, importPreview = null,
                    importSummary = null, importError = null
                )
            }
            // File read + JSON parse off Main.
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("Couldn't read that file.")
                    if (text.length > 10 * 1024 * 1024) {
                        throw IllegalStateException("File is too large to be a library backup.")
                    }
                    text
                }
            }
            val text = result.getOrElse { e ->
                _uiState.update { it.copy(importError = e.message ?: "Couldn't read that file.") }
                return@launch
            }
            val preview = withContext(Dispatchers.IO) {
                songRepository.describeImport(text)
            }
            preview
                .onSuccess { parsed ->
                    lastImportJson = text
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                    _uiState.update {
                        it.copy(importFileName = name, importPreview = parsed)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(importError = e.message) }
                }
        }
    }

    fun startImport() {
        val json = lastImportJson ?: return
        if (_uiState.value.importWorking) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    importWorking = true, importSummary = null, importError = null,
                    importDone = 0, importTotal = it.importPreview?.total ?: 0, importCurrent = ""
                )
            }
            // Parse + restore run off Main; progress updates are thread-safe.
            val result = withContext(Dispatchers.IO) {
                songRepository.importLibraryJson(json) { done, total, current ->
                    _uiState.update {
                        it.copy(importDone = done, importTotal = total, importCurrent = current)
                    }
                }
            }
            result.onSuccess { summary ->
                _uiState.update { it.copy(importWorking = false, importSummary = summary) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        importWorking = false,
                        importError = e.message ?: "Import failed."
                    )
                }
            }
        }
    }

    fun clearImport() {
        lastImportJson = null
        _uiState.update {
            it.copy(
                importFileName = null, importPreview = null,
                importSummary = null, importError = null,
                importWorking = false, importDone = 0, importTotal = 0, importCurrent = ""
            )
        }
    }
}
