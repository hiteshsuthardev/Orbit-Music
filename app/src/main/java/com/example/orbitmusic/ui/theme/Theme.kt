package com.example.orbitmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.ui.theme.orbit.DarkOrbitColors
import com.example.orbitmusic.ui.theme.orbit.LightOrbitColors
import com.example.orbitmusic.ui.theme.orbit.LocalOrbitColors
import com.example.orbitmusic.ui.theme.orbit.LocalOrbitType
import com.example.orbitmusic.ui.theme.orbit.LocalReduceMotion
import com.example.orbitmusic.ui.theme.orbit.isReduceMotionEnabled
import com.example.orbitmusic.ui.theme.orbit.toMaterialColorScheme

private val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * Root theme. Dark is the default look; dynamic (wallpaper) colour is not used because
 * screens are tinted by album artwork instead.
 */
@Composable
fun OrbitMusicTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkOrbitColors else LightOrbitColors
    val colorScheme = remember(colors) { colors.toMaterialColorScheme() }
    val context = LocalContext.current
    val reduceMotion = remember(context) { isReduceMotionEnabled(context) }

    CompositionLocalProvider(
        LocalOrbitColors provides colors,
        LocalOrbitType provides OrbitTypeScaleDefault,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OrbitTypography,
            shapes = MaterialShapes,
            content = content,
        )
    }
}
