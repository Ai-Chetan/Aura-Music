package com.aura.music.domain.repository

import java.io.File

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

data class DownloadResult(
    val file: File,
    val bytesWritten: Long
)

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

interface ExtractionRepository {
    suspend fun resolveStreamInfo(url: String): ExtractedStreamInfo

    suspend fun resolvePlaylist(url: String): ExtractedPlaylist

    suspend fun downloadAudio(
        streamInfo: ExtractedStreamInfo,
        destination: File
    ): DownloadResult
}