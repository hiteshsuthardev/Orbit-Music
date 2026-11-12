package com.example.orbitmusic.ui.components.orbit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme

enum class LogoStyle {
    /** White planet + ring, for coloured backgrounds (Splash, Login). */
    White,

    /** Rose planet with an Amber ring, everywhere else. */
    Brand,
}

/** Planet with a tilted ring; the back half of the ring passes behind the planet. */
@Composable
fun OrbitLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    style: LogoStyle = LogoStyle.Brand,
    contentDescription: String? = stringResource(R.string.cd_orbit_logo),
) {
    val planet: Brush = when (style) {
        LogoStyle.White -> SolidColor(OnColor.Primary)
        LogoStyle.Brand -> SolidColor(BrandColors.Rose)
    }
    val ring = when (style) {
        LogoStyle.White -> OnColor.Primary
        LogoStyle.Brand -> BrandColors.Amber
    }
    Canvas(
        modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val planetRadius = this.size.minDimension * 0.27f
        val ringSize = Size(this.size.width * 0.94f, this.size.height * 0.34f)
        val ringTopLeft = Offset(center.x - ringSize.width / 2f, center.y - ringSize.height / 2f)
        val stroke = Stroke(width = this.size.minDimension * 0.055f, cap = StrokeCap.Round)
        rotate(-18f, center) {
            drawArc(ring, 180f, 180f, false, ringTopLeft, ringSize, style = stroke)
        }
        drawCircle(planet, radius = planetRadius, center = center)
        rotate(-18f, center) {
            drawArc(ring, 0f, 180f, false, ringTopLeft, ringSize, style = stroke)
        }
    }
}

@OrbitPreviews
@Composable
private fun OrbitLogoPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Row(Modifier.background(OrbitTheme.colors.background).padding(16.dp)) {
            OrbitLogo(size = 72.dp)
            OrbitLogo(size = 72.dp, style = LogoStyle.White, modifier = Modifier.background(BrandColors.Gradient))
        }
    }
}
