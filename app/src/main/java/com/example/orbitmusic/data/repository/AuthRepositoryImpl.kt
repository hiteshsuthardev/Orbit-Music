package com.example.orbitmusic.data.repository

import android.content.Context
import android.content.Intent
import com.example.orbitmusic.data.local.secure.SessionStore
import com.example.orbitmusic.data.local.secure.StoredSession
import com.example.orbitmusic.data.remote.auth.AuthException
import com.example.orbitmusic.data.remote.auth.GoogleAuthorizationDataSource
import com.example.orbitmusic.data.remote.auth.GoogleCredentialDataSource
import com.example.orbitmusic.data.remote.auth.YouTubeScopes
import com.example.orbitmusic.di.ApplicationScope
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.domain.model.YouTubeAccess
import com.example.orbitmusic.domain.repository.AuthRepository
import com.example.orbitmusic.domain.repository.SessionCleaner
import com.example.orbitmusic.util.TimeProvider
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val credentialDataSource: GoogleCredentialDataSource,
    private val authorizationDataSource: GoogleAuthorizationDataSource,
    private val sessionStore: SessionStore,
    private val sessionCleaners: Set<@JvmSuppressWildcards SessionCleaner>,
    private val timeProvider: TimeProvider,
    @ApplicationScope applicationScope: CoroutineScope,
) : AuthRepository {

    /** Serializes token renewal so parallel API calls trigger at most one refresh. */
    private val tokenMutex = Mutex()

    override val authState: StateFlow<AuthState> = sessionStore.session
        .map { it.toAuthState() }
        .stateIn(applicationScope, SharingStarted.Eagerly, AuthState.Loading)

    override suspend fun signInWithGoogle(activityContext: Context): Resource<UserProfile> = try {
        val account = credentialDataSource.requestAccount(activityContext)
        sessionStore.update { existing ->
            val base = existing?.takeIf { it.userId == account.id }
                ?: StoredSession(userId = account.id, email = account.email)
            base.copy(
                email = account.email,
                displayName = account.displayName,
                givenName = account.givenName,
                photoUrl = account.photoUrl,
            )
        }
        Resource.Success(
            UserProfile(
                id = account.id,
                email = account.email,
                displayName = account.displayName,
                givenName = account.givenName,
                photoUrl = account.photoUrl,
            ),
        )
    } catch (e: AuthException) {
        Resource.Error(e.error)
    }

    override suspend fun authorizeYouTube(includeWriteAccess: Boolean): AuthorizationOutcome {
        val session = sessionStore.current() ?: return AuthorizationOutcome.Failed(AppError.NotSignedIn)
        val scopes = buildList {
            add(YouTubeScopes.READ_ONLY)
            if (includeWriteAccess) add(YouTubeScopes.MANAGE)
        }
        return try {
            val result = authorizationDataSource.authorize(scopes, session.email)
            val pendingIntent = result.pendingIntent
            if (result.hasResolution() && pendingIntent != null) {
                AuthorizationOutcome.NeedsConsent(pendingIntent)
            } else {
                saveGrant(result)
                AuthorizationOutcome.Granted
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthorizationOutcome.Failed(e.toAppError())
        }
    }

    override suspend fun completeAuthorization(data: Intent?): Resource<Unit> {
        if (data == null) return Resource.Error(AppError.ConsentDenied)
        return try {
            saveGrant(authorizationDataSource.resultFromIntent(data))
            Resource.Success(Unit)
        } catch (e: ApiException) {
            Resource.Error(
                if (e.statusCode == CommonStatusCodes.CANCELED) AppError.ConsentDenied else e.toAppError(),
            )
        } catch (e: AuthException) {
            Resource.Error(e.error)
        }
    }

    override suspend fun getAccessToken(forceRefresh: Boolean): Resource<String> = tokenMutex.withLock {
        val session = sessionStore.current() ?: return@withLock Resource.Error(AppError.NotSignedIn)
        val cached = session.accessToken
        val now = timeProvider.currentTimeMillis()
        if (!forceRefresh && cached != null && now < session.tokenExpiresAtMillis) {
            return@withLock Resource.Success(cached)
        }
        if (session.grantedScopes.isEmpty() && cached == null) {
            return@withLock Resource.Error(AppError.AuthorizationRequired)
        }

        try {
            // A 401 means Google may still hand back the same cached token; evict it first.
            if (forceRefresh && cached != null) {
                runCatching { authorizationDataSource.clearToken(cached) }
            }
            val scopes = session.grantedScopes.ifEmpty { listOf(YouTubeScopes.READ_ONLY) }
            var result = authorizationDataSource.authorize(scopes, session.email)
            if (result.hasResolution() && scopes != listOf(YouTubeScopes.READ_ONLY)) {
                // Playlist editing can't be renewed silently: keep the app working with read
                // access. Saving a playlist asks for the edit permission again.
                result = authorizationDataSource.authorize(listOf(YouTubeScopes.READ_ONLY), session.email)
            }
            if (result.hasResolution()) {
                // Access was revoked or needs re-consent: this cannot be done silently.
                sessionStore.update {
                    it?.copy(accessToken = null, tokenExpiresAtMillis = 0L, reauthorizationRequired = true)
                }
                Resource.Error(AppError.AuthorizationRequired)
            } else {
                Resource.Success(saveGrant(result))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.toAppError())
        }
    }

    override suspend fun signOut(revokeAccess: Boolean) {
        val session = sessionStore.current()
        // Remote clean-up is best effort; local data is always wiped.
        if (session != null) {
            session.accessToken?.let { token ->
                runCatching { authorizationDataSource.clearToken(token) }
            }
            if (revokeAccess && session.grantedScopes.isNotEmpty()) {
                runCatching { authorizationDataSource.revokeAccess(session.email, session.grantedScopes) }
            }
        }
        runCatching { credentialDataSource.clearCredentialState() }
        sessionCleaners.forEach { cleaner -> runCatching { cleaner.clear() } }
        sessionStore.clear()
    }

    /** Persists the token from [result] and returns it. */
    private suspend fun saveGrant(result: AuthorizationResult): String {
        val token = result.accessToken
            ?: throw AuthException(AppError.Unknown("Authorization returned no access token"))
        val expiresAt = timeProvider.currentTimeMillis() + tokenLifetimeMillis(result)
        sessionStore.update { existing ->
            existing?.copy(
                accessToken = token,
                tokenExpiresAtMillis = expiresAt,
                // Only what this token really carries, so a read-only renewal never looks
                // like it can still edit playlists.
                grantedScopes = result.grantedScopes.ifEmpty { existing.grantedScopes },
                reauthorizationRequired = false,
            )
        }
        return token
    }

    /**
     * Google access tokens usually live one hour. Use `expires_in` when the token response
     * includes it, and renew a few minutes early to avoid racing the expiry.
     */
    private fun tokenLifetimeMillis(result: AuthorizationResult): Long {
        val params = result.tokenResponseParams
        @Suppress("DEPRECATION")
        val expiresInSeconds = params?.get("expires_in")?.toString()?.toLongOrNull()
            ?: DEFAULT_TOKEN_LIFETIME_SECONDS
        val seconds = expiresInSeconds.coerceIn(MIN_TOKEN_LIFETIME_SECONDS, DEFAULT_TOKEN_LIFETIME_SECONDS)
        return (seconds - EXPIRY_SAFETY_MARGIN_SECONDS) * 1000L
    }

    private fun Exception.toAppError(): AppError = when (this) {
        is AuthException -> error
        is IOException -> AppError.NoInternet
        is ApiException -> when (statusCode) {
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> AppError.NoInternet
            CommonStatusCodes.CANCELED -> AppError.ConsentDenied
            CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.RESOLUTION_REQUIRED ->
                AppError.AuthorizationRequired
            CommonStatusCodes.DEVELOPER_ERROR -> AppError.MissingConfiguration
            else -> AppError.Unknown(message)
        }
        else -> AppError.Unknown(message)
    }

    private fun StoredSession?.toAuthState(): AuthState {
        if (this == null) return AuthState.SignedOut
        val hasReadAccess = grantedScopes.any { it == YouTubeScopes.READ_ONLY || it == YouTubeScopes.MANAGE }
        return AuthState.SignedIn(
            user = UserProfile(
                id = userId,
                email = email,
                displayName = displayName,
                givenName = givenName,
                photoUrl = photoUrl,
            ),
            youTubeAccess = if (hasReadAccess && !reauthorizationRequired) YouTubeAccess.Granted else YouTubeAccess.Required,
            canEditPlaylists = YouTubeScopes.MANAGE in grantedScopes,
        )
    }

    private companion object {
        const val DEFAULT_TOKEN_LIFETIME_SECONDS = 3600L
        const val MIN_TOKEN_LIFETIME_SECONDS = 600L
        const val EXPIRY_SAFETY_MARGIN_SECONDS = 300L
    }
}
