package com.aura.music.data.recommendations

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Per-URL listening behaviour, including streamed tracks with no DB row. */
data class TrackStat(
    val url: String,
    val title: String,
    val artist: String?,
    val plays: Int,
    val skips: Int,
    val lastPlayedAt: Long
)

/** Per-artist aggregate of the same two signals. */
data class ArtistStat(val plays: Int, val skips: Int)

data class ListenStats(
    val tracks: Map<String, TrackStat> = emptyMap(),
    val artists: Map<String, ArtistStat> = emptyMap()
)

private val Context.listenStatsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_listen_stats"
)

/**
 * Persists what playback actually looked like — plays, skips and when — for
 * every song that went through the player, including transient streaming
 * tracks that never land in Room. The recommendation engine reads two things
 * from here:
 *
 * 1. Stream-track taste seeds: a URL you replayed often and recently (and
 *    didn't skip) becomes a radio seed even though it isn't in the vault.
 * 2. Artist affinity: artists you keep skipping get demoted in every pick
 *    pool; artists you keep replaying get a bonus.
 *
 * Storage is one JSON blob in DataStore (write-through, bounded size) —
 * these are soft signals, not library data, so no Room table is warranted.
 */
@Singleton
class ListenStatsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val key = stringPreferencesKey("stats_json")

    private val _stats = MutableStateFlow(ListenStats())
    val stats: StateFlow<ListenStats> = _stats.asStateFlow()

    private var loaded = false
    private var dirty = false

    init {
        scope.launch {
            _stats.value = decode(
                try {
                    context.listenStatsDataStore.data.first()[key]
                } catch (_: Exception) {
                    null
                }
            )
            loaded = true
            if (dirty) persist()
        }
    }

    /** Counts a play start (any transition into the track). */
    suspend fun recordPlay(url: String, title: String, artist: String?) {
        bump(url, title, artist, skip = false)
    }

    /** Counts a skip (left the track before it meaningfully played). */
    suspend fun recordSkip(url: String, title: String, artist: String?) {
        bump(url, title, artist, skip = true)
    }

    private suspend fun bump(url: String, title: String, artist: String?, skip: Boolean) {
        val now = System.currentTimeMillis()
        val current = _stats.value

        val track = current.tracks[url] ?: TrackStat(url, title, artist, 0, 0, 0)
        val updatedTrack = track.copy(
            title = title.ifBlank { track.title },
            artist = (artist?.takeIf { it.isNotBlank() } ?: track.artist),
            plays = track.plays + if (skip) 0 else 1,
            skips = track.skips + if (skip) 1 else 0,
            lastPlayedAt = if (skip) track.lastPlayedAt else now
        )
        val tracks = (current.tracks + (url to updatedTrack)).evict(TRACK_CAP) { it.plays + it.skips }

        val artistKey = normalizeArtist(artist) ?: normalizeArtist(track.artist)
        val artists = if (artistKey != null) {
            val stat = current.artists[artistKey] ?: ArtistStat(0, 0)
            val updated = ArtistStat(
                plays = stat.plays + if (skip) 0 else 1,
                skips = stat.skips + if (skip) 1 else 0
            )
            (current.artists + (artistKey to updated)).evict(ARTIST_CAP) { it.plays + it.skips }
        } else current.artists

        _stats.value = ListenStats(tracks, artists)
        persist()
    }

    /** Artist affinity: positive = replayed, negative = skipped. Log-damped. */
    fun affinity(artist: String?): Double {
        val key = normalizeArtist(artist) ?: return 0.0
        val stat = _stats.value.artists[key] ?: return 0.0
        if (stat.plays + stat.skips == 0) return 0.0
        return (ln1p(stat.plays) - 1.6 * ln1p(stat.skips)).coerceIn(-1.5, 1.5)
    }

    private suspend fun persist() {
        if (!loaded) {
            dirty = true
            return
        }
        val snapshot = _stats.value
        try {
            context.listenStatsDataStore.edit { prefs -> prefs[key] = encode(snapshot) }
        } catch (_: Exception) {
            // Stats are best-effort; losing one write is harmless.
        }
    }

    private fun encode(stats: ListenStats): String {
        val tracks = JSONArray()
        stats.tracks.values
            .sortedByDescending { it.lastPlayedAt }
            .forEach { t ->
                tracks.put(
                    JSONObject()
                        .put("u", t.url)
                        .put("t", t.title)
                        .put("a", t.artist ?: JSONObject.NULL)
                        .put("p", t.plays)
                        .put("s", t.skips)
                        .put("l", t.lastPlayedAt)
                )
            }
        val artists = JSONArray()
        stats.artists.forEach { (name, a) ->
            artists.put(JSONObject().put("n", name).put("p", a.plays).put("s", a.skips))
        }
        return JSONObject().put("tracks", tracks).put("artists", artists).toString()
    }

    private fun decode(raw: String?): ListenStats {
        if (raw.isNullOrBlank()) return ListenStats()
        return try {
            val root = JSONObject(raw)
            val tracksJson = root.optJSONArray("tracks") ?: JSONArray()
            val tracks = mutableMapOf<String, TrackStat>()
            for (i in 0 until tracksJson.length()) {
                val o = tracksJson.optJSONObject(i) ?: continue
                val url = o.optString("u")
                if (url.isBlank()) continue
                tracks[url] = TrackStat(
                    url = url,
                    title = o.optString("t"),
                    artist = o.optString("a").takeIf { it.isNotBlank() },
                    plays = o.optInt("p"),
                    skips = o.optInt("s"),
                    lastPlayedAt = o.optLong("l")
                )
            }
            val artistsJson = root.optJSONArray("artists") ?: JSONArray()
            val artists = mutableMapOf<String, ArtistStat>()
            for (i in 0 until artistsJson.length()) {
                val o = artistsJson.optJSONObject(i) ?: continue
                val name = o.optString("n")
                if (name.isBlank()) continue
                artists[name] = ArtistStat(o.optInt("p"), o.optInt("s"))
            }
            ListenStats(tracks, artists)
        } catch (_: Exception) {
            ListenStats()
        }
    }

    companion object {
        private const val TRACK_CAP = 250
        private const val ARTIST_CAP = 150

        fun normalizeArtist(artist: String?): String? =
            artist?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        private fun <V> Map<String, V>.evict(cap: Int, weightOf: (V) -> Int): Map<String, V> {
            if (size <= cap) return this
            return entries
                .sortedByDescending { weightOf(it.value) }
                .take(cap)
                .associate { it.toPair() }
        }

        private fun ln1p(value: Int): Double = kotlin.math.ln(1.0 + value)
    }
}
