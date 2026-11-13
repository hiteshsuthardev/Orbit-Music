package com.example.orbitmusic.domain.model

/**
 * Every failure the app can surface to the user. The UI maps each case to a friendly,
 * localized message (see ui/common/ErrorMessages.kt).
 */
sealed interface AppError {
    data object NoInternet : AppError
    data object QuotaExceeded : AppError
    data object TokenExpired : AppError
    data object EmptyPlaylist : AppError
    data object VideoUnavailable : AppError

    /** The playlist / video no longer exists or is not visible to this account. */
    data object NotFound : AppError

    /** The action needs the YouTube write scope, which the user has not granted. */
    data object PermissionRequired : AppError

    // Authentication / authorization
    data object NotSignedIn : AppError
    data object SignInCancelled : AppError
    data object NoGoogleAccount : AppError
    data object MissingConfiguration : AppError
    data object PlayServicesUnavailable : AppError
    data object ConsentDenied : AppError

    /** The Google account has no YouTube channel, which YouTube needs to create playlists. */
    data object NoYouTubeChannel : AppError

    /** The user revoked access or the grant can no longer be renewed silently. */
    data object AuthorizationRequired : AppError

    // OrbitNova AI
    /** No AI endpoint, model or (debug) API key configured. */
    data object AiNotConfigured : AppError

    /** The AI provider rejected the API key (HTTP 401). */
    data object AiInvalidKey : AppError

    /** HTTP 429. [retryAfterSeconds] comes from the Retry-After header. */
    data class AiRateLimited(val retryAfterSeconds: Int) : AppError

    /** The AI provider is down or overloaded (HTTP 5xx). */
    data object AiUnavailable : AppError

    /** No answer within the 30 second timeout. */
    data object AiTimeout : AppError

    /** The reply wasn't a usable playlist, even after asking again for valid JSON. */
    data object AiBadResponse : AppError

    data class Unknown(val message: String? = null) : AppError
}
