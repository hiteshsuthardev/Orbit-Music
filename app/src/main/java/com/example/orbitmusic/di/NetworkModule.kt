package com.example.orbitmusic.di

import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.data.remote.ai.AiConfig
import com.example.orbitmusic.data.remote.ai.ChatCompletionsApi
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

/** Client for update files (version.json, remote config, APKs). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Update downloads: no YouTube credentials, sensible timeouts, and redirects may never
     * leave HTTPS (GitHub release links redirect to its download servers over HTTPS).
     */
    @Provides
    @Singleton
    @UpdateHttpClient
    fun provideUpdateHttpClient(
        @BuildTypeInterceptors extraInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .apply { extraInterceptors.forEach(::addInterceptor) }
        .build()

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

    /** Orbit AI endpoint, model and (debug only) key, from BuildConfig. */
    @Provides
    @Singleton
    fun provideAiConfig(): AiConfig = AiConfig(
        endpoint = BuildConfig.AI_ENDPOINT,
        apiKey = BuildConfig.GROQ_API_KEY,
        model = BuildConfig.GROQ_MODEL,
        sendsApiKey = BuildConfig.AI_SENDS_API_KEY,
        proxyToken = BuildConfig.AI_PROXY_TOKEN,
    )

    /**
     * Orbit AI client, separate from the YouTube one: 30s timeouts, and the Groq key (debug
     * builds only) added as the Authorization header. Debug logging comes from
     * [BuildTypeInterceptors], which redacts the Authorization header and never logs bodies.
     */
    @Provides
    @Singleton
    fun provideChatCompletionsApi(
        config: AiConfig,
        json: Json,
        @BuildTypeInterceptors extraInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): ChatCompletionsApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                if (config.sendsApiKey && config.apiKey.isNotBlank()) {
                    request.header("Authorization", "Bearer ${config.apiKey}")
                }
                if (config.proxyToken.isNotBlank()) request.header("X-Orbit-Token", config.proxyToken)
                chain.proceed(request.build())
            }
            .apply { extraInterceptors.forEach(::addInterceptor) }
            .build()
        return Retrofit.Builder()
            // Every call passes its full endpoint (@Url); this base is only required by Retrofit.
            .baseUrl(AI_PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChatCompletionsApi::class.java)
    }

    private const val AI_TIMEOUT_SECONDS = 30L
    private const val AI_PLACEHOLDER_BASE_URL = "https://api.groq.com/"

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
