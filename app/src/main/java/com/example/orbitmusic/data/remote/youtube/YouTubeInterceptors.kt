package com.example.orbitmusic.data.remote.youtube

import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.repository.AuthRepository
import com.example.orbitmusic.util.AppSignature
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val AUTHORIZATION = "Authorization"
private const val BEARER = "Bearer "

/**
 * Adds the OAuth access token. If no token can be obtained (signed out / access
 * revoked) the request goes out without one: public endpoints (search, trending) still
 * work through the API key, and "mine" endpoints fail with 401, handled below.
 *
 * OkHttp interceptors run on background threads, so blocking on the (mutex-guarded,
 * usually cached) token lookup is acceptable here.
 */
@Singleton
class AuthTokenInterceptor @Inject constructor(
    // Provider breaks the construction cycle AuthRepository -> ... -> OkHttp.
    private val authRepository: Provider<AuthRepository>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { authRepository.get().getAccessToken(forceRefresh = false) }
        val request = chain.request().newBuilder().apply {
            if (token is Resource.Success) header(AUTHORIZATION, BEARER + token.data)
        }.build()
        return chain.proceed(request)
    }
}

/**
 * On HTTP 401, asks for a fresh token once and retries. Gives up (returning the 401)
 * when renewal needs user consent; the auth state then routes the user to Login.
 */
@Singleton
class AuthTokenAuthenticator @Inject constructor(
    private val authRepository: Provider<AuthRepository>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponseCount() >= 1) return null
        // No YouTube channel: a new token won't help, and renewing could wrongly sign the user out.
        val body = runCatching { response.peekBody(PEEK_BYTES).string() }.getOrDefault("")
        if (ApiErrorMapper.NO_CHANNEL_REASON in body) return null
        val failedToken = response.request.header(AUTHORIZATION)?.removePrefix(BEARER)
        val refreshed = runBlocking { authRepository.get().getAccessToken(forceRefresh = true) }
        if (refreshed !is Resource.Success || refreshed.data == failedToken) return null
        return response.request.newBuilder()
            .header(AUTHORIZATION, BEARER + refreshed.data)
            .build()
    }

    private companion object {
        const val PEEK_BYTES = 16_384L
    }

    private fun Response.priorResponseCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

/**
 * Adds the API key plus the Android app identification headers required when the key
 * is restricted to this app (see the setup guide).
 */
@Singleton
class ApiKeyInterceptor @Inject constructor(
    private val appSignature: AppSignature,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isBlank()) return chain.proceed(chain.request())

        val original = chain.request()
        val url = original.url.newBuilder().addQueryParameter("key", apiKey).build()
        val request = original.newBuilder()
            .url(url)
            .header("X-Android-Package", appSignature.packageName)
            .apply { appSignature.certificateSha1?.let { header("X-Android-Cert", it) } }
            .build()
        return chain.proceed(request)
    }
}
