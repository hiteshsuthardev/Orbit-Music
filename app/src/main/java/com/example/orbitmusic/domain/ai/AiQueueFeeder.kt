package com.example.orbitmusic.domain.ai

import com.example.orbitmusic.di.ApplicationScope
import com.example.orbitmusic.domain.model.AiSongSuggestion
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.QueueManager
import com.example.orbitmusic.domain.repository.SearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the YouTube music video for an AI suggestion with one song search. Searches cost 100
 * quota units, but go through the 24h search cache and the music-only filter. Calls the
 * repository directly so AI lookups don't show up in the user's recent searches.
 */
class ResolveAiSongUseCase @Inject constructor(private val search: SearchRepository) {

    suspend operator fun invoke(suggestion: AiSongSuggestion): Resource<Song?> {
        val query = "${suggestion.title} ${suggestion.artist}"
        return when (val result = search.search(query, SearchFilter.Songs)) {
            is Resource.Success -> {
                val playable = result.data.songs.filter { it.isPlayable }
                val wanted = normalize(suggestion.title)
                // Prefer a result whose title contains the suggested title; else the top hit.
                Resource.Success(playable.firstOrNull { normalize(it.title).contains(wanted) } ?: playable.firstOrNull())
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Success(null)
        }
    }

    private fun normalize(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

/**
 * Plays an AI playlist while looking songs up only as they are needed: the chosen song is
 * resolved and played at once, and each following song is resolved just when the queue is
 * about to run out (one song ahead), so quota is spent only on songs actually reached.
 * Songs YouTube can't match are skipped. Feeding stops when the user plays something else.
 */
@Singleton
class AiQueueFeeder @Inject constructor(
    private val queue: QueueManager,
    private val resolve: ResolveAiSongUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var feeding: Job? = null

    /** Guards [pending] and [ownVideoIds], used by both [play] and the feeding coroutine. */
    private val lock = Mutex()
    private val pending = ArrayDeque<AiSongSuggestion>()
    private val ownVideoIds = mutableSetOf<String>()

    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 1)

    /** Lookup failures while feeding later songs (e.g. quota exceeded), for the UI to show. */
    val errors: SharedFlow<AppError> = _errors.asSharedFlow()

    /**
     * Starts playing [songs] from [startIndex] (the rest of the list in [shuffle]d order if
     * asked). Returns an error if not even the first song could be found.
     */
    suspend fun play(songs: List<AiSongSuggestion>, startIndex: Int = 0, shuffle: Boolean = false): Resource<Unit> {
        feeding?.cancel()
        val fromStart = songs.drop(startIndex.coerceIn(0, songs.size))
        val firstResult = lock.withLock {
            pending.clear()
            ownVideoIds.clear()
            pending += if (shuffle) fromStart.shuffled() else fromStart
            resolveNext()
        }
        val first = when (val next = firstResult) {
            is Resource.Success -> next.data ?: return Resource.Error(AppError.NotFound)
            is Resource.Error -> return next
            Resource.Loading -> return Resource.Error(AppError.NotFound)
        }
        lock.withLock { ownVideoIds += first.videoId }
        queue.setQueue(listOf(first))
        feeding = scope.launch { feed() }
        return Resource.Success(Unit)
    }

    private suspend fun feed() {
        queue.state.collect { state ->
            lock.withLock {
                val current = state.current?.song?.videoId
                // The user started something else: this AI playlist is no longer playing.
                if (current != null && current !in ownVideoIds) {
                    pending.clear()
                    return@withLock
                }
                if (state.upNext.isEmpty() && pending.isNotEmpty()) {
                    when (val next = resolveNext()) {
                        is Resource.Success -> next.data?.let { song ->
                            ownVideoIds += song.videoId
                            queue.addToQueue(song)
                        }
                        is Resource.Error -> {
                            pending.clear()
                            _errors.tryEmit(next.error)
                        }
                        Resource.Loading -> Unit
                    }
                }
            }
        }
    }

    /** Resolves pending suggestions until one matches; Success(null) when none is left. Call under [lock]. */
    private suspend fun resolveNext(): Resource<Song?> {
        while (pending.isNotEmpty()) {
            val suggestion = pending.removeFirst()
            when (val result = resolve(suggestion)) {
                is Resource.Success -> result.data?.let { return Resource.Success(it) }
                is Resource.Error -> return result
                Resource.Loading -> Unit
            }
        }
        return Resource.Success(null)
    }
}
