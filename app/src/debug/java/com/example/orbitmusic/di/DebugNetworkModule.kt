package com.example.orbitmusic.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug-only HTTP logging. Lives in src/debug so release builds never include it. */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {

    private val apiKeyParam = Regex("([?&]key=)[^&\\s]+")

    @Provides
    @IntoSet
    @BuildTypeInterceptors
    fun provideLoggingInterceptor(): Interceptor =
        HttpLoggingInterceptor { message ->
            // Never print the API key, even in debug logs.
            Log.d("OrbitHttp", message.replace(apiKeyParam, "$1<redacted>"))
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }
}
