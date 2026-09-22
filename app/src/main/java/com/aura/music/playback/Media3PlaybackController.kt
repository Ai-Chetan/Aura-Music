package com.aura.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.music.data.db.SongDao
import com.aura.music.data.db.QueueStateDao
import com.aura.music.data.db.SavedTrackDao
import com.aura.music.data.db.SongEntity
import com.aura.music.data.recommendations.ListenStatsStore
import com.aura.music.data.stream.StreamResolver
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioVisualizer: AudioVisualizer,
    private val streamResolver: StreamResolver,
    private val songDao: SongDao,
    private val queueStateDao: QueueStateDao,
    private val savedTrackDao: SavedTrackDao,
    private val listenStats: ListenStatsStore
) : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionUpdateJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    override val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    override val waveform: StateFlow<FloatArray> = audioVisualizer.buckets

    // Cache of songs currently in the queue, keyed by song ID.
    // LinkedHashMap so trimSongCache can evict insertion-oldest.
    private val songCache = LinkedHashMap<Long, SongEntity>()
    // Queue play requests until the MediaController connects.
    private var pendingQueue: Pair<List<SongEntity>, Int>? = null

    companion object {
        /** Song-metadata cache cap (queue mapping only — audio bytes live in SimpleCache). */
        private const val SONG_CACHE_MAX = 400
    }

    @OptIn(UnstableApi::class)
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recordTransitionStats(reason, mediaItem)
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
            } ?: run {
                updateState()
                // Cold start with an empty player: bring back the last
                // session (queue + position, paused) so the song the user
                // was listening to is sitting in the mini-player on return.
                restoreLastSession()
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Taste tracking on every track change — runs BEFORE [updateState] so
     * the previous state still describes the outgoing song.
     *
     * - **Skips**: a SEEK into a different item while the outgoing song
     *   hasn't meaningfully played (under 70% of its length and 15s+ short;
     *   under 2 minutes when the duration is unknown) counts as a skip —
     *   the DB counter for vault rows, the saved-table counter for Saved
     *   bookmarks, and artist/URL stats for everything (streams included).
     *   These are the recommendation engine's negative signal.
     * - **Plays**: URL/artist stats for the incoming item; vault play counts
     *   are recorded by [PlaybackService].
     */
    private fun recordTransitionStats(reason: Int, mediaItem: MediaItem?) {
        val prev = _playbackState.value
        val prevSong = prev.currentSong
        val incomingId = mediaItem?.mediaId?.toLongOrNull()

        if (prevSong != null && prevSong.id != incomingId &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        ) {
            val playedMs = prev.positionMs
            val skipped = if (prevSong.durationMs > 0) {
                val limit = (prevSong.durationMs * 0.7).toLong()
                playedMs in 0 until limit && playedMs < prevSong.durationMs - 15_000
            } else {
                playedMs in 0 until 120_000
            }
            if (skipped) {
                scope.launch(Dispatchers.IO) {
                    try {
                        if (prevSong.id >= 0) songDao.incrementSkipCount(prevSong.id)
                        else savedTrackDao.recordSkip(prevSong.sourceUrl)
                        listenStats.recordSkip(prevSong.sourceUrl, prevSong.title, prevSong.artist)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val playTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        val incoming = incomingId?.let { songCache[it] }
        if (playTransition && incoming != null && incoming.id != prevSong?.id) {
            scope.launch(Dispatchers.IO) {
                try {
                    listenStats.recordPlay(incoming.sourceUrl, incoming.title, incoming.artist)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun updateState() {
        val player = controller ?: return

        val queueSize = player.mediaItemCount
        // Never drop items here: the sheet's row indices must match the
        // player 1:1, otherwise remove/play/reorder hits the wrong song.
        // Cache misses (e.g. process restart) fall back to session metadata.
        val queue = (0 until queueSize).mapNotNull { index ->
            val mediaId = player.getMediaItemAt(index).mediaId.toLongOrNull()
            mediaId?.let { songCache[it] }
                ?: songFromMetadata(player, index, mediaId)
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
                if (currentIndex in 0 until queueSize) {
                    songFromMetadata(player, currentIndex, id)
                } else null
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

    /**
     * Best-effort SongEntity for a player item with no cache entry (e.g.
     * after a process restart while the service kept playing). Keeps the
     * queue list 1:1 with the player so row indices stay valid.
     */
    private fun songFromMetadata(player: Player, index: Int, id: Long?): SongEntity? {
        if (id == null) return null
        val meta = player.getMediaItemAt(index).mediaMetadata
        return SongEntity(
            id = id,
            title = meta?.title?.toString()?.takeIf { it.isNotBlank() }
                ?: "Unknown title",
            artist = meta?.artist?.toString(),
            sourceUrl = "",
            sourcePlatform = "youtube",
            localFilePath = "",
            thumbnailPath = meta?.artworkUri?.path,
            durationMs = if (index == player.currentMediaItemIndex) {
                player.duration.coerceAtLeast(0L)
            } else 0L,
            bitrateKbps = null,
            audioFormat = "",
            isLossless = false,
            dateAdded = 0L
        )
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
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val player = controller
        if (player == null) {
            songs.forEach { songCache[it.id] = it }
            pendingQueue = songs to startIndex
            return
        }

        // Keep the cache in sync: updateState() maps player items through it,
        // and any miss would shift the sheet's indices off the player's.
        songs.forEach { songCache[it.id] = it }
        trimSongCache()
        val mediaItems = songs.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, safeIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        updateState()
        // Streaming queue: warm the next watch URLs so auto-advance doesn't
        // stall on resolution. Downloaded files need no prefetch.
        prefetchUpcoming(songs, startIndex)
    }

    override fun playNext(song: SongEntity) {
        val player = controller ?: return
        songCache[song.id] = song
        trimSongCache()

        // Empty queue (or no current item): appending == playing next.
        if (player.mediaItemCount == 0 || player.currentMediaItemIndex < 0) {
            player.addMediaItem(song.toMediaItem())
        } else {
            val insertIndex = (player.currentMediaItemIndex + 1)
                .coerceIn(0, player.mediaItemCount)
            player.addMediaItem(insertIndex, song.toMediaItem())
        }
        updateState()
    }

    override fun addToQueueEnd(song: SongEntity) {
        val player = controller ?: return
        songCache[song.id] = song
        trimSongCache()

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
        if (fromIndex !in 0 until player.mediaItemCount) return
        if (toIndex !in 0..player.mediaItemCount) return
        player.moveMediaItem(fromIndex, toIndex)
        updateState()
    }

    /**
     * Cold-start resume: rebuilds the last queue from the DB snapshot
     * (downloaded songs only — streaming URLs expire), anchored on the
     * current vault song and clamped inside its duration, paused. Only runs
     * when the player is otherwise empty.
     */
    private fun restoreLastSession() {
        scope.launch(Dispatchers.IO) {
            try {
                val saved = queueStateDao.getQueueState() ?: return@launch
                val ids = saved.songIdsJson.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                    .filter { it > 0 }
                if (ids.isEmpty()) return@launch
                val byId = songDao.getSongsByIds(ids).associateBy { it.id }
                val ordered = ids.mapNotNull { byId[it] }
                if (ordered.isEmpty()) return@launch
                // Anchor on the remembered vault song (mixed streaming tails
                // shift raw indices); fall back to the stored index.
                val index = ordered.indexOfFirst { it.id == saved.currentSongId }
                    .takeIf { it >= 0 }
                    ?: saved.currentIndex.coerceIn(0, ordered.size - 1)
                val anchor = ordered[index]
                val duration = anchor.durationMs
                val position = when {
                    saved.currentSongId <= 0 || saved.currentSongId != anchor.id -> 0L
                    duration <= 0 -> 0L
                    saved.positionMs >= duration - 5_000 -> 0L
                    else -> saved.positionMs.coerceIn(0L, (duration - 3_000).coerceAtLeast(0L))
                }
                withContext(Dispatchers.Main) {
                    val player = controller ?: return@withContext
                    if (player.mediaItemCount != 0) return@withContext
                    ordered.forEach { songCache[it.id] = it }
                    trimSongCache()
                    player.setMediaItems(
                        ordered.map { it.toMediaItem() },
                        index,
                        position
                    )
                    player.shuffleModeEnabled = saved.shuffleEnabled
                    player.repeatMode = when (saved.repeatMode) {
                        "one" -> Player.REPEAT_MODE_ONE
                        "all" -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.prepare()
                    player.playWhenReady = false
                    updateState()
                }
            } catch (_: Exception) {
                // Resume is best-effort; a fresh empty player is a fine fallback.
            }
        }
    }

    /** Bounded song cache: drops oldest entries past the cap. */
    private fun trimSongCache() {
        if (songCache.size <= SONG_CACHE_MAX) return
        val iterator = songCache.entries.iterator()
        var toDrop = songCache.size - SONG_CACHE_MAX
        while (toDrop-- > 0 && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    /** Prefetch watch URLs for the next streaming items in a just-started queue. */
    private fun prefetchUpcoming(songs: List<SongEntity>, startIndex: Int) {
        val upcoming = songs
            .drop(startIndex + 1)
            .take(5)
            .mapNotNull { song ->
                // Transient streaming rows carry the watch URL in sourceUrl
                // and an http(s) audio URL in localFilePath. Downloaded rows
                // point at a local file and need no warm-up.
                val watch = song.sourceUrl.takeIf { it.isNotBlank() }
                val isStreaming = song.localFilePath.startsWith("http") ||
                    song.id < 0
                if (isStreaming) watch else null
            }
            .filter { !streamResolver.isCached(it) }
        if (upcoming.isNotEmpty()) streamResolver.prefetch(upcoming)
    }
}