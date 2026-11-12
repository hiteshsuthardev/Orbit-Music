package com.example.orbitmusic.domain.usecase.auth

import android.content.Context
import android.content.Intent
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): StateFlow<AuthState> = repository.authState
}

/**
 * Full "Continue with Google" flow: pick the account (skipped when someone is already
 * signed in and only needs to re-authorize) and then request YouTube read access.
 */
class SignInAndAuthorizeUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(activityContext: Context): AuthorizationOutcome {
        if (repository.authState.value !is AuthState.SignedIn) {
            when (val signIn = repository.signInWithGoogle(activityContext)) {
                is Resource.Error -> return AuthorizationOutcome.Failed(signIn.error)
                Resource.Loading, is Resource.Success -> Unit
            }
        }
        return repository.authorizeYouTube(includeWriteAccess = false)
    }
}

/** Upgrades to the youtube (write) scope. Only call after an explicit user action. */
class RequestPlaylistEditAccessUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthorizationOutcome =
        repository.authorizeYouTube(includeWriteAccess = true)
}

class CompleteAuthorizationUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(data: Intent?): Resource<Unit> =
        repository.completeAuthorization(data)
}

/** Renews the access token in the background so revoked access is detected early. */
class RefreshAuthorizationUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): Resource<String> = repository.getAccessToken(forceRefresh = false)
}

class SignOutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(revokeAccess: Boolean = false) = repository.signOut(revokeAccess)
}
