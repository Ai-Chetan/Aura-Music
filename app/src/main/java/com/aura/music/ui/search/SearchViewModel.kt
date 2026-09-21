package com.aura.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.network.GateSnapshot
import com.aura.music.data.network.NetworkGate
import com.aura.music.data.stream.TransientTrackFactory
import com.aura.music.data.stream.UpNextManager
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.YouTubeTrack
import com.aura.music.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    private val savedTrackRepository: SavedTrackRepository,
    private val playbackController: PlaybackController,
    private val transients: TransientTrackFactory,
    private val upNext: UpNextManager,
    private val gate: NetworkGate
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val gateState: StateFlow<GateSnapshot> = gate.state

    val savedUrls: StateFlow<Set<String>> =
        savedTrackRepository.observeSavedUrls()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var searchJob: Job? = null
    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            upNext.events.collect { _messages.emit(it) }
        }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        if (!gate.runIfAllowed(action = { runSearch(query) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Search unavailable.")
            }
        }
    }

    private fun runSearch(query: String) {
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
     * Instant play: the tapped result starts at once and the recommendation
     * engine fills everything after it — a search tap is a radio seed, never
     * "play the results in order".
     */
    fun playNow(track: YouTubeTrack, onPlaying: () -> Unit) {
        if (_uiState.value.resolvingUrl != null) return
        if (!gate.runIfAllowed(action = { resolveAndPlay(track, onPlaying) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    private fun resolveAndPlay(track: YouTubeTrack, onPlaying: () -> Unit) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _uiState.update { it.copy(resolvingUrl = track.url) }
            try {
                val transient = transients.fromTrack(track)
                playbackController.playQueue(listOf(transient), 0)
                upNext.startRadio()
                savedTrackRepository.recordPlay(track.url)
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
                val workId = songRepository.enqueueDownload(track.url)
                _messages.emit("Downloading \"${track.title}\" — see Library.")
                // Unstick the row icon when the download leaves the queue.
                viewModelScope.launch {
                    try {
                        songRepository.observeDownloadWork(workId)
                            .first { it?.state?.isFinished == true }
                    } catch (_: Exception) {
                    }
                    _uiState.update { it.copy(queuedUrls = it.queuedUrls - track.url) }
                }
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't start download.")
                _uiState.update { it.copy(queuedUrls = it.queuedUrls - track.url) }
            }
        }
    }

    /** Bookmark as a streaming Saved track (needs internet, no download). */
    fun toggleSave(track: YouTubeTrack) {
        viewModelScope.launch {
            try {
                if (savedTrackRepository.isSaved(track.url)) {
                    savedTrackRepository.unsaveByUrl(track.url)
                    _messages.emit("Removed from Saved")
                } else {
                    savedTrackRepository.save(track)
                    _messages.emit("Saved — find it in Library → Saved")
                }
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't save.")
            }
        }
    }
}
