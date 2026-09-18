package com.aura.music.data.download

import android.content.Context
import com.aura.music.data.db.SongDao
import com.aura.music.data.db.SongEntity
import com.aura.music.domain.repository.ExtractionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Turns raw extractor/network exceptions into something a user can act
 * on. Technical detail is kept in parentheses for debugging.
 */
fun friendlyDownloadError(e: Exception): String {
    val raw = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
    return when {
        raw.contains("400") ->
            "Bad request (400) — the link looks malformed. Check it and retry. ($raw)"
        raw.contains("403") ->
            "YouTube refused this video (403) — usually temporary throttling. " +
                "Retry in a bit. ($raw)"
        raw.contains("429") ->
            "YouTube is rate-limiting (429) — too many downloads at once. " +
                "Retry in a bit. ($raw)"
        raw.contains("404") ->
            "Video not found (404) — it may be deleted, private, or the link is wrong. ($raw)"
        raw.contains("navailable", ignoreCase = true) ->
            "YouTube says this video is unavailable — region-lock, private/deleted link, " +
                "or temporary throttling. Retry in a bit. ($raw)"
        e is java.io.IOException ->
            "Network error — check your connection and retry. ($raw)"
        else -> raw
    }.take(300)
}

/**
 * Single-song resolve → bytes → thumbnail → DB insert, shared by the
 * one-off download worker and the playlist import worker so both paths
 * behave (and fail) identically.
 */
@Singleton
class SongFileDownloader @Inject constructor(
    private val extractionRepository: ExtractionRepository,
    private val songDao: SongDao,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    sealed interface Event {
        data class Resolved(val title: String) : Event
        data class Progress(val percent: Int, val bytesDone: Long, val bytesTotal: Long?) : Event
    }

    /**
     * Downloads [canonicalUrl] (already validated + canonicalized) and
     * returns the song id. Throws on failure; deletes partial files.
     * [isStopped] is polled for worker cancellation.
     */
    suspend fun downloadSingle(
        canonicalUrl: String,
        isStopped: () -> Boolean,
        onEvent: suspend (Event) -> Unit = {}
    ): Long {
        var destinationFile: File? = null
        try {
            val streamInfo = extractionRepository.resolveStreamInfo(canonicalUrl)
            onEvent(Event.Resolved(streamInfo.title))

            val songsDir = File(context.getExternalFilesDir(null), "songs")
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

            downloadBytes(streamInfo.bestAudioStreamUrl, destinationFile, isStopped) { percent, done, total ->
                onEvent(Event.Progress(percent, done, total))
            }

            val fileLen = destinationFile.length()
            require(fileLen > 1_000L) { "Downloaded file is empty — the stream expired. Please retry." }

            val thumbnailPath = fetchThumbnail(streamInfo.thumbnailUrl)

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

            val insertedId = songDao.insertSong(songEntity)
            // IGNORE returns -1 when a concurrent download won the race on
            // the same URL — resolve to the existing row, still a success.
            val songId = if (insertedId > 0) {
                insertedId
            } else {
                songDao.getBySourceUrl(canonicalUrl)?.id ?: -1L
            }
            require(songId > 0) { "Download finished without a song." }
            return songId
        } catch (e: CancellationException) {
            try {
                destinationFile?.takeIf { it.exists() }?.delete()
            } catch (_: Exception) { }
            throw e
        } catch (e: Exception) {
            // Never leave a partial file behind.
            try {
                destinationFile?.takeIf { it.exists() }?.delete()
            } catch (_: Exception) { }
            throw e
        }
    }

    private suspend fun downloadBytes(
        streamUrl: String,
        destination: File,
        isStopped: () -> Boolean,
        onProgress: suspend (percent: Int, done: Long, total: Long?) -> Unit
    ) {
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
                        if (isStopped()) throw CancellationException("Download cancelled")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes != null) {
                            val pct = (10 + (downloaded * 80 / totalBytes)).toInt().coerceIn(10, 90)
                            if (pct - lastReported >= 5) {
                                lastReported = pct
                                onProgress(pct, downloaded, totalBytes)
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    /** Best-effort artwork fetch (streamed, never fully buffered). Null on any failure. */
    private suspend fun fetchThumbnail(thumbnailUrl: String?): String? {
        if (thumbnailUrl.isNullOrBlank()) return null
        return try {
            val thumbDir = File(context.getExternalFilesDir(null), "thumbnails")
            thumbDir.mkdirs()
            val thumbFile = File(thumbDir, "${UUID.randomUUID()}.jpg")

            val thumbRequest = Request.Builder()
                .url(thumbnailUrl)
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
                        thumbFile.absolutePath
                    } else {
                        thumbFile.delete()
                        null
                    }
                } else null
            }
        } catch (_: Exception) {
            // Thumbnail download is non-critical
            null
        }
    }
}
