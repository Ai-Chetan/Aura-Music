package com.aura.music.di

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Disk cache for streamed (non-downloaded) audio.
 *
 * How instant-play works:
 * - First tap: [com.aura.music.data.stream.StreamResolver] has ideally
 *   pre-resolved the audio URL, so ExoPlayer starts buffering at once with
 *   a small `bufferForPlaybackMs` (see PlaybackService); the rest of the
 *   file downloads progressively while it plays.
 * - Repeat taps / seeks / next-in-queue: bytes already fetched are served
 *   from this [SimpleCache] instead of the network, so starts feel instant.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackCacheModule {

    @Provides
    @Singleton
    fun provideStreamCache(
        @ApplicationContext context: Context
    ): SimpleCache {
        val dir = File(context.cacheDir, "stream_cache").apply { mkdirs() }
        val evictor = LeastRecentlyUsedCacheEvictor(CACHE_BYTES)
        return SimpleCache(dir, evictor, StandaloneDatabaseProvider(context))
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: SimpleCache,
        @ApplicationContext context: Context
    ): CacheDataSource.Factory {
        val httpUpstream = DefaultHttpDataSource.Factory()
            .setUserAgent(CHROME_MOBILE_UA)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        // DefaultDataSource routes file:// (downloaded vault songs) to
        // FileDataSource and http(s) to the HTTP factory; an HTTP-only
        // upstream makes every local file fail with "Source error".
        val upstream = DefaultDataSource.Factory(context, httpUpstream)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(
                CacheDataSource.FLAG_BLOCK_ON_CACHE or
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
            )
            .setEventListener(
                object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(
                        cacheSizeBytes: Long,
                        cachedBytesRead: Long
                    ) = Unit

                    override fun onCacheIgnored(reason: Int) = Unit
                }
            )
    }

    private const val CACHE_BYTES = 300L * 1024 * 1024

    private const val CHROME_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
}
