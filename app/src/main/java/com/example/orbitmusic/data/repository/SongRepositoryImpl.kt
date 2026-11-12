package com.example.orbitmusic.data.repository

import androidx.room.withTransaction
import com.example.orbitmusic.data.local.db.OrbitDatabase
import com.example.orbitmusic.data.local.db.entity.CacheMetadataEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemType
import com.example.orbitmusic.data.local.db.entity.CollectionKeys
import com.example.orbitmusic.data.local.db.entity.SongEntity
import com.example.orbitmusic.data.mapper.toDomain
import com.example.orbitmusic.data.mapper.isMusicVideo
import com.example.orbitmusic.data.mapper.toEntity
import com.example.orbitmusic.data.mapper.toSongEntity
import com.example.orbitmusic.data.mapper.toSongs
import com.example.orbitmusic.data.remote.youtube.ApiErrorMapper
import com.example.orbitmusic.data.remote.youtube.YouTubeApi
import com.example.orbitmusic.data.remote.youtube.dto.ListResponseDto
import com.example.orbitmusic.data.remote.youtube.dto.VideoDto
import com.example.orbitmusic.data.remote.youtube.safeCall
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.SongRepository
import com.example.orbitmusic.util.TimeProvider
import com.example.orbitmusic.util.currentRegionCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

@Singleton
class SongRepositoryImpl @Inject constructor(
    private val api: YouTubeApi,
    private val db: OrbitDatabase,
    private val videoDetails: VideoDetailsFetcher,
    private val freshness: CacheFreshness,
    private val errorMapper: ApiErrorMapper,
    private val timeProvider: TimeProvider,
) : SongRepository {

    private val songDao = db.songDao()
    private val collectionDao = db.collectionDao()
    private val metadataDao = db.cacheMetadataDao()
    private val locks = KeyedMutex()

    // Liked songs (videos?myRating=like) ------------------------------------------------

    override fun observeLikedSongs(): Flow<List<Song>> =
        collectionDao.observeSongs(CollectionKeys.LIKED).map { it.toSongs() }

    override fun observeLikedHasMore(): Flow<Boolean> =
        metadataDao.observe(CollectionKeys.LIKED).map { it?.nextPageToken != null }.distinctUntilChanged()

    override suspend fun refreshLikedSongs(force: Boolean): Resource<Unit> =
        refreshVideoCollection(
            CollectionKeys.LIKED,
            CachePolicy.LIKED,
            force,
            fetchNextPage = { token -> api.likedVideos(pageToken = token) },
        ) { api.likedVideos() }

    override suspend fun loadMoreLikedSongs(): Resource<Boolean> =
        loadMoreVideoCollection(CollectionKeys.LIKED) { token -> api.likedVideos(pageToken = token) }

    // Trending music (chart=mostPopular, category 10) ------------------------------------

    override fun observeTrending(): Flow<List<Song>> =
        collectionDao.observeSongs(CollectionKeys.trending(currentRegionCode())).map { it.toSongs() }

    override suspend fun refreshTrending(force: Boolean): Resource<Unit> {
        val region = currentRegionCode()
        return refreshVideoCollection(
            CollectionKeys.trending(region),
            CachePolicy.TRENDING,
            force,
            fetchNextPage = { token -> api.trendingMusic(regionCode = region, pageToken = token) },
        ) {
            api.trendingMusic(regionCode = region)
        }
    }

    // Single songs ---------------------------------------------------------------------

    override fun observeSong(videoId: String): Flow<Song?> =
        songDao.observeById(videoId).map { it?.toDomain() }

    override suspend fun getSongs(videoIds: List<String>): Resource<List<Song>> {
        if (videoIds.isEmpty()) return Resource.Success(emptyList())
        val cached = songDao.getByIds(videoIds).associateBy { it.videoId }
        val missing = videoIds.filterNot { it in cached }
        val fetched: Map<String, SongEntity> = if (missing.isEmpty()) {
            emptyMap()
        } else {
            when (val result = errorMapper.safeCall { videoDetails.fetch(missing, timeProvider.currentTimeMillis()).music }) {
                is Resource.Success -> result.data.also { songDao.upsertAll(it.values.toList()) }
                is Resource.Error -> if (cached.isEmpty()) return result else emptyMap()
                Resource.Loading -> emptyMap()
            }
        }
        val all = cached + fetched
        return Resource.Success(videoIds.mapNotNull { all[it]?.toDomain() })
    }

    override suspend fun markUnplayable(videoId: String) = songDao.markUnplayable(videoId)

    override suspend fun rateSong(song: Song, liked: Boolean): Resource<Unit> {
        val result = errorMapper.safeCall {
            api.rateVideo(videoId = song.videoId, rating = if (liked) "like" else "none")
        }
        if (result is Resource.Success) {
            if (songDao.getById(song.videoId) == null) {
                songDao.upsertAll(listOf(song.toEntity(timeProvider.currentTimeMillis())))
            }
            // Re-read the liked list so order and membership match YouTube (1 unit).
            refreshLikedSongs(force = true)
        }
        return result
    }

    // Shared paging for video lists ------------------------------------------------------

    private suspend fun refreshVideoCollection(
        key: String,
        ttl: Duration,
        force: Boolean,
        fetchNextPage: suspend (token: String) -> ListResponseDto<VideoDto>,
        fetchFirstPage: suspend () -> ListResponseDto<VideoDto>,
    ): Resource<Unit> = locks.withLock(key) {
        if (!force && freshness.isFresh(key, ttl)) {
            Resource.Success(Unit)
        } else {
            errorMapper.safeCall {
                val (videos, nextToken) = fetchMusicVideos(fetchFirstPage(), fetchNextPage)
                val now = timeProvider.currentTimeMillis()
                val songs = videos.map { it.toSongEntity(now) }
                db.withTransaction {
                    songDao.upsertAll(songs)
                    collectionDao.replace(key, songs.toCollectionItems(key, 0))
                    metadataDao.upsert(CacheMetadataEntity(key, now, nextToken))
                }
            }
        }
    }

    private suspend fun loadMoreVideoCollection(
        key: String,
        fetchPage: suspend (token: String) -> ListResponseDto<VideoDto>,
    ): Resource<Boolean> = locks.withLock(key) {
        val metadata = metadataDao.get(key)
        val token = metadata?.nextPageToken
        if (metadata == null || token == null) {
            Resource.Success(false)
        } else {
            errorMapper.safeCall {
                val (videos, nextToken) = fetchMusicVideos(fetchPage(token), fetchPage)
                val now = timeProvider.currentTimeMillis()
                val songs = videos.map { it.toSongEntity(now) }
                db.withTransaction {
                    songDao.upsertAll(songs)
                    collectionDao.insertAll(songs.toCollectionItems(key, collectionDao.nextPosition(key)))
                    metadataDao.upsert(metadata.copy(nextPageToken = nextToken))
                }
                nextToken != null
            }
        }
    }

    /**
     * Keeps only music videos. Lists like Liked can be mostly non-music, so when a page
     * yields too few songs the next pages are fetched too (bounded by [MAX_FILTER_PAGES]).
     */
    private suspend fun fetchMusicVideos(
        firstPage: ListResponseDto<VideoDto>,
        fetchPage: suspend (token: String) -> ListResponseDto<VideoDto>,
    ): Pair<List<VideoDto>, String?> {
        val music = firstPage.items.filter { it.isMusicVideo() }.toMutableList()
        var token = firstPage.nextPageToken
        var pages = 1
        while (music.size < MIN_MUSIC_ITEMS && token != null && pages < MAX_FILTER_PAGES) {
            val page = fetchPage(token)
            music += page.items.filter { it.isMusicVideo() }
            token = page.nextPageToken
            pages++
        }
        return music.distinctBy { it.id } to token
    }

    private fun List<SongEntity>.toCollectionItems(key: String, start: Int) = mapIndexed { index, song ->
        CollectionItemEntity(
            collectionKey = key,
            position = start + index,
            itemId = song.videoId,
            itemType = CollectionItemType.SONG,
        )
    }
}
