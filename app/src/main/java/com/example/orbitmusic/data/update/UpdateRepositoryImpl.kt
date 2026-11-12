package com.example.orbitmusic.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.di.AppUpdateDataStore
import com.example.orbitmusic.di.IoDispatcher
import com.example.orbitmusic.di.UpdateHttpClient
import com.example.orbitmusic.domain.update.AppUpdate
import com.example.orbitmusic.domain.update.RemoteConfig
import com.example.orbitmusic.domain.update.RemoteConfigRepository
import com.example.orbitmusic.domain.update.UpdateHistory
import com.example.orbitmusic.domain.update.UpdatePolicy
import com.example.orbitmusic.domain.update.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VersionJsonDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val minVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val notes: String = "",
)

@Serializable
data class RemoteConfigDto(
    @SerialName("announcement_text") val announcementText: String? = null,
    @SerialName("feature_ai_enabled") val featureAiEnabled: Boolean = true,
    @SerialName("ai_model") val aiModel: String? = null,
)

/** version.json -> [AppUpdate], or null if anything is missing or unsafe (non-HTTPS, bad hash). */
fun VersionJsonDto.toAppUpdate(): AppUpdate? {
    if (versionCode <= 0 || versionName.isBlank()) return null
    if (!UpdatePolicy.isSecureUrl(apkUrl) || !UpdatePolicy.isValidSha256(sha256)) return null
    return AppUpdate(
        versionCode = versionCode,
        versionName = versionName.trim(),
        apkUrl = apkUrl.trim(),
        sha256 = sha256.lowercase(),
        minVersionCode = minVersionCode,
        forceUpdate = forceUpdate,
        notes = notes.trim(),
    )
}

fun RemoteConfigDto.toRemoteConfig() = RemoteConfig(
    announcementText = announcementText?.trim()?.takeIf { it.isNotEmpty() },
    featureAiEnabled = featureAiEnabled,
    aiModel = aiModel?.trim()?.takeIf { it.isNotEmpty() },
)

/** Small HTTPS-only GET helper shared by the update files. Returns null on any failure. */
@Singleton
class UpdateFiles @Inject constructor(
    @UpdateHttpClient private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun getText(url: String): String? {
        if (!UpdatePolicy.isSecureUrl(url)) return null
        return withContext(io) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null // Malformed URL.
            }
        }
    }
}

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    private val files: UpdateFiles,
    private val json: Json,
    @AppUpdateDataStore private val dataStore: DataStore<Preferences>,
) : UpdateRepository {

    override suspend fun fetchLatest(): AppUpdate? {
        val text = files.getText(BuildConfig.UPDATE_JSON_URL) ?: return null
        return runCatching { json.decodeFromString(VersionJsonDto.serializer(), text) }.getOrNull()?.toAppUpdate()
    }

    override val history: Flow<UpdateHistory> = dataStore.safeData().map { prefs ->
        UpdateHistory(
            lastCheckAt = prefs[LAST_CHECK_AT] ?: 0L,
            skippedVersionCode = prefs[SKIPPED_VERSION],
            lastPromptedVersionCode = prefs[LAST_PROMPTED_VERSION],
            lastPromptedAt = prefs[LAST_PROMPTED_AT] ?: 0L,
        )
    }.distinctUntilChanged()

    override suspend fun recordCheck(at: Long) {
        dataStore.edit { it[LAST_CHECK_AT] = at }
    }

    override suspend fun recordPrompt(versionCode: Int, at: Long) {
        dataStore.edit {
            it[LAST_PROMPTED_VERSION] = versionCode
            it[LAST_PROMPTED_AT] = at
        }
    }

    override suspend fun skipVersion(versionCode: Int) {
        dataStore.edit { it[SKIPPED_VERSION] = versionCode }
    }

    private companion object {
        val LAST_CHECK_AT = longPreferencesKey("last_check_at")
        val SKIPPED_VERSION = intPreferencesKey("skipped_version_code")
        val LAST_PROMPTED_VERSION = intPreferencesKey("last_prompted_version_code")
        val LAST_PROMPTED_AT = longPreferencesKey("last_prompted_at")
    }
}

@Singleton
class RemoteConfigRepositoryImpl @Inject constructor(
    private val files: UpdateFiles,
    private val json: Json,
    @AppUpdateDataStore private val dataStore: DataStore<Preferences>,
) : RemoteConfigRepository {

    override val config: Flow<RemoteConfig> = dataStore.safeData().map { prefs ->
        prefs[CACHED_CONFIG]?.let(::parse) ?: RemoteConfig()
    }.distinctUntilChanged()

    override suspend fun refresh() {
        val text = files.getText(BuildConfig.REMOTE_CONFIG_URL) ?: return
        // Only cache a config that parses, so a broken file never replaces a good one.
        if (parse(text) != null) dataStore.edit { it[CACHED_CONFIG] = text }
    }

    override val dismissedAnnouncement: Flow<String?> =
        dataStore.safeData().map { it[DISMISSED_ANNOUNCEMENT] }.distinctUntilChanged()

    override suspend fun dismissAnnouncement(text: String) {
        dataStore.edit { it[DISMISSED_ANNOUNCEMENT] = text }
    }

    private fun parse(text: String): RemoteConfig? =
        runCatching { json.decodeFromString(RemoteConfigDto.serializer(), text) }.getOrNull()?.toRemoteConfig()

    private companion object {
        val CACHED_CONFIG = stringPreferencesKey("remote_config_json")
        val DISMISSED_ANNOUNCEMENT = stringPreferencesKey("dismissed_announcement")
    }
}

private fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
