package com.example.orbitmusic.ui.screens.playlist

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.orbitmusic.domain.ai.SaveAiPlaylistResult
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.PlaylistDetail
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.usecase.auth.CompleteAuthorizationUseCase
import com.example.orbitmusic.domain.usecase.auth.RequestPlaylistEditAccessUseCase
import com.example.orbitmusic.domain.usecase.player.PlaySongsUseCase
import com.example.orbitmusic.domain.usecase.playlist.DeleteLocalPlaylistUseCase
import com.example.orbitmusic.domain.usecase.playlist.GetPlaylistQueueUseCase
import com.example.orbitmusic.domain.usecase.playlist.LoadMorePlaylistItemsUseCase
import com.example.orbitmusic.domain.usecase.playlist.ObservePlaylistDetailUseCase
import com.example.orbitmusic.domain.usecase.playlist.RefreshPlaylistUseCase
import com.example.orbitmusic.domain.usecase.playlist.SavePlaylistToYouTubeUseCase
import com.example.orbitmusic.ui.screens.ai.YouTubeSave
import com.example.orbitmusic.ui.navigation.PlaylistDetailRoute
import com.example.orbitmusic.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistDetailEvent {
    /** Google must ask for permission to manage the user's YouTube playlists. */
    data class LaunchConsent(val pendingIntent: PendingIntent) : PlaylistDetailEvent

    /** The OrbitNova playlist was deleted; leave the screen. */
    data object Deleted : PlaylistDetailEvent
}

data class PlaylistDetailUiState(
    val detail: PlaylistDetail? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Play all / Shuffle is gathering the full track list. */
    val isPreparingPlayback: Boolean = false,
    val error: AppError? = null,
    val isOffline: Boolean = false,
    /** "Save to YouTube" progress for an OrbitNova playlist. */
    val youTubeSave: YouTubeSave? = null,
) {
    val isInitialLoading: Boolean get() = detail == null && isRefreshing
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePlaylistDetail: ObservePlaylistDetailUseCase,
    private val refreshPlaylist: RefreshPlaylistUseCase,
    private val loadMorePlaylistItems: LoadMorePlaylistItemsUseCase,
    private val getPlaylistQueue: GetPlaylistQueueUseCase,
    private val playSongs: PlaySongsUseCase,
    networkMonitor: NetworkMonitor,
    private val savePlaylistToYouTube: SavePlaylistToYouTubeUseCase,
    private val deleteLocalPlaylist: DeleteLocalPlaylistUseCase,
    private val requestPlaylistEditAccess: RequestPlaylistEditAccessUseCase,
    private val completeAuthorization: CompleteAuthorizationUseCase,
) : ViewModel() {

    private val _events = Channel<PlaylistDetailEvent>(Channel.BUFFERED)
    val events: Flow<PlaylistDetailEvent> = _events.receiveAsFlow()

    private val youTubeSave = MutableStateFlow<YouTubeSave?>(null)

    /** A YouTube copy that was started but not finished, to continue instead of starting over. */
    private var partialYouTubeId: String? = null
    private var partialAdded = 0

    private val playlistId = savedStateHandle.toRoute<PlaylistDetailRoute>().playlistId

    private val refreshing = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val preparing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    private val status = combine(refreshing, loadingMore, preparing, error) { r, l, p, e -> Status(r, l, p, e) }

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        observePlaylistDetail(playlistId),
        status,
        networkMonitor.isOnline,
        youTubeSave,
    ) { detail, s, online, save ->
        PlaylistDetailUiState(
            detail = detail,
            isRefreshing = s.refreshing,
            isLoadingMore = s.loadingMore,
            isPreparingPlayback = s.preparing,
            error = s.error,
            isOffline = !online,
            youTubeSave = save,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState(isRefreshing = true))

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        if (refreshing.value && force) return
        refreshing.value = true
        viewModelScope.launch {
            error.value = (refreshPlaylist(playlistId, force) as? Resource.Error)?.error
            refreshing.value = false
        }
    }

    fun loadMore() {
        if (loadingMore.value || uiState.value.detail?.hasMore != true) return
        loadingMore.value = true
        viewModelScope.launch {
            (loadMorePlaylistItems(playlistId) as? Resource.Error)?.let { error.value = it.error }
            loadingMore.value = false
        }
    }

    /** Play all (in order) or Shuffle (random start, shuffle mode on). Pages in every track first. */
    fun play(shuffle: Boolean) {
        if (preparing.value) return
        preparing.value = true
        viewModelScope.launch {
            when (val result = getPlaylistQueue(playlistId, shuffle = false)) {
                is Resource.Success -> {
                    val songs = result.data
                    playSongs(songs, startSong = if (shuffle) songs.random() else null, shuffle = shuffle)
                }
                is Resource.Error -> error.value = result.error
                Resource.Loading -> Unit
            }
            preparing.value = false
        }
    }

    /** Copies this OrbitNova playlist to YouTube, asking for playlist permission first if needed. */
    fun saveToYouTube() {
        if (youTubeSave.value is YouTubeSave.Saving) return
        youTubeSave.value = YouTubeSave.Saving(partialAdded, uiState.value.detail?.songs?.size ?: 0)
        viewModelScope.launch {
            when (val outcome = requestPlaylistEditAccess()) {
                AuthorizationOutcome.Granted -> copyToYouTube()
                is AuthorizationOutcome.NeedsConsent -> _events.send(PlaylistDetailEvent.LaunchConsent(outcome.pendingIntent))
                is AuthorizationOutcome.Failed -> youTubeSave.value = YouTubeSave.Failed(outcome.error)
            }
        }
    }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            youTubeSave.value = YouTubeSave.Failed(AppError.PermissionRequired)
            return
        }
        viewModelScope.launch {
            when (val result = completeAuthorization(data)) {
                is Resource.Success -> copyToYouTube()
                is Resource.Error -> youTubeSave.value = YouTubeSave.Failed(result.error)
                Resource.Loading -> Unit
            }
        }
    }

    private suspend fun copyToYouTube() {
        val playlist = uiState.value.detail?.playlist ?: return
        val result = savePlaylistToYouTube(playlist, partialYouTubeId, partialAdded) { done, total ->
            youTubeSave.value = YouTubeSave.Saving(done, total)
        }
        youTubeSave.value = when (result) {
            is SaveAiPlaylistResult.Saved -> {
                partialYouTubeId = null
                partialAdded = 0
                YouTubeSave.Saved(result.playlist, result.added, result.missing)
            }
            is SaveAiPlaylistResult.Failed -> {
                result.playlist?.let {
                    partialYouTubeId = it.id
                    partialAdded = result.added
                }
                YouTubeSave.Failed(result.error)
            }
        }
    }

    /** Deletes this OrbitNova playlist (YouTube playlists are never deleted from the app). */
    fun delete() {
        viewModelScope.launch {
            deleteLocalPlaylist(playlistId)
            _events.send(PlaylistDetailEvent.Deleted)
        }
    }

    fun dismissError() {
        error.value = null
    }

    private data class Status(val refreshing: Boolean, val loadingMore: Boolean, val preparing: Boolean, val error: AppError?)
}
