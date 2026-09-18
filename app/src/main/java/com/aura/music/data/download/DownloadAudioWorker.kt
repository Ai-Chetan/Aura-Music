package com.aura.music.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aura.music.data.db.SongDao
import com.aura.music.data.extraction.isTransientYtError
import com.aura.music.util.YoutubeUrls
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        } catch (_: Exception) {
            // Non-fatal; continue with download.
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
    }
}
