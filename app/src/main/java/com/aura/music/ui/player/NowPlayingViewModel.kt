package com.aura.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SongEntity
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.YouTubeTrack
import com.aura.music.playback.PlaybackController
import com.aura.music.playback.PlaybackUiState
import com.aura.music.playback.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val savedTrackRepository: SavedTrackRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    /**
     * Track-only slice: emits solely on song change, never on the 500ms
     * position ticks. Screens that only need "what's playing" (tabs,
     * library flags, mini-player visibility) collect this instead of the
     * full state so they don't recompose twice a second.
     */
    val currentTrack: StateFlow<SongEntity?> = playbackController.playbackState
        .map { it.currentSong }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val waveform: StateFlow<FloatArray> = playbackController.waveform

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Watch URLs bookmarked into Saved — drives save/unsave menu state. */
    val savedUrls: StateFlow<Set<String>> =
        savedTrackRepository.observeSavedUrls()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Save/remove the currently playing stream in Saved. */
    fun toggleSaveCurrent() {
        val song = playbackState.value.currentSong ?: return
        if (song.id >= 0) return
        viewModelScope.launch {
            try {
                if (savedTrackRepository.isSaved(song.sourceUrl)) {
                    savedTrackRepository.unsaveByUrl(song.sourceUrl)
                    _messages.emit("Removed from Saved")
                } else {
                    savedTrackRepository.save(
                        YouTubeTrack(
                            url = song.sourceUrl,
                            title = song.title,
                            artist = song.artist,
                            durationMs = song.durationMs,
                            thumbnailUrl = song.thumbnailPath
                        )
                    )
                    _messages.emit("Saved — find it in Library → Saved")
                }
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't save.")
            }
        }
    }

    /** Download the currently playing stream into the vault. */
    fun downloadCurrent() {
        val song = playbackState.value.currentSong ?: return
        if (song.id >= 0 || song.sourceUrl.isBlank()) return
        viewModelScope.launch {
            try {
                songRepository.enqueueDownload(song.sourceUrl)
                _messages.emit("Downloading \"${song.title}\" — see Library.")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't start download.")
            }
        }
    }

    /** Delete the currently playing download and move on. */
    fun deleteCurrent() {
        val state = playbackState.value
        val song = state.currentSong ?: return
        if (song.id < 0) return
        viewModelScope.launch {
            try {
                val index = state.currentIndexInQueue
                songRepository.deleteSong(song.id)
                if (index in 0 until playbackState.value.queue.size) {
                    playbackController.removeFromQueue(index)
                }
                _messages.emit("Deleted \"${song.title}\"")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't delete.")
            }
        }
    }

    /**
     * Chrome slice: track + transport flags + queue. Position/duration are
     * deliberately excluded, so collectors skip the 500ms progress ticks —
     * only ProgressSection collects those, in isolation.
     */
    val playerChrome: StateFlow<PlayerChrome> = playbackController.playbackState
        .map { s ->
            PlayerChrome(
                song = s.currentSong,
                isPlaying = s.isPlaying,
                shuffleEnabled = s.shuffleEnabled,
                repeatMode = s.repeatMode,
                errorMessage = s.errorMessage,
                queue = s.queue,
                currentIndex = s.currentIndexInQueue
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerChrome())

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun skipToNext() = playbackController.skipToNext()

    fun skipToPrevious() = playbackController.skipToPrevious()

    fun setShuffleEnabled(enabled: Boolean) = playbackController.setShuffleEnabled(enabled)

    fun playAtIndex(index: Int) = playbackController.playAtIndex(index)

    fun removeFromQueue(index: Int) = playbackController.removeFromQueue(index)

    fun moveQueue(fromIndex: Int, toIndex: Int) = playbackController.reorderQueue(fromIndex, toIndex)

    fun cycleRepeatMode() {
        val current = playbackState.value.repeatMode
        val next = when (current) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackController.setRepeatMode(next)
    }
}

/** Rarely-changing player chrome (excludes position ticks by construction). */
data class PlayerChrome(
    val song: SongEntity? = null,
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val errorMessage: String? = null,
    val queue: List<SongEntity> = emptyList(),
    val currentIndex: Int = -1
)