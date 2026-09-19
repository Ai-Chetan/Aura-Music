package com.aura.music.data.stream

import com.aura.music.domain.repository.ExtractedStreamInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny in-memory LRU for resolved YouTube stream URLs.
 *
 * Why this exists: tapping a streaming song otherwise pays a 1–3s
 * `resolveStreamInfo` cost (player API) *before* ExoPlayer even starts
 * buffering. When Discover / Saved lists are shown we pre-resolve the
 * first screenful in the background, so a tap finds a hot entry and
 * playback starts from ExoPlayer buffering immediately.
 *
 * YouTube audio URLs expire after a few hours, so entries older than
 * [MAX_AGE_MS] are treated as misses and re-resolved.
 */
@Singleton
class StreamUrlCache @Inject constructor() {

    private data class Entry(val info: ExtractedStreamInfo, val fetchedAt: Long)

    private val lock = Any()
    private val map = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(url: String): ExtractedStreamInfo? = synchronized(lock) {
        val entry = map[url] ?: return null
        if (System.currentTimeMillis() - entry.fetchedAt > MAX_AGE_MS) {
            map.remove(url)
            return null
        }
        entry.info
    }

    fun put(url: String, info: ExtractedStreamInfo) = synchronized(lock) {
        map[url] = Entry(info, System.currentTimeMillis())
    }

    fun containsFresh(url: String): Boolean = get(url) != null


    companion object {
        const val MAX_ENTRIES = 60
        /** 4h — comfortably inside YouTube's ~6h URL lifetime. */
        const val MAX_AGE_MS = 4L * 60 * 60 * 1000
    }
}
