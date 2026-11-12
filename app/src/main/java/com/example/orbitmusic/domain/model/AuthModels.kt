package com.example.orbitmusic.domain.model

import android.app.PendingIntent

/** The signed-in Google account, as shown in the Home header and Settings. */
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String?,
    val givenName: String?,
    val photoUrl: String?,
) {
    /** Best short name for greetings. */
    val firstName: String
        get() = givenName?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@')
}

enum class YouTubeAccess {
    /** A youtube.readonly grant exists and can be renewed silently. */
    Granted,

    /** The user must (re)approve YouTube access (first run or access was revoked). */
    Required,
}

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(
        val user: UserProfile,
        val youTubeAccess: YouTubeAccess,
        val canEditPlaylists: Boolean,
    ) : AuthState
}

/** Outcome of asking Google for YouTube scopes. */
sealed interface AuthorizationOutcome {
    data object Granted : AuthorizationOutcome

    /**
     * Google needs the user to see a consent screen. The UI launches [pendingIntent] and
     * passes the returned Intent back to the repository.
     */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthorizationOutcome

    data class Failed(val error: AppError) : AuthorizationOutcome
}
