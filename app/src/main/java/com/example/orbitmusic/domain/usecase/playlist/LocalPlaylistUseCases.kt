package com.example.orbitmusic.domain.usecase.playlist

import com.example.orbitmusic.domain.ai.ResolveAiSongUseCase
import com.example.orbitmusic.domain.ai.SaveAiPlaylistResult
import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.PlaylistRepository
import javax.inject.Inject

/**
 * Saves an OrbitNova AI playlist in OrbitNova itself (on the device). Each suggestion is looked
 * up on YouTube (cached searches are free); unmatched songs are left out. If a lookup fails
 * (no internet, daily limit) the songs found so far are still saved.
 */
class SaveAiPlaylistToOrbitNovaUseCase @Inject constructor(
    private val repository: PlaylistRepository,
    private val resolve: ResolveAiSongUseCase,
) {
    suspend operator fun invoke(
        playlist: AiPlaylist,
        fallbackName: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SaveAiPlaylistResult {
        val total = playlist.songs.size
        val found = LinkedHashMap<String, Song>()
        onProgress(0, total)
        for ((index, suggestion) in playlist.songs.withIndex()) {
            when (val match = resolve(suggestion)) {
                is Resource.Success -> match.data?.let { found.putIfAbsent(it.videoId, it) }
                is Resource.Error -> {
                    if (found.isEmpty()) return SaveAiPlaylistResult.Failed(match.error, playlist = null, added = 0)
                    break
                }
                Resource.Loading -> Unit
            }
            onProgress(index + 1, total)
        }
        if (found.isEmpty()) return SaveAiPlaylistResult.Failed(AppError.EmptyPlaylist, playlist = null, added = 0)
        val saved = repository.createLocalPlaylist(
            title = playlist.name.ifBlank { fallbackName },
            description = playlist.description,
            songs = found.values.toList(),
        )
        return SaveAiPlaylistResult.Saved(saved, added = found.size, missing = total - found.size)
    }
}

/**
 * Copies an OrbitNova playlist to the user's YouTube account as a private playlist (no
 * searching needed: the songs are already known). Needs playlist edit access. When a
 * previous attempt stopped half way, pass its [resumeYouTubeId] and the songs already
 * [alreadyAdded] so it continues in the same YouTube playlist instead of making a new one.
 */
class SavePlaylistToYouTubeUseCase @Inject constructor(
    private val repository: PlaylistRepository,
    private val createPlaylist: CreatePlaylistUseCase,
) {
    suspend operator fun invoke(
        playlist: Playlist,
        resumeYouTubeId: String? = null,
        alreadyAdded: Int = 0,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SaveAiPlaylistResult {
        val songs = when (val all = repository.getAllPlaylistSongs(playlist.id)) {
            is Resource.Success -> all.data.filter { it.isPlayable }
            is Resource.Error -> return SaveAiPlaylistResult.Failed(all.error, playlist = null, added = 0)
            Resource.Loading -> return SaveAiPlaylistResult.Failed(AppError.Unknown(null), playlist = null, added = 0)
        }
        if (songs.isEmpty()) return SaveAiPlaylistResult.Failed(AppError.EmptyPlaylist, playlist = null, added = 0)

        val target = if (resumeYouTubeId != null) {
            playlist.copy(id = resumeYouTubeId, linkedYouTubeId = null)
        } else {
            when (val created = createPlaylist(playlist.title, playlist.description, PlaylistPrivacy.Private)) {
                is Resource.Success -> created.data
                is Resource.Error -> return SaveAiPlaylistResult.Failed(created.error, playlist = null, added = 0)
                Resource.Loading -> return SaveAiPlaylistResult.Failed(AppError.Unknown(null), playlist = null, added = 0)
            }
        }

        var added = alreadyAdded.coerceIn(0, songs.size)
        onProgress(added, songs.size)
        while (added < songs.size) {
            when (val result = repository.addSongToPlaylist(target.id, songs[added])) {
                is Resource.Error -> return SaveAiPlaylistResult.Failed(result.error, target, added)
                else -> added++
            }
            onProgress(added, songs.size)
        }
        repository.linkToYouTube(playlist.id, target.id)
        return SaveAiPlaylistResult.Saved(target, added = added, missing = 0)
    }
}

class DeleteLocalPlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: String) = repository.deleteLocalPlaylist(playlistId)
}
