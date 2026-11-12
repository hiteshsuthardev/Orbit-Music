package com.example.orbitmusic.domain.usecase.song

import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSongUseCase @Inject constructor(private val repository: SongRepository) {
    operator fun invoke(videoId: String): Flow<Song?> = repository.observeSong(videoId)
}

/** Resolves ids to songs (cache first); used to restore the last session's queue. */
class GetSongsUseCase @Inject constructor(private val repository: SongRepository) {
    suspend operator fun invoke(videoIds: List<String>): Resource<List<Song>> = repository.getSongs(videoIds)
}

/** The player reports videos YouTube refuses to play so they are skipped next time. */
class MarkSongUnplayableUseCase @Inject constructor(private val repository: SongRepository) {
    suspend operator fun invoke(videoId: String) = repository.markUnplayable(videoId)
}

/** Like / unlike on YouTube. Requires the youtube scope (PermissionRequired otherwise). */
class RateSongUseCase @Inject constructor(private val repository: SongRepository) {
    suspend operator fun invoke(song: Song, liked: Boolean): Resource<Unit> = repository.rateSong(song, liked)
}
