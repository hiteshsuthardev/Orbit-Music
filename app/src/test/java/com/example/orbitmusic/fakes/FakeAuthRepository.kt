package com.example.orbitmusic.fakes

import android.content.Context
import android.content.Intent
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.domain.model.YouTubeAccess
import com.example.orbitmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scriptable in-memory [AuthRepository] for ViewModel tests. */
class FakeAuthRepository : AuthRepository {

    val user = UserProfile("id", "nova@example.com", "Nova Star", "Nova", null)

    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = state

    var signInResult: Resource<UserProfile> = Resource.Success(user)
    var authorizeOutcome: AuthorizationOutcome = AuthorizationOutcome.Granted
    var completeResult: Resource<Unit> = Resource.Success(Unit)

    var signInCalls = 0
        private set
    var signOutCalls = 0
        private set

    override suspend fun signInWithGoogle(activityContext: Context): Resource<UserProfile> {
        signInCalls++
        val result = signInResult
        if (result is Resource.Success) {
            state.value = AuthState.SignedIn(result.data, YouTubeAccess.Required, canEditPlaylists = false)
        }
        return result
    }

    override suspend fun authorizeYouTube(includeWriteAccess: Boolean): AuthorizationOutcome {
        val outcome = authorizeOutcome
        if (outcome == AuthorizationOutcome.Granted) grant()
        return outcome
    }

    override suspend fun completeAuthorization(data: Intent?): Resource<Unit> {
        val result = completeResult
        if (result is Resource.Success) grant()
        return result
    }

    override suspend fun getAccessToken(forceRefresh: Boolean): Resource<String> =
        if (state.value is AuthState.SignedIn) Resource.Success("token") else Resource.Error(AppError.NotSignedIn)

    override suspend fun signOut(revokeAccess: Boolean) {
        signOutCalls++
        state.value = AuthState.SignedOut
    }

    private fun grant() {
        state.value = AuthState.SignedIn(user, YouTubeAccess.Granted, canEditPlaylists = false)
    }
}
