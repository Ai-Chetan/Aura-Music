package com.aura.music.data.recommendations

import com.aura.music.data.network.NetworkGate
import com.aura.music.data.stream.StreamResolver
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.YouTubeTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import javax.inject.Inject
import javax.inject.Singleton

data class Recommendations(
    val trending: List<YouTubeTrack> = emptyList(),
    val forYou: List<YouTubeTrack> = emptyList(),
    val forYouSubtitle: String = "",
    val fetchedAtMs: Long? = null
)

/**
 * Single source of truth for recommendations, fetched once and shared.
 *
 * Pipeline, in order (sequential — For-You explicitly excludes whatever
 * trending already shows):
 * 1. Trending: YouTube Charts "Trending Music" kiosk, title-deduped.
 * 2. Seeds: what you're listening to *right now* — your most recently
 *    played songs first (recency wins over all-time stats), falling back to
 *    most-played only when there's no recent history.
 * 3. Per seed: YouTube's related-items graph (the "up next" data behind
 *    each video) via on-device extraction — no account, no server.
 * 4. Round-robin interleave across seeds (variety, not seed-1-takes-all),
 *    skipping vault-owned songs (by URL *and* normalized title) and
 *    anything trending already shows.
 * 5. Thin-result fallback: "<top artist> best songs" search.
 * Empty vault → empty For-You (screens explain why) instead of echoing
 * trending a second time.
 */
@Singleton
class RecommendationsRepository @Inject constructor(
    private val extraction: ExtractionRepository,
    private val songs: SongRepository,
    private val saved: SavedTrackRepository,
    private val gate: NetworkGate,
    private val streamResolver: StreamResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(Recommendations())
    val data: StateFlow<Recommendations> = _data.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var autoLoaded = false
    private var loadJob: Job? = null

    init {
        if (gate.isAllowedNow()) refresh()
        scope.launch {
            var wasAllowed = gate.snapshot().allowed
            gate.state.collect { snap ->
                if (snap.allowed && (!wasAllowed || !autoLoaded)) {
                    if (_data.value.trending.isEmpty()) refresh()
                }
                wasAllowed = snap.allowed
            }
        }
    }

    /**
     * Explicit refresh. Returns false when the gate held it back — callers
     * toast the reason. Concurrent refreshes collapse into one load.
     */
    fun refresh(): Boolean =
        gate.runIfAllowed(
            action = {
                if (loadJob?.isActive == true) return@runIfAllowed
                loadJob = scope.launch { load() }
            }
        )

    private suspend fun load() {
        _loading.value = true
        _error.value = null
        try {
            val trending = TrackDedup.dedupe(extraction.getTrendingMusic(TRENDING_MAX))
            val forYou = loadForYou(trendingKeys = trending.map { TrackDedup.keyOf(it) }.toSet())
            _data.value = Recommendations(
                trending = trending,
                forYou = forYou.tracks,
                forYouSubtitle = forYou.subtitle,
                fetchedAtMs = System.currentTimeMillis()
            )
            autoLoaded = true
            // Warm the top of the chart: taps start from buffering, not resolving.
            streamResolver.prefetch(trending.take(5).map { it.url })
        } catch (e: Exception) {
            _error.value = e.message ?: "Couldn't load."
        } finally {
            _loading.value = false
        }
    }

    private data class Picks(val tracks: List<YouTubeTrack>, val subtitle: String)

    /** Minimal seed shape — vault songs and streamed bookmarks alike. */
    private data class Seed(val url: String, val title: String)

    private suspend fun loadForYou(trendingKeys: Set<String>): Picks {
        val library = try {
            songs.observeAllSongs().first().map { it.song }
        } catch (_: Exception) {
            emptyList()
        }
        val streamed = try {
            saved.observeSavedTracks().first()
        } catch (_: Exception) {
            emptyList()
        }
        if (library.isEmpty() && streamed.isEmpty()) return Picks(emptyList(), "")
        val ownedUrls = library.map { it.sourceUrl }.toSet() + streamed.map { it.url }.toSet()
        val ownedKeys = library.map { TrackDedup.keyOf(it.title, it.artist) }.toSet() +
            streamed.map { TrackDedup.keyOf(it.title, it.artist) }.toSet()

        // Recency-weighted taste score: what you like NOW, not all-time.
        // Recency is the dominant signal (half-life ~2 days: a track played
        // today scores ~4x one played a week ago), scaled by play frequency
        // so a repeat-played current favourite beats a one-off. Blending the
        // two is what makes seeds track your live rotation instead of
        // echoing the whole vault.
        val now = System.currentTimeMillis()
        fun tasteScore(lastPlayedAt: Long, playCount: Int): Double {
            val ageDays = ((now - lastPlayedAt) / 86_400_000.0).coerceAtLeast(0.0)
            val recency = 0.5.pow(ageDays / HALF_LIFE_DAYS)
            return recency * (1.0 + ln(1.0 + playCount))
        }

        data class ScoredSeed(
            val seed: Seed,
            val score: Double,
            val artist: String?
        )

        val scored = (
            library.map {
                ScoredSeed(Seed(it.sourceUrl, it.title), tasteScore(it.lastPlayedAt ?: 0L, it.playCount), it.artist)
            } +
                streamed.map {
                    ScoredSeed(Seed(it.url, it.title), tasteScore(it.lastPlayedAt ?: 0L, it.playCount), it.artist)
                }
            )
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
        val seeds = scored.take(RECENT_SEEDS).map { it.seed }
            .ifEmpty { library.sortedByDescending { it.playCount }.take(2).map { Seed(it.sourceUrl, it.title) } }
            .ifEmpty { streamed.sortedByDescending { it.playCount }.take(2).map { Seed(it.url, it.title) } }
            .ifEmpty { library.shuffled().take(2).map { Seed(it.sourceUrl, it.title) } }

        // Related graphs fetch in parallel (gated to 3 concurrent upstream)
        // instead of one slow seed after another.
        val relatedPerSeed = coroutineScope {
            seeds.map { seed ->
                async {
                    seed to try {
                        extraction.getRelatedTracks(seed.url, PER_SEED_MAX)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }

        // Round-robin across seeds so one seed's lookalikes can't flood the
        // list; the per-seed cap keeps variety even when one related graph
        // is much richer than the others.
        val seen = (trendingKeys + ownedKeys).toMutableSet()
        val picked = mutableListOf<YouTubeTrack>()
        val contributors = mutableListOf<String>()
        val perSeedCount = mutableMapOf<Int, Int>()
        var index = 0
        while (picked.size < FOR_YOU_MAX) {
            var progressed = false
            for ((i, entry) in relatedPerSeed.withIndex()) {
                val (seed, related) = entry
                if (index >= related.size) continue
                if ((perSeedCount[i] ?: 0) >= PER_SEED_CAP) continue
                val track = related[index]
                val key = TrackDedup.keyOf(track)
                if (track.url !in ownedUrls && key !in seen && key != "|") {
                    seen += key
                    picked += track
                    perSeedCount[i] = (perSeedCount[i] ?: 0) + 1
                    progressed = true
                    if (contributors.none { it == seed.title }) contributors += seed.title
                }
            }
            if (!progressed) break
            index++
        }

        if (picked.size < FOR_YOU_MIN) {
            // Fallback artist comes from the same recency-weighted taste
            // pool (what you're into now), not all-time library stats.
            val topArtist = scored
                .groupBy { it.artist?.takeIf { a -> a.isNotBlank() } ?: "" }
                .filterKeys { it.isNotBlank() }
                .maxByOrNull { (_, group) -> group.sumOf { it.score } }?.key
                .orEmpty()
            if (topArtist.isNotBlank()) {
                try {
                    val searched = extraction.searchMusic("$topArtist best songs", 15)
                    for (track in searched) {
                        val key = TrackDedup.keyOf(track)
                        if (track.url !in ownedUrls && key !in seen && key != "|") {
                            seen += key
                            picked += track
                        }
                        if (picked.size >= FOR_YOU_MAX) break
                    }
                    if (contributors.isEmpty()) contributors += topArtist
                } catch (_: Exception) {
                }
            }
        }

        val subtitle = when (contributors.size) {
            0 -> if (picked.isNotEmpty()) "Picks from your vault" else ""
            1 -> "Because you played “${contributors[0]}”"
            else -> "Because you played “${contributors[0]}” and “${contributors[1]}”"
        }
        return Picks(picked, subtitle)
    }

    companion object {
        const val TRENDING_MAX = 30
        const val PER_SEED_MAX = 12
        const val FOR_YOU_MAX = 15
        const val FOR_YOU_MIN = 8
        /** Recency window: recommendations follow your current rotation. */
        const val RECENT_SEEDS = 6
        /** Taste half-life (days): older plays matter less, exponentially. */
        const val HALF_LIFE_DAYS = 2.0
        /** Max For-You picks contributed by a single seed's related graph. */
        const val PER_SEED_CAP = 5
    }
}
