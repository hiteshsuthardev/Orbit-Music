package com.example.orbitmusic.ui.screens.playlist

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistDetail
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.common.LoadMoreEffect
import com.example.orbitmusic.ui.components.OfflineBanner
import com.example.orbitmusic.ui.components.OrbitArtwork
import com.example.orbitmusic.ui.components.OrbitEmptyState
import com.example.orbitmusic.ui.components.OrbitErrorBanner
import com.example.orbitmusic.ui.components.OrbitErrorState
import com.example.orbitmusic.ui.components.SongListSkeleton
import com.example.orbitmusic.ui.components.SongRow
import com.example.orbitmusic.ui.components.StaggeredAppear
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.previewSong
import com.example.orbitmusic.ui.player.rememberArtworkTint
import com.example.orbitmusic.ui.screens.songactions.LocalSongActions
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.IconButtonStyle
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPlayButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSheet
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.contentColorOn
import com.example.orbitmusic.ui.theme.orbit.floatingShadow

private val CoverSize = 216.dp
private const val TINT_FADE_FRACTION = 0.55f

@Composable
fun PlaylistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = LocalSongActions.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, viewModel::loadMore)

    PlaylistDetailContent(
        state = state,
        contentPadding = contentPadding,
        listState = listState,
        callbacks = PlaylistCallbacks(
            onBack = onBack,
            onRefresh = { viewModel.refresh(force = true) },
            onDismissError = viewModel::dismissError,
            onPlayAll = { viewModel.play(shuffle = false) },
            onShuffle = { viewModel.play(shuffle = true) },
            onShare = { playlist -> sharePlaylist(context, playlist) },
            onOpenInYouTube = { playlist -> context.startActivity(Intent(Intent.ACTION_VIEW, playlist.shareUrl.toUri())) },
            onPlaySong = { songs, song -> actions.play(songs, song) },
            onSongMenu = actions::showMenu,
        ),
    )
}

internal class PlaylistCallbacks(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onDismissError: () -> Unit,
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
    val onShare: (Playlist) -> Unit,
    val onOpenInYouTube: (Playlist) -> Unit,
    val onPlaySong: (List<Song>, Song) -> Unit,
    val onSongMenu: (Song) -> Unit,
)

/**
 * Artwork-tinted gradient (tint at the top to the background by ~55% of the height), round
 * translucent back and menu buttons, a centred 216dp cover with a deep shadow, title, meta,
 * then share (left) and shuffle + gradient play (right) above the song rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistDetailContent(
    state: PlaylistDetailUiState,
    contentPadding: PaddingValues,
    callbacks: PlaylistCallbacks,
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = OrbitTheme.colors
    val pullState = rememberPullToRefreshState()
    val detail = state.detail
    val tint = rememberArtworkTint(detail?.playlist?.thumbnailUrl)
    val onTint = contentColorOn(tint)
    var showMenu by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(maxHeight * TINT_FADE_FRACTION)
                .background(Brush.verticalGradient(listOf(tint, colors.background))),
        )
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            when {
                state.isInitialLoading -> Column(Modifier.padding(top = 72.dp)) { SongListSkeleton() }
                detail == null -> OrbitErrorState(
                    error = state.error ?: AppError.NotFound,
                    onRetry = callbacks.onRefresh,
                    fallbackTitle = R.string.playlist_error_title,
                    modifier = Modifier.padding(top = 72.dp),
                )
                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = callbacks.onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
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
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                        item(key = "header") {
                            PlaylistHeader(
                                detail = detail,
                                isPreparing = state.isPreparingPlayback,
                                onPlayAll = callbacks.onPlayAll,
                                onShuffle = callbacks.onShuffle,
                                onShare = { callbacks.onShare(detail.playlist) },
                            )
                        }
                        if (state.isOffline) {
                            item(key = "offline") {
                                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) { OfflineBanner() }
                            }
                        }
                        state.error?.let { error ->
                            item(key = "error") {
                                OrbitErrorBanner(
                                    error = error,
                                    onRetry = callbacks.onRefresh,
                                    onDismiss = callbacks.onDismissError,
                                    modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp),
                                )
                            }
                        }
                        if (detail.songs.isEmpty() && !state.isRefreshing) {
                            item(key = "empty") {
                                OrbitEmptyState(
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    title = stringResource(R.string.playlist_empty_title),
                                    body = stringResource(R.string.playlist_empty_body),
                                    actionLabel = stringResource(R.string.action_retry),
                                    onAction = callbacks.onRefresh,
                                )
                            }
                        }
                        itemsIndexed(detail.songs, key = { index, song -> "${song.videoId}#$index" }) { index, song ->
                            StaggeredAppear(index) {
                                SongRow(
                                    song = song,
                                    onClick = { callbacks.onPlaySong(detail.songs, song) },
                                    onMoreClick = { callbacks.onSongMenu(song) },
                                )
                            }
                        }
                        if (state.isLoadingMore) {
                            item(key = "loadingMore") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OrbitLoader(size = 40.dp) }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                OrbitIconButton(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = callbacks.onBack,
                    style = IconButtonStyle.Translucent,
                    tint = onTint,
                )
                Spacer(Modifier.weight(1f))
                if (detail != null) {
                    OrbitIconButton(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.cd_more_options),
                        onClick = { showMenu = true },
                        style = IconButtonStyle.Translucent,
                        tint = onTint,
                    )
                }
            }
        }
    }

    if (showMenu && detail != null) {
        PlaylistMenuSheet(
            onDismiss = { showMenu = false },
            onShare = {
                showMenu = false
                callbacks.onShare(detail.playlist)
            },
            onOpenInYouTube = {
                showMenu = false
                callbacks.onOpenInYouTube(detail.playlist)
            },
        )
    }
}

@Composable
internal fun PlaylistHeader(
    detail: PlaylistDetail,
    isPreparing: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = OrbitTheme.colors
    val playlist = detail.playlist
    val trackCount = playlist.itemCount.takeIf { it > 0 } ?: detail.songs.size
    val count = pluralStringResource(R.plurals.track_count, trackCount, trackCount)
    val duration = formatLongDuration(detail.totalDurationMs).let {
        if (detail.hasMore) stringResource(R.string.duration_more, it) else it
    }
    val canPlay = !isPreparing && detail.songs.any { it.isPlayable }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OrbitDimens.ScreenPadding)
            .padding(top = 64.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbitArtwork(
            url = playlist.thumbnailUrl,
            contentDescription = stringResource(R.string.cd_playlist_cover, playlist.title),
            placeholderKey = playlist.id,
            shape = OrbitShapes.PlaylistCover,
            placeholderIcon = Icons.AutoMirrored.Rounded.QueueMusic,
            modifier = Modifier
                .size(CoverSize)
                .floatingShadow(32.dp, OrbitShapes.PlaylistCover, alpha = 0.55f),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = playlist.title,
            style = OrbitTheme.type.nowPlayingTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        val meta = if (detail.totalDurationMs > 0) stringResource(R.string.playlist_meta, count, duration) else count
        val owner = playlist.ownerName?.takeIf { it.isNotBlank() }
        Text(
            text = if (owner != null) stringResource(R.string.playlist_meta, owner, meta) else meta,
            style = OrbitTheme.type.secondary,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (playlist.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = playlist.description,
                style = OrbitTheme.type.secondary,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .animateContentSize()
                    .clickable(role = Role.Button) { descriptionExpanded = !descriptionExpanded },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OrbitIconButton(
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.playlist_share),
                onClick = onShare,
                style = IconButtonStyle.Tonal,
            )
            Spacer(Modifier.weight(1f))
            OrbitIconButton(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.playlist_shuffle),
                onClick = onShuffle,
                style = IconButtonStyle.Tonal,
                enabled = canPlay,
            )
            Spacer(Modifier.width(12.dp))
            if (isPreparing) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) { OrbitLoader(size = 40.dp) }
            } else {
                OrbitPlayButton(isPlaying = false, onClick = onPlayAll, size = 58.dp, enabled = canPlay)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlaylistMenuSheet(onDismiss: () -> Unit, onShare: () -> Unit, onOpenInYouTube: () -> Unit) {
    OrbitSheet(onDismissRequest = onDismiss) {
        MenuRow(Icons.Rounded.Share, stringResource(R.string.playlist_share), onShare)
        MenuRow(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.playlist_open_in_youtube), onOpenInYouTube)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = OrbitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = OrbitDimens.ScreenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary)
        Spacer(Modifier.width(18.dp))
        Text(label, style = OrbitTheme.type.songTitle, color = colors.textPrimary)
    }
}

private fun sharePlaylist(context: Context, playlist: Playlist) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, playlist.title)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_playlist_text, playlist.title, playlist.shareUrl))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
}

/** "1 hr 5 min" / "42 min". */
@Composable
fun formatLongDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
    }
}

internal val noPlaylistCallbacks = PlaylistCallbacks({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})

@OrbitPreviews
@Composable
private fun PlaylistDetailPreview() {
    val songs = List(6) { previewSong.copy(videoId = "pl-$it", title = "Track ${it + 1}") }
    val playlist = Playlist("preview", "Late night lofi", "", null, songs.size, "Nova")
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        PlaylistDetailContent(
            state = PlaylistDetailUiState(detail = PlaylistDetail(playlist, songs, hasMore = false)),
            contentPadding = PaddingValues(),
            callbacks = noPlaylistCallbacks,
        )
    }
}
