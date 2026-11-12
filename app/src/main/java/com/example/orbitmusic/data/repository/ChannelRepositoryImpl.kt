package com.example.orbitmusic.data.repository

import com.example.orbitmusic.data.local.db.OrbitDatabase
import com.example.orbitmusic.data.local.db.entity.CacheMetadataEntity
import com.example.orbitmusic.data.local.db.entity.CollectionKeys
import com.example.orbitmusic.data.mapper.toArtist
import com.example.orbitmusic.data.mapper.toEntity
import com.example.orbitmusic.data.remote.youtube.ApiErrorMapper
import com.example.orbitmusic.data.remote.youtube.YouTubeApi
import com.example.orbitmusic.data.remote.youtube.safeCall
import com.example.orbitmusic.domain.model.Artist
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.repository.ChannelRepository
import com.example.orbitmusic.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val api: YouTubeApi,
    private val db: OrbitDatabase,
    private val freshness: CacheFreshness,
    private val errorMapper: ApiErrorMapper,
    private val timeProvider: TimeProvider,
) : ChannelRepository {

    private val channelDao = db.channelDao()
    private val metadataDao = db.cacheMetadataDao()

    override fun observeMyChannel(): Flow<Artist?> = channelDao.observeMine().map { it?.toArtist() }

    override suspend fun refreshMyChannel(force: Boolean): Resource<Unit> {
        if (!force && freshness.isFresh(CollectionKeys.MY_CHANNEL, CachePolicy.CHANNEL)) return Resource.Success(Unit)
        return errorMapper.safeCall {
            // Accounts without a YouTube channel get an empty list; that's not an error.
            val channel = api.myChannel().items.firstOrNull() ?: return@safeCall
            val now = timeProvider.currentTimeMillis()
            channelDao.upsert(channel.toEntity(now, isMine = true))
            metadataDao.upsert(CacheMetadataEntity(CollectionKeys.MY_CHANNEL, now, null))
        }
    }
}
