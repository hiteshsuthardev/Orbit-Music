package com.example.orbitmusic.ui.components.orbit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val LOADER_LAP_MS = 1_300
private const val SPIN_LAP_MS = 4_000

/** Where the dot rests when animations are turned off. */
private const val STILL_ANGLE = 315f

/** Small brand-gradient planet with a thin ring and one dot orbiting it. Announced as loading. */
@Composable
fun OrbitLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ringColor: Color = OrbitTheme.colors.outline,
    dotColor: Color = BrandColors.Amber,
) {
    val description = stringResource(R.string.cd_loading)
    val angle by rememberOrbitAngle(LOADER_LAP_MS)
    OrbitPlanet(
        angle = { angle },
        ringColor = ringColor,
        dotColor = dotColor,
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    )
}

/**
 * Decorative orbit for screen headers: the same planet with the dot circling slowly. Hidden
 * from accessibility services, and still when the system "Remove animations" setting is on.
 */
@Composable
fun OrbitSpin(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val angle by rememberOrbitAngle(SPIN_LAP_MS)
    OrbitPlanet(
        angle = { angle },
        ringColor = OrbitTheme.colors.outline,
        dotColor = BrandColors.Amber,
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {},
    )
}

/** Continuous 0..360 rotation, or a fixed angle when animations are off. */
@Composable
private fun rememberOrbitAngle(lapMillis: Int): State<Float> {
    if (OrbitTheme.reduceMotion) return remember { mutableFloatStateOf(STILL_ANGLE) }
    val transition = rememberInfiniteTransition(label = "orbit")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(lapMillis, easing = LinearEasing)),
        label = "orbitAngle",
    )
}

@Composable
private fun OrbitPlanet(angle: () -> Float, ringColor: Color, dotColor: Color, modifier: Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbit = size.minDimension / 2f * 0.82f
        drawCircle(ringColor, radius = orbit, center = center, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(BrandColors.Gradient, radius = size.minDimension * 0.2f, center = center)
        val rad = angle() * PI.toFloat() / 180f
        drawCircle(
            dotColor,
            radius = size.minDimension * 0.075f,
            center = Offset(center.x + orbit * cos(rad), center.y + orbit * sin(rad)),
        )
    }
}

@OrbitPreviews
@Composable
private fun OrbitLoaderPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Row(
            Modifier.background(OrbitTheme.colors.background).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OrbitLoader()
            OrbitSpin()
        }
    }
}
