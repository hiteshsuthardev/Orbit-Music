package com.example.orbitmusic.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.usecase.home.ObserveTrendingUseCase
import com.example.orbitmusic.domain.usecase.home.RefreshHomeFeedUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveLikedSongsUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveMyPlaylistsUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveQuickPicksUseCase
import com.example.orbitmusic.domain.usecase.library.ObserveRecentlyPlayedUseCase
import com.example.orbitmusic.util.NetworkMonitor
import com.example.orbitmusic.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

enum class Greeting { Morning, Afternoon, Evening, Night }

fun greetingFor(time: LocalTime): Greeting = when (time.hour) {
    in 5..11 -> Greeting.Morning
    in 12..16 -> Greeting.Afternoon
    in 17..21 -> Greeting.Evening
    else -> Greeting.Night
}

data class HomeSections(
    val recentlyPlayed: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val quickPicks: List<Song> = emptyList(),
    val liked: List<Song> = emptyList(),
    val trending: List<Song> = emptyList(),
) {
    val isEmpty: Boolean
        get() = recentlyPlayed.isEmpty() && playlists.isEmpty() && quickPicks.isEmpty() &&
            liked.isEmpty() && trending.isEmpty()
}

enum class HeroKind { Continue, Trending }

/** The large card at the top of Home. */
data class HomeHero(
    val kind: HeroKind,
    val song: Song,
    /** What plays when the hero's play button is pressed (starting at [song]). */
    val queue: List<Song>,
)

/** Continue from the most recent song; otherwise feature the top trending song. */
fun heroFor(sections: HomeSections): HomeHero? {
    sections.recentlyPlayed.firstOrNull { it.isPlayable }?.let { return HomeHero(HeroKind.Continue, it, sections.recentlyPlayed) }
    sections.trending.firstOrNull { it.isPlayable }?.let { return HomeHero(HeroKind.Trending, it, sections.trending) }
    return null
}

data class HomeUiState(
    val greeting: Greeting = Greeting.Morning,
    val sections: HomeSections = HomeSections(),
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val isOffline: Boolean = false,
) {
    /** First launch with nothing cached yet: show skeletons instead of empty rows. */
    val isInitialLoading: Boolean get() = isRefreshing && sections.isEmpty

    val hero: HomeHero? get() = heroFor(sections)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeRecentlyPlayed: ObserveRecentlyPlayedUseCase,
    observeMyPlaylists: ObserveMyPlaylistsUseCase,
    observeQuickPicks: ObserveQuickPicksUseCase,
    observeLikedSongs: ObserveLikedSongsUseCase,
    observeTrending: ObserveTrendingUseCase,
    private val refreshHomeFeed: RefreshHomeFeedUseCase,
    networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    private val sections = combine(
        observeRecentlyPlayed(limit = RECENT_LIMIT),
        observeMyPlaylists(),
        observeQuickPicks(),
        observeLikedSongs(),
        observeTrending(),
    ) { recent, playlists, quickPicks, liked, trending ->
        HomeSections(
            recentlyPlayed = recent,
            playlists = playlists,
            quickPicks = quickPicks,
            liked = liked.take(ROW_LIMIT),
            trending = trending.take(ROW_LIMIT),
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        sections,
        refreshing,
        error,
        networkMonitor.isOnline,
    ) { content, isRefreshing, err, online ->
        HomeUiState(
            greeting = greetingFor(timeProvider.localTime()),
            sections = content,
            isRefreshing = isRefreshing,
            error = err,
            isOffline = !online,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(greeting = greetingFor(timeProvider.localTime()), isRefreshing = true),
    )

    init {
        refresh(force = false)
    }

    /** Pull-to-refresh forces a network refresh; otherwise cache TTLs decide. */
    fun refresh(force: Boolean = true) {
        if (refreshing.value && force) return
        refreshing.value = true
        viewModelScope.launch {
            val result = refreshHomeFeed(force)
            error.value = (result as? Resource.Error)?.error
            refreshing.value = false
        }
    }

    fun dismissError() {
        error.value = null
    }

    private companion object {
        const val RECENT_LIMIT = 12
        const val ROW_LIMIT = 20
    }
}
