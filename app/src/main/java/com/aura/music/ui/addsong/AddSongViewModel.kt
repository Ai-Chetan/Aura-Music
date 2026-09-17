package com.aura.music.ui.addsong

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.aura.music.data.download.DownloadAudioWorker
import com.aura.music.domain.repository.ExtractedStreamInfo
import com.aura.music.domain.repository.PlaylistPreview
import com.aura.music.domain.repository.SongRepository
import com.aura.music.util.YoutubeUrls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface AddSongPhase {
    data object Idle : AddSongPhase
    data object Resolving : AddSongPhase
    data class Preview(
        val info: ExtractedStreamInfo,
        /** Set when the pasted video link also carries a playlist — offers batch import. */
        val playlistOptionUrl: String? = null
    ) : AddSongPhase
    data class PreviewPlaylist(
        val preview: PlaylistPreview,
        val tagWithPlaylist: Boolean = true
    ) : AddSongPhase
    data class Downloading(
        val percent: Int = 0,
        val stage: String = DownloadAudioWorker.PROGRESS_DOWNLOADING,
        val bytesDone: Long? = null,
        val bytesTotal: Long? = null,
        /** Extra line, e.g. "3 of 25 • title" during playlist batches. */
        val subtitle: String? = null
    ) : AddSongPhase
    data class Success(val songId: Long) : AddSongPhase
    data class PlaylistSuccess(
        val playlistTitle: String,
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<String>
    ) : AddSongPhase
    data class Error(val message: String) : AddSongPhase
}

data class AddSongUiState(
    val url: String = "",
    val phase: AddSongPhase = AddSongPhase.Idle
) {
    val isWorking: Boolean get() = phase is AddSongPhase.Resolving ||
        phase is AddSongPhase.Downloading
    val isPlaylistUrl: Boolean get() = YoutubeUrls.isPlaylistUrl(url.trim())
}

@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddSongUiState())
    val uiState: StateFlow<AddSongUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    fun setUrl(value: String) {
        downloadJob?.cancel()
        _uiState.update { it.copy(url = value, phase = AddSongPhase.Idle) }
    }

    /** Step 1 — validate + resolve metadata for the preview card. */
    fun preview() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) return
        if (!YoutubeUrls.isYouTubeUrl(url)) {
            _uiState.update {
                it.copy(phase = AddSongPhase.Error(YoutubeUrls.rejectionReason(url)))
            }
            return
        }
        if (YoutubeUrls.isPlaylistUrl(url)) {
            previewPlaylist()
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = AddSongPhase.Resolving) }
            // A watch link shared from inside a playlist still resolves as one
            // video — but remember its playlist so the UI can offer the batch.
            val playlistOptionUrl = if (YoutubeUrls.isVideoWithPlaylist(url)) {
                YoutubeUrls.canonicalPlaylistUrl(url)
            } else null
            songRepository.previewFromUrl(url)
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(phase = AddSongPhase.Preview(info, playlistOptionUrl))
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            phase = AddSongPhase.Error(
                                throwable.message ?: "Couldn't read that video."
                            )
                        )
                    }
                }
        }
    }

    private fun previewPlaylist() {
        val url = _uiState.value.url.trim()
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = AddSongPhase.Resolving) }
            songRepository.describePlaylist(url)
                .onSuccess { preview ->
                    _uiState.update { it.copy(phase = AddSongPhase.PreviewPlaylist(preview)) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            phase = AddSongPhase.Error(
                                throwable.message ?: "Couldn't read that playlist."
                            )
                        )
                    }
                }
        }
    }

    fun togglePlaylistTag() {
        val phase = _uiState.value.phase as? AddSongPhase.PreviewPlaylist ?: return
        _uiState.update {
            it.copy(phase = phase.copy(tagWithPlaylist = !phase.tagWithPlaylist))
        }
    }

    /** Switches a watch-link preview to its playlist's batch-import flow. */
    fun usePlaylistInstead() {
        val phase = _uiState.value.phase as? AddSongPhase.Preview ?: return
        val playlistUrl = phase.playlistOptionUrl ?: return
        _uiState.update { it.copy(url = playlistUrl) }
        previewPlaylist()
    }

    /** Step 2 — enqueue the WorkManager download and stream its progress. */
    fun download() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) return

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val workId: UUID
            try {
                workId = songRepository.enqueueDownload(url)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = AddSongPhase.Error(e.message ?: "Couldn't start download."))
                }
                return@launch
            }

            _uiState.update {
                it.copy(phase = AddSongPhase.Downloading(percent = 0, stage = "resolving"))
            }

            songRepository.observeDownloadWork(workId).collect { workInfo ->
                if (workInfo == null) return@collect
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val songId = workInfo.outputData
                            .getLong(DownloadAudioWorker.KEY_SONG_ID, -1L)
                        if (songId > 0) {
                            _uiState.update { it.copy(phase = AddSongPhase.Success(songId)) }
                        } else {
                            _uiState.update {
                                it.copy(phase = AddSongPhase.Error("Download finished without a song."))
                            }
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData
                            .getString(DownloadAudioWorker.KEY_ERROR) ?: "Download failed"
                        _uiState.update { it.copy(phase = AddSongPhase.Error(error)) }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.update { it.copy(phase = AddSongPhase.Error("Download cancelled.")) }
                    }
                    else -> {
                        val progress = workInfo.progress
                        val stage = progress.getString(DownloadAudioWorker.KEY_PROGRESS)
                            ?: DownloadAudioWorker.PROGRESS_DOWNLOADING
                        val percent = progress.getInt(DownloadAudioWorker.KEY_PERCENT, 0)
                        val done = progress.getLong(DownloadAudioWorker.KEY_BYTES_DONE, -1L)
                            .takeIf { it >= 0 }
                        val total = progress.getLong(DownloadAudioWorker.KEY_BYTES_TOTAL, -1L)
                            .takeIf { it > 0 }
                        _uiState.update {
                            it.copy(
                                phase = AddSongPhase.Downloading(
                                    percent = percent,
                                    stage = stage,
                                    bytesDone = done,
                                    bytesTotal = total
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun downloadPlaylist() {
        val phase = _uiState.value.phase as? AddSongPhase.PreviewPlaylist ?: return
        val url = _uiState.value.url.trim()
        if (url.isBlank()) return

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AddSongPhase.Downloading(percent = 0, stage = "resolving"))
            }
            val tag = if (phase.tagWithPlaylist) phase.preview.title else null
            songRepository.importPlaylist(url, tag) { done, total, current ->
                val pct = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
                _uiState.update {
                    it.copy(
                        phase = AddSongPhase.Downloading(
                            percent = pct,
                            stage = DownloadAudioWorker.PROGRESS_DOWNLOADING,
                            subtitle = if (total > 0 && current.isNotEmpty()) {
                                "$current"
                            } else null
                        )
                    )
                }
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        phase = AddSongPhase.PlaylistSuccess(
                            playlistTitle = summary.playlistTitle,
                            imported = summary.imported,
                            skipped = summary.skippedDuplicate,
                            failed = summary.failed,
                            errors = summary.errors
                        )
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(phase = AddSongPhase.Error(e.message ?: "Playlist import failed."))
                }
            }
        }
    }

    /** Single-tap path: preview first, then download. */
    fun submit() {
        when (val phase = _uiState.value.phase) {
            is AddSongPhase.Preview -> download()
            is AddSongPhase.PreviewPlaylist -> downloadPlaylist()
            is AddSongPhase.Idle, is AddSongPhase.Error -> preview()
            else -> Unit
        }
    }

    fun reset() {
        downloadJob?.cancel()
        _uiState.update { AddSongUiState() }
    }
}
