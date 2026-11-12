package com.example.orbitmusic.ui.screens.main

import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import com.example.orbitmusic.ui.theme.orbit.OrbitBottomBarCenterRaise
import com.example.orbitmusic.ui.theme.orbit.OrbitBottomBarHeight
import com.example.orbitmusic.ui.theme.orbit.OrbitBottomBar
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.rememberOrbitBottomBarItems
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.domain.player.PlayerEvent
import com.example.orbitmusic.ui.common.ObserveAsEvents
import android.content.Intent
import com.example.orbitmusic.ui.player.MiniPlayerHeight
import com.example.orbitmusic.ui.player.PlaybackSpeedSheet
import com.example.orbitmusic.ui.player.PlayerActions
import com.example.orbitmusic.ui.player.PlayerSheet
import com.example.orbitmusic.ui.player.QueueSheet
import com.example.orbitmusic.ui.player.SleepTimerSheet
import com.example.orbitmusic.ui.player.PlayerUiState
import com.example.orbitmusic.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.ui.navigation.HomeRoute
import com.example.orbitmusic.ui.navigation.LibraryRoute
import com.example.orbitmusic.ui.navigation.PlaylistDetailRoute
import com.example.orbitmusic.ui.navigation.SearchRoute
import com.example.orbitmusic.ui.components.ProfileTabIcon
import com.example.orbitmusic.ui.screens.ai.OrbitAiScreen
import com.example.orbitmusic.ui.update.RemoteConfigViewModel
import com.example.orbitmusic.ui.theme.orbit.OrbitBottomBarCenter
import com.example.orbitmusic.ui.navigation.OrbitAiRoute
import com.example.orbitmusic.ui.navigation.SettingsRoute
import com.example.orbitmusic.ui.screens.home.HomeScreen
import com.example.orbitmusic.ui.screens.library.LibraryScreen
import com.example.orbitmusic.ui.screens.library.LibraryTab
import com.example.orbitmusic.ui.screens.playlist.PlaylistDetailScreen
import com.example.orbitmusic.ui.screens.search.SearchScreen
import com.example.orbitmusic.ui.screens.settings.SettingsScreen
import com.example.orbitmusic.ui.screens.songactions.SongActionsHost
import com.example.orbitmusic.ui.screens.songactions.SongActionsViewModel

/** Tab order matches [rememberOrbitBottomBarItems]. */
private val tabRoutes: List<Any> = listOf(HomeRoute, SearchRoute, LibraryRoute, SettingsRoute)

/** selectedIndex value while the centre Orbit AI button is the current destination. */
private const val ORBIT_AI_SELECTED = -1

/** Bar content + its 1dp top divider (the navigation-bar inset is added separately). */
private val BottomBarHeight = OrbitBottomBarHeight + 1.dp

/**
 * App shell: a NavHost for the tabs (plus pushed screens such as Playlist Detail), the
 * full-width frosted bottom bar, shared song actions and the snackbar. Phase 6 docks the mini
 * player directly above the bar.
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val songActionsViewModel: SongActionsViewModel = hiltViewModel()
    val remoteConfig: RemoteConfigViewModel = hiltViewModel()
    val aiEnabled by remoteConfig.aiEnabled.collectAsStateWithLifecycle()
    val announcement by remoteConfig.announcement.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Leave Orbit AI if it gets switched off remotely while open.
    LaunchedEffect(aiEnabled) {
        if (!aiEnabled && backStackEntry?.destination?.hasRoute(OrbitAiRoute::class) == true) {
            navController.navigateToTab(HomeRoute)
        }
    }

    // Pushed screens (Playlist Detail) keep the tab they were opened from highlighted.
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(backStackEntry) {
        val destination = backStackEntry?.destination ?: return@LaunchedEffect
        if (destination.hasRoute(OrbitAiRoute::class)) {
            selectedIndex = ORBIT_AI_SELECTED
        } else {
            tabRoutes.indexOfFirst { destination.hasRoute(it::class) }.takeIf { it >= 0 }?.let { selectedIndex = it }
        }
    }

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerUi by playerViewModel.uiState.collectAsStateWithLifecycle()
    val playerState = playerUi.player
    val hasCurrentSong = playerState.currentSong != null
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasCurrentSong) { if (!hasCurrentSong) playerExpanded = false }
    val context = LocalContext.current

    ObserveAsEvents(playerViewModel.events) { event ->
        val message = when (event) {
            is PlayerEvent.SkippedUnavailable -> context.getString(R.string.player_skipped_unavailable, event.title)
            PlayerEvent.NothingPlayable -> context.getString(R.string.player_nothing_playable)
            PlayerEvent.PlaybackFailed -> context.getString(R.string.player_failed)
        }
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    NotificationPermissionRequest(trigger = hasCurrentSong)

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniPlayerSpace = if (hasCurrentSong) MiniPlayerHeight + MiniPlayerGap else 0.dp
    val collapsedPlayerBottom = BottomBarHeight + navBarInset + MiniPlayerGap
    // Tab content is the blur source for the frosted bottom bar (real blur on API 31+).
    val hazeState = rememberHazeState()

    val playerActions = remember(playerViewModel, songActionsViewModel, context) {
        object : PlayerActions {
            override fun togglePlayPause() = playerViewModel.togglePlayPause()
            override fun next() = playerViewModel.next()
            override fun previous() = playerViewModel.previous()
            override fun seekTo(positionMs: Long) = playerViewModel.seekTo(positionMs)
            override fun toggleShuffle() = playerViewModel.toggleShuffle()
            override fun cycleRepeat() = playerViewModel.cycleRepeatMode()
            override fun toggleFavorite() = playerViewModel.toggleCurrentFavorite()
            override fun openMenu() {
                playerViewModel.currentState.currentSong?.let(songActionsViewModel::showMenu)
            }
            override fun selectView(view: NowPlayingView) = playerViewModel.selectNowPlayingView(view)
            override fun retryLyrics() = playerViewModel.retryLyrics()
            override fun openQueue() { showQueue = true }
            override fun openSleepTimer() { showSleepTimer = true }
            override fun openSpeed() { showSpeed = true }
            override fun share() {
                val song = playerViewModel.currentState.currentSong ?: return
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_song_text, song.title, song.artist, song.shareUrl))
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
            }
        }
    }
    // Lets scrolling content pass under the floating bar while its last item stays reachable.
    val contentPadding = PaddingValues(bottom = BottomBarHeight + navBarInset + miniPlayerSpace)
    val openPlaylist: (Playlist) -> Unit = { navController.navigate(PlaylistDetailRoute(it.id)) }
    var libraryTabRequest by remember { mutableStateOf<LibraryTab?>(null) }
    val openLibraryTab: (LibraryTab) -> Unit = { tab ->
        libraryTabRequest = tab
        navController.navigateToTab(LibraryRoute)
    }

    SongActionsHost(viewModel = songActionsViewModel, snackbarHostState = snackbarHostState) {
        Box(
            Modifier
                .fillMaxSize()
                .background(OrbitTheme.colors.background),
        ) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        contentPadding = contentPadding,
                        onOpenPlaylist = openPlaylist,
                        onOpenLiked = { openLibraryTab(LibraryTab.Liked) },
                        onSeeAllPlaylists = { openLibraryTab(LibraryTab.Playlists) },
                        announcement = announcement,
                        onDismissAnnouncement = remoteConfig::dismissAnnouncement,
                    )
                }
                composable<SearchRoute> {
                    SearchScreen(contentPadding = contentPadding, onOpenPlaylist = openPlaylist)
                }
                composable<LibraryRoute> {
                    LibraryScreen(
                        contentPadding = contentPadding,
                        onOpenPlaylist = openPlaylist,
                        requestedTab = libraryTabRequest,
                        onTabRequestConsumed = { libraryTabRequest = null },
                    )
                }
                composable<OrbitAiRoute> { OrbitAiScreen(contentPadding = contentPadding) }
                composable<SettingsRoute> {
                    SettingsScreen(contentPadding = contentPadding)
                }
                composable<PlaylistDetailRoute> {
                    PlaylistDetailScreen(contentPadding = contentPadding, onBack = { navController.popBackStack() })
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                SnackbarHost(
                    snackbarHostState,
                    // Above the mini player, or at least above the raised Orbit AI button.
                    Modifier.padding(start = 8.dp, end = 8.dp, bottom = maxOf(miniPlayerSpace, OrbitBottomBarCenterRaise)),
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = OrbitShapes.GroupedList,
                        containerColor = OrbitTheme.colors.surfaceRaised,
                        contentColor = OrbitTheme.colors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                OrbitBottomBar(
                    hazeState = hazeState,
                    items = rememberOrbitBottomBarItems(profileContent = { selected -> ProfileTabIcon(selected) }),
                    selectedIndex = selectedIndex,
                    onItemSelected = { index ->
                        selectedIndex = index
                        navController.navigateToTab(tabRoutes[index])
                    },
                    // Orbit AI can be switched off remotely; its button then disappears.
                    center = if (aiEnabled) {
                        OrbitBottomBarCenter(
                            label = stringResource(R.string.nav_orbit_ai),
                            selected = selectedIndex == ORBIT_AI_SELECTED,
                            onClick = {
                                selectedIndex = ORBIT_AI_SELECTED
                                navController.navigateToTab(OrbitAiRoute)
                            },
                        )
                    } else {
                        null
                    },
                )
            }

            AnimatedVisibility(
                visible = hasCurrentSong,
                enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                PlayerSheet(
                    state = playerUi,
                    expanded = playerExpanded,
                    onExpandedChange = { playerExpanded = it },
                    collapsedBottomOffset = collapsedPlayerBottom,
                    actions = playerActions,
                )
            }
        }
        PlayerSheets(
            viewModel = playerViewModel,
            state = playerUi,
            showQueue = showQueue,
            showSleepTimer = showSleepTimer,
            showSpeed = showSpeed,
            onDismissQueue = { showQueue = false },
            onDismissSleepTimer = { showSleepTimer = false },
            onDismissSpeed = { showSpeed = false },
        )
    }
}

/** Gap between the mini player and the bottom bar. */
/** The mini player floats above the bar's raised Orbit AI button, with an 8dp gap. */
private val MiniPlayerGap = OrbitBottomBarCenterRaise + 8.dp

@Composable
private fun PlayerSheets(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    showQueue: Boolean,
    showSleepTimer: Boolean,
    showSpeed: Boolean,
    onDismissQueue: () -> Unit,
    onDismissSleepTimer: () -> Unit,
    onDismissSpeed: () -> Unit,
) {
    if (showQueue && !state.player.queue.isEmpty) {
        QueueSheet(
            queue = state.player.queue,
            onDismiss = onDismissQueue,
            onSkipTo = viewModel::skipTo,
            onRemove = viewModel::remove,
            onMove = viewModel::move,
            isPlaying = state.player.playWhenReady,
        )
    }
    if (showSleepTimer) {
        SleepTimerSheet(
            state = state.sleepTimer,
            onStart = viewModel::startSleepTimer,
            onEndOfSong = viewModel::sleepAtEndOfTrack,
            onCancel = viewModel::cancelSleepTimer,
            onDismiss = onDismissSleepTimer,
        )
    }
    if (showSpeed) {
        PlaybackSpeedSheet(
            currentSpeed = state.player.playbackSpeed,
            onSelect = viewModel::setPlaybackSpeed,
            onDismiss = onDismissSpeed,
        )
    }
}

/**
 * Media notifications need POST_NOTIFICATIONS on Android 13+. Asked once, the first time
 * something starts playing (when the reason is obvious to the user).
 */
@Composable
private fun NotificationPermissionRequest(trigger: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(trigger) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (trigger && !asked && !granted) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Standard tab navigation: single top, with each tab's back stack saved and restored. */
private fun NavController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
