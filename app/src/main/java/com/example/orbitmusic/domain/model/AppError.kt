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

    /** The user revoked access or the grant can no longer be renewed silently. */
    data object AuthorizationRequired : AppError

    data class Unknown(val message: String? = null) : AppError
}
