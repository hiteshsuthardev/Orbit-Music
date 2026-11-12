package com.example.orbitmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Lyrics
import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.player.PlayerController
import com.example.orbitmusic.domain.player.PlayerEvent
import com.example.orbitmusic.domain.player.PlayerState
import com.example.orbitmusic.domain.player.SleepTimer
import com.example.orbitmusic.domain.player.SleepTimerState
import com.example.orbitmusic.domain.usecase.library.ObserveFavoritesUseCase
import com.example.orbitmusic.domain.usecase.library.ToggleFavoriteUseCase
import com.example.orbitmusic.domain.usecase.lyrics.GetLyricsUseCase
import com.example.orbitmusic.domain.usecase.preferences.ObservePreferencesUseCase
import com.example.orbitmusic.domain.usecase.preferences.SetNowPlayingViewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lyrics for the current song, loaded only while the Lyrics view is shown. */
sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Found(val lyrics: Lyrics) : LyricsUiState
    data object NotFound : LyricsUiState
    data class Failed(val error: AppError) : LyricsUiState
}

data class PlayerUiState(
    val player: PlayerState = PlayerState(),
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
    val isCurrentFavorite: Boolean = false,
    val nowPlayingView: NowPlayingView = NowPlayingView.Video,
    val lyrics: LyricsUiState = LyricsUiState.Idle,
)

/** UI-facing playback: mini player, Now Playing, queue, sleep timer, speed and lyrics. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    private val sleepTimer: SleepTimer,
    observeFavorites: ObserveFavoritesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val setNowPlayingView: SetNowPlayingViewUseCase,
    private val getLyrics: GetLyricsUseCase,
) : ViewModel() {

    private val view = observePreferences().map { it.nowPlayingView }.distinctUntilChanged()

    /** Bumped by "Try again" so the same song's lyrics are requested again. */
    private val lyricsRetry = MutableStateFlow(0)

    private val lyrics: Flow<LyricsUiState> = combine(
        controller.state.map { it.currentSong }.distinctUntilChanged { old, new -> old?.videoId == new?.videoId },
        view,
        lyricsRetry,
    ) { song, currentView, _ -> song.takeIf { currentView == NowPlayingView.Lyrics } }
        .flatMapLatest { song ->
            if (song == null) {
                flowOf<LyricsUiState>(LyricsUiState.Idle)
            } else {
                flow<LyricsUiState> {
                    emit(LyricsUiState.Loading)
                    emit(
                        when (val result = getLyrics(song)) {
                            is Resource.Success -> result.data?.let(LyricsUiState::Found) ?: LyricsUiState.NotFound
                            is Resource.Error -> LyricsUiState.Failed(result.error)
                            Resource.Loading -> LyricsUiState.Loading
                        },
                    )
                }
            }
        }

    val uiState: StateFlow<PlayerUiState> = combine(
        controller.state,
        sleepTimer.state,
        observeFavorites.ids(),
        view,
        lyrics,
    ) { player, timer, favorites, currentView, lyricsState ->
        PlayerUiState(
            player = player,
            sleepTimer = timer,
            isCurrentFavorite = player.currentSong?.videoId in favorites,
            nowPlayingView = currentView,
            lyrics = lyricsState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState(player = controller.state.value))

    val events: Flow<PlayerEvent> = controller.events

    val currentState: PlayerState get() = controller.state.value

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun setPlaybackSpeed(speed: Float) = controller.setPlaybackSpeed(speed)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeatMode() = controller.cycleRepeatMode()
    fun skipTo(queueId: String) = controller.skipToQueueItem(queueId)
    fun remove(queueId: String) = controller.removeFromQueue(queueId)
    fun move(from: Int, to: Int) = controller.moveQueueItem(from, to)

    fun toggleCurrentFavorite() {
        val song = controller.state.value.currentSong ?: return
        viewModelScope.launch { toggleFavorite(song) }
    }

    /** Poster, Video or Lyrics; remembered across launches. */
    fun selectNowPlayingView(view: NowPlayingView) {
        viewModelScope.launch { setNowPlayingView(view) }
    }

    fun retryLyrics() {
        lyricsRetry.value++
    }

    fun startSleepTimer(minutes: Int) = sleepTimer.start(minutes)
    fun sleepAtEndOfTrack() = sleepTimer.startEndOfTrack()
    fun cancelSleepTimer() = sleepTimer.cancel()
}
