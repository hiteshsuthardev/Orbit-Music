package com.example.orbitmusic.ui.screens.showcase

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.components.CategoryTile
import com.example.orbitmusic.ui.components.OrbitEmptyState
import com.example.orbitmusic.ui.components.QuickPickTile
import com.example.orbitmusic.ui.components.SongCard
import com.example.orbitmusic.ui.components.SongRow
import com.example.orbitmusic.ui.components.orbit.LogoStyle
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.orbit.OrbitLogo
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.IconButtonStyle
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitBottomBar
import com.example.orbitmusic.ui.theme.orbit.OrbitChip
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPlayButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitPrimaryButton
import com.example.orbitmusic.ui.theme.orbit.OrbitSearchField
import com.example.orbitmusic.ui.theme.orbit.OrbitSeekBar
import com.example.orbitmusic.ui.theme.orbit.OrbitSegmented
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSwitch
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitTonalButton
import com.example.orbitmusic.ui.theme.orbit.OrbitWhiteButton
import com.example.orbitmusic.ui.theme.orbit.PlayButtonStyle
import com.example.orbitmusic.ui.theme.orbit.SettingsGroup
import com.example.orbitmusic.ui.theme.orbit.SettingsRow
import com.example.orbitmusic.ui.theme.orbit.brandMesh
import com.example.orbitmusic.ui.theme.orbit.rememberOrbitBottomBarItems
import com.example.orbitmusic.ui.theme.orbit.supportsBackdropBlur

/**
 * Design-system preview: every Orbit token and component, used to check the look in
 * light and dark. Demo state lives here because this screen has no business logic.
 */
@Composable
fun OrbitShowcaseScreen(
    isDark: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrbitTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Header(isDark, onDarkModeChange)
        TokensSection()
        TypeSection()
        ButtonsSection()
        ControlsSection()
        ListsSection()
        SettingsSection()
        StatesSection()
        BottomBarSection()
    }
}

@Composable
private fun Header(isDark: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    val colors = OrbitTheme.colors
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrbitLogo(size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.showcase_title), style = OrbitTheme.type.screenTitle, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.showcase_subtitle), style = OrbitTheme.type.secondary, color = colors.textSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.showcase_dark_mode), style = OrbitTheme.type.songTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            OrbitSwitch(checked = isDark, onCheckedChange = onDarkModeChange, contentDescription = stringResource(R.string.showcase_dark_mode))
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(title),
            style = OrbitTheme.type.sectionTitle,
            color = OrbitTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
        )
        Column(
            modifier = if (padded) Modifier.padding(horizontal = 20.dp) else Modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokensSection() = Section(R.string.showcase_section_palette) {
    val c = OrbitTheme.colors
    val swatches = listOf(
        R.string.showcase_swatch_background to c.background,
        R.string.showcase_swatch_surface to c.surface,
        R.string.showcase_swatch_raised to c.surfaceRaised,
        R.string.showcase_swatch_text to c.textPrimary,
        R.string.showcase_swatch_text_secondary to c.textSecondary,
        R.string.showcase_swatch_accent to c.accentText,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        swatches.forEach { (label, color) -> Swatch(stringResource(label), color) }
    }
    Box(Modifier.fillMaxWidth().height(56.dp).clip(OrbitShapes.GroupedList).background(BrandColors.Gradient), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.showcase_brand_gradient), style = OrbitTheme.type.smallLabel, color = BrandColors.OnBrand)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrandColors.Supporting.take(10).forEach { color ->
            Box(Modifier.size(24.dp).clip(OrbitShapes.IconTile).background(color))
        }
    }
}

@Composable
private fun Swatch(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(OrbitShapes.Artwork)
                .background(color)
                .border(1.dp, OrbitTheme.colors.outline, OrbitShapes.Artwork),
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = OrbitTheme.type.tabLabel, color = OrbitTheme.colors.textSecondary)
    }
}

@Composable
private fun TypeSection() = Section(R.string.showcase_section_typography) {
    val c = OrbitTheme.colors
    val t = OrbitTheme.type
    Text(stringResource(R.string.showcase_display), style = t.screenTitle, color = c.textPrimary)
    Text(stringResource(R.string.showcase_headline), style = t.sectionTitle, color = c.textPrimary)
    Text(stringResource(R.string.showcase_title_text), style = t.songTitle, color = c.textPrimary)
    Text(stringResource(R.string.showcase_body), style = t.secondary, color = c.textSecondary)
    Text(stringResource(R.string.showcase_caption), style = t.duration, color = c.textSecondary)
}

@Composable
private fun ButtonsSection() = Section(R.string.showcase_section_buttons) {
    var playing by rememberSaveable { mutableStateOf(false) }
    OrbitPrimaryButton(stringResource(R.string.playlist_play_all), {}, Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.PlayArrow)
    OrbitWhiteButton(stringResource(R.string.login_continue_with_google), {}, Modifier.fillMaxWidth())
    OrbitTonalButton(stringResource(R.string.playlist_shuffle), {}, Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Shuffle)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OrbitPlayButton(isPlaying = playing, onClick = { playing = !playing })
        Box(Modifier.clip(OrbitShapes.Pill).brandMesh().padding(8.dp)) {
            OrbitPlayButton(isPlaying = playing, onClick = { playing = !playing }, style = PlayButtonStyle.White, size = 56.dp)
        }
        OrbitIconButton(Icons.Rounded.Favorite, stringResource(R.string.cd_like), {}, style = IconButtonStyle.Tonal)
        OrbitIconButton(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.cd_queue), {}, style = IconButtonStyle.Gradient)
        OrbitIconButton(Icons.Rounded.MoreHoriz, stringResource(R.string.cd_more_options), {})
    }
}

@Composable
private fun ControlsSection() = Section(R.string.showcase_section_controls) {
    var query by rememberSaveable { mutableStateOf("") }
    var chip by rememberSaveable { mutableIntStateOf(0) }
    var progress by rememberSaveable { mutableFloatStateOf(0.35f) }
    var toggle by rememberSaveable { mutableStateOf(true) }
    var theme by rememberSaveable { mutableIntStateOf(1) }
    OrbitSearchField(query, { query = it }, stringResource(R.string.search_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.search_filter_songs, R.string.search_filter_playlists).forEachIndexed { i, label ->
            OrbitChip(stringResource(label), selected = chip == i, onClick = { chip = i })
        }
    }
    Box(Modifier.fillMaxWidth().clip(OrbitShapes.GroupedList).brandMesh().padding(16.dp)) {
        OrbitSeekBar(progress, { progress = it }, trackColor = OnColor.ChipFill)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.showcase_toggle_label), style = OrbitTheme.type.songTitle, color = OrbitTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        OrbitSwitch(toggle, { toggle = it }, contentDescription = stringResource(R.string.showcase_toggle_label))
    }
    OrbitSegmented(
        options = listOf(stringResource(R.string.settings_theme_light), stringResource(R.string.settings_theme_dark), stringResource(R.string.settings_theme_system)),
        selectedIndex = theme,
        onSelect = { theme = it },
    )
}

private val demoSongs = listOf("Midnight Drive" to "Nova Star", "Ocean Eyes" to "Lumen", "Golden Hour" to "Aster")
    .mapIndexed { i, (t, a) -> Song("demo$i", t, a, null, null, 180_000L + i * 21_000L, true) }

@Composable
private fun ListsSection() = Section(R.string.showcase_section_surfaces, padded = false) {
    demoSongs.forEachIndexed { i, song -> SongRow(song, onClick = {}, onMoreClick = {}, rank = i + 1, isCurrent = i == 0) }
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SongCard(demoSongs[1], onClick = {}, onLongClick = {})
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickPickTile(demoSongs[2], onClick = {}, onMoreClick = {}, modifier = Modifier.fillMaxWidth())
            CategoryTile(stringResource(R.string.showcase_category_chill), "chill", {}, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsSection() = Section(R.string.settings_section_playback) {
    var resume by rememberSaveable { mutableStateOf(true) }
    SettingsGroup(
        rows = listOf(
            { SettingsRow(Icons.Rounded.Bedtime, BrandColors.Supporting[1], stringResource(R.string.sleep_timer_title), subtitle = stringResource(R.string.sleep_timer_off), onClick = {}) },
            { SettingsRow(Icons.Rounded.PlayArrow, BrandColors.Supporting[8], stringResource(R.string.showcase_resume_session), trailing = { OrbitSwitch(resume, { resume = it }) }) },
            { SettingsRow(Icons.Rounded.Storage, BrandColors.Supporting[4], stringResource(R.string.showcase_clear_cache), onClick = {}) },
        ),
    )
}

@Composable
private fun StatesSection() = Section(R.string.showcase_section_orbit, padded = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        OrbitLoader()
        OrbitLogo(size = 64.dp)
        Box(Modifier.size(72.dp).clip(OrbitShapes.Circle).brandMesh(), contentAlignment = Alignment.Center) {
            OrbitLogo(size = 52.dp, style = LogoStyle.White)
        }
    }
    OrbitEmptyState(
        icon = Icons.Rounded.CloudOff,
        title = stringResource(R.string.error_no_internet),
        body = stringResource(R.string.offline_banner),
        actionLabel = stringResource(R.string.action_retry),
        onAction = {},
    )
}

@Composable
private fun BottomBarSection() = Section(R.string.showcase_section_bottom_bar, padded = false) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Text(
        stringResource(if (supportsBackdropBlur) R.string.showcase_blur_real else R.string.showcase_blur_fallback),
        style = OrbitTheme.type.secondary,
        color = OrbitTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    OrbitBottomBar(items = rememberOrbitBottomBarItems(), selectedIndex = selected, onItemSelected = { selected = it })
}

@OrbitPreviews
@Preview(name = "Tall", heightDp = 2800)
@Composable
private fun OrbitShowcasePreview() {
    val dark = isSystemInDarkTheme()
    OrbitMusicTheme(darkTheme = dark) {
        OrbitShowcaseScreen(isDark = dark, onDarkModeChange = {})
    }
}
