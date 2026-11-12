package com.example.orbitmusic.ui

import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import com.example.orbitmusic.ui.screens.showcase.OrbitShowcaseScreen
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistDetail
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.ui.screens.home.Greeting
import com.example.orbitmusic.ui.screens.home.HomeContent
import com.example.orbitmusic.ui.screens.home.HomeSections
import com.example.orbitmusic.ui.screens.home.HomeUiState
import com.example.orbitmusic.ui.screens.login.LoginContent
import com.example.orbitmusic.ui.screens.login.LoginUiState
import com.example.orbitmusic.ui.screens.onboarding.OnboardingContent
import com.example.orbitmusic.ui.screens.playlist.PlaylistDetailContent
import com.example.orbitmusic.ui.screens.playlist.PlaylistDetailUiState
import com.example.orbitmusic.ui.screens.playlist.noPlaylistCallbacks
import com.example.orbitmusic.ui.screens.settings.SettingsContent
import com.example.orbitmusic.ui.screens.settings.SettingsUiState
import com.example.orbitmusic.ui.screens.settings.noSettingsCallbacks
import com.example.orbitmusic.ui.screens.songactions.SongMenuContent
import com.example.orbitmusic.ui.screens.library.CreatePlaylistContent
import com.example.orbitmusic.ui.screens.library.CreatePlaylistUiState
import com.example.orbitmusic.ui.player.SleepTimerContent
import com.example.orbitmusic.ui.components.OrbitErrorState
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.domain.player.SleepTimerState
import com.example.orbitmusic.ui.screens.search.SearchCallbacks
import com.example.orbitmusic.ui.screens.search.SearchContent
import com.example.orbitmusic.ui.screens.search.SearchMode
import com.example.orbitmusic.ui.screens.search.SearchUiState
import com.example.orbitmusic.ui.screens.splash.SplashScreen
import com.example.orbitmusic.ui.screens.ai.AiChatEntry
import com.example.orbitmusic.ui.screens.ai.OrbitAiContent
import com.example.orbitmusic.ui.screens.ai.OrbitAiUiState
import com.example.orbitmusic.ui.screens.ai.noAiCallbacks
import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AiSongSuggestion
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders key screens with sample data and saves PNGs (for visual review) to the app's
 * external files dir. Also acts as a smoke test that these screens compose without crashing.
 */
@RunWith(AndroidJUnit4::class)
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val songs = listOf(
        "Midnight Drive" to "Nova Star",
        "Ocean Eyes" to "Lumen",
        "Golden Hour" to "Aster",
        "Static Bloom" to "Kite Club",
        "Paper Planets" to "Juniper",
        "Velvet Rain" to "Orion Bay",
    ).mapIndexed { i, (title, artist) ->
        Song("v$i", title, artist, null, null, 150_000L + i * 23_000L, isPlayable = i != 4)
    }
    private val playlists = listOf("Late night lofi", "Gym energy", "Sunday coffee", "Road trip")
        .mapIndexed { i, title -> Playlist("p$i", title, "", null, 12 + i * 9, "Nova") }
    private val user = UserProfile("1", "nova@example.com", "Nova Star", "Nova", null)

    private fun render(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            OrbitMusicTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().background(OrbitTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        saveRoot(name)
    }

    private fun saveRoot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("renders")!!
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun homeState() = HomeUiState(
        greeting = Greeting.Evening,
        sections = HomeSections(
            recentlyPlayed = songs.take(4),
            playlists = playlists,
            quickPicks = songs,
            liked = songs.reversed(),
            trending = songs,
        ),
    )

    @Composable
    private fun Home(state: HomeUiState) = HomeContent(
        state = state,
        contentPadding = PaddingValues(),
        onRefresh = {},
        onDismissError = {},
        onOpenPlaylist = {},
        onOpenLiked = {},
        onSeeAllPlaylists = {},
        onPlaySong = { _, _ -> },
        onSongMenu = {},
    )

    private val noSearchCallbacks = SearchCallbacks({}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})

    @Composable
    private fun Search(state: SearchUiState) = SearchContent(
        state = state,
        contentPadding = PaddingValues(),
        callbacks = noSearchCallbacks,
    )

    @Test
    fun showcaseDark() = renderPages("showcase_dark", dark = true, pages = 4) {
        OrbitShowcaseScreen(isDark = true, onDarkModeChange = {})
    }

    @Test
    fun showcaseLight() = renderPages("showcase_light", dark = false, pages = 4) {
        OrbitShowcaseScreen(isDark = false, onDarkModeChange = {})
    }

    /** Renders a tall scrolling screen and saves one PNG per page (swiping up between). */
    private fun renderPages(name: String, dark: Boolean, pages: Int, content: @Composable () -> Unit) {
        render("${name}_1", dark, content)
        for (page in 2..pages) {
            compose.onRoot().performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.1f, durationMillis = 400) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(800)
            saveRoot("${name}_$page")
        }
    }

    @Test
    fun homeLight() = renderPages("home_light", dark = false, pages = 3) { Home(homeState()) }

    @Test
    fun homeDark() = renderPages("home_dark", dark = true, pages = 3) { Home(homeState()) }

    @Test
    fun searchBrowse() = render("search_browse_dark", dark = true) {
        Search(SearchUiState(recentSearches = listOf("lofi beats", "Daft Punk", "synthwave", "Nova Star")))
    }

    @Test
    fun searchTyping() = render("search_typing_light", dark = false) {
        Search(SearchUiState(query = "lo", mode = SearchMode.Typing, suggestions = listOf("lofi beats", "lofi hip hop", "love songs")))
    }

    @Test
    fun orbitAi() = render("orbit_ai_dark", dark = true) {
        OrbitAiContent(
            state = OrbitAiUiState(
                entries = listOf(
                    AiChatEntry.Request(0, "Songs for a late night drive"),
                    AiChatEntry.Answer(
                        1,
                        AiPlaylist(
                            name = "Late night drive",
                            description = "Smooth synths for empty roads",
                            songs = listOf(AiSongSuggestion("Midnight City", "M83"), AiSongSuggestion("Nightcall", "Kavinsky")),
                        ),
                    ),
                ),
            ),
            contentPadding = PaddingValues(),
            callbacks = noAiCallbacks,
        )
    }

    @Test
    fun splash() = render("splash", dark = true) { SplashScreen() }

    @Test
    fun onboarding() = render("onboarding_dark", dark = true) { OnboardingContent(onFinish = {}) }

    @Test
    fun login() = render("login", dark = true) {
        LoginContent(state = LoginUiState(), onContinue = {}, onUseDifferentAccount = {}, onDismissError = {})
    }

    @Composable
    private fun Playlist() = PlaylistDetailContent(
        state = PlaylistDetailUiState(detail = PlaylistDetail(playlists[0], songs, hasMore = true)),
        contentPadding = PaddingValues(),
        callbacks = noPlaylistCallbacks,
    )

    @Test
    fun playlistDetailLight() = render("playlist_light", dark = false) { Playlist() }

    @Test
    fun playlistDetailDark() = render("playlist_dark", dark = true) { Playlist() }

    @Test
    fun settings() = render("settings_dark", dark = true) {
        SettingsContent(
            state = SettingsUiState(user = user, themeMode = ThemeMode.Dark),
            contentPadding = PaddingValues(),
            imageCacheSize = "24 MB",
            callbacks = noSettingsCallbacks,
        )
    }

    @Test
    fun errorStates() = render("states_light", dark = false) {
        OrbitErrorState(error = AppError.QuotaExceeded, onRetry = {})
        OrbitErrorState(error = AppError.NoInternet, onRetry = {})
    }

    @Test
    fun sheets() = render("sheets_dark", dark = true) {
        Column(Modifier.background(OrbitTheme.colors.surface)) {
            SongMenuContent(songs[0], isFavorite = true, onPlayNext = {}, onAddToQueue = {}, onAddToPlaylist = {}, onToggleFavorite = {}, onShare = {})
            SleepTimerContent(state = SleepTimerState.Off, onStart = {}, onEndOfSong = {}, onCancel = {})
        }
    }

    @Test
    fun createPlaylist() = render("create_playlist_light", dark = false) {
        Column(Modifier.background(OrbitTheme.colors.surface)) {
            CreatePlaylistContent(state = CreatePlaylistUiState(), onCreate = { _, _ -> })
        }
    }
}
