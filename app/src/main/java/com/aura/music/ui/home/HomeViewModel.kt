package com.aura.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SavedTrackEntity
import com.aura.music.data.db.SongEntity
import com.aura.music.data.recommendations.RecommendationsRepository
import com.aura.music.data.network.GateSnapshot
import com.aura.music.data.network.NetworkGate
import com.aura.music.data.stream.TransientTrackFactory
import com.aura.music.data.stream.UpNextManager
import com.aura.music.data.stream.asTracks
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val trending: List<YouTubeTrack> = emptyList(),
    val forYou: List<YouTubeTrack> = emptyList(),
    val forYouSubtitle: String = "",
    val isLoadingDynamic: Boolean = false,
    /** Watch URL currently resolving into playable audio. */
    val resolvingUrl: String? = null
)

/**
 * One "continue listening" tile: either a downloaded vault song or a Saved
 * streaming bookmark — whichever you played more recently. Playing one
 * resumes the list it belongs to (its playlist), then radio continues.
 */
sealed interface ContinueItem {
    val id: Long
    val title: String
    val artist: String?
    val art: String?
    val lastPlayedAt: Long?

    data class Vault(val song: SongEntity) : ContinueItem {
        override val id get() = song.id
        override val title get() = song.title
        override val artist get() = song.artist
        override val art get() = song.thumbnailPath
        override val lastPlayedAt get() = song.lastPlayedAt
    }

    data class Saved(val track: SavedTrackEntity) : ContinueItem {
        override val id get() = track.id
        override val title get() = track.title
        override val artist get() = track.artist
        override val art get() = track.thumbnailUrl
        override val lastPlayedAt get() = track.lastPlayedAt
    }
}

/**
 * Home hub: your music first (continue listening, vault), then the most
 * significant dynamic item (today's #1 as a hero), then rails. Dynamic
 * sections stay empty-but-explained while the data gate is closed.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val savedTrackRepository: SavedTrackRepository,
    private val playbackController: PlaybackController,
    private val transients: TransientTrackFactory,
    private val upNext: UpNextManager,
    private val gate: NetworkGate,
    private val recommendations: RecommendationsRepository
) : ViewModel() {

    private val _resolvingUrl = MutableStateFlow<String?>(null)

    /** Dynamic lists come from the shared repository (single fetch). */
    val uiState: StateFlow<HomeUiState> = combine(
        recommendations.data,
        recommendations.loading,
        _resolvingUrl
    ) { data, loading, resolvingUrl ->
        HomeUiState(
            trending = data.trending.take(HOME_TRENDING),
            forYou = data.forYou.take(HOME_FOR_YOU),
            forYouSubtitle = data.forYouSubtitle,
            isLoadingDynamic = loading && data.trending.isEmpty() && data.forYou.isEmpty(),
            resolvingUrl = resolvingUrl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    val gateState: StateFlow<GateSnapshot> = gate.state

    /** Recently played across BOTH collections — downloads and Saved — most recent first. */
    val recent: StateFlow<List<ContinueItem>> = combine(
        songRepository.observeRecentlyPlayed(10),
        savedTrackRepository.observeSavedTracks()
    ) { songs, saved ->
        (
            songs.map { ContinueItem.Vault(it) } +
                saved.filter { it.lastPlayedAt != null }.map { ContinueItem.Saved(it) }
            )
            .sortedByDescending { it.lastPlayedAt ?: 0L }
            .take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Watch URLs bookmarked into Saved — drives save/unsave menu state. */
    val savedUrls: StateFlow<Set<String>> =
        savedTrackRepository.observeSavedUrls()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Watch URLs with a download already enqueued — drives the menu's "downloading" state. */
    private val _queuedDownloadUrls = MutableStateFlow<Set<String>>(emptySet())
    val queuedDownloadUrls: StateFlow<Set<String>> = _queuedDownloadUrls.asStateFlow()

    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            upNext.events.collect { _messages.emit(it) }
        }
    }

    /** Explicit refresh. */
    fun refresh() {
        if (!recommendations.refresh()) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't load.")
            }
        }
    }

    /**
     * Continue listening: the tapped song plays immediately, then the rest
     * of the list it belongs to — the downloads vault for vault songs, the
     * Saved list for bookmarks. After that list ends, radio continues.
     */
    fun playContinue(item: ContinueItem, onPlaying: () -> Unit) {
        when (item) {
            is ContinueItem.Vault -> viewModelScope.launch {
                upNext.clear()
                val all = try {
                    songRepository.observeAllSongs().first()
                        .map { it.song }
                        .sortedByDescending { it.dateAdded }
                } catch (_: Exception) {
                    emptyList()
                }
                val index = all.indexOfFirst { it.id == item.song.id }
                if (index >= 0) {
                    playbackController.playQueue(all, index)
                } else {
                    playbackController.playQueue(listOf(item.song), 0)
                }
                onPlaying()
            }
            is ContinueItem.Saved -> playSavedStream(item.track, onPlaying)
        }
    }

    /** Saved bookmark from the continue rail: stream it, then its Saved list, then radio. */
    private fun playSavedStream(track: SavedTrackEntity, onPlaying: () -> Unit) {
        if (_resolvingUrl.value != null) return
        if (!gate.runIfAllowed(action = { resolveSavedAndPlay(track, onPlaying) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    private fun resolveSavedAndPlay(track: SavedTrackEntity, onPlaying: () -> Unit) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _resolvingUrl.value = track.url
            try {
                val transient = transients.fromSaved(track)
                playbackController.playQueue(listOf(transient), 0)
                val tracks = savedTrackRepository.observeSavedTracks().first().asTracks()
                upNext.startPlaylistSession(
                    tracks,
                    tracks.indexOfFirst { it.url == track.url }
                )
                savedTrackRepository.recordPlay(track.url)
                onPlaying()
            } catch (e: Exception) {
                _messages.emit("Couldn't stream: ${e.message ?: "unknown error"}")
            } finally {
                _resolvingUrl.value = null
            }
        }
    }

    /** Bookmark toggle for top-hits rows. */
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

    /** Explicit offline download from top-hits rows. */
    fun download(track: YouTubeTrack) {
        if (track.url in _queuedDownloadUrls.value) return
        viewModelScope.launch {
            _queuedDownloadUrls.value = _queuedDownloadUrls.value + track.url
            try {
                val workId = songRepository.enqueueDownload(track.url)
                _messages.emit("Downloading \"${track.title}\" — see Library.")
                // Unstick the menu item once the download leaves the queue.
                viewModelScope.launch {
                    try {
                        songRepository.observeDownloadWork(workId)
                            .first { it?.state?.isFinished == true }
                    } catch (_: Exception) {
                    }
                    _queuedDownloadUrls.value = _queuedDownloadUrls.value - track.url
                }
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't start download.")
                _queuedDownloadUrls.value = _queuedDownloadUrls.value - track.url
            }
        }
    }

    /** Queue a top-hits row behind the current song (gated resolve). */
    fun queueTrack(track: YouTubeTrack) {
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.addToQueueEnd(transients.fromTrack(track))
                        _messages.emit("Queued \"${track.title}\" — plays after this song")
                    } catch (e: Exception) {
                        _messages.emit("Couldn't queue: ${e.message ?: "unknown error"}")
                    }
                }
            })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't queue.")
            }
        }
    }

    /** Slot a top-hits row to play immediately after the current song. */
    fun playNextTrack(track: YouTubeTrack) {
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.playNext(transients.fromTrack(track))
                        _messages.emit("Will play next: ${track.title}")
                    } catch (e: Exception) {
                        _messages.emit("Couldn't queue: ${e.message ?: "unknown error"}")
                    }
                }
            })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't queue.")
            }
        }
    }

    /**
     * Streaming playback — gated like everywhere else. Top hits and For-you
     * taps start radio: the tapped song plays and the engine fills everything
     * after it, so the next song is never just "the next row in the list".
     */
    fun playStream(track: YouTubeTrack, onPlaying: () -> Unit) {
        if (_resolvingUrl.value != null) return
        if (!gate.runIfAllowed(action = { resolveAndPlay(track, onPlaying) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    private fun resolveAndPlay(track: YouTubeTrack, onPlaying: () -> Unit) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _resolvingUrl.value = track.url
            try {
                val transient = transients.fromTrack(track)
                playbackController.playQueue(listOf(transient), 0)
                upNext.startRadio()
                // Streamed taste counts too (no-op unless bookmarked).
                savedTrackRepository.recordPlay(track.url)
                onPlaying()
            } catch (e: Exception) {
                _messages.emit("Couldn't stream: ${e.message ?: "unknown error"}")
            } finally {
                _resolvingUrl.value = null
            }
        }
    }

    companion object {
        /** Home shows previews; Discover owns the full lists. */
        const val HOME_TRENDING = 5
        const val HOME_FOR_YOU = 8
    }
}
