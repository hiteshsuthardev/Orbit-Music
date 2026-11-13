package com.example.orbitmusic.data.remote.youtube

import com.example.orbitmusic.data.remote.auth.AuthException
import com.example.orbitmusic.data.remote.youtube.dto.ApiErrorResponseDto
import com.example.orbitmusic.data.repository.AppErrorException
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Translates network / HTTP failures into the user-facing [AppError] taxonomy. */
@Singleton
class ApiErrorMapper @Inject constructor(
    private val json: Json,
) {

    fun map(throwable: Throwable): AppError = when (throwable) {
        is AuthException -> throwable.error
        is AppErrorException -> throwable.error
        is HttpException -> mapHttp(throwable)
        is IOException -> AppError.NoInternet
        is SerializationException -> AppError.Unknown("Unexpected response from YouTube")
        else -> AppError.Unknown(throwable.message)
    }

    private fun mapHttp(exception: HttpException): AppError {
        val reasons = runCatching {
            val body = exception.response()?.errorBody()?.string().orEmpty()
            if (body.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString(ApiErrorResponseDto.serializer(), body)
                    .error?.errors.orEmpty().mapNotNull { it.reason }
            }
        }.getOrDefault(emptyList())
        return mapStatus(exception.code(), reasons)
    }

    /** Visible for tests. */
    fun mapStatus(code: Int, reasons: List<String>): AppError = when {
        reasons.any { it in QUOTA_REASONS } -> AppError.QuotaExceeded
        reasons.any { it == NO_CHANNEL_REASON } -> AppError.NoYouTubeChannel
        code == 401 -> AppError.AuthorizationRequired
        code == 403 && reasons.any { it in PERMISSION_REASONS } -> AppError.PermissionRequired
        code == 403 && reasons.any { it in UNAVAILABLE_REASONS } -> AppError.VideoUnavailable
        code == 404 -> AppError.NotFound
        code == 403 -> AppError.PermissionRequired
        code in 500..599 -> AppError.Unknown("YouTube is having trouble ($code)")
        else -> AppError.Unknown("HTTP $code")
    }

    companion object {
        /** 401 for accounts without a YouTube channel; signing in again doesn't help. */
        const val NO_CHANNEL_REASON = "youtubeSignupRequired"
        private val QUOTA_REASONS = setOf("quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded")
        private val PERMISSION_REASONS = setOf("insufficientPermissions", "forbidden", "playlistForbidden", "channelClosed")
        private val UNAVAILABLE_REASONS = setOf("videoNotFound", "videoRatingDisabled", "playlistItemsNotAccessible")
    }
}

/** Runs [block], mapping any failure to [Resource.Error]. Rethrows cancellation. */
suspend inline fun <T> ApiErrorMapper.safeCall(crossinline block: suspend () -> T): Resource<T> =
    try {
        Resource.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resource.Error(map(e))
    }
