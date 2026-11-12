package com.example.orbitmusic.ui.theme.orbit

import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

val LocalOrbitColors = staticCompositionLocalOf { DarkOrbitColors }
val LocalOrbitType = staticCompositionLocalOf { OrbitTypeScale() }

/** True when the system "Remove animations" setting is on: skip decorative motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Accessor for design tokens, e.g. `OrbitTheme.colors.surface`. */
object OrbitTheme {
    val colors: OrbitColors
        @Composable @ReadOnlyComposable get() = LocalOrbitColors.current

    val type: OrbitTypeScale
        @Composable @ReadOnlyComposable get() = LocalOrbitType.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

/** Corner radii from the spec. */
object OrbitShapes {
    val SmallArtwork = RoundedCornerShape(6.dp)
    val Artwork = RoundedCornerShape(12.dp)
    val PlaylistCover = RoundedCornerShape(14.dp)
    val NowPlayingCover = RoundedCornerShape(18.dp)
    val HeroCard = RoundedCornerShape(24.dp)
    val GroupedList = RoundedCornerShape(16.dp)
    val SearchField = RoundedCornerShape(14.dp)
    val QuickPickTile = RoundedCornerShape(8.dp)
    val CategoryTile = RoundedCornerShape(14.dp)
    val MiniPlayer = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val OnboardingPanel = RoundedCornerShape(32.dp)
    val IconTile = RoundedCornerShape(8.dp)
    val Pill = CircleShape
    val Circle = CircleShape
}

object OrbitDimens {
    val ScreenPadding = 20.dp
    val MinTouchTarget = 48.dp
    const val PressedScale = 0.96f
}

/** Reads the system animator duration scale ("Remove animations" sets it to 0). */
fun isReduceMotionEnabled(context: android.content.Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

/** Multi-preview: renders in the dark (default) and light themes. */
@Preview(
    name = "Dark",
    showBackground = true,
    backgroundColor = 0xFF08080C,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Light",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
annotation class OrbitPreviews
