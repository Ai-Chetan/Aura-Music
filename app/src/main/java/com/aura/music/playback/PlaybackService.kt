package com.aura.music.playback

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aura.music.R
import com.aura.music.data.db.QueueStateDao
import com.aura.music.data.db.QueueStateEntity
import com.aura.music.data.db.SongDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private companion object {
        const val TAG = "PlaybackService"
    }

    @Inject
    lateinit var songDao: SongDao

    @Inject
    lateinit var queueStateDao: QueueStateDao

    @Inject
    lateinit var audioVisualizer: AudioVisualizer

    @Inject
    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSaveJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Fast-start tuning for streaming tracks: begin playback after ~1.5s
        // is buffered, keep 10–30s ahead, and serve repeats/seeks from the
        // disk cache. Local files are unaffected (already on disk).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 10_000,
                /* maxBufferMs */ 30_000,
                /* bufferForPlaybackMs */ 1_500,
                /* bufferForPlaybackAfterRebufferMs */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    // Vault rows only — transient streaming ids (< 0) have no
                    // DB row (streaming taste is recorded per-bookmark instead).
                    val mediaId = mediaItem?.mediaId?.toLongOrNull()?.takeIf { it >= 0 } ?: return
                    serviceScope.launch {
                        songDao.incrementPlayCount(mediaId, System.currentTimeMillis())
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startPositionSaving(player)
                } else {
                    stopPositionSaving()
                    // Pausing is the most common "I'll be back" moment —
                    // snapshot now so resume lands exactly here.
                    saveQueueState()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // Feed the live waveform behind the player + notch strip.
                audioVisualizer.attach(audioSessionId)
            }
        })

        // Attach immediately in case the session was set before listeners ran.
        try {
            audioVisualizer.attach(player.audioSessionId)
        } catch (_: Exception) {
        }

        mediaSession = MediaSession.Builder(this, player).build()

        // Customize the system playback notification (also drives lock-screen panel).
        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: run {
            stopSelf()
            return
        }

        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Persist the resume position, but bounded: an unbounded block on the
        // main thread here risks an ANR if the DB is contended during teardown.
        // A short latch keeps the write in the vast majority of cases and
        // gives up gracefully when the system is tearing us down hard.
        try {
            val snapshot = buildQueueSnapshot()
            val done = java.util.concurrent.CountDownLatch(1)
            val write = serviceScope.launch(Dispatchers.IO) {
                try {
                    if (snapshot != null) queueStateDao.saveQueueState(snapshot)
                    else queueStateDao.clearQueueState()
                } catch (e: Exception) {
                    Log.w(TAG, "Queue-state save on destroy failed", e)
                } finally {
                    done.countDown()
                }
            }
            done.await(2, java.util.concurrent.TimeUnit.SECONDS)
            if (!write.isCompleted) write.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Queue-state teardown failed", e)
        }
        stopPositionSaving()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPositionSaving(player: Player) {
        stopPositionSaving()
        positionSaveJob = serviceScope.launch {
            while (isActive) {
                delay(5_000)
                saveQueueState()
            }
        }
    }

    private fun stopPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    private fun saveQueueState() {
        val snapshot = buildQueueSnapshot()
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Empty player (queue cleared / all removed) must clear the
                // snapshot too, or a deleted queue resurrects on next launch.
                if (snapshot != null) queueStateDao.saveQueueState(snapshot)
                else queueStateDao.clearQueueState()
            } catch (e: Exception) {
                // Silent loss here means the resume position silently breaks.
                Log.w(TAG, "Queue-state save failed", e)
            }
        }
    }

    /** Snapshot on the caller thread (player state), persisted off Main. */
    private fun buildQueueSnapshot(): QueueStateEntity? {
        val player = mediaSession?.player ?: return null
        val songIds = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.toLongOrNull()
        }
        if (songIds.isEmpty()) return null

        val repeatModeStr = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> "one"
            Player.REPEAT_MODE_ALL -> "all"
            else -> "off"
        }
        val currentId = if (player.currentMediaItemIndex in 0 until player.mediaItemCount) {
            player.getMediaItemAt(player.currentMediaItemIndex).mediaId.toLongOrNull() ?: -1L
        } else -1L

        return QueueStateEntity(
            id = 0,
            songIdsJson = songIds.joinToString(","),
            currentIndex = player.currentMediaItemIndex,
            currentSongId = currentId,
            positionMs = player.currentPosition,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = repeatModeStr
        )
    }
}