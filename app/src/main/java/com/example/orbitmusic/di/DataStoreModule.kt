package com.example.orbitmusic.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Theme, onboarding flag and (later) playback settings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferencesDataStore

/** Encrypted session (tokens + profile). Excluded from backups. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionDataStore

/** Last play queue for "resume last session". Cleared on sign-out. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackSessionDataStore

/** Orbit AI request counts per user. Kept on sign-out and included in backups. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiUsageDataStore

/** Self-hosted update history and the cached remote config. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppUpdateDataStore

/** File names are also referenced from res/xml backup rules. */
object DataStoreFiles {
    const val USER_PREFERENCES = "user_preferences"
    const val SESSION = "secure_session"
    const val PLAYBACK_SESSION = "playback_session"
    const val AI_USAGE = "ai_usage"
    const val APP_UPDATE = "app_update"
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @UserPreferencesDataStore
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(DataStoreFiles.USER_PREFERENCES) },
    )

    @Provides
    @Singleton
    @SessionDataStore
    fun provideSessionDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(DataStoreFiles.SESSION) },
    )

    @Provides
    @Singleton
    @AppUpdateDataStore
    fun provideAppUpdateDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(DataStoreFiles.APP_UPDATE) },
    )

    @Provides
    @Singleton
    @AiUsageDataStore
    fun provideAiUsageDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(DataStoreFiles.AI_USAGE) },
    )

    @Provides
    @Singleton
    @PlaybackSessionDataStore
    fun providePlaybackSessionDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(DataStoreFiles.PLAYBACK_SESSION) },
    )
}
