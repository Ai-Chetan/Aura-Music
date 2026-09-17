package com.aura.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.music.data.db.SongEntity
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioVisualizer: AudioVisualizer
) : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionUpdateJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    override val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    override val waveform: StateFlow<FloatArray> = audioVisualizer.buckets

    // Cache of songs currently in the queue, keyed by song ID
    private val songCache = mutableMapOf<Long, SongEntity>()
    // Queue play requests until the MediaController connects.
    private var pendingQueue: Pair<List<SongEntity>, Int>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState()
        }

        override fun onPlayerError(error: PlaybackException) {
            updateState()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            // Feed the live waveform behind the player + notch strip.
            audioVisualizer.attach(audioSessionId)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateState()
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val mediaController = controllerFuture?.let {
                if (it.isDone && !it.isCancelled) it.get() else null
            }
            controller = mediaController
            mediaController?.addListener(playerListener)
            pendingQueue?.let { (songs, index) ->
                pendingQueue = null
                playQueue(songs, index)
            } ?: updateState()
        }, MoreExecutors.directExecutor())
    }

    private fun updateState() {
        val player = controller ?: return

        val queueSize = player.mediaItemCount
        val queue = (0 until queueSize).mapNotNull { index ->
            val mediaId = player.getMediaItemAt(index).mediaId.toLongOrNull()
            mediaId?.let { songCache[it] }
        }

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = if (currentIndex in 0 until queueSize) {
            player.getMediaItemAt(currentIndex).mediaId.toLongOrNull()
        } else null

        val currentSong = currentMediaId?.let { songCache[it] }
            // Fallback: if the cache missed (e.g. process restart cleared it while
            // the service kept playing), show the session metadata instead of
            // "No song playing" with a moving seek bar.
            ?: currentMediaId?.let { id ->
                val meta = if (currentIndex in 0 until queueSize) {
                    player.getMediaItemAt(currentIndex).mediaMetadata
                } else null
                SongEntity(
                    id = id,
                    title = meta?.title?.toString()?.takeIf { it.isNotBlank() }
                        ?: "Unknown title",
                    artist = meta?.artist?.toString(),
                    sourceUrl = "",
                    sourcePlatform = "youtube",
                    localFilePath = "",
                    thumbnailPath = meta?.artworkUri?.path,
                    durationMs = player.duration.coerceAtLeast(0L),
                    bitrateKbps = null,
                    audioFormat = "",
                    isLossless = false,
                    dateAdded = 0L
                )
            }

        val repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

        val playerError = player.playerError
        val errorMessage = playerError?.let {
            (it.message?.takeIf { m -> m.isNotBlank() } ?: it.toString()).take(200)
        }

        _playbackState.value = PlaybackUiState(
            currentSong = currentSong,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0L),
            queue = queue,
            currentIndexInQueue = currentIndex,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = repeatMode,
            errorMessage = errorMessage
        )
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                delay(500)
                val player = controller ?: continue
                _playbackState.value = _playbackState.value.copy(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0L)
                )
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun SongEntity.toMediaItem(): MediaItem {
        val uri = try {
            val f = java.io.File(localFilePath)
            if (f.exists()) Uri.fromFile(f) else Uri.parse(localFilePath)
        } catch (_: Exception) {
            Uri.parse(localFilePath)
        }
        // Artwork URI powers the notification + lock-screen cover art.
        val artworkUri = try {
            thumbnailPath?.let { path ->
                when {
                    path.startsWith("http") -> Uri.parse(path)
                    java.io.File(path).exists() -> Uri.fromFile(java.io.File(path))
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()
    }

    override fun playQueue(songs: List<SongEntity>, startIndex: Int) {
        val player = controller
        if (player == null) {
            songs.forEach { songCache[it.id] = it }
            pendingQueue = songs to startIndex
            return
        }

        val mediaItems = songs.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        updateState()
    }

    override fun playNext(song: SongEntity) {
        val player = controller ?: return
        songCache[song.id] = song

        val insertIndex = player.currentMediaItemIndex + 1
        player.addMediaItem(insertIndex, song.toMediaItem())
        updateState()
    }

    override fun addToQueueEnd(song: SongEntity) {
        val player = controller ?: return
        songCache[song.id] = song

        player.addMediaItem(song.toMediaItem())
        updateState()
    }

    override fun playAtIndex(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekToDefaultPosition(index)
        player.prepare()
        player.playWhenReady = true
        updateState()
    }

    override fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        updateState()
    }

    override fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        updateState()
    }

    override fun skipToNext() {
        controller?.seekToNextMediaItem()
        updateState()
    }

    override fun skipToPrevious() {
        controller?.seekToPreviousMediaItem()
        updateState()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        updateState()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val player = controller ?: return
        player.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        updateState()
    }

    override fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val player = controller ?: return
        player.moveMediaItem(fromIndex, toIndex)
        updateState()
    }
}