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
 * Turns raw extractor/network errors into plain words anyone can act on.
 */
fun friendlyDownloadError(e: Exception): String {
    val raw = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
    return when {
        raw.contains("400") ->
            "That link looks broken. Check it and try again."
        raw.contains("403") ->
            "YouTube said no for now — usually temporary. Try again in a bit."
        raw.contains("429") ->
            "Too many downloads at once — slow down and try again in a bit."
        raw.contains("404") ->
            "Video not found — it may be deleted, private, or the link is wrong."
        raw.contains("navailable", ignoreCase = true) ->
            "YouTube says this video isn't available — it may be private, deleted, blocked in your country, or just busy. Try again in a bit."
        e is java.io.IOException ->
            "No internet connection — check it and try again."
        else -> "Something went wrong — try again."
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
        var partFile: File? = null
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

            // Unicode-safe: keep letters/numbers across scripts (Hindi,
            // Tamil, CJK…) so non-Latin titles keep a readable file stem.
            val safeTitle = streamInfo.title
                .take(60)
                .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .takeIf { it.isNotEmpty() } ?: "audio"
            val fileName = "${safeTitle}_${UUID.randomUUID().toString().take(8)}.$extension"
            destinationFile = File(songsDir, fileName)
            // Write to a .part sibling and rename: a process-death mid-write
            // then leaves an unreferenced .part behind instead of a corrupt
            // "finished-looking" file in the vault.
            partFile = File(songsDir, "$fileName.part")

            downloadBytes(streamInfo.bestAudioStreamUrl, partFile, isStopped) { percent, done, total ->
                onEvent(Event.Progress(percent, done, total))
            }

            val fileLen = partFile.length()
            require(fileLen > 1_000L) { "The download came back empty. Please try again." }
            if (!partFile.renameTo(destinationFile)) {
                // Rare (same volume should always rename) — fall back to copy.
                partFile.copyTo(destinationFile, overwrite = true)
                partFile.delete()
            }

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
            // Our just-written file becomes an orphan; delete it.
            val songId = if (insertedId > 0) {
                insertedId
            } else {
                try {
                    destinationFile?.takeIf { it.exists() }?.delete()
                } catch (_: Exception) {
                }
                songDao.getBySourceUrl(canonicalUrl)?.id ?: -1L
            }
            require(songId > 0) { "Something went wrong saving this song. Try again." }
            return songId
        } catch (e: CancellationException) {
            try {
                destinationFile?.takeIf { it.exists() }?.delete()
                partFile?.takeIf { it.exists() }?.delete()
            } catch (_: Exception) { }
            throw e
        } catch (e: Exception) {
            // Never leave a partial file behind.
            try {
                destinationFile?.takeIf { it.exists() }?.delete()
                partFile?.takeIf { it.exists() }?.delete()
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
