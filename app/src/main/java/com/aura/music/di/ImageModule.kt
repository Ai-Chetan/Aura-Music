package com.aura.music.di

import android.content.Context
import coil.ImageLoader
import com.aura.music.data.network.NetworkGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Singleton

/**
 * Thrown (as IOException so Coil treats it as a plain load failure) when a
 * remote artwork request is attempted while the dynamic-data gate is closed.
 */
class DataSaverBlockedException : IOException("Blocked by Data Saver (dynamic data off)")

/**
 * Coil-dedicated network stack. Local files (downloaded vault artwork) never
 * touch this interceptor's network path — only http(s) artwork is gated, so
 * "data off" truly means zero bytes off-device while the vault still shows
 * its covers.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideDataSaverInterceptor(gate: NetworkGate): Interceptor =
        Interceptor { chain ->
            val url = chain.request().url
            if ((url.scheme == "http" || url.scheme == "https") && !gate.isAllowedNow()) {
                throw DataSaverBlockedException()
            }
            chain.proceed(chain.request())
        }

    @Provides
    @Singleton
    fun provideCoilImageLoader(
        @ApplicationContext context: Context,
        dataSaverInterceptor: Interceptor
    ): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(dataSaverInterceptor)
            .followRedirects(true)
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
