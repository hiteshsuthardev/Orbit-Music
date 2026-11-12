package com.example.orbitmusic.ui.components.orbit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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

/** Small brand-gradient planet with a thin ring and one dot orbiting it. */
@Composable
fun OrbitLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ringColor: Color = OrbitTheme.colors.outline,
    dotColor: Color = BrandColors.Amber,
) {
    val description = stringResource(R.string.cd_loading)
    val transition = rememberInfiniteTransition(label = "orbitLoader")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
        label = "orbitAngle",
    )
    Canvas(
        modifier
            .size(size)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val orbit = this.size.minDimension / 2f * 0.82f
        drawCircle(ringColor, radius = orbit, center = center, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(BrandColors.Gradient, radius = this.size.minDimension * 0.2f, center = center)
        val rad = angle * PI.toFloat() / 180f
        drawCircle(
            dotColor,
            radius = this.size.minDimension * 0.075f,
            center = Offset(center.x + orbit * cos(rad), center.y + orbit * sin(rad)),
        )
    }
}

@OrbitPreviews
@Composable
private fun OrbitLoaderPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.background(OrbitTheme.colors.background).padding(16.dp)) { OrbitLoader() }
    }
}
