package com.aura.music.data

import com.aura.music.data.db.SongDao
import com.aura.music.data.db.TagDao
import com.aura.music.data.db.TagEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds a small set of starter tags on first launch when the library is empty.
 * Tags only — songs are never faked, so a fresh install shows the empty state.
 */
@Singleton
class DefaultTagSeeder @Inject constructor(
    private val songDao: SongDao,
    private val tagDao: TagDao
) {
    suspend fun seedIfEmpty() {
        if (songDao.getSongCount() > 0) return

        // Starter set: 2 languages, 4 moods, 2 activities — all in the
        // blue→cyan family so selected chips stay readable on dark UI.
        val tagColors = listOf(
            "english" to "#22D3EE",
            "hindi" to "#FBBF24",
            "sad" to "#2563EB",
            "chill" to "#38BDF8",
            "party" to "#E879F9",
            "romance" to "#F87171",
            "workout" to "#FB923C",
            "focus" to "#60A5FA"
        )

        tagColors.forEach { (name, color) ->
            tagDao.insertTag(TagEntity(name = name, colorHex = color))
        }
    }
}
