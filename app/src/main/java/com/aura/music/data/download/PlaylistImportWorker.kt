package com.aura.music.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aura.music.R
import com.aura.music.data.db.SongDao
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.TagRepository
import com.aura.music.util.YoutubeUrls
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Imports a whole playlist in a background worker, so leaving the Download
 * screen (or the app being trimmed) can't abort it halfway — every video
 * resolves, downloads and saves independently, and already-saved songs
 * stay saved no matter when the user navigates away.
 *
 * Duplicates (same link already in the library) are skipped for download
 * but still get the playlist tag merged. Private/deleted videos fail
 * individually without aborting the batch.
 */
@HiltWorker
class PlaylistImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val extractionRepository: ExtractionRepository,
    private val songDao: SongDao,
    private val tagRepository: TagRepository,
    private val downloader: SongFileDownloader
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rawUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure(
            workDataOf(O_ERROR to "Missing playlist URL")
        )
        val playlistTag = inputData.getString(KEY_TAG)?.trim()?.takeIf { it.isNotEmpty() }

        val canonical = YoutubeUrls.canonicalPlaylistUrl(rawUrl)
        if (!YoutubeUrls.isPlaylistUrl(canonical)) {
            return@withContext Result.failure(
                workDataOf(O_ERROR to YoutubeUrls.rejectionReason(rawUrl))
            )
        }

        val playlist = try {
            reportProgress(title = "", done = 0, total = 0, current = "Reading playlist…")
            extractionRepository.resolvePlaylist(canonical)
        } catch (e: Exception) {
            return@withContext Result.failure(
                workDataOf(O_ERROR to friendlyDownloadError(e as? Exception ?: RuntimeException(e)))
            )
        }

        val tagId = playlistTag?.let { name ->
            try {
                tagRepository.getOrCreateTag(name, null).takeIf { it > 0 }
            } catch (_: Exception) {
                null
            }
        }

        // Promote to a foreground service: a 50-track import outlives the
        // background execution window, and without this the OS can stop it
        // mid-list. Combined with per-song idempotent skips, an import now
        // survives app closure and resumes instead of restarting.
        // Foreground promotion can be rejected (background start limits);
        // importing without it beats failing the whole playlist.
        runCatching { setForeground(foregroundInfo(playlist.title.ifBlank { "Importing playlist…" })) }

        val urls = playlist.videoUrls
        var imported = 0
        var skippedDuplicate = 0
        var failed = 0
        val errors = mutableListOf<String>()

        urls.forEachIndexed { index, videoUrl ->
            if (isStopped) throw CancellationException("Playlist import cancelled")
            reportProgress(
                title = playlist.title,
                done = index,
                total = urls.size,
                current = "Song ${index + 1} of ${urls.size}"
            )
            try {
                val existing = try {
                    songDao.getBySourceUrl(videoUrl)
                } catch (e: Exception) {
                    android.util.Log.w("PlaylistImportWorker", "Duplicate check failed", e)
                    null
                }
                if (existing != null) {
                    skippedDuplicate++
                    if (tagId != null) {
                        try { tagRepository.addTagToSong(existing.id, tagId) } catch (_: Exception) { }
                    }
                } else {
                    var currentTitle = "Song ${index + 1} of ${urls.size}"
                    val songId = downloader.downloadSingle(
                        canonicalUrl = videoUrl,
                        isStopped = ::isStopped
                    ) { event ->
                        if (event is SongFileDownloader.Event.Resolved) {
                            currentTitle = event.title
                            reportProgress(
                                title = playlist.title,
                                done = index,
                                total = urls.size,
                                current = currentTitle
                            )
                        }
                    }
                    if (tagId != null) {
                        try { tagRepository.addTagToSong(songId, tagId) } catch (_: Exception) { }
                    }
                    imported++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                if (errors.size < MAX_ERRORS) {
                    val reason = try {
                        friendlyDownloadError(e as? Exception ?: RuntimeException(e))
                    } catch (_: Exception) {
                        e.message ?: "download failed"
                    }
                    errors.add("Song ${index + 1}: $reason")
                }
            }
        }

        reportProgress(title = playlist.title, done = urls.size, total = urls.size, current = "")
        Result.success(
            workDataOf(
                O_TITLE to playlist.title,
                O_IMPORTED to imported,
                O_SKIPPED to skippedDuplicate,
                O_FAILED to failed,
                O_ERRORS to errors.toTypedArray()
            )
        )
    }

    private suspend fun reportProgress(title: String, done: Int, total: Int, current: String) {
        setProgress(
            workDataOf(
                P_TITLE to title,
                P_DONE to done,
                P_TOTAL to total,
                P_CURRENT to current
            )
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo("Importing playlist…")

    private fun foregroundInfo(title: String): ForegroundInfo {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = ctx.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification: Notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Saving songs — safe to leave the app.")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_URL = "playlist_url"
        const val KEY_TAG = "playlist_tag"

        const val P_TITLE = "p_title"
        const val P_DONE = "p_done"
        const val P_TOTAL = "p_total"
        const val P_CURRENT = "p_current"

        const val O_TITLE = "o_title"
        const val O_IMPORTED = "o_imported"
        const val O_SKIPPED = "o_skipped"
        const val O_FAILED = "o_failed"
        const val O_ERRORS = "o_errors"
        const val O_ERROR = "o_error"

        /** WorkManager tag for live playlist imports (Library progress card). */
        const val PLAYLIST_TAG = "playlist-import"

        private const val MAX_ERRORS = 8
        private const val CHANNEL_ID = "aura_downloads"
        private const val NOTIFICATION_ID = 41
    }
}
