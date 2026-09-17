package com.aura.music.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aura.music.data.db.SongDao
import com.aura.music.data.db.SongEntity
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.util.YoutubeUrls
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltWorker
class DownloadAudioWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val extractionRepository: ExtractionRepository,
    private val songDao: SongDao,
    private val okHttpClient: OkHttpClient
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

        var destinationFile: File? = null
        try {
            reportStage(PROGRESS_RESOLVING, percent = 5)

            val streamInfo = extractionRepository.resolveStreamInfo(canonicalUrl)

            reportStage(PROGRESS_DOWNLOADING, percent = 10)

            val songsDir = File(applicationContext.getExternalFilesDir(null), "songs")
            songsDir.mkdirs()

            // Never re-encode: keep YouTube's native container for the codec.
            // Opus -> .webm, AAC -> .m4a (ExoPlayer plays both gaplessly).
            val extension = when (streamInfo.codec.lowercase()) {
                "opus" -> "webm"
                "aac" -> "m4a"
                "flac" -> "flac"
                else -> "m4a"
            }

            val safeTitle = streamInfo.title
                .take(60)
                .replace(Regex("[^a-zA-Z0-9 _-]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .takeIf { it.isNotEmpty() } ?: "audio"
            val fileName = "${safeTitle}_${UUID.randomUUID().toString().take(8)}.$extension"
            destinationFile = File(songsDir, fileName)

            downloadWithProgress(streamInfo.bestAudioStreamUrl, destinationFile)

            val fileLen = destinationFile.length()
            require(fileLen > 1_000L) { "Downloaded file is empty — the stream expired. Please retry." }

            reportStage(PROGRESS_SAVING, percent = 92)

            // Download thumbnail if available (streamed, not body.bytes()).
            var thumbnailPath: String? = null
            streamInfo.thumbnailUrl?.let { thumbUrl ->
                try {
                    val thumbDir = File(applicationContext.getExternalFilesDir(null), "thumbnails")
                    thumbDir.mkdirs()
                    val thumbFile = File(thumbDir, "${UUID.randomUUID()}.jpg")

                    val thumbRequest = Request.Builder()
                        .url(thumbUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                        )
                        .build()
                    okHttpClient.newCall(thumbRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(thumbFile).use { output ->
                                    input.copyTo(output, bufferSize = 8192)
                                }
                            }
                            if (thumbFile.length() > 0) {
                                thumbnailPath = thumbFile.absolutePath
                            } else {
                                thumbFile.delete()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Thumbnail download is non-critical
                }
            }

            val songEntity = SongEntity(
                title = streamInfo.title,
                artist = streamInfo.uploader?.takeIf { it.isNotBlank() },
                sourceUrl = canonicalUrl,
                sourcePlatform = "youtube",
                localFilePath = destinationFile.absolutePath,
                thumbnailPath = thumbnailPath,
                durationMs = streamInfo.durationMs,
                bitrateKbps = streamInfo.bitrateKbps,
                audioFormat = streamInfo.codec,
                isLossless = false,
                dateAdded = System.currentTimeMillis()
            )

            val songId = songDao.insertSong(songEntity)

            reportStage(PROGRESS_DONE, percent = 100)

            Result.success(workDataOf(KEY_SONG_ID to songId))
        } catch (e: Exception) {
            // Never leave a partial file behind.
            try {
                destinationFile?.takeIf { it.exists() }?.delete()
            } catch (_: Exception) { }
            if (runAttemptCount < 2 && e.message?.contains("expired", ignoreCase = true) == true) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
            }
        }
    }

    private suspend fun reportStage(stage: String, percent: Int) {
        setProgress(workDataOf(KEY_PROGRESS to stage, KEY_PERCENT to percent))
    }

    private suspend fun downloadWithProgress(streamUrl: String, destination: File) {
        destination.parentFile?.mkdirs()
        val request = Request.Builder()
            .url(streamUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            // YouTube aggressively throttles plain non-range GETs (a few MB can
            // take minutes). Ranged requests are served at full speed — this is
            // the same reason yt-dlp/NewPipe always download with Range.
            .header("Range", "bytes=0-")
            .header("Accept", "*/*")
            .header("Connection", "keep-alive")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed with HTTP ${response.code}")
            }
            val body = response.body ?: throw RuntimeException("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0 }

            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastReported = 10
                    while (true) {
                        if (isStopped) throw RuntimeException("Download cancelled")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes != null) {
                            val pct = (10 + (downloaded * 80 / totalBytes)).toInt().coerceIn(10, 90)
                            if (pct - lastReported >= 5) {
                                lastReported = pct
                                setProgress(
                                    workDataOf(
                                        KEY_PROGRESS to PROGRESS_DOWNLOADING,
                                        KEY_PERCENT to pct,
                                        KEY_BYTES_DONE to downloaded,
                                        KEY_BYTES_TOTAL to totalBytes
                                    )
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    companion object {
        const val KEY_URL = "url"
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
    }
}