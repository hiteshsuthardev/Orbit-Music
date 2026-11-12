package com.example.orbitmusic.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.PlaylistDetail
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.usecase.player.PlaySongsUseCase
import com.example.orbitmusic.domain.usecase.playlist.GetPlaylistQueueUseCase
import com.example.orbitmusic.domain.usecase.playlist.LoadMorePlaylistItemsUseCase
import com.example.orbitmusic.domain.usecase.playlist.ObservePlaylistDetailUseCase
import com.example.orbitmusic.domain.usecase.playlist.RefreshPlaylistUseCase
import com.example.orbitmusic.ui.navigation.PlaylistDetailRoute
import com.example.orbitmusic.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val detail: PlaylistDetail? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Play all / Shuffle is gathering the full track list. */
    val isPreparingPlayback: Boolean = false,
    val error: AppError? = null,
    val isOffline: Boolean = false,
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
) : ViewModel() {

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
    ) { detail, s, online ->
        PlaylistDetailUiState(
            detail = detail,
            isRefreshing = s.refreshing,
            isLoadingMore = s.loadingMore,
            isPreparingPlayback = s.preparing,
            error = s.error,
            isOffline = !online,
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

    fun dismissError() {
        error.value = null
    }

    private data class Status(val refreshing: Boolean, val loadingMore: Boolean, val preparing: Boolean, val error: AppError?)
}
