package com.aura.music.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var songDao: SongDao

    @Inject
    lateinit var queueStateDao: QueueStateDao

    @Inject
    lateinit var audioVisualizer: AudioVisualizer

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

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    val mediaId = mediaItem?.mediaId?.toLongOrNull() ?: return
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
        saveQueueState()
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
        val player = mediaSession?.player ?: return
        val songIds = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.toLongOrNull()
        }

        if (songIds.isEmpty()) return

        val repeatModeStr = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> "one"
            Player.REPEAT_MODE_ALL -> "all"
            else -> "off"
        }

        serviceScope.launch {
            queueStateDao.saveQueueState(
                QueueStateEntity(
                    id = 0,
                    songIdsJson = songIds.joinToString(","),
                    currentIndex = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
                    shuffleEnabled = player.shuffleModeEnabled,
                    repeatMode = repeatModeStr
                )
            )
        }
    }
}