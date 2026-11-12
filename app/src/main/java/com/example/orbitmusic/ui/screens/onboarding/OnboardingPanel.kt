package com.example.orbitmusic.ui.screens.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.floatingShadow
import com.example.orbitmusic.ui.theme.orbit.meshGradient
import com.example.orbitmusic.ui.theme.orbit.placeholderMesh

/** Colours and floating decorations for one onboarding panel. */
data class OnboardingPanelStyle(
    val base: Color,
    val blobs: List<Color>,
    val centerIcon: ImageVector,
    val buttonIcon: ImageVector,
    val pillIcon: ImageVector,
    val pillLabel: String,
    val coverKey: String,
)

/**
 * 400dp rounded mesh-gradient panel: two thin orbit rings, a white centre circle with
 * an icon, and small floating items (a cover, a round icon button, a pill) that bob
 * gently (static when "Remove animations" is on). Decorative only.
 */
@Composable
fun OnboardingPanel(
    style: OnboardingPanelStyle,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val reduceMotion = OrbitTheme.reduceMotion
    val transition = rememberInfiniteTransition(label = "panelFloat")
    val bobA by transition.animateFloat(-6f, 6f, infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "bobA")
    val bobB by transition.animateFloat(
        -6f,
        6f,
        infiniteRepeatable(tween(3_100), RepeatMode.Reverse, initialStartOffset = StartOffset(700)),
        label = "bobB",
    )
    val density = LocalDensity.current
    fun bob(value: Float) = if (reduceMotion) 0f else value * density.density

    Box(
        modifier
            .fillMaxWidth()
            .height(400.dp)
            .clip(OrbitShapes.OnboardingPanel)
            .meshGradient(style.base, style.blobs, seed = style.coverKey.hashCode()),
    ) {
        // Two thin orbit rings around the centre.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1.2.dp.toPx())
            drawCircle(OnColor.ChipFill.copy(alpha = 0.32f), radius = 104.dp.toPx(), style = stroke)
            drawCircle(OnColor.ChipFill.copy(alpha = 0.24f), radius = 160.dp.toPx(), style = stroke)
        }
        // White centre circle with the page icon.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(112.dp)
                .floatingShadow(24.dp, OrbitShapes.Circle, alpha = 0.35f)
                .clip(OrbitShapes.Circle)
                .background(OnColor.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.centerIcon, contentDescription = null, tint = style.base, modifier = Modifier.size(52.dp))
        }
        // Floating cover (top-left).
        FloatingItem(Alignment.TopStart, x = 36.dp, y = 74.dp, bob = { bob(bobA) }) {
            Box(
                Modifier
                    .size(72.dp)
                    .floatingShadow(18.dp, OrbitShapes.Artwork, alpha = 0.4f)
                    .clip(OrbitShapes.Artwork)
                    .placeholderMesh(style.coverKey),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = OnColor.Secondary, modifier = Modifier.size(28.dp))
            }
        }
        // Round icon button (right).
        FloatingItem(Alignment.CenterEnd, x = (-34).dp, y = (-56).dp, bob = { bob(bobB) }) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(OrbitShapes.Circle)
                    .background(OnColor.ChipFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(style.buttonIcon, contentDescription = null, tint = OnColor.Primary, modifier = Modifier.size(24.dp))
            }
        }
        // Pill (bottom).
        FloatingItem(Alignment.BottomCenter, x = 28.dp, y = (-56).dp, bob = { bob(-bobA) }) {
            Row(
                Modifier
                    .clip(OrbitShapes.Pill)
                    .background(OnColor.ChipFill)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(style.pillIcon, contentDescription = null, tint = OnColor.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(style.pillLabel, style = OrbitTheme.type.smallLabel, color = OnColor.Primary)
            }
        }
        overlay()
    }
}

@Composable
private fun BoxScope.FloatingItem(
    alignment: Alignment,
    x: Dp,
    y: Dp,
    bob: () -> Float,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .align(alignment)
            .offset(x = x, y = y)
            .graphicsLayer { translationY = bob() },
    ) { content() }
}

@Preview(name = "Onboarding panel", showBackground = true, backgroundColor = 0xFF08080C)
@Composable
private fun OnboardingPanelPreview() {
    OrbitMusicTheme {
        OnboardingPanel(
            OnboardingPanelStyle(
                base = BrandColors.Violet,
                blobs = listOf(BrandColors.Rose, BrandColors.Amber),
                centerIcon = Icons.Rounded.MusicNote,
                buttonIcon = Icons.Rounded.Shuffle,
                pillIcon = Icons.Rounded.Favorite,
                pillLabel = stringResource(R.string.onboarding_pill_1),
                coverKey = "preview",
            ),
            modifier = Modifier.padding(12.dp),
        )
    }
}
