package com.example.orbitmusic.data.repository

import androidx.room.withTransaction
import com.example.orbitmusic.data.local.db.OrbitDatabase
import com.example.orbitmusic.data.local.db.entity.CacheMetadataEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemType
import com.example.orbitmusic.data.local.db.entity.CollectionKeys
import com.example.orbitmusic.data.local.db.entity.SearchHistoryEntity
import com.example.orbitmusic.data.mapper.toEntity
import com.example.orbitmusic.data.mapper.toPartialSongEntity
import com.example.orbitmusic.data.mapper.toPlaylistEntity
import com.example.orbitmusic.data.mapper.toPlaylists
import com.example.orbitmusic.data.mapper.toSongs
import com.example.orbitmusic.data.remote.youtube.ApiErrorMapper
import com.example.orbitmusic.data.remote.youtube.YouTubeApi
import com.example.orbitmusic.data.remote.youtube.dto.ListResponseDto
import com.example.orbitmusic.data.remote.youtube.dto.SearchResultDto
import com.example.orbitmusic.data.remote.youtube.safeCall
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.repository.SearchRepository
import com.example.orbitmusic.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val api: YouTubeApi,
    private val db: OrbitDatabase,
    private val videoDetails: VideoDetailsFetcher,
    private val freshness: CacheFreshness,
    private val errorMapper: ApiErrorMapper,
    private val timeProvider: TimeProvider,
) : SearchRepository {

    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()
    private val collectionDao = db.collectionDao()
    private val metadataDao = db.cacheMetadataDao()
    private val searchHistoryDao = db.searchHistoryDao()
    private val locks = KeyedMutex()

    override suspend fun search(query: String, filter: SearchFilter, forceRefresh: Boolean): Resource<SearchResults> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return Resource.Success(SearchResults(query, filter))
        val key = CollectionKeys.search(filter.name, normalized)

        return locks.withLock(key) {
            val hasCache = metadataDao.get(key) != null
            if (!forceRefresh && freshness.isFresh(key, CachePolicy.SEARCH)) {
                Resource.Success(readResults(query, filter, key, fromCache = true))
            } else {
                val fetched = errorMapper.safeCall { fetchPage(query, filter, key, pageToken = null) }
                when {
                    fetched is Resource.Success -> Resource.Success(readResults(query, filter, key, fromCache = false))
                    // Out of quota or offline: a stale cached answer beats an error screen.
                    fetched is Resource.Error && hasCache && fetched.error.isRecoverableWithCache() ->
                        Resource.Success(readResults(query, filter, key, fromCache = true))
                    fetched is Resource.Error -> fetched
                    else -> Resource.Loading
                }
            }
        }
    }

    override suspend fun loadMore(query: String, filter: SearchFilter): Resource<SearchResults> {
        val normalized = normalize(query)
        val key = CollectionKeys.search(filter.name, normalized)
        return locks.withLock(key) {
            val token = metadataDao.get(key)?.nextPageToken
            if (token == null) {
                Resource.Success(readResults(query, filter, key, fromCache = true))
            } else {
                when (val fetched = errorMapper.safeCall { fetchPage(query, filter, key, token) }) {
                    is Resource.Success -> Resource.Success(readResults(query, filter, key, fromCache = false))
                    is Resource.Error -> fetched
                    Resource.Loading -> Resource.Loading
                }
            }
        }
    }

    override suspend fun cachedResults(query: String, filter: SearchFilter): SearchResults? {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return null
        val key = CollectionKeys.search(filter.name, normalized)
        if (metadataDao.get(key) == null) return null
        return readResults(query, filter, key, fromCache = true)
    }

    /** Fetches one page and writes it to the cache (replacing it for the first page). */
    private suspend fun fetchPage(query: String, filter: SearchFilter, key: String, pageToken: String?) {
        val now = timeProvider.currentTimeMillis()
        val page: ListResponseDto<SearchResultDto>
        val items: List<CollectionItemEntity>
        val start = if (pageToken == null) 0 else collectionDao.nextPosition(key)

        when (filter) {
            SearchFilter.Songs -> {
                page = api.search(
                    query = query,
                    type = "video",
                    videoCategoryId = YouTubeApi.MUSIC_CATEGORY_ID,
                    pageToken = pageToken,
                )
                val partial = page.items.mapNotNull { it.toPartialSongEntity(now) }
                // Search has no durations: one cheap videos.list call (1 unit) fills them in.
                // ...and drops Shorts, live streams and anything outside the Music category.
                val details = videoDetails.fetch(partial.map { it.videoId }, now)
                val songs = partial.mapNotNull { details.music[it.videoId] }
                songDao.upsertAll(songs)
                items = songs.mapIndexed { i, song ->
                    CollectionItemEntity(key, start + i, song.videoId, CollectionItemType.SONG)
                }
            }
            SearchFilter.Playlists -> {
                page = api.search(query = query, type = "playlist", pageToken = pageToken)
                val partial = page.items.mapNotNull { it.toPlaylistEntity(now) }
                playlistDao.insertIfAbsent(partial)
                // Track counts come from playlists.list (1 unit).
                if (partial.isNotEmpty()) {
                    val full = api.playlistsById(partial.joinToString(",") { it.id }).items
                    playlistDao.upsertAll(full.map { it.toEntity(now) })
                }
                items = partial.mapIndexed { i, playlist ->
                    CollectionItemEntity(key, start + i, playlist.id, CollectionItemType.PLAYLIST)
                }
            }
        }

        db.withTransaction {
            if (pageToken == null) collectionDao.replace(key, items) else collectionDao.insertAll(items)
            val previous = metadataDao.get(key)
            metadataDao.upsert(
                CacheMetadataEntity(
                    key = key,
                    fetchedAt = if (pageToken == null) now else previous?.fetchedAt ?: now,
                    nextPageToken = page.nextPageToken,
                ),
            )
        }
    }

    private suspend fun readResults(query: String, filter: SearchFilter, key: String, fromCache: Boolean): SearchResults {
        val hasMore = metadataDao.get(key)?.nextPageToken != null
        return when (filter) {
            SearchFilter.Songs -> SearchResults(
                query = query,
                filter = filter,
                songs = collectionDao.getSongs(key).toSongs(),
                hasMore = hasMore,
                fromCache = fromCache,
            )
            SearchFilter.Playlists -> SearchResults(
                query = query,
                filter = filter,
                playlists = collectionDao.getPlaylists(key).toPlaylists(),
                hasMore = hasMore,
                fromCache = fromCache,
            )
        }
    }

    // History & suggestions --------------------------------------------------------------

    override fun observeRecentSearches(limit: Int): Flow<List<String>> =
        searchHistoryDao.observeRecent(limit).map { entries -> entries.map { it.query } }

    override suspend fun saveSearch(query: String) {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return
        searchHistoryDao.upsert(
            SearchHistoryEntity(
                normalizedQuery = normalized,
                query = query.trim(),
                searchedAt = timeProvider.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteSearch(query: String) = searchHistoryDao.delete(normalize(query))

    override suspend fun clearSearchHistory() = searchHistoryDao.clear()

    override suspend fun suggestions(prefix: String, limit: Int): List<String> {
        val normalized = normalize(prefix)
        if (normalized.isEmpty()) return emptyList()
        val pattern = normalized.escapeForLike()
        val fromHistory = searchHistoryDao.matching(pattern, limit).map { it.query }
        val fromTitles = songDao.titlesStartingWith(prefix.trim().escapeForLike(), limit)
        return (fromHistory + fromTitles)
            .distinctBy { it.lowercase() }
            .filterNot { it.equals(prefix.trim(), ignoreCase = true) }
            .take(limit)
    }

    private fun AppError.isRecoverableWithCache() =
        this == AppError.QuotaExceeded || this == AppError.NoInternet

    companion object {
        private val whitespace = Regex("\\s+")

        fun normalize(query: String): String = query.trim().replace(whitespace, " ").lowercase()
    }
}
