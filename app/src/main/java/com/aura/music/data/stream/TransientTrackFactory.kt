package com.aura.music.data.stream

import com.aura.music.data.db.SavedTrackEntity
import com.aura.music.data.db.SongEntity
import com.aura.music.domain.repository.YouTubeTrack
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds transient (streaming, never saved) queue entries from bookmarks.
 * Single shared implementation so Discover, Home, Search, and Saved all
 * produce identical playable items.
 */
@Singleton
class TransientTrackFactory @Inject constructor(
    private val resolver: StreamResolver
) {
    /**
     * Unique negative id per built item. Hash-based ids could collide across
     * URLs and merge two different songs in the queue cache — a monotonic
     * counter can never collide.
     */
    fun freshId(): Long = idCounter.decrementAndGet()

    suspend fun fromTrack(track: YouTubeTrack): SongEntity {
        val info = resolver.resolveFresh(track.url)
        return SongEntity(
            id = freshId(),
            title = info.title,
            artist = info.uploader?.takeIf { it.isNotBlank() } ?: track.artist,
            sourceUrl = track.url,
            sourcePlatform = "youtube",
            localFilePath = info.bestAudioStreamUrl,
                    thumbnailPath = info.thumbnailUrl ?: track.thumbnailUrl,
                    durationMs = info.durationMs,
                    bitrateKbps = info.bitrateKbps,
                    audioFormat = info.codec,
                    isLossless = false,
                    dateAdded = System.currentTimeMillis()
                )
    }

    companion object {
        private val idCounter = AtomicLong(-1L)
    }

    suspend fun fromSaved(saved: SavedTrackEntity): SongEntity =
        fromTrack(
            YouTubeTrack(
                url = saved.url,
                title = saved.title,
                artist = saved.artist,
                durationMs = saved.durationMs,
                thumbnailUrl = saved.thumbnailUrl
            )
        )
}

/** Saved bookmarks as streamable origin lists for up-next sessions. */
fun List<SavedTrackEntity>.asTracks(): List<YouTubeTrack> = map {
    YouTubeTrack(
        url = it.url,
        title = it.title,
        artist = it.artist,
        durationMs = it.durationMs,
        thumbnailUrl = it.thumbnailUrl
    )
}
