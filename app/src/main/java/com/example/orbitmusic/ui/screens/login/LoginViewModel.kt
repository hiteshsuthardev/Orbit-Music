package com.example.orbitmusic.ui.screens.login

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.domain.usecase.auth.CompleteAuthorizationUseCase
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.domain.usecase.auth.SignInAndAuthorizeUseCase
import com.example.orbitmusic.domain.usecase.auth.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    /** Set when someone is signed in but YouTube access must be granted again. */
    val reconnectUser: UserProfile? = null,
    val error: AppError? = null,
)

sealed interface LoginEvent {
    /** The UI must launch this Google consent screen and report back the result. */
    data class LaunchConsent(val pendingIntent: PendingIntent) : LoginEvent
    data object SignedIn : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val signInAndAuthorize: SignInAndAuthorizeUseCase,
    private val completeAuthorization: CompleteAuthorizationUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<LoginUiState> = combine(observeAuthState(), loading, error) { auth, isLoading, err ->
        LoginUiState(
            isLoading = isLoading,
            reconnectUser = (auth as? AuthState.SignedIn)?.user,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoginUiState())

    /**
     * Starts "Continue with Google". [activityContext] is required by Credential Manager
     * to show its bottom sheet; it is used for this call only and never retained.
     */
    fun onContinueWithGoogle(activityContext: Context) {
        if (loading.value) return
        loading.value = true
        error.value = null
        viewModelScope.launch {
            handleOutcome(signInAndAuthorize(activityContext))
        }
    }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            if (resultCode != Activity.RESULT_OK) {
                fail(AppError.ConsentDenied)
                return@launch
            }
            when (val result = completeAuthorization(data)) {
                is Resource.Success -> succeed()
                is Resource.Error -> fail(result.error)
                Resource.Loading -> Unit
            }
        }
    }

    /** In reconnect mode: forget the current account so a different one can be picked. */
    fun onUseDifferentAccount() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            signOut()
            loading.value = false
        }
    }

    fun onDismissError() {
        error.value = null
    }

    private suspend fun handleOutcome(outcome: AuthorizationOutcome) {
        when (outcome) {
            AuthorizationOutcome.Granted -> succeed()
            is AuthorizationOutcome.NeedsConsent -> _events.send(LoginEvent.LaunchConsent(outcome.pendingIntent))
            is AuthorizationOutcome.Failed -> fail(outcome.error)
        }
    }

    private suspend fun succeed() {
        loading.value = false
        _events.send(LoginEvent.SignedIn)
    }

    private fun fail(appError: AppError) {
        loading.value = false
        // Closing the account picker is a deliberate choice; don't scold the user for it.
        error.update { if (appError == AppError.SignInCancelled) null else appError }
    }
}
