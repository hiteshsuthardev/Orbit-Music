package com.example.orbitmusic.data.remote.auth

import android.accounts.Account
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

object YouTubeScopes {
    const val READ_ONLY = "https://www.googleapis.com/auth/youtube.readonly"

    /** Only requested after an explicit user action (create / edit playlists). */
    const val MANAGE = "https://www.googleapis.com/auth/youtube"
}

/** Thin coroutine wrapper around the Google Identity [AuthorizationClient]. */
@Singleton
class GoogleAuthorizationDataSource @Inject constructor(
    private val client: AuthorizationClient,
) {

    /**
     * Requests [scopes] for [email]. When the user has already granted them this returns a
     * fresh access token without UI; otherwise the result carries a consent PendingIntent.
     */
    suspend fun authorize(scopes: List<String>, email: String): AuthorizationResult {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(scopes.map(::Scope))
            .setAccount(googleAccount(email))
            .build()
        return client.authorize(request).await()
    }

    /** Throws ApiException when the user cancelled or the flow failed. */
    fun resultFromIntent(data: Intent?): AuthorizationResult =
        client.getAuthorizationResultFromIntent(data)

    /** Evicts a cached token so the next [authorize] call returns a fresh one. */
    suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    suspend fun revokeAccess(email: String, scopes: List<String>) {
        val request = RevokeAccessRequest.builder()
            .setAccount(googleAccount(email))
            .setScopes(scopes.map(::Scope))
            .build()
        client.revokeAccess(request).await()
    }

    private fun googleAccount(email: String) = Account(email, GOOGLE_ACCOUNT_TYPE)

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
