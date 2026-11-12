package com.example.orbitmusic.domain.update

import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    /** Fetches version.json; null when it can't be read (failures are silent by design). */
    suspend fun fetchLatest(): AppUpdate?

    val history: Flow<UpdateHistory>
    suspend fun recordCheck(at: Long)
    suspend fun recordPrompt(versionCode: Int, at: Long)
    suspend fun skipVersion(versionCode: Int)
}

interface RemoteConfigRepository {
    /** The last good config (from cache when offline); defaults before the first fetch. */
    val config: Flow<RemoteConfig>

    /** Fetches the remote config and caches it; failures keep the cached copy. */
    suspend fun refresh()

    /** The announcement the user closed (so it isn't shown again). */
    val dismissedAnnouncement: Flow<String?>
    suspend fun dismissAnnouncement(text: String)
}
