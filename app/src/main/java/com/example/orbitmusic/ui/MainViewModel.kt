package com.example.orbitmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.domain.model.UserPreferences
import com.example.orbitmusic.domain.model.YouTubeAccess
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.domain.usecase.auth.RefreshAuthorizationUseCase
import com.example.orbitmusic.domain.usecase.preferences.ObservePreferencesUseCase
import com.example.orbitmusic.domain.usecase.preferences.SetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { Onboarding, Login, Main }

data class AppUiState(
    /** False while preferences and the session are still loading (splash stays up). */
    val isReady: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val startDestination: StartDestination = StartDestination.Onboarding,
    val authState: AuthState = AuthState.Loading,
)

/** Pure routing rule, unit-tested in MainViewModelTest. */
fun resolveStartDestination(preferences: UserPreferences, authState: AuthState): StartDestination =
    when {
        !preferences.onboardingCompleted -> StartDestination.Onboarding
        authState.hasYouTubeAccess() -> StartDestination.Main
        else -> StartDestination.Login
    }

fun AuthState.hasYouTubeAccess(): Boolean =
    this is AuthState.SignedIn && youTubeAccess == YouTubeAccess.Granted

@HiltViewModel
class MainViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val refreshAuthorization: RefreshAuthorizationUseCase,
    private val setThemeMode: SetThemeModeUseCase,
) : ViewModel() {

    private val authState = observeAuthState()

    val uiState: StateFlow<AppUiState> = combine(observePreferences(), authState) { prefs, auth ->
        AppUiState(
            isReady = auth !is AuthState.Loading,
            themeMode = prefs.themeMode,
            startDestination = resolveStartDestination(prefs, auth),
            authState = auth,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        // Silently renew the YouTube token at launch so revoked access is detected early
        // (the auth state then flips to Required and the app routes back to Login).
        viewModelScope.launch {
            val initial = authState.first { it !is AuthState.Loading }
            if (initial.hasYouTubeAccess()) refreshAuthorization()
        }
    }

    /** Used by the design-system screen's dark-mode switch. */
    fun onDarkThemeChange(dark: Boolean) {
        viewModelScope.launch { setThemeMode(if (dark) ThemeMode.Dark else ThemeMode.Light) }
    }
}
