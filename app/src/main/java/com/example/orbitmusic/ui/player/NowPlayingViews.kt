package com.example.orbitmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.Lyrics
import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.ui.common.messageRes
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme

private val viewOptions = listOf(
    NowPlayingView.Poster to R.string.now_playing_view_poster,
    NowPlayingView.Video to R.string.now_playing_view_video,
    NowPlayingView.Lyrics to R.string.now_playing_view_lyrics,
)

/** Inactive lyric lines: white at 50% still reads at 4.5:1+ on the dark Now Playing scrim. */
private const val INACTIVE_LYRIC_ALPHA = 0.5f

/** Poster / Video / Lyrics switch, on-colour style (white pill for the selected option). */
@Composable
internal fun NowPlayingViewSwitch(
    selected: NowPlayingView,
    onSelect: (NowPlayingView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(OrbitShapes.Pill)
            .background(OnColor.ChipFill)
            .padding(4.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        viewOptions.forEach { (view, label) ->
            val isSelected = view == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(OrbitShapes.Pill)
                    .background(if (isSelected) OnColor.Primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(view) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(label),
                    style = OrbitTheme.type.smallLabel,
                    color = if (isSelected) BrandColors.OnWhitePlay else OnColor.Primary,
                )
            }
        }
    }
}

/**
 * Lyrics for the current song. Time-synced lyrics highlight and follow the line being sung
 * (tap a line to jump there); plain lyrics are a simple scrolling text. [bottomInset] keeps
 * the last lines clear of the small video tile.
 */
@Composable
internal fun LyricsPanel(
    state: LyricsUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LyricsUiState.Idle, LyricsUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            OrbitLoader(ringColor = OnColor.ChipFill, dotColor = OnColor.Primary)
        }
        LyricsUiState.NotFound -> LyricsMessage(
            title = stringResource(R.string.lyrics_not_found_title),
            body = stringResource(R.string.lyrics_not_found_body),
            modifier = modifier,
        )
        is LyricsUiState.Failed -> LyricsMessage(
            title = stringResource(R.string.lyrics_error_title),
            body = stringResource(state.error.messageRes()),
            modifier = modifier,
            action = { OrbitTextButton(stringResource(R.string.action_retry), onRetry, color = OnColor.Primary) },
        )
        is LyricsUiState.Found -> LyricsList(state.lyrics, positionMs, onSeek, bottomInset, modifier)
    }
}

@Composable
private fun LyricsList(
    lyrics: Lyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val synced = lyrics.isSynced
    val active = if (synced) lyrics.activeLineIndex(positionMs) else -1
    val reduceMotion = OrbitTheme.reduceMotion
    val seekLabel = stringResource(R.string.lyrics_play_from_here)

    LaunchedEffect(active) {
        if (active >= 0) {
            if (reduceMotion) listState.scrollToItem(active) else listState.animateScrollToItem(active)
        }
    }

    BoxWithConstraints(modifier) {
        // Top padding puts the followed line about a third of the way down.
        val topPadding = if (synced) maxHeight / 3 else 8.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomInset + 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (synced) 14.dp else 6.dp),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isActive = index == active
                val time = line.timeMs
                Text(
                    text = line.text.ifBlank { stringResource(R.string.lyrics_instrumental_mark) },
                    style = if (synced) OrbitTheme.type.sectionTitle else OrbitTheme.type.nowPlayingArtist,
                    color = when {
                        !synced || isActive -> OnColor.Primary
                        else -> OnColor.Primary.copy(alpha = INACTIVE_LYRIC_ALPHA)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (time != null) {
                                Modifier.clickable(role = Role.Button, onClickLabel = seekLabel) { onSeek(time) }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            item(key = "source") {
                Text(
                    text = stringResource(R.string.lyrics_source, lyrics.source),
                    style = OrbitTheme.type.tabLabel,
                    color = OnColor.Secondary,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = OrbitTheme.type.sectionTitle,
            color = OnColor.Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(body, style = OrbitTheme.type.secondary, color = OnColor.Secondary, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Floor for the small live-video tile in Poster and Lyrics views (see [minimumPlayerSize]). */
internal val VideoTileFloor = 104.dp
