package com.example.orbitmusic.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.brandMesh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The system splash draws ic_splash_logo at this size (icon without background: 288dp canvas). */
private val SystemSplashIconSize = 288.dp
private const val SETTLED_LOGO_SCALE = 0.78f
private val LogoLift = 56.dp
private val OrbitRingSize = 236.dp
private val TitleOffset = 112.dp

/** Frames held unchanged first, so the hand-off from the system splash is invisible. */
private const val HOLD_MS = 120L
private const val RING_DELAY_MS = 150L
private const val TEXT_DELAY_MS = 420L
private const val DOT_LAP_MS = 1_800
private const val GLOW_PULSE_MS = 1_400

/**
 * Animated launch screen. Android 12+ always shows its own splash first (plum + the white
 * logo, from Theme.OrbitMusic.Starting); this screen's first frame is pixel-identical to it,
 * so the two read as one splash that comes alive:
 * the brand mesh blooms in, the logo settles and lifts, a thin orbit ring draws itself around
 * it with a glowing dot circling, and the name and tagline rise in. With "Remove animations"
 * on, the finished frame is shown immediately.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val reduceMotion = OrbitTheme.reduceMotion
    val start = if (reduceMotion) 1f else 0f
    val intro = remember { Animatable(start) }
    val ring = remember { Animatable(start) }
    val text = remember { Animatable(start) }

    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        delay(HOLD_MS)
        launch { intro.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow)) }
        launch {
            delay(RING_DELAY_MS)
            ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(TEXT_DELAY_MS)
            text.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
    }

    val loop = rememberInfiniteTransition(label = "splashLoop")
    val dotAngle by loop.animateFloat(
        initialValue = -90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(tween(DOT_LAP_MS, easing = LinearEasing)),
        label = "dotAngle",
    )
    val glow by loop.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GLOW_PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val angle = if (reduceMotion) -45f else dotAngle
    val glowLevel = if (reduceMotion) 0.8f else glow

    Box(
        modifier
            .fillMaxSize()
            .background(BrandColors.PlumBase),
    ) {
        // Brand mesh blooms in over the plum the system splash used.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = intro.value }
                .brandMesh(),
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationY = -LogoLift.toPx() * intro.value },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(OrbitRingSize)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val r = ring.value
                // Soft glow behind the planet.
                drawCircle(
                    Brush.radialGradient(
                        listOf(OnColor.Primary.copy(alpha = 0.22f * glowLevel * r), OnColor.Primary.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                // The orbit draws itself in, then a dot circles it.
                drawArc(
                    color = OnColor.Primary.copy(alpha = 0.4f),
                    startAngle = -90f,
                    sweepAngle = 360f * r,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                    size = Size(radius * 1.84f, radius * 1.84f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                if (r > 0.98f) {
                    val rad = angle * PI.toFloat() / 180f
                    val dot = Offset(center.x + radius * 0.92f * cos(rad), center.y + radius * 0.92f * sin(rad))
                    drawCircle(BrandColors.Amber.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = dot)
                    drawCircle(BrandColors.Amber, radius = 4.5.dp.toPx(), center = dot)
                }
            }
            // The exact drawable the system splash shows, so the first frame matches it.
            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(SystemSplashIconSize)
                    .graphicsLayer {
                        val scale = 1f + (SETTLED_LOGO_SCALE - 1f) * intro.value
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = TitleOffset)
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 18.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = OrbitTheme.type.screenTitle,
                color = OnColor.Primary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = OrbitTheme.type.body,
                color = OnColor.Secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Splash", showBackground = true, backgroundColor = 0xFF1A0820)
@Composable
private fun SplashPreview() {
    OrbitMusicTheme {
        SplashScreen()
    }
}
