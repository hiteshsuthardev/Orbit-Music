package com.example.orbitmusic.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.orbitmusic.di.PlaybackSessionDataStore
import com.example.orbitmusic.domain.model.QueueItem
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.QueueState
import com.example.orbitmusic.domain.player.RepeatMode
import com.example.orbitmusic.domain.repository.PlaybackSessionRepository
import com.example.orbitmusic.domain.repository.PlaybackSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class SongDto(
    val videoId: String,
    val title: String,
    val artist: String,
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    val isPlayable: Boolean = true,
)

@Serializable
internal data class QueueItemDto(val queueId: String, val song: SongDto)

@Serializable
internal data class PlaybackSnapshotDto(
    val items: List<QueueItemDto>,
    val unshuffledIds: List<String>,
    val currentIndex: Int,
    val shuffleEnabled: Boolean,
    val repeatMode: String,
    val positionMs: Long,
)

internal fun PlaybackSnapshot.toDto() = PlaybackSnapshotDto(
    items = queue.items.map { QueueItemDto(it.queueId, it.song.toDto()) },
    unshuffledIds = queue.unshuffledItems.map { it.queueId },
    currentIndex = queue.currentIndex,
    shuffleEnabled = queue.shuffleEnabled,
    repeatMode = queue.repeatMode.name,
    positionMs = positionMs,
)

internal fun PlaybackSnapshotDto.toDomain(): PlaybackSnapshot? {
    val items = items.map { QueueItem(it.queueId, it.song.toDomain()) }
    if (items.isEmpty() || currentIndex !in items.indices) return null
    val byId = items.associateBy { it.queueId }
    val unshuffled = unshuffledIds.mapNotNull { byId[it] }.takeIf { it.size == items.size } ?: items
    return PlaybackSnapshot(
        queue = QueueState(
            items = items,
            currentIndex = currentIndex,
            shuffleEnabled = shuffleEnabled,
            repeatMode = RepeatMode.entries.firstOrNull { it.name == repeatMode } ?: RepeatMode.Off,
            unshuffledItems = unshuffled,
        ),
        positionMs = positionMs,
    )
}

private fun Song.toDto() = SongDto(videoId, title, artist, channelId, thumbnailUrl, durationMs, isPlayable)
private fun SongDto.toDomain() = Song(videoId, title, artist, channelId, thumbnailUrl, durationMs, isPlayable)

/** Stores the last queue as JSON so playback can resume after the app is killed. */
@Singleton
class PlaybackSessionRepositoryImpl @Inject constructor(
    @PlaybackSessionDataStore private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : PlaybackSessionRepository {

    override suspend fun save(snapshot: PlaybackSnapshot) {
        val encoded = json.encodeToString(PlaybackSnapshotDto.serializer(), snapshot.toDto())
        dataStore.edit { it[KEY] = encoded }
    }

    override suspend fun load(): PlaybackSnapshot? {
        val encoded = runCatching { dataStore.data.first()[KEY] }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(PlaybackSnapshotDto.serializer(), encoded).toDomain() }.getOrNull()
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("last_session")
    }
}
