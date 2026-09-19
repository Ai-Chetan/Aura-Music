package com.aura.music.domain.repository

data class ExtractedStreamInfo(
    val title: String,
    val uploader: String?,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val bestAudioStreamUrl: String,
    val bitrateKbps: Int,
    val codec: String
) {
    /** Short human label, e.g. "Opus 160k" / "AAC 128k". */
    val qualityLabel: String get() = "${codec.uppercase()} ${bitrateKbps}k"

    /**
     * YouTube tops out at ~160kbps Opus — that is "best available" and
     * labelled HQ. Anything unknown falls back to HQ rather than
     * over-claiming lossless.
     */
    val qualityBadge: String get() = when {
        codec.equals("opus", ignoreCase = true) && bitrateKbps >= 150 -> "HQ • BEST"
        bitrateKbps >= 128 -> "HQ"
        else -> "SD"
    }
}

data class ExtractedPlaylist(
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    /** Total items YouTube reports (may exceed [videoUrls] when capped). */
    val totalCount: Long,
    /** Watch URLs, capped for sanity on huge playlists. */
    val videoUrls: List<String>
) {
    val isCapped: Boolean get() = totalCount > videoUrls.size
}

/** One YouTube Music search hit — streamable, downloadable, not yet saved. */
data class YouTubeTrack(
    val url: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val thumbnailUrl: String?
)

interface ExtractionRepository {
    suspend fun resolveStreamInfo(url: String): ExtractedStreamInfo

    suspend fun resolvePlaylist(url: String): ExtractedPlaylist

    /** YouTube Music song search, best matches first (capped). */
    suspend fun searchMusic(query: String, maxResults: Int = 25): List<YouTubeTrack>

    /**
     * YouTube Charts "Trending Music" kiosk (what is hot right now).
     * No API key, on-device via NewPipe. Falls back to a "top hits"
     * music search when Charts is unavailable for the current country.
     */
    suspend fun getTrendingMusic(maxResults: Int = 30): List<YouTubeTrack>

    /**
     * Up-next style suggestions for one video (StreamInfo related items).
     * Used for "Because you listened to X" recommendations. Never throws
     * for empty — returns emptyList() when YouTube has nothing related.
     */
    suspend fun getRelatedTracks(url: String, maxResults: Int = 20): List<YouTubeTrack>
}