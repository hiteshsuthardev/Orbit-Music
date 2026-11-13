package com.example.orbitmusic.ui.screens.songactions

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.usecase.auth.CompleteAuthorizationUseCase
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.domain.usecase.auth.RequestPlaylistEditAccessUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveFavoritesUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveMyPlaylistsUseCase
import com.example.orbitmusic.domain.usecase.library.RefreshMyPlaylistsUseCase
import com.example.orbitmusic.domain.usecase.library.ToggleFavoriteUseCase
import com.example.orbitmusic.domain.usecase.player.AddToQueueUseCase
import com.example.orbitmusic.domain.usecase.player.PlayNextUseCase
import com.example.orbitmusic.domain.usecase.player.PlaySongsUseCase
import com.example.orbitmusic.domain.usecase.playlist.AddSongToPlaylistUseCase
import com.example.orbitmusic.ui.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongActionsUiState(
    /** Song whose three-dot menu is open. */
    val menuSong: Song? = null,
    /** Song being added to a playlist (the picker sheet is open). */
    val addToPlaylistSong: Song? = null,
    val favoriteIds: Set<String> = emptySet(),
    val myPlaylists: List<Playlist> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val addingToPlaylistId: String? = null,
)

sealed interface SongActionsEvent {
    data class Message(@StringRes val messageRes: Int, val formatArg: String? = null) : SongActionsEvent
    data class LaunchConsent(val pendingIntent: PendingIntent) : SongActionsEvent
    data class Share(val song: Song) : SongActionsEvent
}

/**
 * Cross-screen song actions (menu, queueing, favorites, share, add to playlist). Scoped to
 * the main shell so every tab shares one menu sheet and one snackbar.
 */
@HiltViewModel
class SongActionsViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    observeFavorites: ObserveFavoritesUseCase,
    observeMyPlaylists: ObserveMyPlaylistsUseCase,
    private val refreshMyPlaylists: RefreshMyPlaylistsUseCase,
    private val playSongs: PlaySongsUseCase,
    private val playNext: PlayNextUseCase,
    private val addToQueue: AddToQueueUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val addSongToPlaylist: AddSongToPlaylistUseCase,
    private val requestPlaylistEditAccess: RequestPlaylistEditAccessUseCase,
    private val completeAuthorization: CompleteAuthorizationUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(SongActionsUiState())
    private val authState = observeAuthState()

    private val _events = Channel<SongActionsEvent>(Channel.BUFFERED)
    val events: Flow<SongActionsEvent> = _events.receiveAsFlow()

    /** Song waiting for the playlist-edit consent screen to come back. */
    private var pendingAddSong: Song? = null

    val uiState: StateFlow<SongActionsUiState> = combine(
        local,
        observeFavorites.ids(),
        observeMyPlaylists(),
    ) { state, favorites, playlists ->
        state.copy(favoriteIds = favorites, myPlaylists = playlists)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongActionsUiState())

    // Playback ---------------------------------------------------------------------------

    fun play(songs: List<Song>, startSong: Song?, shuffle: Boolean = false) {
        if (!playSongs(songs, startSong, shuffle)) message(R.string.error_video_unavailable)
    }

    // Menu -------------------------------------------------------------------------------

    fun showMenu(song: Song) = local.update { it.copy(menuSong = song) }

    fun dismissMenu() = local.update { it.copy(menuSong = null) }

    fun onPlayNext(song: Song) {
        dismissMenu()
        message(if (playNext(song)) R.string.message_playing_next else R.string.error_video_unavailable)
    }

    fun onAddToQueue(song: Song) {
        dismissMenu()
        message(if (addToQueue(song)) R.string.message_added_to_queue else R.string.error_video_unavailable)
    }

    fun onToggleFavorite(song: Song) {
        dismissMenu()
        viewModelScope.launch {
            val favorite = toggleFavorite(song)
            message(if (favorite) R.string.message_added_to_favorites else R.string.message_removed_from_favorites)
        }
    }

    fun onShare(song: Song) {
        dismissMenu()
        viewModelScope.launch { _events.send(SongActionsEvent.Share(song)) }
    }

    // Add to playlist (needs the youtube write scope) ------------------------------------

    fun onAddToPlaylist(song: Song) {
        dismissMenu()
        viewModelScope.launch {
            // Silent when the write scope is still granted; returns a token that has it.
            when (val outcome = requestPlaylistEditAccess()) {
                AuthorizationOutcome.Granted -> openPlaylistPicker(song)
                is AuthorizationOutcome.NeedsConsent -> {
                    pendingAddSong = song
                    _events.send(SongActionsEvent.LaunchConsent(outcome.pendingIntent))
                }
                is AuthorizationOutcome.Failed -> error(outcome.error)
            }
        }
    }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        val song = pendingAddSong ?: return
        pendingAddSong = null
        if (resultCode != Activity.RESULT_OK) {
            message(R.string.error_permission_required)
            return
        }
        viewModelScope.launch {
            when (val result = completeAuthorization(data)) {
                is Resource.Success -> openPlaylistPicker(song)
                is Resource.Error -> error(result.error)
                Resource.Loading -> Unit
            }
        }
    }

    fun onPlaylistChosen(playlist: Playlist) {
        val song = local.value.addToPlaylistSong ?: return
        local.update { it.copy(addingToPlaylistId = playlist.id) }
        viewModelScope.launch {
            val result = addSongToPlaylist(playlist.id, song)
            local.update { it.copy(addingToPlaylistId = null, addToPlaylistSong = null) }
            when (result) {
                is Resource.Success -> message(R.string.message_added_to_playlist, playlist.title)
                is Resource.Error -> error(result.error)
                Resource.Loading -> Unit
            }
        }
    }

    fun dismissPlaylistPicker() {
        local.update { it.copy(addToPlaylistSong = null, addingToPlaylistId = null) }
    }

    private fun openPlaylistPicker(song: Song) {
        local.update { it.copy(addToPlaylistSong = song, isLoadingPlaylists = true) }
        viewModelScope.launch {
            refreshMyPlaylists(force = false)
            local.update { it.copy(isLoadingPlaylists = false) }
        }
    }

    private fun message(@StringRes res: Int, arg: String? = null) {
        viewModelScope.launch { _events.send(SongActionsEvent.Message(res, arg)) }
    }

    private fun error(error: AppError) = message(error.messageRes())
}
