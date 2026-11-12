package com.example.orbitmusic.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.orbitmusic.di.AiUsageDataStore
import com.example.orbitmusic.domain.ai.AiUsage
import com.example.orbitmusic.domain.repository.AiUsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orbit AI usage in its own DataStore, keyed by user id: recent request times (comma
 * separated millis, only those inside the limit window) and the all-time count.
 */
@Singleton
class AiUsageRepositoryImpl @Inject constructor(
    @AiUsageDataStore private val dataStore: DataStore<Preferences>,
) : AiUsageRepository {

    override fun observe(userId: String): Flow<AiUsage> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.usageFor(userId) }
        .distinctUntilChanged()

    override suspend fun update(userId: String, transform: (AiUsage) -> AiUsage) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.usageFor(userId))
            prefs[recentKey(userId)] = updated.recentRequestTimes.joinToString(",")
            prefs[totalKey(userId)] = updated.totalRequests
        }
    }

    private fun Preferences.usageFor(userId: String) = AiUsage(
        recentRequestTimes = this[recentKey(userId)].orEmpty().split(',').mapNotNull { it.toLongOrNull() },
        totalRequests = this[totalKey(userId)] ?: 0,
    )

    private fun recentKey(userId: String) = stringPreferencesKey("recent_$userId")

    private fun totalKey(userId: String) = intPreferencesKey("total_$userId")
}
