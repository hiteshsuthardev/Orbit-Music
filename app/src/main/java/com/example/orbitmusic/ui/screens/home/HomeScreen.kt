package com.example.orbitmusic.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.components.OfflineBanner
import com.example.orbitmusic.ui.components.OrbitArtwork
import com.example.orbitmusic.ui.components.OrbitEmptyState
import com.example.orbitmusic.ui.components.OrbitErrorBanner
import com.example.orbitmusic.ui.components.OrbitErrorState
import com.example.orbitmusic.ui.components.PlaylistCard
import com.example.orbitmusic.ui.components.QuickPickTile
import com.example.orbitmusic.ui.components.ScreenHeader
import com.example.orbitmusic.ui.components.SectionHeader
import com.example.orbitmusic.ui.components.SongCard
import com.example.orbitmusic.ui.components.SongListSkeleton
import com.example.orbitmusic.ui.components.SongRow
import com.example.orbitmusic.ui.components.StaggeredAppear
import com.example.orbitmusic.ui.components.orbit.OrbitSpin
import com.example.orbitmusic.ui.components.previewSong
import com.example.orbitmusic.ui.screens.songactions.LocalSongActions
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPlayButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.PlayButtonStyle
import com.example.orbitmusic.ui.theme.orbit.rememberPressScale
import com.example.orbitmusic.ui.theme.orbit.scaled

private val RowContentPadding = PaddingValues(horizontal = OrbitDimens.ScreenPadding)
private val HeroHeight = 196.dp
private val TopTintHeight = 300.dp
private const val TOP_TINT_ALPHA = 0.26f
private const val QUICK_PICK_COUNT = 5
private const val TRENDING_COUNT = 10

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenLiked: () -> Unit,
    onSeeAllPlaylists: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = LocalSongActions.current
    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onRefresh = { viewModel.refresh(force = true) },
        onDismissError = viewModel::dismissError,
        onOpenPlaylist = onOpenPlaylist,
        onOpenLiked = onOpenLiked,
        onSeeAllPlaylists = onSeeAllPlaylists,
        onPlaySong = { list, song -> actions.play(list, song) },
        onSongMenu = actions::showMenu,
    )
}

/**
 * Greeting + avatar, hero card, 2-column quick picks (Liked songs first), Recently played
 * and Your playlists carousels, and a ranked Trending list, over a soft Rose wash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenLiked: () -> Unit,
    onSeeAllPlaylists: () -> Unit,
    onPlaySong: (List<Song>, Song) -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    val colors = OrbitTheme.colors
    val pullState = rememberPullToRefreshState()
    val sections = state.sections
    val showRefresh = state.isRefreshing && !state.isInitialLoading

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Rose wash at 26% that fades out within the first 300dp.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TopTintHeight)
                .background(
                    Brush.verticalGradient(listOf(BrandColors.Rose.copy(alpha = TOP_TINT_ALPHA), Color.Transparent)),
                ),
        )
        PullToRefreshBox(
            isRefreshing = showRefresh,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = showRefresh,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = colors.surfaceRaised,
                    color = colors.accentText,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") {
                    HomeHeader(greeting = state.greeting)
                }
                if (state.isOffline) {
                    item(key = "offline") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { OfflineBanner() }
                    }
                }
                // With nothing cached the full-screen error state below replaces the banner.
                state.error?.takeUnless { sections.isEmpty }?.let { error ->
                    item(key = "error") {
                        OrbitErrorBanner(
                            error = error,
                            onRetry = onRefresh,
                            onDismiss = onDismissError,
                            modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding),
                        )
                    }
                }

                if (state.isInitialLoading) {
                    item(key = "skeleton") { SongListSkeleton(Modifier.padding(top = 8.dp)) }
                    return@LazyColumn
                }
                if (sections.isEmpty && state.error != null) {
                    item(key = "errorState") {
                        OrbitErrorState(error = state.error, onRetry = onRefresh, modifier = Modifier.padding(top = 32.dp))
                    }
                    return@LazyColumn
                }
                if (sections.isEmpty) {
                    item(key = "empty") {
                        OrbitEmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(R.string.home_empty_body),
                            modifier = Modifier.padding(top = 32.dp),
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = onRefresh,
                        )
                    }
                    return@LazyColumn
                }

                var sectionIndex = 0
                state.hero?.let { hero ->
                    val index = sectionIndex++
                    item(key = "hero") {
                        StaggeredAppear(index) {
                            HeroCard(hero = hero, onPlay = { onPlaySong(hero.queue, hero.song) })
                        }
                    }
                }
                run {
                    val index = sectionIndex++
                    item(key = "quickGrid") {
                        StaggeredAppear(index) {
                            QuickGrid(
                                songs = sections.quickPicks.take(QUICK_PICK_COUNT),
                                onOpenLiked = onOpenLiked,
                                onPlay = onPlaySong,
                                onMenu = onSongMenu,
                            )
                        }
                    }
                }
                if (sections.recentlyPlayed.isNotEmpty()) {
                    val index = sectionIndex++
                    item(key = "recent") {
                        StaggeredAppear(index) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionHeader(title = stringResource(R.string.home_section_recent))
                                LazyRow(
                                    contentPadding = RowContentPadding,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(sections.recentlyPlayed, key = { it.videoId }) { song ->
                                        SongCard(
                                            song = song,
                                            onClick = { onPlaySong(sections.recentlyPlayed, song) },
                                            onLongClick = { onSongMenu(song) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (sections.playlists.isNotEmpty()) {
                    val index = sectionIndex++
                    item(key = "playlists") {
                        StaggeredAppear(index) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionHeader(
                                    title = stringResource(R.string.home_section_playlists),
                                    action = {
                                        OrbitTextButton(text = stringResource(R.string.action_see_all), onClick = onSeeAllPlaylists)
                                    },
                                )
                                LazyRow(
                                    contentPadding = RowContentPadding,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(sections.playlists, key = { it.id }) { playlist ->
                                        PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
                                    }
                                }
                            }
                        }
                    }
                }
                if (sections.trending.isNotEmpty()) {
                    val index = sectionIndex
                    val top = sections.trending.take(TRENDING_COUNT)
                    item(key = "trendingHeader") {
                        StaggeredAppear(index) { SectionHeader(title = stringResource(R.string.home_section_trending)) }
                    }
                    itemsIndexed(top, key = { _, song -> "trending-${song.videoId}" }) { rank, song ->
                        StaggeredAppear(index) {
                            SongRow(
                                song = song,
                                rank = rank + 1,
                                onClick = { onPlaySong(top, song) },
                                onMoreClick = { onSongMenu(song) },
                            )
                        }
                    }
                }
                item(key = "bottomSpace") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun HomeHeader(greeting: Greeting) {
    ScreenHeader(
        title = stringResource(
            when (greeting) {
                Greeting.Morning -> R.string.home_greeting_morning
                Greeting.Afternoon -> R.string.home_greeting_afternoon
                Greeting.Evening -> R.string.home_greeting_evening
                Greeting.Night -> R.string.home_greeting_night
            },
        ),
        trailing = { OrbitSpin(modifier = Modifier.padding(end = 8.dp)) },
    )
}

/** 196dp hero: artwork (or mesh) under a bottom scrim, white text and a white play button. */
@Composable
private fun HeroCard(hero: HomeHero, onPlay: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    val eyebrow = stringResource(
        when (hero.kind) {
            HeroKind.Continue -> R.string.home_hero_continue
            HeroKind.Trending -> R.string.home_hero_trending
        },
    )
    Box(
        Modifier
            .padding(horizontal = OrbitDimens.ScreenPadding)
            .fillMaxWidth()
            .height(HeroHeight)
            .scaled { scale.value }
            .clip(OrbitShapes.HeroCard)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.cd_play),
                onClick = onPlay,
            ),
    ) {
        OrbitArtwork(
            url = hero.song.thumbnailUrl,
            contentDescription = null,
            placeholderKey = hero.song.videoId,
            shape = OrbitShapes.HeroCard,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to OnColor.Scrim.copy(alpha = 0.05f),
                        0.45f to OnColor.Scrim.copy(alpha = 0.3f),
                        1f to OnColor.Scrim.copy(alpha = 0.82f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(eyebrow, style = OrbitTheme.type.smallLabel, color = OnColor.Secondary)
                Text(
                    text = hero.song.title,
                    style = OrbitTheme.type.sectionTitle,
                    color = OnColor.Primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hero.song.artist,
                    style = OrbitTheme.type.secondary,
                    color = OnColor.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(12.dp))
            OrbitPlayButton(isPlaying = false, onClick = onPlay, size = 52.dp, style = PlayButtonStyle.White)
        }
    }
}

/** Two-column grid: the gradient "Liked songs" tile first, then up to five quick picks. */
@Composable
private fun QuickGrid(
    songs: List<Song>,
    onOpenLiked: () -> Unit,
    onPlay: (List<Song>, Song) -> Unit,
    onMenu: (Song) -> Unit,
) {
    val likedLabel = stringResource(R.string.library_tab_liked)
    val tiles: List<@Composable (Modifier) -> Unit> = buildList {
        add { modifier ->
            QuickPickTile(
                title = likedLabel,
                onClick = onOpenLiked,
                modifier = modifier,
                leading = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(BrandColors.Gradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = BrandColors.OnBrand, modifier = Modifier.size(24.dp))
                    }
                },
            )
        }
        songs.forEach { song ->
            add { modifier -> QuickPickTile(song = song, onClick = { onPlay(songs, song) }, onMoreClick = { onMenu(song) }, modifier = modifier) }
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OrbitPreviews
@Composable
private fun HomePreview() {
    val songs = List(8) { previewSong.copy(videoId = "home-$it", title = "Song ${it + 1}") }
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        HomeContent(
            state = HomeUiState(
                greeting = Greeting.Evening,
                sections = HomeSections(recentlyPlayed = songs, quickPicks = songs, trending = songs),
            ),
            contentPadding = PaddingValues(),
            onRefresh = {},
            onDismissError = {},
            onOpenPlaylist = {},
            onOpenLiked = {},
            onSeeAllPlaylists = {},
            onPlaySong = { _, _ -> },
            onSongMenu = {},
        )
    }
}
