package com.example.orbitmusic.domain.model

/** A playable track. Backed by a YouTube video. */
data class Song(
    val videoId: String,
    /** Display title with "(Official Video)", "[Lyrics]" etc. removed. */
    val title: String,
    /** Artist name (channel name without " - Topic", or parsed from "Artist - Title"). */
    val artist: String,
    val channelId: String?,
    val thumbnailUrl: String?,
    /** 0 when unknown (e.g. live streams). */
    val durationMs: Long,
    /** False for private, deleted or non-embeddable videos; the player skips these. */
    val isPlayable: Boolean,
) {
    val shareUrl: String get() = "https://music.youtube.com/watch?v=$videoId"
}

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val itemCount: Int,
    val ownerName: String?,
) {
    val shareUrl: String get() = "https://music.youtube.com/playlist?list=$id"
}

/** A YouTube channel, presented as an artist. */
data class Artist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
)

/**
 * An entry in the play queue. [queueId] is unique per entry so the same song can appear
 * in the queue more than once (needed for drag-and-drop and swipe-to-remove).
 */
data class QueueItem(
    val queueId: String,
    val song: Song,
)

data class PlaylistDetail(
    val playlist: Playlist,
    val songs: List<Song>,
    /** More items can be paged in from YouTube. */
    val hasMore: Boolean,
) {
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
}

enum class SearchFilter { Songs, Playlists }

data class SearchResults(
    val query: String,
    val filter: SearchFilter,
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val hasMore: Boolean = false,
    /** True when served from the local cache without spending API quota. */
    val fromCache: Boolean = false,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && playlists.isEmpty()
}

enum class PlaylistPrivacy(val apiValue: String) {
    Private("private"),
    Unlisted("unlisted"),
    Public("public"),
}
