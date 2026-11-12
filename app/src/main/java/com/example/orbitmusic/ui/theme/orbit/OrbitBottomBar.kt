package com.example.orbitmusic.ui.theme.orbit

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Immutable
data class OrbitBottomBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** Height of the bar's content (excluding the navigation bar inset). */
val OrbitBottomBarHeight = 64.dp

/**
 * Full-width frosted bottom bar: 4 items with icon above label, selected item in
 * accentText, 1dp top divider.
 *
 * Frosting: with a [hazeState] (whose source is the scrolling content) we use the Haze
 * library, which renders a real backdrop blur via RenderEffect on API 31+. Below API 31
 * Haze cannot blur, and its fallback tint is the background colour at 92% alpha, as
 * specified. Without a hazeState the bar simply uses that 92% background.
 */
@Composable
fun OrbitBottomBar(
    items: List<OrbitBottomBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val colors = OrbitTheme.colors
    val fallback = colors.background.copy(alpha = 0.92f)
    val frosted = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(colors.background.copy(alpha = if (colors.isDark) 0.62f else 0.7f)),
                blurRadius = 28.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(fallback),
            ),
        )
    } else {
        Modifier.background(fallback)
    }
    Column(modifier.fillMaxWidth().then(frosted)) {
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(OrbitBottomBarHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                BottomBarEntry(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onItemSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun BottomBarEntry(
    item: OrbitBottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrbitTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, pressedScale = 0.9f)
    val tint by animateColorAsState(if (selected) colors.accentText else colors.textSecondary, label = "barTint")
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .scaled { scale.value },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = null, // The label describes the item.
            tint = tint,
            modifier = Modifier.size(25.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(item.label, style = OrbitTheme.type.tabLabel, color = tint, maxLines = 1)
    }
}

/** The four destinations, with labels from string resources. */
@Composable
fun rememberOrbitBottomBarItems(): List<OrbitBottomBarItem> {
    val home = stringResource(R.string.nav_home)
    val search = stringResource(R.string.nav_search)
    val library = stringResource(R.string.nav_library)
    val settings = stringResource(R.string.nav_settings)
    return remember(home, search, library, settings) {
        listOf(
            OrbitBottomBarItem(home, Icons.Outlined.Home, Icons.Rounded.Home),
            OrbitBottomBarItem(search, Icons.Outlined.Search, Icons.Rounded.Search),
            OrbitBottomBarItem(library, Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
            OrbitBottomBarItem(settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
        )
    }
}

/** True when the device can render a real backdrop blur (API 31+). */
val supportsBackdropBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@OrbitPreviews
@Composable
private fun OrbitBottomBarPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        var selected by remember { mutableIntStateOf(0) }
        Box(Modifier.background(OrbitTheme.colors.background)) {
            OrbitBottomBar(
                items = rememberOrbitBottomBarItems(),
                selectedIndex = selected,
                onItemSelected = { selected = it },
            )
        }
    }
}
