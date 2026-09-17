package com.aura.music.util

/**
 * YouTube-only URL handling.
 *
 * Aura v1 supports YouTube exclusively — no other platform. This helper
 * centralises validation + canonicalisation so every layer (ViewModel,
 * Worker, extractor) agrees on what "supported" means.
 *
 * Supported forms: watch, youtu.be, shorts, music.youtube, embed, /live/.
 */
object YoutubeUrls {

    private val VIDEO_ID_REGEX = Regex(
        "(?:youtube\\.com/(?:watch\\?.*v=|shorts/|embed/|live/|v/)|youtu\\.be/|music\\.youtube\\.com/watch\\?.*v=)([A-Za-z0-9_-]{11})"
    )

    fun isYouTubeUrl(raw: String): Boolean {
        val url = raw.trim()
        if (url.isEmpty()) return false
        val lower = url.lowercase()
        val hostOk = lower.contains("youtube.com") ||
            lower.contains("youtu.be") ||
            lower.contains("music.youtube.com")
        if (!hostOk) return false
        if (isPlaylistUrl(url)) return true
        // Require either a parseable 11-char id or a watch/shorts path so that
        // channel pages are rejected with a clear error.
        return extractVideoId(url) != null ||
            lower.contains("/watch") ||
            lower.contains("/shorts/") ||
            lower.contains("/live/") ||
            lower.contains("/embed/")
    }

    /** Returns the 11-char video id, or null if it cannot be parsed. */
    fun extractVideoId(raw: String): String? {
        val url = raw.trim()
        VIDEO_ID_REGEX.find(url)?.let { return it.groupValues[1] }
        // Fallback: query param v=
        return try {
            val query = url.substringAfter("?", "")
            query.split("&").forEach { param ->
                val kv = param.split("=", limit = 2)
                if (kv.size == 2 && kv[0] == "v" && kv[1].matches(Regex("[A-Za-z0-9_-]{11}"))) {
                    return kv[1]
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Canonical watch URL used for duplicate detection + DB storage. */
    fun canonicalUrl(raw: String): String {
        val id = extractVideoId(raw)
        return if (id != null) "https://www.youtube.com/watch?v=$id" else raw.trim()
    }

    private val PLAYLIST_ID_REGEX = Regex("[?&]list=([A-Za-z0-9_-]+)")

    /**
     * True for playlist links (`/playlist?list=`). A `watch?v=…&list=…` link
     * is treated as a SINGLE video for routing — see [isVideoWithPlaylist]:
     * the UI surfaces its playlist as an explicit option instead.
     */
    fun isPlaylistUrl(raw: String): Boolean {
        val lower = raw.trim().lowercase()
        if (!lower.contains("list=")) return false
        if (lower.contains("/playlist")) return true
        // watch URLs carrying list= stay single-video; anything else with a
        // list param (mixes, radio) is treated as a playlist.
        if (lower.contains("/watch")) return false
        return lower.contains("youtube.com") || lower.contains("music.youtube.com")
    }

    fun extractPlaylistId(raw: String): String? =
        PLAYLIST_ID_REGEX.find(raw.trim())?.groupValues?.get(1)

    /**
     * True when a VIDEO link also carries a playlist (`watch?v=…&list=…`,
     * shared from inside a playlist). The caller should offer both paths:
     * this video alone, or the whole playlist via [canonicalPlaylistUrl].
     * Pure `/playlist` links return false here — they are already playlists.
     */
    fun isVideoWithPlaylist(raw: String): Boolean {
        val url = raw.trim()
        if (url.isEmpty() || isPlaylistUrl(url)) return false
        return extractVideoId(url) != null && extractPlaylistId(url) != null
    }

    /** Canonical playlist URL used for fetching. */
    fun canonicalPlaylistUrl(raw: String): String {
        val id = extractPlaylistId(raw)
        return if (id != null) "https://www.youtube.com/playlist?list=$id" else raw.trim()
    }

    fun rejectionReason(raw: String): String {
        val url = raw.trim()
        if (url.isEmpty()) return "Paste a YouTube link first."
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return "That doesn't look like a link — paste a full YouTube URL."
        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) {
            return "Only YouTube links are supported in this version."
        }
        return "That YouTube URL isn't a playable video or playlist link (try a watch, shorts, playlist or youtu.be link)."
    }
}
