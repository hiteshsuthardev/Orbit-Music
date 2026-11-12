package com.example.orbitmusic.domain.repository

import android.content.Context
import android.content.Intent
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * Google sign-in (Credential Manager) plus YouTube authorization (AuthorizationClient).
 *
 * Note: the domain layer here is intentionally "Android-aware": Credential Manager
 * needs an Activity context to show its UI and the consent flow travels as an
 * Intent, so those platform types appear in this contract.
 */
interface AuthRepository {

    val authState: StateFlow<AuthState>

    /**
     * Shows the Google account picker. [activityContext] must be an Activity context; it
     * is only used for the duration of the call and never stored.
     */
    suspend fun signInWithGoogle(activityContext: Context): Resource<UserProfile>

    /**
     * Requests youtube.readonly (and the youtube write scope when [includeWriteAccess]).
     * Completes silently when the user already granted it.
     */
    suspend fun authorizeYouTube(includeWriteAccess: Boolean = false): AuthorizationOutcome

    /** Finishes a consent flow started from [AuthorizationOutcome.NeedsConsent]. */
    suspend fun completeAuthorization(data: Intent?): Resource<Unit>

    /**
     * Returns a usable OAuth access token, silently renewing it when expired.
     * Pass [forceRefresh] after the API rejected the current token (HTTP 401).
     */
    suspend fun getAccessToken(forceRefresh: Boolean = false): Resource<String>

    /**
     * Clears tokens, the Credential Manager state and every local cache.
     * When [revokeAccess] is true the YouTube grant is also revoked at Google.
     */
    suspend fun signOut(revokeAccess: Boolean = false)
}
