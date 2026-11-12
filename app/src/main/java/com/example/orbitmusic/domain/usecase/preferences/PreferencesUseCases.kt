package com.example.orbitmusic.domain.usecase.preferences

import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.domain.model.UserPreferences
import com.example.orbitmusic.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePreferencesUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    operator fun invoke(): Flow<UserPreferences> = repository.preferences
}

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke() = repository.setOnboardingCompleted(true)
}

class SetThemeModeUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) = repository.setThemeMode(mode)
}

class SetNowPlayingViewUseCase @Inject constructor(
    private val repository: PreferencesRepository,
) {
    suspend operator fun invoke(view: NowPlayingView) = repository.setNowPlayingView(view)
}
