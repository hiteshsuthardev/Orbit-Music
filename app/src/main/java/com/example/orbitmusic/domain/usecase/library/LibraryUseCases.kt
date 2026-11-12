package com.example.orbitmusic.domain.usecase.library

import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.PlaylistRepository
import com.example.orbitmusic.domain.repository.SongRepository
import com.example.orbitmusic.domain.repository.UserLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveMyPlaylistsUseCase @Inject constructor(private val repository: PlaylistRepository) {
    operator fun invoke(): Flow<List<Playlist>> = repository.observeMyPlaylists()
}

class RefreshMyPlaylistsUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(force: Boolean = false): Resource<Unit> = repository.refreshMyPlaylists(force)
}

class ObserveLikedSongsUseCase @Inject constructor(private val repository: SongRepository) {
    operator fun invoke(): Flow<List<Song>> = repository.observeLikedSongs()
    fun hasMore(): Flow<Boolean> = repository.observeLikedHasMore()
}

class RefreshLikedSongsUseCase @Inject constructor(private val repository: SongRepository) {
    suspend operator fun invoke(force: Boolean = false): Resource<Unit> = repository.refreshLikedSongs(force)
}

class LoadMoreLikedSongsUseCase @Inject constructor(private val repository: SongRepository) {
    suspend operator fun invoke(): Resource<Boolean> = repository.loadMoreLikedSongs()
}

class ObserveFavoritesUseCase @Inject constructor(private val repository: UserLibraryRepository) {
    operator fun invoke(): Flow<List<Song>> = repository.observeFavorites()
    fun ids(): Flow<Set<String>> = repository.observeFavoriteIds()
}

class ToggleFavoriteUseCase @Inject constructor(private val repository: UserLibraryRepository) {
    /** Returns the new favorite state. */
    suspend operator fun invoke(song: Song): Boolean {
        val favorite = !repository.isFavorite(song.videoId)
        repository.setFavorite(song, favorite)
        return favorite
    }
}

class ObserveRecentlyPlayedUseCase @Inject constructor(private val repository: UserLibraryRepository) {
    operator fun invoke(limit: Int = 30): Flow<List<Song>> = repository.observeRecentlyPlayed(limit)
}

class RecordPlayUseCase @Inject constructor(private val repository: UserLibraryRepository) {
    suspend operator fun invoke(song: Song) = repository.recordPlay(song)
}

class ClearHistoryUseCase @Inject constructor(private val repository: UserLibraryRepository) {
    suspend operator fun invoke() = repository.clearHistory()
}

/**
 * "Quick picks": the user's most played songs, topped up with liked songs and then
 * trending tracks when there is little history yet. Quota-free (cache only).
 */
class ObserveQuickPicksUseCase @Inject constructor(
    private val libraryRepository: UserLibraryRepository,
    private val songRepository: SongRepository,
) {
    operator fun invoke(limit: Int = QUICK_PICKS_SIZE): Flow<List<Song>> = combine(
        libraryRepository.observeMostPlayed(limit),
        songRepository.observeLikedSongs(),
        songRepository.observeTrending(),
    ) { mostPlayed, liked, trending -> buildQuickPicks(mostPlayed, liked, trending, limit) }

    companion object {
        const val QUICK_PICKS_SIZE = 12
    }
}

/** Pure merge rule, unit-tested. */
fun buildQuickPicks(
    mostPlayed: List<Song>,
    liked: List<Song>,
    trending: List<Song>,
    limit: Int,
): List<Song> = (mostPlayed + liked + trending)
    .filter { it.isPlayable }
    .distinctBy { it.videoId }
    .take(limit)
