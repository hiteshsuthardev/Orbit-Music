package com.example.orbitmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.player.SleepTimerOptionsMinutes
import com.example.orbitmusic.domain.player.SleepTimerState
import com.example.orbitmusic.domain.player.SupportedPlaybackSpeeds
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OrbitChip
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSheet
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitWhiteButton
import com.example.orbitmusic.ui.theme.orbit.rememberPressScale
import com.example.orbitmusic.ui.theme.orbit.scaled
import com.example.orbitmusic.util.formatTrackTime

/** "End of this song" is stored as this sentinel in the sheet's selection. */
private const val END_OF_SONG = -1
private const val DEFAULT_MINUTES = 30

/**
 * Sleep timer: 15/30/45/60 min in a 2x2 grid plus "End of this song". Picking an option
 * only selects it; the white button (whose label follows the selection) starts it.
 */
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStart: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitSheet(onDismissRequest = onDismiss) {
        SleepTimerContent(
            state = state,
            onStart = {
                onStart(it)
                onDismiss()
            },
            onEndOfSong = {
                onEndOfSong()
                onDismiss()
            },
            onCancel = {
                onCancel()
                onDismiss()
            },
        )
    }
}

@Composable
internal fun ColumnScope.SleepTimerContent(
    state: SleepTimerState,
    onStart: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    onCancel: () -> Unit,
) {
    val initial = when (state) {
        SleepTimerState.EndOfTrack -> END_OF_SONG
        is SleepTimerState.Running -> SleepTimerOptionsMinutes.firstOrNull { it * 60_000L == state.totalMs } ?: DEFAULT_MINUTES
        SleepTimerState.Off -> DEFAULT_MINUTES
    }
    var selection by rememberSaveable { mutableStateOf(initial) }

    SheetTitle(stringResource(R.string.sleep_timer_title))
    Text(
        text = when (state) {
            SleepTimerState.Off -> stringResource(R.string.sleep_timer_off)
            SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_timer_end_of_song_status)
            is SleepTimerState.Running -> stringResource(R.string.sleep_timer_running, formatTrackTime(state.remainingMs))
        },
        style = OrbitTheme.type.secondary,
        color = OrbitTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding),
    )
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier.padding(horizontal = OrbitDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SleepTimerOptionsMinutes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { minutes ->
                    OptionTile(
                        label = stringResource(R.string.sleep_timer_minutes, minutes),
                        selected = selection == minutes,
                        onClick = { selection = minutes },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        OptionTile(
            label = stringResource(R.string.sleep_timer_end_of_this_song),
            selected = selection == END_OF_SONG,
            onClick = { selection = END_OF_SONG },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OrbitWhiteButton(
            text = if (selection == END_OF_SONG) {
                stringResource(R.string.sleep_timer_start_end_of_song)
            } else {
                stringResource(R.string.sleep_timer_start_minutes, selection)
            },
            onClick = { if (selection == END_OF_SONG) onEndOfSong() else onStart(selection) },
            leadingIcon = Icons.Rounded.Bedtime,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state != SleepTimerState.Off) {
            OrbitTextButton(
                text = stringResource(R.string.sleep_timer_cancel),
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
}

/** 56dp option: surfaceRaised, or the brand gradient with a check when selected. */
@Composable
private fun OptionTile(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OrbitTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    val content = if (selected) BrandColors.OnBrand else colors.textPrimary
    Row(
        modifier = modifier
            .height(56.dp)
            .scaled { scale.value }
            .clip(OrbitShapes.CategoryTile)
            .background(if (selected) BrandColors.Gradient else SolidColor(colors.surfaceRaised))
            .clickable(interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = OrbitTheme.type.button, color = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(R.string.playback_speed_title))
        FlowRow(
            modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding).padding(top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SupportedPlaybackSpeeds.forEach { speed ->
                OrbitChip(
                    label = formatSpeed(speed),
                    selected = speed == currentSpeed,
                    onClick = {
                        onSelect(speed)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
internal fun SheetTitle(title: String) {
    Text(
        text = title,
        style = OrbitTheme.type.sectionTitle,
        color = OrbitTheme.colors.textPrimary,
        modifier = Modifier
            .padding(horizontal = OrbitDimens.ScreenPadding)
            .padding(top = 8.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

@OrbitPreviews
@Composable
private fun SleepTimerPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Column(Modifier.background(OrbitTheme.colors.surface)) {
            SleepTimerContent(state = SleepTimerState.Off, onStart = {}, onEndOfSong = {}, onCancel = {})
        }
    }
}
