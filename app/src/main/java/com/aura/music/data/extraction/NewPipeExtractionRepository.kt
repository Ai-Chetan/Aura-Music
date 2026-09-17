package com.aura.music.data.extraction

import android.util.Log
import com.aura.music.domain.repository.DownloadResult
import com.aura.music.domain.repository.ExtractedPlaylist
import com.aura.music.domain.repository.ExtractedStreamInfo
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.util.YoutubeUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeExtractionRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ExtractionRepository {

    init {
        try {
            // Initialize NewPipe with OkHttp-based downloader
            NewPipe.init(OkHttpDownloader(okHttpClient))
        } catch (e: Exception) {
            Log.w("NewPipeRepo", "NewPipe initialization failed: ${e.message}")
        }
    }

    override suspend fun resolveStreamInfo(url: String): ExtractedStreamInfo =
        withContext(Dispatchers.IO) {
            val canonical = YoutubeUrls.canonicalUrl(url)
            require(YoutubeUrls.isYouTubeUrl(canonical)) {
                YoutubeUrls.rejectionReason(url)
            }
            // Stream URLs issued by YouTube expire / get throttled; retry the
            // player-response fetch a few times before surfacing an error.
            var lastError: Exception? = null
            repeat(3) { attempt ->
                try {
                    return@withContext resolveOnce(canonical)
                } catch (e: Exception) {
                    lastError = e
                    Log.w("NewPipeRepo", "resolve attempt ${attempt + 1} failed: ${e.message}")
                    if (attempt < 2) delay(1_000L * (attempt + 1))
                }
            }
            Log.e("NewPipeRepo", "Stream info extraction failed", lastError)
            // Surface the root cause (truncated) so failures are diagnosable
            // from the phone screen instead of a generic message.
            var root: Throwable? = lastError
            while (root?.cause != null && root.cause !== root) root = root.cause
            val detail = root?.message?.takeIf { it.isNotBlank() }?.take(140)
            throw RuntimeException(
                buildString {
                    append("Couldn't read that video (it may be private, age-restricted or region-locked)")
                    if (detail != null) append(": $detail")
                },
                lastError
            )
        }

    override suspend fun resolvePlaylist(url: String): ExtractedPlaylist =
        withContext(Dispatchers.IO) {
            val canonical = YoutubeUrls.canonicalPlaylistUrl(url)
            require(YoutubeUrls.isPlaylistUrl(canonical)) {
                YoutubeUrls.rejectionReason(url)
            }

            var lastError: Exception? = null
            repeat(3) { attempt ->
                try {
                    return@withContext resolvePlaylistOnce(canonical)
                } catch (e: Exception) {
                    lastError = e
                    Log.w("NewPipeRepo", "playlist attempt ${attempt + 1} failed: ${e.message}")
                    if (attempt < 2) delay(1_000L * (attempt + 1))
                }
            }
            var root: Throwable? = lastError
            while (root?.cause != null && root.cause !== root) root = root.cause
            val detail = root?.message?.takeIf { it.isNotBlank() }?.take(140)
            throw RuntimeException(
                buildString {
                    append("Couldn't read that playlist (it may be private or deleted)")
                    if (detail != null) append(": $detail")
                },
                lastError
            )
        }

    private fun resolvePlaylistOnce(canonicalUrl: String): ExtractedPlaylist {
        val service = ServiceList.YouTube
        val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(service, canonicalUrl)

        val urls = mutableListOf<String>()
        info.relatedItems?.forEach { item ->
            if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                item.url?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
            }
        }
        // Paginate until we hit the cap — huge playlists stay sane.
        var page = try { info.nextPage } catch (_: Exception) { null }
        while (urls.size < MAX_PLAYLIST_ITEMS && page != null) {
            try {
                val more = org.schabi.newpipe.extractor.playlist.PlaylistInfo
                    .getMoreItems(service, canonicalUrl, page)
                more.items?.forEach { item ->
                    item.url?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
                }
                page = if (more.hasNextPage()) more.nextPage else null
            } catch (e: Exception) {
                Log.w("NewPipeRepo", "playlist page failed: ${e.message}")
                break
            }
        }

        require(urls.isNotEmpty()) { "This playlist has no playable videos." }

        val thumbnail = try {
            info.thumbnails?.maxByOrNull { it.height * it.width }?.url
                ?: info.thumbnails?.lastOrNull()?.url
        } catch (_: Exception) {
            null
        }
        val total = try { info.streamCount } catch (_: Exception) { -1L }

        return ExtractedPlaylist(
            title = try {
                info.name?.takeIf { it.isNotBlank() } ?: "Untitled playlist"
            } catch (_: Exception) {
                "Untitled playlist"
            },
            author = try { info.uploaderName?.takeIf { it.isNotBlank() } } catch (_: Exception) { null },
            thumbnailUrl = thumbnail,
            totalCount = if (total > 0) total else urls.size.toLong(),
            videoUrls = urls.take(MAX_PLAYLIST_ITEMS).map { YoutubeUrls.canonicalUrl(it) }
        )
    }

    companion object {
        /** Hard cap so giant playlists can't run forever. */
        const val MAX_PLAYLIST_ITEMS = 50
    }

    private fun resolveOnce(canonicalUrl: String): ExtractedStreamInfo {
        val service = ServiceList.YouTube
        val streamInfo = StreamInfo.getInfo(service, canonicalUrl)

        val audioStreams = streamInfo.audioStreams
        require(audioStreams.isNotEmpty()) { "No audio streams found for this URL" }

        // Pick the best available audio: Opus (~160k) first, then AAC (~128k),
        // then the highest bitrate of any remaining format.
        val bestAudio = selectBestAudio(audioStreams)
            ?: throw IllegalStateException("Could not select best audio stream")

        val codec = when {
            bestAudio.codec?.contains("opus", ignoreCase = true) == true -> "opus"
            bestAudio.codec?.contains("mp4a", ignoreCase = true) == true ||
                bestAudio.codec?.contains("aac", ignoreCase = true) == true -> "aac"
            bestAudio.format?.name?.contains("opus", ignoreCase = true) == true -> "opus"
            bestAudio.format?.name?.contains("m4a", ignoreCase = true) == true -> "aac"
            else -> bestAudio.codec?.lowercase()?.take(12) ?: "unknown"
        }

        // Highest-resolution thumbnail available.
        val thumbnail = try {
            streamInfo.thumbnails?.maxByOrNull { it.height * it.width }?.url
                ?: streamInfo.thumbnails?.lastOrNull()?.url
        } catch (_: Exception) {
            streamInfo.thumbnails?.lastOrNull()?.url
        }

        val durationSec = try { streamInfo.duration } catch (_: Exception) { -1L }

        return ExtractedStreamInfo(
            title = streamInfo.name?.takeIf { it.isNotBlank() } ?: "Unknown Title",
            uploader = streamInfo.uploaderName?.takeIf { it.isNotBlank() },
            durationMs = if (durationSec > 0) durationSec * 1000 else 0L,
            thumbnailUrl = thumbnail,
            bestAudioStreamUrl = bestAudio.content,
            bitrateKbps = bestAudio.averageBitrate.takeIf { it > 0 } ?: 128,
            codec = codec
        )
    }

    private fun selectBestAudio(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        val opus = streams.filter {
            it.codec?.contains("opus", ignoreCase = true) == true ||
                it.format?.name?.contains("opus", ignoreCase = true) == true
        }
        if (opus.isNotEmpty()) return opus.maxByOrNull { it.averageBitrate }
        val aac = streams.filter {
            it.codec?.contains("mp4a", ignoreCase = true) == true ||
                it.codec?.contains("aac", ignoreCase = true) == true ||
                it.format?.name?.contains("m4a", ignoreCase = true) == true
        }
        if (aac.isNotEmpty()) return aac.maxByOrNull { it.averageBitrate }
        return streams.maxByOrNull { it.averageBitrate }
    }

    override suspend fun downloadAudio(
        streamInfo: ExtractedStreamInfo,
        destination: File
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            destination.parentFile?.mkdirs()

            val request = Request.Builder()
                .url(streamInfo.bestAudioStreamUrl)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("Download failed with HTTP ${response.code}")
                }

                val body = response.body
                    ?: throw RuntimeException("Empty response body")

                var bytesWritten = 0L
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    val source = body.byteStream()
                    var bytesRead: Int
                    while (source.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesWritten += bytesRead
                    }
                    output.flush()
                }

                DownloadResult(file = destination, bytesWritten = bytesWritten)
            }
        } catch (e: Exception) {
            Log.e("NewPipeRepo", "Audio download failed", e)
            throw RuntimeException("Failed to download audio: ${e.message}", e)
        }
    }
}
