package com.example.orbitmusic.ui.screens.library

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.usecase.auth.CompleteAuthorizationUseCase
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.domain.usecase.auth.RequestPlaylistEditAccessUseCase
import com.example.orbitmusic.domain.usecase.library.ClearHistoryUseCase
import com.example.orbitmusic.domain.usecase.playlist.CreatePlaylistUseCase
import com.example.orbitmusic.domain.usecase.library.LoadMoreLikedSongsUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveFavoritesUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveLikedSongsUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveMyPlaylistsUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveRecentlyPlayedUseCase
import com.example.orbitmusic.domain.usecase.library.RefreshLikedSongsUseCase
import com.example.orbitmusic.domain.usecase.library.RefreshMyPlaylistsUseCase
import com.example.orbitmusic.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { Playlists, Liked, Favorites, History }

data class LibraryContent(
    val playlists: List<Playlist> = emptyList(),
    val liked: List<Song> = emptyList(),
    val likedHasMore: Boolean = false,
    val favorites: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
)

/** The "New playlist" sheet. */
data class CreatePlaylistUiState(
    val isCreating: Boolean = false,
    val error: AppError? = null,
)

sealed interface LibraryEvent {
    /** Google needs the user to grant playlist-management access first. */
    data class LaunchConsent(val pendingIntent: PendingIntent) : LibraryEvent

    data class PlaylistCreated(val playlist: Playlist) : LibraryEvent
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.Playlists,
    val content: LibraryContent = LibraryContent(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: AppError? = null,
    val isOffline: Boolean = false,
    /** Non-null while the "New playlist" sheet is open. */
    val createPlaylist: CreatePlaylistUiState? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeMyPlaylists: ObserveMyPlaylistsUseCase,
    observeLikedSongs: ObserveLikedSongsUseCase,
    observeFavorites: ObserveFavoritesUseCase,
    observeRecentlyPlayed: ObserveRecentlyPlayedUseCase,
    private val refreshMyPlaylists: RefreshMyPlaylistsUseCase,
    private val refreshLikedSongs: RefreshLikedSongsUseCase,
    private val loadMoreLikedSongs: LoadMoreLikedSongsUseCase,
    private val clearHistory: ClearHistoryUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val requestPlaylistEditAccess: RequestPlaylistEditAccessUseCase,
    private val completeAuthorization: CompleteAuthorizationUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val authState = observeAuthState()
    private val createSheet = MutableStateFlow<CreatePlaylistUiState?>(null)
    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    /** Title and privacy waiting for the consent screen to come back. */
    private var pendingCreate: Pair<String, PlaylistPrivacy>? = null

    private val selectedTab = savedStateHandle.getStateFlow(KEY_TAB, LibraryTab.Playlists.name)
    private val refreshing = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    private val content = combine(
        observeMyPlaylists(),
        observeLikedSongs(),
        observeLikedSongs.hasMore(),
        observeFavorites(),
        observeRecentlyPlayed(limit = HISTORY_LIMIT),
    ) { playlists, liked, likedHasMore, favorites, history ->
        LibraryContent(playlists, liked, likedHasMore, favorites, history)
    }

    private val status = combine(refreshing, loadingMore, error, networkMonitor.isOnline) { r, l, e, online ->
        Status(r, l, e, !online)
    }

    val uiState: StateFlow<LibraryUiState> = combine(selectedTab, content, status, createSheet) { tab, data, s, create ->
        LibraryUiState(
            createPlaylist = create,
            selectedTab = LibraryTab.valueOf(tab),
            content = data,
            isRefreshing = s.refreshing,
            isLoadingMore = s.loadingMore,
            error = s.error,
            isOffline = s.offline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState(isRefreshing = true))

    init {
        refresh(force = false)
    }

    fun selectTab(tab: LibraryTab) {
        savedStateHandle[KEY_TAB] = tab.name
    }

    fun refresh(force: Boolean = true) {
        if (refreshing.value && force) return
        refreshing.value = true
        viewModelScope.launch {
            val playlists = async { refreshMyPlaylists(force) }
            val liked = async { refreshLikedSongs(force) }
            val results = listOf(playlists.await(), liked.await())
            // "Not found" for a whole library list isn't actionable (see RefreshHomeFeedUseCase).
            error.value = results.filterIsInstance<Resource.Error>().map { it.error }.firstOrNull { it != AppError.NotFound }
            refreshing.value = false
        }
    }

    /** Called when the Liked list scrolls near its end. */
    fun loadMoreLiked() {
        if (loadingMore.value || !uiState.value.content.likedHasMore) return
        loadingMore.value = true
        viewModelScope.launch {
            (loadMoreLikedSongs() as? Resource.Error)?.let { error.value = it.error }
            loadingMore.value = false
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch { clearHistory() }
    }

    // New playlist (needs the YouTube playlist-management scope) -----------------------

    fun onOpenCreatePlaylist() {
        createSheet.value = CreatePlaylistUiState()
    }

    fun onDismissCreatePlaylist() {
        if (createSheet.value?.isCreating == true) return
        createSheet.value = null
        pendingCreate = null
    }

    fun onCreatePlaylist(title: String, privacy: PlaylistPrivacy) {
        if (createSheet.value?.isCreating == true) return
        createSheet.value = CreatePlaylistUiState(isCreating = true)
        viewModelScope.launch {
            // Silent when the write scope is still granted; returns a token that has it.
            when (val outcome = requestPlaylistEditAccess()) {
                AuthorizationOutcome.Granted -> create(title, privacy)
                is AuthorizationOutcome.NeedsConsent -> {
                    pendingCreate = title to privacy
                    _events.send(LibraryEvent.LaunchConsent(outcome.pendingIntent))
                }
                is AuthorizationOutcome.Failed -> createSheet.value = CreatePlaylistUiState(error = outcome.error)
            }
        }
    }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        val (title, privacy) = pendingCreate ?: return
        pendingCreate = null
        if (resultCode != Activity.RESULT_OK) {
            createSheet.value = CreatePlaylistUiState(error = AppError.PermissionRequired)
            return
        }
        viewModelScope.launch {
            when (val result = completeAuthorization(data)) {
                is Resource.Success -> create(title, privacy)
                is Resource.Error -> createSheet.value = CreatePlaylistUiState(error = result.error)
                Resource.Loading -> Unit
            }
        }
    }

    private suspend fun create(title: String, privacy: PlaylistPrivacy) {
        when (val result = createPlaylistUseCase(title, privacy = privacy)) {
            is Resource.Success -> {
                createSheet.value = null
                _events.send(LibraryEvent.PlaylistCreated(result.data))
            }
            is Resource.Error -> createSheet.value = CreatePlaylistUiState(error = result.error)
            Resource.Loading -> Unit
        }
    }

    fun dismissError() {
        error.value = null
    }

    private data class Status(val refreshing: Boolean, val loadingMore: Boolean, val error: AppError?, val offline: Boolean)

    private companion object {
        const val KEY_TAB = "library_tab"
        const val HISTORY_LIMIT = 200
    }
}
