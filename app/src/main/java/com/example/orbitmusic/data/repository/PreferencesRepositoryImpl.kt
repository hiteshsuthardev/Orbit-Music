package com.example.orbitmusic.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.orbitmusic.di.UserPreferencesDataStore
import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.domain.model.UserPreferences
import com.example.orbitmusic.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            UserPreferences(
                onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
                themeMode = prefs[THEME_MODE]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: ThemeMode.Dark,
                nowPlayingView = prefs[NOW_PLAYING_VIEW]
                    ?.let { stored -> NowPlayingView.entries.firstOrNull { it.name == stored } }
                    ?: NowPlayingView.Video,
            )
        }
        .distinctUntilChanged()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setNowPlayingView(view: NowPlayingView) {
        dataStore.edit { it[NOW_PLAYING_VIEW] = view.name }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOW_PLAYING_VIEW = stringPreferencesKey("now_playing_view")
    }
}
