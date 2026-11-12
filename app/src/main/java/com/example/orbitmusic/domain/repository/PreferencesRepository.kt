package com.example.orbitmusic.domain.repository

import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setNowPlayingView(view: NowPlayingView)
}
