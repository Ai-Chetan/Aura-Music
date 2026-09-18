package com.aura.music.data

import com.aura.music.data.db.SongDao
import com.aura.music.data.db.TagDao
import com.aura.music.data.db.TagEntity
import com.aura.music.ui.theme.TagColors
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

        // Starter set on the controlled semantic palette (TagColors).
        val starters = listOf(
            "chill", "dark", "emotional", "english",
            "focus", "hindi", "party", "retro",
            "sad", "workout", "romance"
        )

        starters.forEach { name ->
            tagDao.insertTag(
                TagEntity(
                    name = name,
                    colorHex = TagColors.semanticFor(name) ?: TagColors.Palette.first()
                )
            )
        }
    }
}
