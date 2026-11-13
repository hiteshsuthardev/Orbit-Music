package com.example.orbitmusic.domain.ai

import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.usecase.playlist.AddSongToPlaylistUseCase
import com.example.orbitmusic.domain.usecase.playlist.CreatePlaylistUseCase
import javax.inject.Inject

/** How saving an OrbitNova AI playlist to YouTube ended. */
sealed interface SaveAiPlaylistResult {
    /** Created; [missing] suggestions had no match on YouTube and were left out. */
    data class Saved(val playlist: Playlist, val added: Int, val missing: Int) : SaveAiPlaylistResult

    /** Stopped by [error]; [playlist] is set if it was already created (with [added] songs). */
    data class Failed(val error: AppError, val playlist: Playlist?, val added: Int) : SaveAiPlaylistResult
}

/**
 * Saves an OrbitNova AI playlist to the user's YouTube account as a private playlist: creates it,
 * then looks each suggestion up on YouTube and adds the match. Needs playlist edit access
 * (the caller asks for it first). Unmatched songs are skipped; any other error stops.
 */
class SaveAiPlaylistUseCase @Inject constructor(
    private val createPlaylist: CreatePlaylistUseCase,
    private val addSong: AddSongToPlaylistUseCase,
    private val resolve: ResolveAiSongUseCase,
) {
    suspend operator fun invoke(
        playlist: AiPlaylist,
        fallbackName: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SaveAiPlaylistResult {
        val description = listOf(playlist.description.trim(), MADE_WITH).filter { it.isNotEmpty() }.joinToString("\n\n")
        val created = when (
            val result = createPlaylist(playlist.name.ifBlank { fallbackName }, description, PlaylistPrivacy.Private)
        ) {
            is Resource.Success -> result.data
            is Resource.Error -> return SaveAiPlaylistResult.Failed(result.error, playlist = null, added = 0)
            Resource.Loading -> return SaveAiPlaylistResult.Failed(AppError.Unknown(null), playlist = null, added = 0)
        }

        val total = playlist.songs.size
        val addedIds = mutableSetOf<String>()
        var missing = 0
        onProgress(0, total)
        playlist.songs.forEachIndexed { index, suggestion ->
            when (val match = resolve(suggestion)) {
                is Resource.Error -> return SaveAiPlaylistResult.Failed(match.error, created, addedIds.size)
                is Resource.Success -> {
                    val song = match.data
                    when {
                        song == null -> missing++
                        song.videoId in addedIds -> Unit // The same video for two suggestions.
                        else -> when (val added = addSong(created.id, song)) {
                            is Resource.Error -> return SaveAiPlaylistResult.Failed(added.error, created, addedIds.size)
                            else -> addedIds += song.videoId
                        }
                    }
                }
                Resource.Loading -> missing++
            }
            onProgress(index + 1, total)
        }
        return SaveAiPlaylistResult.Saved(created, added = addedIds.size, missing = missing)
    }

    private companion object {
        const val MADE_WITH = "Made with OrbitNova AI"
    }
}
