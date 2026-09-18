package com.aura.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SongEntity
import com.aura.music.playback.PlaybackController
import com.aura.music.playback.PlaybackUiState
import com.aura.music.playback.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController
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