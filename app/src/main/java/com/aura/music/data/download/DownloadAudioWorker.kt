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
import com.aura.music.data.extraction.isTransientYtError
import com.aura.music.util.YoutubeUrls
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class DownloadAudioWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val songDao: SongDao,
    private val downloader: SongFileDownloader
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rawUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure(
            workDataOf(KEY_ERROR to "Missing URL")
        )
        val canonicalUrl = YoutubeUrls.canonicalUrl(rawUrl)
        if (!YoutubeUrls.isYouTubeUrl(canonicalUrl)) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to YoutubeUrls.rejectionReason(rawUrl))
            )
        }

        // Duplicate detection — same canonical watch URL already saved.
        try {
            val existing = songDao.getBySourceUrl(canonicalUrl)
            if (existing != null) {
                return@withContext Result.success(workDataOf(KEY_SONG_ID to existing.id))
            }
        } catch (e: Exception) {
            // Non-fatal; continue with download (worst case the IGNORE-insert
            // dedupes at the end).
            android.util.Log.w("DownloadAudioWorker", "Duplicate check failed", e)
        }

        try {
            reportStage(PROGRESS_RESOLVING, percent = 5, url = canonicalUrl)

            var resolvedTitle: String? = null
            val songId = downloader.downloadSingle(
                canonicalUrl = canonicalUrl,
                isStopped = ::isStopped
            ) { event ->
                when (event) {
                    is SongFileDownloader.Event.Resolved -> {
                        resolvedTitle = event.title
                        // Long mixes can outlive background limits — promote
                        // once the title is known so the OS keeps us alive.
                        // Foreground promotion can be rejected (background
                        // start limits); downloading on without it beats
                        // failing a perfectly good download.
                        runCatching { setForeground(foregroundInfo(event.title)) }
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS to PROGRESS_DOWNLOADING,
                                KEY_PERCENT to 10,
                                KEY_URL to canonicalUrl,
                                KEY_TITLE to event.title
                            )
                        )
                    }
                    is SongFileDownloader.Event.Progress -> {
                        val progressBuilder = androidx.work.Data.Builder()
                            .putString(KEY_PROGRESS, PROGRESS_DOWNLOADING)
                            .putInt(KEY_PERCENT, event.percent)
                            .putLong(KEY_BYTES_DONE, event.bytesDone)
                            .putString(KEY_URL, canonicalUrl)
                        resolvedTitle?.let { title ->
                            progressBuilder.putString(KEY_TITLE, title)
                        }
                        event.bytesTotal?.let { total ->
                            progressBuilder.putLong(KEY_BYTES_TOTAL, total)
                        }
                        setProgress(progressBuilder.build())
                    }
                }
            }

            reportStage(PROGRESS_SAVING, percent = 92, url = canonicalUrl)
            reportStage(PROGRESS_DONE, percent = 100, url = canonicalUrl)

            Result.success(workDataOf(KEY_SONG_ID to songId))
        } catch (e: CancellationException) {
            // Stopped (app closed, constraints lost, timeout): rethrow so
            // WorkManager reschedules instead of recording a failure — the
            // download resumes instead of being discarded.
            throw e
        } catch (e: Exception) {
            // Transient YouTube/network hiccups (throttling, timeouts,
            // expired stream URLs) are worth an automatic retry with
            // WorkManager backoff instead of an instant failure.
            if (runAttemptCount < 3 && isTransientYtError(e)) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to friendlyDownloadError(e)))
            }
        }
    }

    private suspend fun reportStage(stage: String, percent: Int, url: String? = null, title: String? = null) {
        val builder = androidx.work.Data.Builder()
            .putString(KEY_PROGRESS, stage)
            .putInt(KEY_PERCENT, percent)
        if (url != null) builder.putString(KEY_URL, url)
        if (title != null) builder.putString(KEY_TITLE, title)
        setProgress(builder.build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo("Downloading song…")

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
            .setContentText("Saving to your vault — safe to leave the app.")
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
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        const val KEY_PERCENT = "percent"
        const val KEY_BYTES_DONE = "bytes_done"
        const val KEY_BYTES_TOTAL = "bytes_total"
        const val KEY_SONG_ID = "song_id"
        const val KEY_ERROR = "error"

        const val PROGRESS_RESOLVING = "resolving"
        const val PROGRESS_DOWNLOADING = "downloading"
        const val PROGRESS_SAVING = "saving"
        const val PROGRESS_DONE = "done"

        /** WorkManager tag applied to every audio download (used for the Library queue). */
        const val DOWNLOAD_TAG = "aura-download"

        private const val CHANNEL_ID = "aura_downloads"
        private const val NOTIFICATION_ID = 42
    }
}
