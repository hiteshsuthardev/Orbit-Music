package com.example.orbitmusic.ui.screens.library

import com.example.orbitmusic.ui.theme.orbit.OrbitChip
import com.example.orbitmusic.ui.theme.orbit.IconButtonStyle
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.common.LoadMoreEffect
import com.example.orbitmusic.ui.common.ObserveAsEvents
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.components.OfflineBanner
import com.example.orbitmusic.ui.components.OrbitEmptyState
import com.example.orbitmusic.ui.components.OrbitErrorBanner
import com.example.orbitmusic.ui.components.PlaylistCard
import com.example.orbitmusic.ui.components.SectionHeader
import com.example.orbitmusic.ui.components.ScreenHeader
import com.example.orbitmusic.ui.components.SongListSkeleton
import com.example.orbitmusic.ui.components.SongRow
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.orbit.OrbitSpin
import com.example.orbitmusic.ui.screens.songactions.LocalSongActions

private data class TabSpec(val tab: LibraryTab, @StringRes val label: Int)

private val tabs = listOf(
    TabSpec(LibraryTab.Playlists, R.string.library_tab_playlists),
    TabSpec(LibraryTab.Liked, R.string.library_tab_liked),
    TabSpec(LibraryTab.Favorites, R.string.library_tab_favorites),
    TabSpec(LibraryTab.History, R.string.library_tab_history),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenPlaylist: (Playlist) -> Unit,
    requestedTab: LibraryTab? = null,
    onTabRequestConsumed: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = LocalSongActions.current
    val colors = OrbitTheme.colors
    val pagerState = rememberPagerState(initialPage = state.selectedTab.ordinal) { tabs.size }
    val pullState = rememberPullToRefreshState()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.resultCode, result.data) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LibraryEvent.LaunchConsent -> consentLauncher.launch(
                IntentSenderRequest.Builder(event.pendingIntent.intentSender).build(),
            )
            is LibraryEvent.PlaylistCreated -> onOpenPlaylist(event.playlist)
        }
    }
    state.createPlaylist?.let { create ->
        CreatePlaylistSheet(
            state = create,
            onCreate = viewModel::onCreatePlaylist,
            onDismiss = viewModel::onDismissCreatePlaylist,
        )
    }

    // Home's "Liked songs" tile and "See all" open a specific tab.
    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            viewModel.selectTab(it)
            onTabRequestConsumed()
        }
    }
    // Keep chips and pager in sync in both directions.
    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab.ordinal) pagerState.animateScrollToPage(state.selectedTab.ordinal)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.selectTab(tabs[it].tab) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ScreenHeader(
            title = stringResource(R.string.library_title),
            trailing = {
                OrbitIconButton(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.create_playlist_title),
                    onClick = viewModel::onOpenCreatePlaylist,
                    style = IconButtonStyle.Tonal,
                )
                OrbitSpin(modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            },
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = OrbitDimens.ScreenPadding)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { spec ->
                OrbitChip(
                    label = stringResource(spec.label),
                    selected = state.selectedTab == spec.tab,
                    onClick = { viewModel.selectTab(spec.tab) },
                )
            }
        }
        if (state.isOffline) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { OfflineBanner() }
        }
        state.error?.let { error ->
            OrbitErrorBanner(
                error = error,
                onRetry = { viewModel.refresh(force = true) },
                onDismiss = viewModel::dismissError,
                modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(force = true) },
            state = pullState,
            modifier = Modifier.weight(1f),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = colors.surfaceRaised,
                    color = colors.accentText,
                )
            },
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val content = state.content
                when (tabs[page].tab) {
                    LibraryTab.Playlists -> PlaylistsPage(
                        playlists = content.playlists,
                        loading = state.isRefreshing,
                        contentPadding = contentPadding,
                        onOpen = onOpenPlaylist,
                    )
                    LibraryTab.Liked -> SongsPage(
                        songs = content.liked,
                        loading = state.isRefreshing,
                        isLoadingMore = state.isLoadingMore,
                        contentPadding = contentPadding,
                        emptyIcon = Icons.Rounded.ThumbUp,
                        emptyTitle = R.string.library_liked_empty_title,
                        emptyBody = R.string.library_liked_empty_body,
                        onPlay = { song -> actions.play(content.liked, song) },
                        onMenu = actions::showMenu,
                        onNearEnd = viewModel::loadMoreLiked,
                    )
                    LibraryTab.Favorites -> SongsPage(
                        songs = content.favorites,
                        loading = false,
                        contentPadding = contentPadding,
                        emptyIcon = Icons.Rounded.Favorite,
                        emptyTitle = R.string.library_favorites_empty_title,
                        emptyBody = R.string.library_favorites_empty_body,
                        onPlay = { song -> actions.play(content.favorites, song) },
                        onMenu = actions::showMenu,
                    )
                    LibraryTab.History -> SongsPage(
                        songs = content.history,
                        loading = false,
                        contentPadding = contentPadding,
                        emptyIcon = Icons.Rounded.History,
                        emptyTitle = R.string.library_history_empty_title,
                        emptyBody = R.string.library_history_empty_body,
                        onPlay = { song -> actions.play(content.history, song) },
                        onMenu = actions::showMenu,
                        header = {
                            SectionHeader(
                                title = stringResource(R.string.home_section_recent),
                                action = {
                                    OrbitTextButton(
                                        text = stringResource(R.string.library_clear_history),
                                        onClick = viewModel::clearListeningHistory,
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsPage(
    playlists: List<Playlist>,
    loading: Boolean,
    contentPadding: PaddingValues,
    onOpen: (Playlist) -> Unit,
) {
    when {
        playlists.isEmpty() && loading -> SongListSkeleton(Modifier.padding(top = 16.dp))
        playlists.isEmpty() -> EmptyPage(
            Icons.AutoMirrored.Rounded.QueueMusic,
            R.string.library_playlists_empty_title,
            R.string.library_playlists_empty_body,
            contentPadding,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OrbitDimens.ScreenPadding,
                end = OrbitDimens.ScreenPadding,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(playlist = playlist, onClick = { onOpen(playlist) }, width = null, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun SongsPage(
    songs: List<Song>,
    loading: Boolean,
    contentPadding: PaddingValues,
    emptyIcon: ImageVector,
    emptyTitle: Int,
    emptyBody: Int,
    onPlay: (Song) -> Unit,
    onMenu: (Song) -> Unit,
    isLoadingMore: Boolean = false,
    onNearEnd: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    if (onNearEnd != null) LoadMoreEffect(listState, onNearEnd)
    when {
        songs.isEmpty() && loading -> SongListSkeleton(Modifier.padding(top = 16.dp))
        songs.isEmpty() -> EmptyPage(emptyIcon, emptyTitle, emptyBody, contentPadding)
        else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            if (header != null) item(key = "header") { header() }
            items(songs, key = { it.videoId }) { song ->
                SongRow(
                    song = song,
                    onClick = { onPlay(song) },
                    onMoreClick = { onMenu(song) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (isLoadingMore) {
                item(key = "loadingMore") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OrbitLoader(size = 40.dp) }
                }
            }
        }
    }
}

@Composable
private fun EmptyPage(icon: ImageVector, title: Int, body: Int, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        // A scrollable container keeps pull-to-refresh working on empty pages.
        item {
            OrbitEmptyState(
                icon = icon,
                title = stringResource(title),
                body = stringResource(body),
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
