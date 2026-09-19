package com.aura.music.data.recommendations

import com.aura.music.domain.repository.YouTubeTrack

/**
 * Content-level dedup for recommendation lists.
 *
 * YouTube hands back the *same song* through different uploads (official
 * video, lyric video, Topic auto-upload, …) with different URLs, so URL
 * dedup alone leaves visible repeats. The normalized key strips upload-type
 * noise ("(Official Video)", "[Lyrics]", …) so one song survives once.
 */
object TrackDedup {

    fun keyOf(title: String, artist: String?): String {
        var t = title.lowercase()
        // Drop bracketed qualifiers: "(Official Video)", "[Lyrics]", …
        t = t.replace(Regex("\\(.*?\\)|\\[.*?\\]|\\{.*?\\}"), " ")
        // Drop upload-type phrases. Bare "audio" is NOT stripped — it mangles
        // real names ("Audio Slave"); "official audio" as a phrase is safe.
        t = t.replace(
            Regex(
                "\\b(official\\s+(music\\s+)?video|official\\s+audio|official\\s+lyric(s)?(\\s+video)?" +
                    "|lyrics?(\\s+video)?|m\\s*/\\s*v|\\bmv\\b|visualiz(s|z)er|topic)\\b"
            ),
            " "
        )
        t = t.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        var a = (artist ?: "").lowercase()
            .replace("- topic", " ")
            .replace("vevo", " ")
        a = a.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        return "$t|$a"
    }

    fun keyOf(track: YouTubeTrack): String = keyOf(track.title, track.artist)

    /**
     * Keeps first occurrence of each song; additionally drops anything whose
     * key is in [excludeKeys] (e.g. songs already in another section or
     * already owned in the vault).
     */
    fun dedupe(
        tracks: List<YouTubeTrack>,
        excludeKeys: Set<String> = emptySet()
    ): List<YouTubeTrack> {
        val seen = excludeKeys.toMutableSet()
        return tracks.filter { track ->
            val key = keyOf(track)
            if (key.isBlank() || key == "|" || key in seen) {
                false
            } else {
                seen += key
                true
            }
        }
    }
}
