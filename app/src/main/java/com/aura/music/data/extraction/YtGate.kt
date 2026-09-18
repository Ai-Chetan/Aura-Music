package com.aura.music.data.extraction

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Politeness gate for all YouTube traffic (search, resolve, stream fetch).
 * At most [MAX_CONCURRENT] resolutions run at once — bursting dozens of
 * parallel player-API calls is what trips HTTP 400/403/429 throttling.
 */
@Singleton
class YtGate @Inject constructor() {
    private val permits = Semaphore(MAX_CONCURRENT)

    suspend fun <T> withPermit(block: suspend () -> T): T {
        permits.acquire()
        try {
            return block()
        } finally {
            permits.release()
        }
    }

    companion object {
        const val MAX_CONCURRENT = 3
    }
}

private val TRANSIENT_HTTP_CODES = listOf("403", "429", "500", "502", "503", "504")

/**
 * True for failures worth retrying after a backoff: network I/O, throttling
 * markers, expired stream URLs, "unavailable" on otherwise-valid links
 * (usually throttling, not a dead video). False for our own validation
 * errors (bad input must fail fast, never retry).
 */
fun isTransientYtError(e: Throwable): Boolean {
    if (e is IllegalArgumentException) return false
    if (e is java.io.IOException) return true
    var cause: Throwable? = e
    while (cause != null) {
        val text = cause.message.orEmpty()
        if (text.contains("expired", ignoreCase = true)) return true
        if (text.contains("unavailable", ignoreCase = true)) return true
        if (text.contains("timeout", ignoreCase = true)) return true
        if (TRANSIENT_HTTP_CODES.any { text.contains(it) }) return true
        cause = cause.cause
        if (cause === e) break
    }
    return false
}

/**
 * Retry helper with exponential backoff + jitter. Never swallows
 * cancellation, never retries validation errors, caps total wait so the
 * UI isn't stuck forever on a truly dead link.
 */
suspend fun <T> ytRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 2000,
    factor: Double = 2.5,
    maxDelayMs: Long = 20_000,
    retryIf: (Throwable) -> Boolean = ::isTransientYtError,
    block: suspend () -> T
): T {
    var lastError: Throwable? = null
    var delayMs = initialDelayMs
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            lastError = e
            if (attempt == maxAttempts - 1 || !retryIf(e)) throw e
            val jittered = (delayMs * (0.75 + Math.random() * 0.5)).toLong()
            delay(jittered)
            delayMs = (delayMs * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }
    throw lastError!!
}
