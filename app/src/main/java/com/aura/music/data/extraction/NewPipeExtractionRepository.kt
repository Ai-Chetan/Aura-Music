package com.aura.music.data.extraction

import android.util.Log
import com.aura.music.domain.repository.ExtractedPlaylist
import com.aura.music.domain.repository.ExtractedStreamInfo
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.YouTubeTrack
import com.aura.music.util.YoutubeUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeExtractionRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val ytGate: YtGate
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
            // Gated + polite backoff: never burst the player API, never spin
            // on validation errors. Hard deadline: a slow-drip resolve must
            // not hold one of the 3 gate permits (and the UI) for minutes.
            try {
                ytGate.withPermit {
                    withTimeout(RESOLVE_TIMEOUT_MS) {
                        ytRetry { resolveOnce(canonical) }
                    }
                }
            } catch (e: Exception) {
                Log.e("NewPipeRepo", "Stream info extraction failed", e)
                // Surface the root cause (truncated) so failures are diagnosable
                // from the phone screen instead of a generic message.
                var root: Throwable? = e
                while (root?.cause != null && root.cause !== root) root = root.cause
                val detail = root?.message?.takeIf { it.isNotBlank() }?.take(140)
                throw RuntimeException(
                    buildString {
                        append("Couldn't read that video (it may be private, age-restricted or region-locked)")
                        if (detail != null) append(": $detail")
                    },
                    e
                )
            }
        }

    override suspend fun resolvePlaylist(url: String): ExtractedPlaylist =
        withContext(Dispatchers.IO) {
            val canonical = YoutubeUrls.canonicalPlaylistUrl(url)
            require(YoutubeUrls.isPlaylistUrl(canonical)) {
                YoutubeUrls.rejectionReason(url)
            }

            var lastError: Exception? = null
            try {
                return@withContext ytGate.withPermit {
                    withTimeout(RESOLVE_TIMEOUT_MS) {
                        ytRetry { resolvePlaylistOnce(canonical) }
                    }
                }
            } catch (e: Exception) {
                lastError = e
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

    override suspend fun searchMusic(query: String, maxResults: Int): List<YouTubeTrack> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            require(trimmed.isNotEmpty()) { "Type a song or artist to search." }
            try {
                ytGate.withPermit {
                    // "Nothing found" is a final answer, not a transient error.
                    ytRetry(
                        retryIf = { e ->
                            e !is org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException &&
                                isTransientYtError(e)
                        }
                    ) { searchOnce(trimmed, maxResults) }
                }
            } catch (e: org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException) {
                emptyList()
            } catch (e: Exception) {
                Log.w("NewPipeRepo", "music search failed: ${e.message}")
                var root: Throwable? = e
                while (root?.cause != null && root.cause !== root) root = root.cause
                val detail = root?.message?.takeIf { it.isNotBlank() }?.take(140)
                throw RuntimeException(
                    buildString {
                        append("YouTube search failed (check connection and retry)")
                        if (detail != null) append(": $detail")
                    },
                    e
                )
            }
        }

    override suspend fun getTrendingMusic(maxResults: Int): List<YouTubeTrack> =
        withContext(Dispatchers.IO) {
            try {
                ytGate.withPermit {
                    ytRetry(maxAttempts = 2) { trendingOnce(maxResults) }
                }
            } catch (e: Exception) {
                Log.w("NewPipeRepo", "trending music failed, falling back to search: ${e.message}")
                // Charts is country-gated — fall back to a top-hits search so
                // Discover never renders empty.
                try {
                    searchMusic("top hits today", maxResults.coerceIn(1, 50))
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

    override suspend fun getRelatedTracks(url: String, maxResults: Int): List<YouTubeTrack> =
        withContext(Dispatchers.IO) {
            val canonical = try {
                YoutubeUrls.canonicalUrl(url)
            } catch (_: Exception) {
                return@withContext emptyList<YouTubeTrack>()
            }
            try {
                ytGate.withPermit {
                    ytRetry(maxAttempts = 2) { relatedOnce(canonical, maxResults) }
                }
            } catch (e: Exception) {
                Log.w("NewPipeRepo", "related failed for $canonical: ${e.message}")
                emptyList()
            }
        }

    private fun trendingOnce(maxResults: Int): List<YouTubeTrack> {
        val service = ServiceList.YouTube
        val kioskList = service.kioskList
        val extractor = kioskList.getExtractorById(TRENDING_MUSIC_KIOSK_ID, null)
            ?: throw IllegalStateException("Trending music isn't available right now")
        extractor.fetchPage()
        val page = extractor.initialPage
        return page.items
            ?.asSequence()
            ?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            ?.filter { !it.url.isNullOrBlank() && !it.name.isNullOrBlank() }
            ?.take(maxResults.coerceIn(1, 50))
            ?.map { item -> item.toTrack() }
            ?.toList()
            .orEmpty()
            .also { require(it.isNotEmpty()) { "Trending list came back empty." } }
    }

    private fun relatedOnce(canonicalUrl: String, maxResults: Int): List<YouTubeTrack> {
        val service = ServiceList.YouTube
        val info = StreamInfo.getInfo(service, canonicalUrl)
        val related = try {
            info.relatedItems
        } catch (_: Exception) {
            null
        }.orEmpty()
        return related
            .asSequence()
            .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            .filter { !it.url.isNullOrBlank() && !it.name.isNullOrBlank() }
            .take(maxResults.coerceIn(1, 50))
            .map { it.toTrack() }
            .toList()
    }

    private fun org.schabi.newpipe.extractor.stream.StreamInfoItem.toTrack(): YouTubeTrack {
        val durationSec = try { duration } catch (_: Exception) { -1L }
        val thumb = try {
            thumbnails?.maxByOrNull { t -> t.height * t.width }?.url
                ?: thumbnails?.lastOrNull()?.url
        } catch (_: Exception) {
            null
        }
        val rawUrl = url!!
        return YouTubeTrack(
            url = try { YoutubeUrls.canonicalUrl(rawUrl) } catch (_: Exception) { rawUrl },
            title = name!!,
            artist = try { uploaderName?.takeIf { it.isNotBlank() } } catch (_: Exception) { null },
            durationMs = if (durationSec > 0) durationSec * 1000 else 0L,
            thumbnailUrl = thumb
        )
    }

    private fun searchOnce(query: String, maxResults: Int): List<YouTubeTrack> {
            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(
                query,
                listOf(
                    org.schabi.newpipe.extractor.services.youtube.linkHandler
                        .YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
                ),
                ""
            )
            val info = org.schabi.newpipe.extractor.search.SearchInfo
                .getInfo(service, handler)
            return info.relatedItems
                ?.asSequence()
                ?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                ?.filter { !it.url.isNullOrBlank() && !it.name.isNullOrBlank() }
                ?.take(maxResults.coerceIn(1, 50))
                ?.map { item ->
                    val durationSec = try { item.duration } catch (_: Exception) { -1L }
                    val thumb = try {
                        item.thumbnails?.maxByOrNull { t -> t.height * t.width }?.url
                            ?: item.thumbnails?.lastOrNull()?.url
                    } catch (_: Exception) {
                        null
                    }
                    val rawUrl = item.url!!
                    YouTubeTrack(
                        url = try { YoutubeUrls.canonicalUrl(rawUrl) } catch (_: Exception) { rawUrl },
                        title = item.name!!,
                        artist = try { item.uploaderName?.takeIf { it.isNotBlank() } } catch (_: Exception) { null },
                        durationMs = if (durationSec > 0) durationSec * 1000 else 0L,
                        thumbnailUrl = thumb
                    )
                }
                ?.toList()
                .orEmpty()
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
        /** YouTube Charts kiosk id for "Trending Music Videos". */
        const val TRENDING_MUSIC_KIOSK_ID = "trending_music"
        /** Wall-clock cap per extraction (all retry attempts included). */
        const val RESOLVE_TIMEOUT_MS = 60_000L
    }

    private fun resolveOnce(canonicalUrl: String): ExtractedStreamInfo {
        val service = ServiceList.YouTube
        val streamInfo = StreamInfo.getInfo(service, canonicalUrl)

        val audioStreams = streamInfo.audioStreams
        require(audioStreams.isNotEmpty()) { "No audio streams found for this URL" }

        // Pick the best available audio: Opus (~160k) first, then AAC (~128k),
        // then the highest bitrate of any remaining format.
        val bestAudio = selectBestAudio(audioStreams)
            ?: throw IllegalStateException("Couldn't get the audio for this song")

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
}
