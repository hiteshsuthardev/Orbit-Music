package com.example.orbitmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.domain.player.RepeatMode
import com.example.orbitmusic.domain.player.SleepTimerState
import com.example.orbitmusic.ui.components.OrbitArtwork
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.IconButtonStyle
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitChip
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPlayButton
import com.example.orbitmusic.ui.theme.orbit.OrbitSeekBar
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.PlayButtonStyle
import com.example.orbitmusic.ui.theme.orbit.floatingShadow
import com.example.orbitmusic.ui.theme.orbit.meshGradient
import com.example.orbitmusic.util.formatTrackTime

/** Callbacks for everything on the player sheet. */
interface PlayerActions {
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun toggleFavorite()
    fun share()
    fun openMenu()
    fun selectView(view: NowPlayingView)
    fun retryLyrics()
    fun openQueue()
    fun openSleepTimer()
    fun openSpeed()
}

/** Largest Now Playing cover; smaller screens use the width minus the side margins. */
internal val NowPlayingMaxArtSize = 334.dp
private val NowPlayingSidePadding = 24.dp
private val PlayButtonSize = 74.dp

/**
 * Full-bleed Now Playing background: soft radial blobs of the artwork swatches on a dark
 * base, under a dark scrim (5% top, 45% middle, 86% bottom) so white text always reads.
 */
@Composable
internal fun NowPlayingBackground(palette: ArtworkPalette, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .meshGradient(BrandColors.NowPlayingBase, palette.blobs, seed = 1)
            .background(
                Brush.verticalGradient(
                    0f to OnColor.Scrim.copy(alpha = 0.05f),
                    0.5f to OnColor.Scrim.copy(alpha = 0.45f),
                    1f to OnColor.Scrim.copy(alpha = 0.86f),
                ),
            ),
    )
}

/**
 * Expanded Now Playing layout. The main area shows the chosen view:
 * - Video: [videoSlot], a large empty measured box the live YouTube player is drawn over
 *   (by the sheet, so it can morph from the mini player);
 * - Poster: the artwork only;
 * - Lyrics: the lyrics only.
 * In Poster and Lyrics the sheet keeps the player running, invisible, in [videoTileSlot]
 * (kept at the player's minimum size so playback isn't interrupted). Always on the
 * colourful background, so it uses the white on-colour tokens.
 */
@Composable
internal fun NowPlayingContent(
    state: PlayerUiState,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    coverScale: Float,
    videoSlot: @Composable (Modifier) -> Unit,
    videoTileSlot: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = state.player
    val song = player.currentSong ?: return
    val type = OrbitTheme.type
    val tileSize = minimumPlayerSize(floor = VideoTileFloor)

    Column(
        modifier = modifier.padding(horizontal = NowPlayingSidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(state, onCollapse = onCollapse, onMenu = actions::openMenu)
        NowPlayingViewSwitch(
            selected = state.nowPlayingView,
            onSelect = actions::selectView,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val coverSize = minOf(maxWidth, maxHeight, NowPlayingMaxArtSize)
            when (state.nowPlayingView) {
                NowPlayingView.Video -> videoSlot(Modifier.size(coverSize))
                NowPlayingView.Poster -> Box(
                    Modifier
                        .size(coverSize)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        },
                ) {
                    OrbitArtwork(
                        url = song.thumbnailUrl,
                        contentDescription = stringResource(R.string.cd_album_art),
                        placeholderKey = song.videoId,
                        shape = OrbitShapes.NowPlayingCover,
                        modifier = Modifier
                            .fillMaxSize()
                            .floatingShadow(28.dp, OrbitShapes.NowPlayingCover, alpha = 0.5f),
                    )
                    videoTileSlot(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(tileSize),
                    )
                }
                NowPlayingView.Lyrics -> Box(Modifier.fillMaxSize()) {
                    LyricsPanel(
                        state = state.lyrics,
                        positionMs = player.positionMs,
                        onSeek = actions::seekTo,
                        onRetry = actions::retryLyrics,
                        modifier = Modifier.fillMaxSize(),
                    )
                    videoTileSlot(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(tileSize),
                    )
                }
            }
        }

        // Title, artist, like
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = type.nowPlayingTitle,
                    color = OnColor.Primary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = song.artist,
                    style = type.nowPlayingArtist,
                    color = OnColor.Secondary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
            }
            Spacer(Modifier.width(12.dp))
            OrbitIconButton(
                imageVector = if (state.isCurrentFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(
                    if (state.isCurrentFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                ),
                onClick = actions::toggleFavorite,
                tint = if (state.isCurrentFavorite) BrandColors.ActiveControl else OnColor.Primary,
            )
        }

        Spacer(Modifier.height(18.dp))
        SeekBar(positionMs = player.positionMs, durationMs = player.durationMs, onSeek = actions::seekTo)
        Spacer(Modifier.height(12.dp))
        TransportControls(state, actions)
        Spacer(Modifier.height(24.dp))
        ExtrasRow(state, actions)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TopBar(state: PlayerUiState, onCollapse: () -> Unit, onMenu: () -> Unit) {
    val queue = state.player.queue
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OrbitIconButton(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_collapse_player),
            onClick = onCollapse,
            tint = OnColor.Primary,
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.now_playing_from),
                style = OrbitTheme.type.tabLabel,
                color = OnColor.Secondary,
            )
            Text(
                text = if (queue.items.size > 1) {
                    stringResource(R.string.now_playing_queue_position, queue.currentIndex + 1, queue.items.size)
                } else {
                    stringResource(R.string.now_playing_your_queue)
                },
                style = OrbitTheme.type.smallLabel,
                color = OnColor.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        OrbitIconButton(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.cd_more_options),
            onClick = onMenu,
            tint = OnColor.Primary,
        )
    }
}

@Composable
private fun TransportControls(state: PlayerUiState, actions: PlayerActions) {
    val queue = state.player.queue
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shuffleOn = queue.shuffleEnabled
        OrbitIconButton(
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = stringResource(if (shuffleOn) R.string.cd_shuffle_on else R.string.cd_shuffle_off),
            onClick = actions::toggleShuffle,
            tint = if (shuffleOn) BrandColors.ActiveControl else OnColor.Primary,
        )
        OrbitIconButton(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.cd_previous),
            onClick = actions::previous,
            size = 56.dp,
            tint = OnColor.Primary,
        )
        OrbitPlayButton(
            isPlaying = state.player.playWhenReady,
            onClick = actions::togglePlayPause,
            size = PlayButtonSize,
            style = PlayButtonStyle.White,
        )
        OrbitIconButton(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next),
            onClick = actions::next,
            size = 56.dp,
            tint = OnColor.Primary,
        )
        val repeat = queue.repeatMode
        OrbitIconButton(
            imageVector = if (repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = stringResource(
                when (repeat) {
                    RepeatMode.Off -> R.string.cd_repeat_off
                    RepeatMode.All -> R.string.cd_repeat_all
                    RepeatMode.One -> R.string.cd_repeat_one
                },
            ),
            onClick = actions::cycleRepeat,
            tint = if (repeat != RepeatMode.Off) BrandColors.ActiveControl else OnColor.Primary,
        )
    }
}

/** Speed chip, sleep timer, queue and share. */
@Composable
private fun ExtrasRow(state: PlayerUiState, actions: PlayerActions) {
    val speed = state.player.playbackSpeed
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitChip(
            label = formatSpeed(speed),
            selected = speed != 1f,
            onClick = actions::openSpeed,
            leadingIcon = Icons.Rounded.Speed,
            onColorful = true,
        )
        if (state.sleepTimer == SleepTimerState.Off) {
            OrbitIconButton(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = stringResource(R.string.sleep_timer),
                onClick = actions::openSleepTimer,
                style = IconButtonStyle.Translucent,
            )
        } else {
            OrbitChip(
                label = sleepTimerLabel(state.sleepTimer),
                selected = true,
                onClick = actions::openSleepTimer,
                leadingIcon = Icons.Rounded.Bedtime,
                onColorful = true,
            )
        }
        OrbitIconButton(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = stringResource(R.string.cd_queue),
            onClick = actions::openQueue,
            style = IconButtonStyle.Translucent,
        )
        OrbitIconButton(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(R.string.action_share),
            onClick = actions::share,
            style = IconButtonStyle.Translucent,
        )
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownPosition = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs

    Column(Modifier.fillMaxWidth()) {
        OrbitSeekBar(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0,
            activeColor = OnColor.Primary,
            trackColor = OnColor.ChipFill,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text(formatTrackTime(shownPosition), style = OrbitTheme.type.duration, color = OnColor.Secondary)
            Spacer(Modifier.weight(1f))
            Text(formatTrackTime(durationMs), style = OrbitTheme.type.duration, color = OnColor.Secondary)
        }
    }
}

@Composable
internal fun sleepTimerLabel(state: SleepTimerState): String = when (state) {
    SleepTimerState.Off -> stringResource(R.string.sleep_timer)
    SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_timer_end_of_song_short)
    is SleepTimerState.Running -> formatTrackTime(state.remainingMs)
}

internal fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
