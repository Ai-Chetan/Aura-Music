package com.aura.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SongEntity
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.YouTubeTrack
import com.aura.music.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchPhase {
    data object Idle : SearchPhase
    data object Searching : SearchPhase
    data class Results(val tracks: List<YouTubeTrack>) : SearchPhase
    data object Empty : SearchPhase
    data class Error(val message: String) : SearchPhase
}

data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    /** URL of the row currently resolving a stream for instant play. */
    val resolvingUrl: String? = null,
    /** URLs with a save already queued (fire-and-forget). */
    val queuedUrls: Set<String> = emptySet()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val extractionRepository: ExtractionRepository,
    private val songRepository: SongRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var searchJob: Job? = null
    private var playJob: Job? = null

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = SearchPhase.Searching) }
            try {
                val tracks = extractionRepository.searchMusic(query)
                _uiState.update {
                    it.copy(
                        phase = if (tracks.isEmpty()) SearchPhase.Empty
                        else SearchPhase.Results(tracks)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = SearchPhase.Error(e.message ?: "Search failed."))
                }
            }
        }
    }

    /**
     * Instant play: resolves the best audio stream and plays it directly
     * without saving. The queue entry is transient (negative id) — it
     * streams, it is never written to the vault.
     */
    fun playNow(track: YouTubeTrack, onPlaying: () -> Unit) {
        if (_uiState.value.resolvingUrl != null) return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _uiState.update { it.copy(resolvingUrl = track.url) }
            try {
                val info = extractionRepository.resolveStreamInfo(track.url)
                val transient = SongEntity(
                    id = -1L * ((track.url.hashCode().toLong() and 0x7fffffffL) + 1L),
                    title = info.title,
                    artist = info.uploader?.takeIf { it.isNotBlank() } ?: track.artist,
                    sourceUrl = track.url,
                    sourcePlatform = "youtube",
                    localFilePath = info.bestAudioStreamUrl,
                    thumbnailPath = info.thumbnailUrl ?: track.thumbnailUrl,
                    durationMs = info.durationMs,
                    bitrateKbps = info.bitrateKbps,
                    audioFormat = info.codec,
                    isLossless = false,
                    dateAdded = System.currentTimeMillis()
                )
                playbackController.playQueue(listOf(transient), 0)
                onPlaying()
            } catch (e: Exception) {
                _messages.emit("Couldn't stream: ${e.message ?: "unknown error"}")
            } finally {
                _uiState.update { it.copy(resolvingUrl = null) }
            }
        }
    }

    /** Save to the vault via the normal background download pipeline. */
    fun download(track: YouTubeTrack) {
        if (track.url in _uiState.value.queuedUrls) return
        viewModelScope.launch {
            _uiState.update { it.copy(queuedUrls = it.queuedUrls + track.url) }
            try {
                songRepository.enqueueDownload(track.url)
                _messages.emit("Downloading \"${track.title}\" — see Library.")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't start download.")
                _uiState.update { it.copy(queuedUrls = it.queuedUrls - track.url) }
            }
        }
    }
}
