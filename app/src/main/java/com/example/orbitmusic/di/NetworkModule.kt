package com.example.orbitmusic.di

import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.data.remote.lyrics.LrcLibApi
import com.example.orbitmusic.data.remote.youtube.ApiKeyInterceptor
import com.example.orbitmusic.data.remote.youtube.AuthTokenAuthenticator
import com.example.orbitmusic.data.remote.youtube.AuthTokenInterceptor
import com.example.orbitmusic.data.remote.youtube.YouTubeApi
import com.example.orbitmusic.util.ConnectivityNetworkMonitor
import com.example.orbitmusic.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Extra interceptors contributed per build type. The debug source set adds an HTTP
 * logger; release contributes nothing, so no logging code ships in release builds.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BuildTypeInterceptors

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authTokenInterceptor: AuthTokenInterceptor,
        apiKeyInterceptor: ApiKeyInterceptor,
        authenticator: AuthTokenAuthenticator,
        @BuildTypeInterceptors extraInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(apiKeyInterceptor)
        .addInterceptor(authTokenInterceptor)
        .authenticator(authenticator)
        .apply { extraInterceptors.forEach(::addInterceptor) }
        .build()

    @Provides
    @Singleton
    fun provideYouTubeApi(client: OkHttpClient, json: Json): YouTubeApi = Retrofit.Builder()
        .baseUrl(YouTubeApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YouTubeApi::class.java)

    /**
     * Lyrics come from LRCLIB over a separate client: it never sees the YouTube API key or
     * the Google access token. Identifies itself with a generic app User-Agent, as LRCLIB asks.
     */
    @Provides
    @Singleton
    fun provideLrcLibApi(json: Json): LrcLibApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "OrbitMusic/${BuildConfig.VERSION_NAME} (Android)")
                        .build(),
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(LrcLibApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LrcLibApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {

    @Multibinds
    @BuildTypeInterceptors
    abstract fun buildTypeInterceptors(): Set<Interceptor>

    @Binds
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
