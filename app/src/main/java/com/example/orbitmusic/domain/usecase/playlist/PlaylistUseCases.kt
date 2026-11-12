package com.example.orbitmusic.domain.usecase.playlist

import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistDetail
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import kotlin.random.Random

/** Playlist header + cached songs + paging flag. Emits null until metadata is cached. */
class ObservePlaylistDetailUseCase @Inject constructor(private val repository: PlaylistRepository) {
    operator fun invoke(playlistId: String): Flow<PlaylistDetail?> = combine(
        repository.observePlaylist(playlistId),
        repository.observePlaylistSongs(playlistId),
        repository.observePlaylistHasMore(playlistId),
    ) { playlist, songs, hasMore ->
        playlist?.let { PlaylistDetail(playlist = it, songs = songs, hasMore = hasMore) }
    }
}

class RefreshPlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: String, force: Boolean = false): Resource<Unit> =
        repository.refreshPlaylist(playlistId, force)
}

class LoadMorePlaylistItemsUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: String): Resource<Boolean> = repository.loadMorePlaylistItems(playlistId)
}

/**
 * Songs to enqueue for "Play all" / "Shuffle": every playable item of the playlist,
 * optionally shuffled. An empty result is reported as [AppError.EmptyPlaylist].
 */
class GetPlaylistQueueUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(
        playlistId: String,
        shuffle: Boolean,
        random: Random = Random.Default,
    ): Resource<List<Song>> = when (val all = repository.getAllPlaylistSongs(playlistId)) {
        is Resource.Success -> {
            val playable = all.data.filter { it.isPlayable }
            when {
                playable.isEmpty() -> Resource.Error(AppError.EmptyPlaylist)
                shuffle -> Resource.Success(playable.shuffled(random))
                else -> Resource.Success(playable)
            }
        }
        is Resource.Error -> all
        Resource.Loading -> Resource.Loading
    }
}

class CreatePlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(
        title: String,
        description: String = "",
        privacy: PlaylistPrivacy = PlaylistPrivacy.Private,
    ): Resource<Playlist> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return Resource.Error(AppError.Unknown("Playlist title is required"))
        return repository.createPlaylist(cleanTitle.take(MAX_TITLE_LENGTH), description, privacy)
    }

    private companion object {
        /** YouTube's playlist title limit. */
        const val MAX_TITLE_LENGTH = 150
    }
}

class AddSongToPlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: String, song: Song): Resource<Unit> =
        repository.addSongToPlaylist(playlistId, song)
}
