package com.example.orbitmusic.ui.theme.orbit

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlin.math.max

// Press feedback -------------------------------------------------------------------------

/** Spring scale (to [pressedScale]) while pressed. 1f when animations are disabled. */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = OrbitDimens.PressedScale,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = OrbitTheme.reduceMotion
    return animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
}

fun Modifier.scaled(scale: () -> Float): Modifier = graphicsLayer {
    val s = scale()
    scaleX = s
    scaleY = s
}

// Shadows (floating elements only) ----------------------------------------------------------

/**
 * Soft black shadow for floating things (mini player, dragged rows, large covers). Uses the
 * platform shadow with tinted ambient/spot colours.
 */
fun Modifier.floatingShadow(
    elevation: Dp,
    shape: Shape,
    alpha: Float = 0.4f,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = BrandColors.Black.copy(alpha = alpha),
    spotColor = BrandColors.Black.copy(alpha = alpha),
)

// Mesh gradient ----------------------------------------------------------------------------

/**
 * Soft "mesh" look: a base colour with two or three large radial colour blobs. Positions
 * are derived from [seed] so the same item always gets the same artwork.
 */
fun Modifier.meshGradient(
    base: Color,
    blobs: List<Color>,
    seed: Int = 0,
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val radius = max(w, h) * 0.85f
    val anchors = listOf(
        Offset(0.15f, 0.2f),
        Offset(0.9f, 0.35f),
        Offset(0.35f, 0.95f),
        Offset(0.8f, 0.9f),
    )
    val shift = Math.floorMod(seed, anchors.size)
    val brushes = blobs.take(3).mapIndexed { i, color ->
        val anchor = anchors[(i + shift) % anchors.size]
        Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = Offset(anchor.x * w, anchor.y * h),
            radius = radius,
        )
    }
    onDrawBehind {
        drawRect(base)
        brushes.forEach { drawRect(it) }
    }
}

/** Mesh-gradient placeholder for a missing thumbnail, picked by hashing [key]. */
fun Modifier.placeholderMesh(key: String): Modifier = meshGradient(
    base = BrandColors.supportingFor(key, offset = 0),
    blobs = listOf(BrandColors.supportingFor(key, 1), BrandColors.supportingFor(key, 2)),
    seed = key.hashCode(),
)

/** Splash / Login backdrop: Rose, Amber and Violet on a dark plum base. */
fun Modifier.brandMesh(): Modifier = meshGradient(
    base = BrandColors.PlumBase,
    blobs = listOf(BrandColors.Rose, BrandColors.Amber, BrandColors.Violet),
    seed = 0,
)

/** A Box filled with [brush]; handy for gradient tiles. */
@Composable
fun GradientBox(brush: Brush, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.drawWithCache { onDrawBehind { drawRect(brush) } }, content = content)
}

/** Remembers a stable Int seed for a string key. */
@Composable
fun rememberSeed(key: String): Int = remember(key) { key.hashCode() }
