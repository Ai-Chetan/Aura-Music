package com.aura.music.data.stream

import com.aura.music.data.network.NetworkGate
import com.aura.music.domain.repository.ExtractedStreamInfo
import com.aura.music.domain.repository.ExtractionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolve-once, play-instantly helper for streaming (non-downloaded) tracks.
 *
 * - [resolveFresh] returns a cached [ExtractedStreamInfo] when hot, else
 *   resolves via NewPipe and caches it.
 * - [prefetch] warms the cache for a list of watch URLs in the background
 *   with its own small concurrency cap so prefetching never stampedes the
 *   [com.aura.music.data.extraction.YtGate] (which caps at 3).
 *
 * Call [prefetch] when a streaming list appears on screen (Discover, Saved
 * tab, search results); call [resolveFresh] at tap time. On good internet
 * the tap then skips resolution entirely and ExoPlayer starts buffering
 * the audio URL immediately, while its disk cache streams the rest.
 */
@Singleton
class StreamResolver @Inject constructor(
    private val extractionRepository: ExtractionRepository,
    private val cache: StreamUrlCache,
    private val gate: NetworkGate
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetchPermits = Semaphore(PREFETCH_CONCURRENCY)

    suspend fun resolveFresh(url: String): ExtractedStreamInfo =
        withContext(Dispatchers.IO) {
            cache.get(url)?.let { return@withContext it }
            val info = extractionRepository.resolveStreamInfo(url)
            cache.put(url, info)
            info
        }

    fun isCached(url: String): Boolean = cache.containsFresh(url)

    /** Fire-and-forget warm-up; skipped silently when the gate is closed. */
    fun prefetch(urls: List<String>) {
        // Never spend data the user didn't approve: prefetch is invisible,
        // so it only runs when dynamic content is already allowed.
        if (!gate.isAllowedNow()) return
        val fresh = urls.filter { it.isNotBlank() && !cache.containsFresh(it) }
            .distinct().take(MAX_PREFETCH)
        if (fresh.isEmpty()) return
        scope.launch {
            fresh.map { url ->
                async {
                    prefetchPermits.acquire()
                    try {
                        val info = extractionRepository.resolveStreamInfo(url)
                        cache.put(url, info)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Prefetch is best-effort only.
                    } finally {
                        prefetchPermits.release()
                    }
                }
            }.awaitAll()
        }
    }

    companion object {
        const val MAX_PREFETCH = 8
        const val PREFETCH_CONCURRENCY = 2
    }
}
