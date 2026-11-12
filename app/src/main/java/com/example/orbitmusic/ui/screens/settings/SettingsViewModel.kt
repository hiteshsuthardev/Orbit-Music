package com.example.orbitmusic.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.domain.usecase.auth.SignOutUseCase
import com.example.orbitmusic.domain.usecase.library.ClearHistoryUseCase
import com.example.orbitmusic.domain.usecase.preferences.ObservePreferencesUseCase
import com.example.orbitmusic.domain.usecase.preferences.SetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignOutConfirmation { SignOut, Disconnect }

data class SettingsUiState(
    val user: UserProfile? = null,
    val canEditPlaylists: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    /** Which confirmation dialog is showing (kept here so it survives rotation). */
    val pendingConfirmation: SignOutConfirmation? = null,
    val isSigningOut: Boolean = false,
    /** Listening history was cleared during this visit (shown as the row's subtitle). */
    val historyCleared: Boolean = false,
)

/** Account, theme, clearing listening history, and sign-out / disconnect. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val signOut: SignOutUseCase,
    private val clearHistory: ClearHistoryUseCase,
) : ViewModel() {

    private val pendingConfirmation = MutableStateFlow<SignOutConfirmation?>(null)
    private val signingOut = MutableStateFlow(false)
    private val historyCleared = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        observeAuthState(),
        observePreferences(),
        pendingConfirmation,
        signingOut,
        historyCleared,
    ) { auth, prefs, pending, isSigningOut, cleared ->
        val signedIn = auth as? AuthState.SignedIn
        SettingsUiState(
            user = signedIn?.user,
            canEditPlaylists = signedIn?.canEditPlaylists == true,
            themeMode = prefs.themeMode,
            pendingConfirmation = pending,
            isSigningOut = isSigningOut,
            historyCleared = cleared,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            clearHistory()
            historyCleared.value = true
        }
    }

    fun onRequestSignOut(kind: SignOutConfirmation) {
        pendingConfirmation.value = kind
    }

    fun onDismissConfirmation() {
        pendingConfirmation.value = null
    }

    /** The session guard in OrbitApp navigates to Login once the session is cleared. */
    fun onConfirmSignOut() {
        val kind = pendingConfirmation.value ?: return
        pendingConfirmation.value = null
        if (signingOut.value) return
        signingOut.value = true
        viewModelScope.launch {
            try {
                signOut(revokeAccess = kind == SignOutConfirmation.Disconnect)
            } finally {
                signingOut.value = false
            }
        }
    }
}
