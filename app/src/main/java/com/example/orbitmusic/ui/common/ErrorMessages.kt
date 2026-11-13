package com.example.orbitmusic.ui.common

import androidx.annotation.StringRes
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.AppError

/** Maps a domain error to its friendly, localized message. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.NoInternet -> R.string.error_no_internet
    AppError.QuotaExceeded -> R.string.error_quota_exceeded
    AppError.TokenExpired -> R.string.error_token_expired
    AppError.EmptyPlaylist -> R.string.error_empty_playlist
    AppError.VideoUnavailable -> R.string.error_video_unavailable
    AppError.NotFound -> R.string.error_not_found
    AppError.PermissionRequired -> R.string.error_permission_required
    AppError.NotSignedIn -> R.string.error_not_signed_in
    AppError.SignInCancelled -> R.string.error_sign_in_cancelled
    AppError.NoGoogleAccount -> R.string.error_no_google_account
    AppError.MissingConfiguration -> R.string.error_missing_configuration
    AppError.PlayServicesUnavailable -> R.string.error_play_services
    AppError.ConsentDenied -> R.string.error_consent_denied
    AppError.AuthorizationRequired -> R.string.error_authorization_required
    AppError.NoYouTubeChannel -> R.string.error_no_youtube_channel
    AppError.AiNotConfigured -> R.string.error_ai_not_configured
    AppError.AiInvalidKey -> R.string.error_ai_invalid_key
    is AppError.AiRateLimited -> R.string.error_ai_busy
    AppError.AiUnavailable -> R.string.error_ai_unavailable
    AppError.AiTimeout -> R.string.error_ai_timeout
    AppError.AiBadResponse -> R.string.error_ai_bad_response
    is AppError.Unknown -> R.string.error_unknown
}
