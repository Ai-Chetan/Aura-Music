package com.aura.music.di

import com.aura.music.data.extraction.NewPipeExtractionRepository
import com.aura.music.domain.repository.ExtractionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractionModule {

    @Binds
    @Singleton
    abstract fun bindExtractionRepository(
        impl: NewPipeExtractionRepository
    ): ExtractionRepository
}