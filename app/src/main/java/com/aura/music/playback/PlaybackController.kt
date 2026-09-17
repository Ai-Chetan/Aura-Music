package com.aura.music.playback

import com.aura.music.data.db.SongEntity
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ONE, ALL }

data class PlaybackUiState(
    val currentSong: SongEntity? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<SongEntity> = emptyList(),
    val currentIndexInQueue: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Non-null when ExoPlayer hit an error (e.g. unplayable file). */
    val errorMessage: String? = null
)

interface PlaybackController {
    val playbackState: StateFlow<PlaybackUiState>

    /** Live FFT buckets (0..1), driven by the playing audio; zeros when idle. */
    val waveform: StateFlow<FloatArray>

    fun playQueue(songs: List<SongEntity>, startIndex: Int = 0)
    fun playNext(song: SongEntity)
    fun addToQueueEnd(song: SongEntity)
    /** Jump to an item already in the queue (used by the queue sheet). */
    fun playAtIndex(index: Int)
    /** Remove an item from the queue (used by the queue sheet). */
    fun removeFromQueue(index: Int)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun setShuffleEnabled(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)
    fun reorderQueue(fromIndex: Int, toIndex: Int)
}