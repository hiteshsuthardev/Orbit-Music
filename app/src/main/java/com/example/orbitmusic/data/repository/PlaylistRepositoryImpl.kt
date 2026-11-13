package com.example.orbitmusic.data.repository

import androidx.room.withTransaction
import com.example.orbitmusic.data.local.db.OrbitDatabase
import com.example.orbitmusic.data.local.db.entity.CacheMetadataEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemType
import com.example.orbitmusic.data.local.db.entity.CollectionKeys
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistEntity
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistRow
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistSongEntity
import com.example.orbitmusic.data.local.db.entity.SongEntity
import com.example.orbitmusic.data.mapper.toDomain
import com.example.orbitmusic.data.mapper.toEntity
import com.example.orbitmusic.data.mapper.toPlaylists
import com.example.orbitmusic.data.mapper.toSongs
import com.example.orbitmusic.data.mapper.toUnavailableSongEntity
import com.example.orbitmusic.data.mapper.videoId
import com.example.orbitmusic.data.remote.youtube.ApiErrorMapper
import com.example.orbitmusic.data.remote.youtube.YouTubeApi
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistInsertRequestDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistInsertSnippetDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistInsertStatusDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemInsertRequestDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemInsertSnippetDto
import com.example.orbitmusic.data.remote.youtube.dto.ResourceIdDto
import com.example.orbitmusic.data.remote.youtube.safeCall
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.PlaylistRepository
import com.example.orbitmusic.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val api: YouTubeApi,
    private val db: OrbitDatabase,
    private val videoDetails: VideoDetailsFetcher,
    private val freshness: CacheFreshness,
    private val errorMapper: ApiErrorMapper,
    private val timeProvider: TimeProvider,
) : PlaylistRepository {

    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()
    private val collectionDao = db.collectionDao()
    private val metadataDao = db.cacheMetadataDao()
    private val localDao = db.localPlaylistDao()
    private val locks = KeyedMutex()

    // My playlists ---------------------------------------------------------------------

    /** OrbitNova playlists (newest first), then the YouTube ones. */
    override fun observeMyPlaylists(): Flow<List<Playlist>> = combine(
        localDao.observeAll(),
        collectionDao.observePlaylists(CollectionKeys.MY_PLAYLISTS),
    ) { local, youTube -> local.map { it.toDomain() } + youTube.toPlaylists() }

    override suspend fun refreshMyPlaylists(force: Boolean): Resource<Unit> =
        locks.withLock(CollectionKeys.MY_PLAYLISTS) {
            if (!force && freshness.isFresh(CollectionKeys.MY_PLAYLISTS, CachePolicy.MY_PLAYLISTS)) {
                Resource.Success(Unit)
            } else {
                errorMapper.safeCall {
                    val playlists = mutableListOf<PlaylistDto>()
                    var token: String? = null
                    var pages = 0
                    do {
                        val page = try {
                            api.myPlaylists(pageToken = token)
                        } catch (e: HttpException) {
                            // 404 (channelNotFound): the Google account has never created a
                            // YouTube channel, so it simply has no playlists. Not an error.
                            if (e.code() == HTTP_NOT_FOUND) null else throw e
                        }
                        playlists += page?.items.orEmpty()
                        token = page?.nextPageToken
                        pages++
                    } while (token != null && pages < CachePolicy.MAX_PAGES)

                    val now = timeProvider.currentTimeMillis()
                    db.withTransaction {
                        playlistDao.upsertAll(playlists.map { it.toEntity(now) })
                        collectionDao.replace(
                            CollectionKeys.MY_PLAYLISTS,
                            playlists.mapIndexed { index, playlist ->
                                CollectionItemEntity(
                                    collectionKey = CollectionKeys.MY_PLAYLISTS,
                                    position = index,
                                    itemId = playlist.id,
                                    itemType = CollectionItemType.PLAYLIST,
                                )
                            },
                        )
                        metadataDao.upsert(CacheMetadataEntity(CollectionKeys.MY_PLAYLISTS, now, null))
                    }
                }
            }
        }

    // Playlist detail ------------------------------------------------------------------

    override fun observePlaylist(playlistId: String): Flow<Playlist?> =
        if (Playlist.isLocalId(playlistId)) {
            localDao.observeById(playlistId).map { it?.toDomain() }
        } else {
            playlistDao.observeById(playlistId).map { it?.toDomain() }
        }

    override fun observePlaylistSongs(playlistId: String): Flow<List<Song>> =
        if (Playlist.isLocalId(playlistId)) {
            localDao.observeSongs(playlistId).map { it.toSongs() }
        } else {
            collectionDao.observeSongs(CollectionKeys.playlist(playlistId)).map { it.toSongs() }
        }

    override fun observePlaylistHasMore(playlistId: String): Flow<Boolean> =
        if (Playlist.isLocalId(playlistId)) {
            flowOf(false)
        } else {
            metadataDao.observe(CollectionKeys.playlist(playlistId))
                .map { it?.nextPageToken != null }
                .distinctUntilChanged()
        }

    override suspend fun refreshPlaylist(playlistId: String, force: Boolean): Resource<Unit> {
        // OrbitNova playlists live on the device: nothing to fetch.
        if (Playlist.isLocalId(playlistId)) {
            return if (localDao.getById(playlistId) != null) Resource.Success(Unit) else Resource.Error(AppError.NotFound)
        }
        val key = CollectionKeys.playlist(playlistId)
        return locks.withLock(key) {
            val cached = playlistDao.getById(playlistId) != null
            if (!force && cached && freshness.isFresh(key, CachePolicy.PLAYLIST_ITEMS)) {
                Resource.Success(Unit)
            } else {
                errorMapper.safeCall {
                    val metadata = api.playlistsById(playlistId).items.firstOrNull()
                        ?: throw AppErrorException(AppError.NotFound)
                    val now = timeProvider.currentTimeMillis()
                    val (resolved, nextToken) = fetchMusicItems(playlistId, pageToken = null, now)
                    db.withTransaction {
                        playlistDao.upsertAll(listOf(metadata.toEntity(now)))
                        songDao.upsertAll(resolved.map { it.second })
                        collectionDao.replace(key, resolved.toCollectionItems(key, startPosition = 0))
                        metadataDao.upsert(CacheMetadataEntity(key, now, nextToken))
                    }
                }
            }
        }
    }

    override suspend fun loadMorePlaylistItems(playlistId: String): Resource<Boolean> {
        if (Playlist.isLocalId(playlistId)) return Resource.Success(false)
        val key = CollectionKeys.playlist(playlistId)
        return locks.withLock(key) { loadNextPageLocked(playlistId, key) }
    }

    private suspend fun loadNextPageLocked(playlistId: String, key: String): Resource<Boolean> {
        val metadata = metadataDao.get(key)
        val token = metadata?.nextPageToken ?: return Resource.Success(false)
        return errorMapper.safeCall {
            val now = timeProvider.currentTimeMillis()
            val (resolved, nextToken) = fetchMusicItems(playlistId, pageToken = token, now)
            db.withTransaction {
                val start = collectionDao.nextPosition(key)
                songDao.upsertAll(resolved.map { it.second })
                collectionDao.insertAll(resolved.toCollectionItems(key, start))
                metadataDao.upsert(metadata.copy(nextPageToken = nextToken))
            }
            nextToken != null
        }
    }

    override suspend fun getAllPlaylistSongs(playlistId: String, maxItems: Int): Resource<List<Song>> {
        if (Playlist.isLocalId(playlistId)) {
            return Resource.Success(localDao.getSongs(playlistId).take(maxItems).toSongs())
        }
        val key = CollectionKeys.playlist(playlistId)
        val refresh = refreshPlaylist(playlistId, force = false)
        if (refresh is Resource.Error && collectionDao.count(key) == 0) return refresh

        var pages = 0
        while (collectionDao.count(key) < maxItems && pages < CachePolicy.MAX_PAGES) {
            when (val more = loadMorePlaylistItems(playlistId)) {
                is Resource.Success -> if (!more.data) break
                // Offline / quota: play what is cached instead of failing.
                is Resource.Error, Resource.Loading -> break
            }
            pages++
        }
        return Resource.Success(collectionDao.getSongs(key).take(maxItems).toSongs())
    }

    // Writes (youtube scope) -----------------------------------------------------------

    override suspend fun createPlaylist(
        title: String,
        description: String,
        privacy: PlaylistPrivacy,
    ): Resource<Playlist> {
        val created = errorMapper.safeCall {
            api.createPlaylist(
                PlaylistInsertRequestDto(
                    snippet = PlaylistInsertSnippetDto(title = title.trim(), description = description.trim()),
                    status = PlaylistInsertStatusDto(privacyStatus = privacy.apiValue),
                ),
            )
        }
        return when (created) {
            is Resource.Success -> {
                val entity = created.data.toEntity(timeProvider.currentTimeMillis())
                playlistDao.upsertAll(listOf(entity))
                refreshMyPlaylists(force = true)
                Resource.Success(entity.toDomain())
            }
            is Resource.Error -> created
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun addSongToPlaylist(playlistId: String, song: Song): Resource<Unit> {
        if (Playlist.isLocalId(playlistId)) return addSongToLocalPlaylist(playlistId, song)
        val result = errorMapper.safeCall {
            api.addPlaylistItem(
                PlaylistItemInsertRequestDto(
                    snippet = PlaylistItemInsertSnippetDto(
                        playlistId = playlistId,
                        resourceId = ResourceIdDto(kind = "youtube#video", videoId = song.videoId),
                    ),
                ),
            )
        }
        if (result is Resource.Success) {
            val now = timeProvider.currentTimeMillis()
            db.withTransaction {
                if (songDao.getById(song.videoId) == null) songDao.upsertAll(listOf(song.toEntity(now)))
                playlistDao.getById(playlistId)?.let { playlistDao.upsertAll(listOf(it.copy(itemCount = it.itemCount + 1))) }
                // Next open of the playlist refetches its items.
                metadataDao.delete(CollectionKeys.playlist(playlistId))
            }
        }
        return when (result) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    // OrbitNova (local) playlists -------------------------------------------------------

    override suspend fun createLocalPlaylist(title: String, description: String, songs: List<Song>): Playlist {
        val now = timeProvider.currentTimeMillis()
        val id = Playlist.LOCAL_ID_PREFIX + UUID.randomUUID()
        val unique = songs.distinctBy { it.videoId }
        db.withTransaction {
            songDao.upsertAll(unique.map { it.toEntity(now) })
            localDao.upsert(
                LocalPlaylistEntity(
                    id = id,
                    title = title.trim(),
                    description = description.trim(),
                    createdAt = now,
                    updatedAt = now,
                    youTubePlaylistId = null,
                ),
            )
            localDao.insertSongs(unique.mapIndexed { index, song -> LocalPlaylistSongEntity(id, index, song.videoId) })
        }
        return LocalPlaylistRow(localDao.getById(id)!!, unique.size, unique.firstOrNull()?.thumbnailUrl).toDomain()
    }

    private suspend fun addSongToLocalPlaylist(playlistId: String, song: Song): Resource<Unit> {
        if (localDao.getById(playlistId) == null) return Resource.Error(AppError.NotFound)
        val now = timeProvider.currentTimeMillis()
        db.withTransaction {
            if (songDao.getById(song.videoId) == null) songDao.upsertAll(listOf(song.toEntity(now)))
            if (!localDao.contains(playlistId, song.videoId)) {
                localDao.insertSongs(listOf(LocalPlaylistSongEntity(playlistId, localDao.nextPosition(playlistId), song.videoId)))
                localDao.touch(playlistId, now)
            }
        }
        return Resource.Success(Unit)
    }

    override suspend fun linkToYouTube(localId: String, youTubeId: String) {
        localDao.link(localId, youTubeId, timeProvider.currentTimeMillis())
    }

    override suspend fun deleteLocalPlaylist(localId: String) {
        if (Playlist.isLocalId(localId)) localDao.delete(localId)
    }

    private fun LocalPlaylistRow.toDomain() = Playlist(
        id = playlist.id,
        title = playlist.title,
        description = playlist.description,
        thumbnailUrl = thumbnailUrl,
        itemCount = itemCount,
        ownerName = LOCAL_OWNER,
        linkedYouTubeId = playlist.youTubePlaylistId,
    )

    // Helpers ----------------------------------------------------------------------------

    /**
     * Fetches playlist pages from [pageToken] until enough music videos are found (playlists
     * can be mostly non-music), bounded by [MAX_FILTER_PAGES]. Returns them and the next token.
     */
    private suspend fun fetchMusicItems(
        playlistId: String,
        pageToken: String?,
        now: Long,
    ): Pair<List<Pair<PlaylistItemDto, SongEntity>>, String?> {
        val resolved = mutableListOf<Pair<PlaylistItemDto, SongEntity>>()
        var token = pageToken
        var pages = 0
        do {
            val page = api.playlistItems(playlistId, pageToken = token)
            resolved += resolveItems(page.items, now)
            token = page.nextPageToken
            pages++
        } while (resolved.size < MIN_MUSIC_ITEMS && token != null && pages < MAX_FILTER_PAGES)
        return resolved to token
    }

    /**
     * Pairs each playlist item with full song data, or an unplayable placeholder when the
     * video was deleted or made private. Non-music videos (other categories, Shorts, live
     * streams) are left out entirely.
     */
    private suspend fun resolveItems(
        items: List<PlaylistItemDto>,
        now: Long,
    ): List<Pair<PlaylistItemDto, SongEntity>> {
        val details = videoDetails.fetch(items.mapNotNull { it.videoId() }, now)
        return items.mapNotNull { item ->
            val videoId = item.videoId() ?: return@mapNotNull null
            if (videoId in details.notMusic) return@mapNotNull null
            val song = details.music[videoId] ?: item.toUnavailableSongEntity(now) ?: return@mapNotNull null
            item to song
        }
    }

    private fun List<Pair<PlaylistItemDto, SongEntity>>.toCollectionItems(
        key: String,
        startPosition: Int,
    ): List<CollectionItemEntity> = mapIndexed { index, (item, song) ->
        CollectionItemEntity(
            collectionKey = key,
            position = startPosition + index,
            itemId = song.videoId,
            itemType = CollectionItemType.SONG,
            playlistItemId = item.id,
        )
    }
}

private const val HTTP_NOT_FOUND = 404

/** Shown as the "owner" of playlists stored in the app. */
private const val LOCAL_OWNER = "OrbitNova"
