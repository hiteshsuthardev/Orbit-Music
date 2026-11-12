package com.example.orbitmusic.domain.repository

import com.example.orbitmusic.domain.model.Artist
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow

/*
 * Offline-first contracts: `observe*` streams always read from the local cache (Room);
 * `refresh*` / `loadMore*` talk to YouTube and write into that cache. Refreshes respect a
 * TTL unless `force` is true, because the YouTube daily quota is limited.
 */

interface PlaylistRepository {
    fun observeMyPlaylists(): Flow<List<Playlist>>
    suspend fun refreshMyPlaylists(force: Boolean = false): Resource<Unit>

    fun observePlaylist(playlistId: String): Flow<Playlist?>
    fun observePlaylistSongs(playlistId: String): Flow<List<Song>>
    fun observePlaylistHasMore(playlistId: String): Flow<Boolean>
    suspend fun refreshPlaylist(playlistId: String, force: Boolean = false): Resource<Unit>

    /** Loads the next page; returns whether even more pages remain. */
    suspend fun loadMorePlaylistItems(playlistId: String): Resource<Boolean>

    /** Every item (paging as needed, up to [maxItems]); used by Play all / Shuffle. */
    suspend fun getAllPlaylistSongs(playlistId: String, maxItems: Int = 500): Resource<List<Song>>

    /** Needs the youtube (write) scope. */
    suspend fun createPlaylist(title: String, description: String, privacy: PlaylistPrivacy): Resource<Playlist>

    /** Needs the youtube (write) scope. */
    suspend fun addSongToPlaylist(playlistId: String, song: Song): Resource<Unit>
}

interface SongRepository {
    fun observeLikedSongs(): Flow<List<Song>>
    fun observeLikedHasMore(): Flow<Boolean>
    suspend fun refreshLikedSongs(force: Boolean = false): Resource<Unit>
    suspend fun loadMoreLikedSongs(): Resource<Boolean>

    fun observeTrending(): Flow<List<Song>>
    suspend fun refreshTrending(force: Boolean = false): Resource<Unit>

    fun observeSong(videoId: String): Flow<Song?>

    /** Cached songs first; missing ids are fetched from YouTube (order preserved). */
    suspend fun getSongs(videoIds: List<String>): Resource<List<Song>>

    /** Called by the player when YouTube refuses to play a video. */
    suspend fun markUnplayable(videoId: String)

    /** Like / unlike on YouTube. Needs the youtube (write) scope. */
    suspend fun rateSong(song: Song, liked: Boolean): Resource<Unit>
}

interface SearchRepository {
    /**
     * Searches YouTube (100 quota units per page). Results are cached per query and
     * filter, so repeating a recent search costs nothing.
     */
    suspend fun search(query: String, filter: SearchFilter, forceRefresh: Boolean = false): Resource<SearchResults>

    suspend fun loadMore(query: String, filter: SearchFilter): Resource<SearchResults>

    /** Previously fetched results for this query (any age), or null. Never calls the API. */
    suspend fun cachedResults(query: String, filter: SearchFilter): SearchResults?

    fun observeRecentSearches(limit: Int = 10): Flow<List<String>>
    suspend fun saveSearch(query: String)
    suspend fun deleteSearch(query: String)
    suspend fun clearSearchHistory()

    /** Quota-free suggestions from search history and cached song titles. */
    suspend fun suggestions(prefix: String, limit: Int = 8): List<String>
}

/** App-local data: favorites and listening history. Never touches the network. */
interface UserLibraryRepository {
    fun observeFavorites(): Flow<List<Song>>
    fun observeFavoriteIds(): Flow<Set<String>>
    suspend fun setFavorite(song: Song, favorite: Boolean)
    suspend fun isFavorite(videoId: String): Boolean

    fun observeRecentlyPlayed(limit: Int = 30): Flow<List<Song>>
    fun observeMostPlayed(limit: Int = 20): Flow<List<Song>>
    suspend fun recordPlay(song: Song)
    suspend fun clearHistory()
}

interface ChannelRepository {
    fun observeMyChannel(): Flow<Artist?>
    suspend fun refreshMyChannel(force: Boolean = false): Resource<Unit>
}
