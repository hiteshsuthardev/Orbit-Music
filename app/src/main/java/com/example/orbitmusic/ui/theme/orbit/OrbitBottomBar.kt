package com.example.orbitmusic.ui.theme.orbit

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.example.orbitmusic.ui.components.orbit.OrbitLogo
import com.example.orbitmusic.ui.components.orbit.LogoStyle
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
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
    /** Draws the item instead of [icon] (e.g. the profile picture); gets whether it is selected. */
    val content: (@Composable (selected: Boolean) -> Unit)? = null,
)

/** The raised centre action (OrbitNova AI): sits between the first and second half of the items. */
@Immutable
data class OrbitBottomBarCenter(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * How far the centre button rises above the bar's top edge. Anything floating above the bar
 * (the mini player) must stay at least this high to keep the button visible.
 */
val OrbitBottomBarCenterRaise = 22.dp
private val CenterButtonSize = 58.dp

/** Height of the bar's content (excluding the navigation bar inset). */
val OrbitBottomBarHeight = 64.dp

/**
 * Full-width frosted bottom bar: items with icon above label, selected item in
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
    center: OrbitBottomBarCenter? = null,
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
            val centerAt = if (center != null) items.size / 2 else -1
            items.forEachIndexed { index, item ->
                if (index == centerAt && center != null) CenterEntry(center, Modifier.weight(1f))
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
        val content = item.content
        if (content != null) {
            Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) { content(selected) }
        } else {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = null, // The label describes the item.
                tint = tint,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(item.label, style = OrbitTheme.type.tabLabel, color = tint, maxLines = 1)
    }
}

/**
 * Raised brand-gradient circle with the white Orbit logo, lifted above the bar, and its label
 * in the bar like the other items. A white ring marks it as selected.
 */
@Composable
private fun CenterEntry(center: OrbitBottomBarCenter, modifier: Modifier = Modifier) {
    val colors = OrbitTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, pressedScale = 0.9f)
    val tint by animateColorAsState(if (center.selected) colors.accentText else colors.textSecondary, label = "centerTint")
    Box(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = center.selected,
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = center.onClick,
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = -OrbitBottomBarCenterRaise)
                .scaled { scale.value }
                .size(CenterButtonSize)
                .floatingShadow(14.dp, OrbitShapes.Circle, alpha = 0.45f)
                .clip(OrbitShapes.Circle)
                .background(BrandColors.Gradient)
                .border(if (center.selected) 2.5.dp else 0.dp, OnColor.Primary, OrbitShapes.Circle),
            contentAlignment = Alignment.Center,
        ) {
            OrbitLogo(size = 36.dp, style = LogoStyle.White, contentDescription = null)
        }
        Text(
            text = center.label,
            style = OrbitTheme.type.tabLabel,
            color = tint,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 9.dp),
        )
    }
}

/**
 * Home, Search, Library and Profile (last, on the right). [profileContent] draws the profile
 * item - the signed-in user's picture; without it a person icon is shown.
 */
@Composable
fun rememberOrbitBottomBarItems(
    profileContent: (@Composable (selected: Boolean) -> Unit)? = null,
): List<OrbitBottomBarItem> {
    val home = stringResource(R.string.nav_home)
    val search = stringResource(R.string.nav_search)
    val library = stringResource(R.string.nav_library)
    val profile = stringResource(R.string.nav_profile)
    return remember(home, search, library, profile, profileContent) {
        listOf(
            OrbitBottomBarItem(home, Icons.Outlined.Home, Icons.Rounded.Home),
            OrbitBottomBarItem(search, Icons.Outlined.Search, Icons.Rounded.Search),
            OrbitBottomBarItem(library, Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
            OrbitBottomBarItem(profile, Icons.Outlined.Person, Icons.Rounded.Person, profileContent),
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
