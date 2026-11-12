package com.example.orbitmusic.domain.usecase.home

import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Artist
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.ChannelRepository
import com.example.orbitmusic.domain.repository.PlaylistRepository
import com.example.orbitmusic.domain.repository.SongRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTrendingUseCase @Inject constructor(private val repository: SongRepository) {
    operator fun invoke(): Flow<List<Song>> = repository.observeTrending()
}

class ObserveMyChannelUseCase @Inject constructor(private val repository: ChannelRepository) {
    operator fun invoke(): Flow<Artist?> = repository.observeMyChannel()
}

/**
 * Refreshes everything the Home screen shows, in parallel. Each source honors its own
 * cache TTL unless [force] (pull-to-refresh). Returns the first error, if any; whatever
 * succeeded is already in the cache and visible.
 */
class RefreshHomeFeedUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val channelRepository: ChannelRepository,
) {
    suspend operator fun invoke(force: Boolean = false): Resource<Unit> = coroutineScope {
        val results = listOf(
            async { playlistRepository.refreshMyPlaylists(force) },
            async { songRepository.refreshLikedSongs(force) },
            async { songRepository.refreshTrending(force) },
            async { channelRepository.refreshMyChannel(force) },
        ).map { it.await() }
        firstError(results)?.let { Resource.Error(it) } ?: Resource.Success(Unit)
    }

    /**
     * Prefer the most actionable error when several sources fail. "Not found" is dropped:
     * it describes a missing list on YouTube's side (e.g. an account with no channel), not
     * something the user opened, so "no longer available" would only confuse on Home.
     */
    private fun firstError(results: List<Resource<Unit>>): AppError? {
        val errors = results.filterIsInstance<Resource.Error>().map { it.error }.filterNot { it == AppError.NotFound }
        return errors.firstOrNull { it == AppError.AuthorizationRequired }
            ?: errors.firstOrNull { it == AppError.QuotaExceeded }
            ?: errors.firstOrNull()
    }
}
