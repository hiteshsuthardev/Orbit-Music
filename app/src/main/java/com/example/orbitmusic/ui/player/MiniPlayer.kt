package com.example.orbitmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.player.PlayerState
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme

/** Collapsed player height (spec: 60dp). */
val MiniPlayerHeight = 60.dp

/** Artwork tile inside the mini player (spec: 44dp). */
val MiniPlayerArtSize = 44.dp

/**
 * Collapsed player row: 44dp artwork slot, title, artist, play/pause, next and a 2.5dp
 * progress line along the bottom edge. Tap (or swipe up on the sheet) expands it.
 */
@Composable
internal fun MiniPlayerContent(
    state: PlayerState,
    tileSize: Dp,
    contentColor: Color,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    tileSlot: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return
    val progressDescription = stringResource(R.string.cd_playback_progress)
    val expandLabel = stringResource(R.string.cd_expand_player)
    val secondary = contentColor.copy(alpha = 0.74f)

    Box(
        modifier
            .height(MiniPlayerHeight)
            .clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onExpand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tileSlot(Modifier.size(tileSize))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = OrbitTheme.type.songTitle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = OrbitTheme.type.secondary,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OrbitIconButton(
                imageVector = if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (state.playWhenReady) R.string.cd_pause else R.string.cd_play),
                onClick = onTogglePlay,
                tint = contentColor,
            )
            OrbitIconButton(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                onClick = onNext,
                tint = contentColor,
            )
        }
        // 2.5dp progress line along the bottom edge.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.5.dp)
                .background(contentColor.copy(alpha = 0.18f))
                .semantics {
                    contentDescription = progressDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(state.progress)
                    .height(2.5.dp)
                    .background(contentColor),
            )
        }
    }
}
