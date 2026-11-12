package com.example.orbitmusic.data.repository

import com.example.orbitmusic.data.local.db.dao.CacheMetadataDao
import com.example.orbitmusic.data.local.db.entity.SongEntity
import com.example.orbitmusic.data.mapper.isMusicVideo
import com.example.orbitmusic.data.mapper.toSongEntity
import com.example.orbitmusic.data.remote.youtube.YouTubeApi
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * How long each cached collection is considered fresh. Search is cached longest because
 * one search page costs 100 of the 10,000 daily quota units.
 */
object CachePolicy {
    val MY_PLAYLISTS: Duration = 6.hours
    val PLAYLIST_ITEMS: Duration = 6.hours
    val LIKED: Duration = 30.minutes
    val TRENDING: Duration = 6.hours
    val SEARCH: Duration = 24.hours
    val CHANNEL: Duration = 24.hours

    /** Safety cap when paging through every page of a list. */
    const val MAX_PAGES = 20
}

/** A domain error raised inside data-layer code; mapped by ApiErrorMapper. */
class AppErrorException(val error: AppError) : Exception(error.toString())

@Singleton
class CacheFreshness @Inject constructor(
    private val metadataDao: CacheMetadataDao,
    private val timeProvider: TimeProvider,
) {
    suspend fun isFresh(key: String, ttl: Duration): Boolean {
        val metadata = metadataDao.get(key) ?: return false
        return timeProvider.currentTimeMillis() - metadata.fetchedAt < ttl.inWholeMilliseconds
    }
}

/** One mutex per cache key so concurrent refreshes of the same list don't interleave. */
class KeyedMutex {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(key: String, block: suspend () -> T): T =
        mutexes.getOrPut(key) { Mutex() }.withLock { block() }
}

/**
 * Result of [VideoDetailsFetcher.fetch]: full song data for music videos, and the ids that
 * exist but are not music videos (other categories, Shorts, live streams). Ids in neither
 * were deleted or are private.
 */
class VideoDetails(val music: Map<String, SongEntity>, val notMusic: Set<String>)

/**
 * Fetches full video details (duration, embeddability, category) in batches of 50 ids per
 * call (1 quota unit each) and sorts them into music and non-music videos.
 */
@Singleton
class VideoDetailsFetcher @Inject constructor(
    private val api: YouTubeApi,
) {
    suspend fun fetch(videoIds: List<String>, now: Long): VideoDetails {
        val videos = videoIds.distinct()
            .chunked(YouTubeApi.MAX_PAGE_SIZE)
            .flatMap { chunk -> api.videosById(chunk.joinToString(",")).items }
        val (music, other) = videos.partition { it.isMusicVideo() }
        return VideoDetails(
            music = music.map { it.toSongEntity(now) }.associateBy { it.videoId },
            notMusic = other.map { it.id }.toSet(),
        )
    }
}

/** Pages fetched at most per refresh / load-more while skipping non-music videos. */
const val MAX_FILTER_PAGES = 4

/** Keep fetching while fewer music videos than this were found (and more pages exist). */
const val MIN_MUSIC_ITEMS = 10

/** Escapes LIKE wildcards so user input is matched literally (ESCAPE '\'). */
fun String.escapeForLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
