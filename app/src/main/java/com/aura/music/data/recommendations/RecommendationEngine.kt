package com.aura.music.data.recommendations

import com.aura.music.data.db.SavedTrackDao
import com.aura.music.data.db.SongDao
import com.aura.music.data.db.SongEntity
import com.aura.music.data.network.NetworkGate
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.YouTubeTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * A radio pick the player can append directly: either an owned vault song
 * (plays offline, no resolve step) or a fresh stream to resolve on demand.
 */
sealed interface RadioPick {
    data class FromVault(val song: SongEntity) : RadioPick
    data class FromStream(val track: YouTubeTrack) : RadioPick
}

/**
 * The taste model behind every "what plays next" decision.
 *
 * Signals it learns from (all on-device):
 * - **Obsessions** — recency-weighted play counts across the vault, Saved
 *   bookmarks, and streamed tracks ([ListenStatsStore]). A track played
 *   today scores ~4x one played a week ago, scaled by frequency.
 * - **Skips** — every early skip demotes the track itself and, through
 *   [ListenStatsStore], its artist. Skip-heavy artists get pushed out of
 *   every candidate pool.
 * - **Replays** — replaying a track is the strongest positive signal; the
 *   artist affinity bonus spreads that across related picks.
 *
 * Pick pipeline for one radio slot:
 * 1. Score everything known (vault + saved + streamed) into obsession seeds.
 * 2. Expand the related-tracks graph of the song playing *right now* (bonus)
 *    and of the top seeds — in parallel, network-gated.
 * 3. Mix in owned replays: your own high-scoring songs compete with fresh
 *    discovery picks, so radio also resurfaces what you already love.
 * 4. Score candidates: graph proximity + artist affinity/skips + jitter for
 *    variety, excluding anything queued, recently radio-played, or owned
 *    (owned songs come back as vault replays instead).
 * 5. Fallbacks: cached For-You/trending, so radio never goes silent while
 *    the network does.
 */
@Singleton
class RecommendationEngine @Inject constructor(
    private val songDao: SongDao,
    private val savedTrackDao: SavedTrackDao,
    private val listenStats: ListenStatsStore,
    private val extraction: ExtractionRepository,
    private val recommendations: RecommendationsRepository,
    private val gate: NetworkGate
) {
    private data class Seed(
        val url: String,
        val title: String,
        val artist: String?,
        val score: Double,
        val lastPlayedAt: Long,
        /** True when the seed exists as a downloadable vault row. */
        val inVault: Boolean = false
    )

    private data class Candidate(
        val pick: RadioPick,
        val key: String,
        val url: String,
        val score: Double
    )

    /**
     * Up to [count] ranked picks. [excludeUrls] must contain everything
     * already queued or recently played — picks never repeat those.
     */
    suspend fun radioPicks(
        currentUrl: String?,
        excludeUrls: Set<String>,
        count: Int
    ): List<RadioPick> {
        if (count <= 0) return emptyList()
        val now = System.currentTimeMillis()

        val vault = try {
            songDao.getAllSongsWithTags().first().map { it.song }
        } catch (_: Exception) {
            emptyList()
        }
        val saved = try {
            savedTrackDao.observeAll().first()
        } catch (_: Exception) {
            emptyList()
        }
        val stats = listenStats.stats.value

        val ownedUrls = (vault.map { it.sourceUrl } + saved.map { it.url }).toSet()
        val ownedKeys = (
            vault.map { TrackDedup.keyOf(it.title, it.artist) } +
                saved.map { TrackDedup.keyOf(it.title, it.artist) }
            ).toSet()

        fun skipFactor(skips: Int): Double = 1.0 / (1.0 + skips * 0.75)

        fun taste(lastPlayedAt: Long?, plays: Int): Double {
            if (lastPlayedAt == null || lastPlayedAt <= 0) return 0.0
            val ageDays = ((now - lastPlayedAt) / 86_400_000.0).coerceAtLeast(0.0)
            return 0.5.pow(ageDays / HALF_LIFE_DAYS) * (1.0 + ln(1.0 + plays))
        }

        // ---- 1. Obsession seeds ------------------------------------------------
        val seeds = buildList {
            vault.forEach {
                add(
                    Seed(
                        it.sourceUrl, it.title, it.artist,
                        taste(it.lastPlayedAt, it.playCount) * skipFactor(it.skipCount),
                        it.lastPlayedAt ?: 0L, inVault = true
                    )
                )
            }
            saved.forEach {
                add(
                    Seed(
                        it.url, it.title, it.artist,
                        taste(it.lastPlayedAt, it.playCount) * skipFactor(it.skipCount),
                        it.lastPlayedAt ?: 0L
                    )
                )
            }
            stats.tracks.values
                .filter { it.url !in ownedUrls }
                .forEach {
                    add(
                        Seed(
                            it.url, it.title, it.artist,
                            taste(it.lastPlayedAt, it.plays) * skipFactor(it.skips),
                            it.lastPlayedAt
                        )
                    )
                }
        }.filter { it.score > 0 && it.url !in excludeUrls }
            .sortedByDescending { it.score }
            .take(SEED_MAX)

        // ---- 2. Related-graph expansion ---------------------------------------
        data class RelatedBatch(val weight: Double, val tracks: List<YouTubeTrack>)

        val batches = mutableListOf<RelatedBatch>()
        if (gate.isAllowedNow()) {
            try {
                val batchesFetched = coroutineScope {
                    val currentFetch = if (!currentUrl.isNullOrBlank()) {
                        async {
                            RelatedBatch(
                                CURRENT_RELATED_WEIGHT,
                                try {
                                    extraction.getRelatedTracks(currentUrl, CURRENT_RELATED_MAX)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            )
                        }
                    } else null
                    val seedFetches = seeds.take(SEED_RELATED_MAX).map { seed ->
                        async {
                            RelatedBatch(
                                seed.score.coerceAtMost(3.0),
                                try {
                                    extraction.getRelatedTracks(seed.url, PER_SEED_MAX)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            )
                        }
                    }
                    (listOfNotNull(currentFetch) + seedFetches).map { it.await() }
                }
                batchesFetched.forEach { batches += it }
            } catch (_: Exception) {
                // Network hiccups fall through to cached pools below.
            }
        }

        // ---- 3 & 4. Candidates: discovery + owned replays, scored -------------
        val candidates = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()

        fun addStream(track: YouTubeTrack, base: Double) {
            if (track.url.isBlank() || track.url in excludeUrls || track.url in ownedUrls) return
            val key = TrackDedup.keyOf(track)
            if (key.isBlank() || key == "|" || key in seen || key in ownedKeys) return
            var score = base
            score += listenStats.affinity(track.artist) * 0.4
            // Artist-level skip demotion: enough evidence and more skips than
            // plays means this sound isn't landing — nearly silence it.
            ListenStatsStore.normalizeArtist(track.artist)?.let { artistKey ->
                val stat = stats.artists[artistKey]
                if (stat != null && stat.skips >= 3 && stat.skips > stat.plays) score *= 0.25
            }
            score += Random.nextDouble(0.0, 0.9)
            if (score <= 0) return
            seen += key
            candidates += Candidate(RadioPick.FromStream(track), key, track.url, score)
        }

        batches.forEach { batch ->
            batch.tracks.forEach { addStream(it, batch.weight) }
        }

        // Cached pools keep radio alive when the network doesn't cooperate.
        val cached = recommendations.data.value
        (cached.forYou + cached.trending).forEach { addStream(it, CACHED_WEIGHT) }

        // Owned replays: high-scoring vault/saved songs compete with fresh
        // picks, weighted a touch below discovery so radio still surprises.
        seeds.asSequence()
            .filter { it.url !in excludeUrls }
            .filter { now - it.lastPlayedAt > REPLAY_COOLDOWN_MS }
            .take(REPLAY_MAX)
            .forEach { seed ->
                val score = seed.score * 0.85 + Random.nextDouble(0.0, 0.4)
                if (score <= 0) return@forEach
                val key = TrackDedup.keyOf(seed.title, seed.artist)
                if (key.isBlank() || key == "|" || key in seen) return@forEach
                seen += key
                if (seed.inVault) {
                    val song = vault.firstOrNull { it.sourceUrl == seed.url } ?: return@forEach
                    candidates += Candidate(RadioPick.FromVault(song), key, seed.url, score)
                } else {
                    val thumb = saved.firstOrNull { it.url == seed.url }?.thumbnailUrl
                    candidates += Candidate(
                        RadioPick.FromStream(YouTubeTrack(seed.url, seed.title, seed.artist, 0L, thumb)),
                        key,
                        seed.url,
                        score
                    )
                }
            }

        return candidates
            .sortedByDescending { it.score }
            .take(count)
            .map { it.pick }
    }

    companion object {
        /** Taste half-life (days): what you played today outweighs last week. */
        const val HALF_LIFE_DAYS = 2.0
        /** Obsession seeds ranked per pick cycle. */
        const val SEED_MAX = 6
        /** How many top seeds get their related graph fetched. */
        const val SEED_RELATED_MAX = 3
        const val CURRENT_RELATED_MAX = 14
        const val PER_SEED_MAX = 10
        /** Currently-playing track's graph is the strongest signal. */
        const val CURRENT_RELATED_WEIGHT = 2.5
        /** Seed graphs get a weight proportional to the seed's taste score. */
        const val CACHED_WEIGHT = 0.5
        /** Owned replays no sooner than this after their last play. */
        const val REPLAY_COOLDOWN_MS = 90 * 60_000L
        const val REPLAY_MAX = 4
    }
}
