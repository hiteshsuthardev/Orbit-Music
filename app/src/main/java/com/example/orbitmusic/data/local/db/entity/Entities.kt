package com.example.orbitmusic.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A video/track. Shared by playlists, liked songs, search, favorites and history. */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val rawTitle: String,
    val channelId: String?,
    val channelTitle: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val isPlayable: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val itemCount: Int,
    val channelTitle: String?,
    val updatedAt: Long,
)

/** Kind of row referenced by a [CollectionItemEntity]. */
object CollectionItemType {
    const val SONG = 0
    const val PLAYLIST = 1
}

/**
 * Ordered membership of songs or playlists in a cached list (my playlists, the items of
 * a playlist, liked songs, trending, a search page). See [CollectionKeys].
 */
@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionKey", "position"],
    indices = [Index("itemId")],
)
data class CollectionItemEntity(
    val collectionKey: String,
    val position: Int,
    val itemId: String,
    val itemType: Int,
    /** YouTube playlistItem id (needed to remove/reorder an item in a playlist). */
    val playlistItemId: String? = null,
)

/** When a collection was fetched and how to continue paging it. */
@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val key: String,
    val fetchedAt: Long,
    val nextPageToken: String?,
)

/** App-local favorites (separate from YouTube likes). */
@Entity(tableName = "favorites", indices = [Index("addedAt")])
data class FavoriteEntity(
    @PrimaryKey val videoId: String,
    val addedAt: Long,
)

/** One row per song: last time played and how often. */
@Entity(tableName = "history", indices = [Index("lastPlayedAt")])
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val lastPlayedAt: Long,
    val playCount: Int,
)

@Entity(tableName = "search_history", indices = [Index("searchedAt")])
data class SearchHistoryEntity(
    /** Normalized (trimmed, lowercase) query, so duplicates collapse. */
    @PrimaryKey val normalizedQuery: String,
    val query: String,
    val searchedAt: Long,
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val isMine: Boolean,
    val updatedAt: Long,
)

/** Builders for [CollectionItemEntity.collectionKey] / [CacheMetadataEntity.key]. */
object CollectionKeys {
    const val MY_PLAYLISTS = "my_playlists"
    const val LIKED = "liked"
    const val MY_CHANNEL = "my_channel"

    fun playlist(id: String) = "playlist:$id"
    fun trending(region: String) = "trending:$region"
    fun search(filter: String, normalizedQuery: String) = "search:$filter:$normalizedQuery"
}
