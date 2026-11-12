package com.example.orbitmusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.ui.common.messageRes
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import kotlinx.coroutines.delay

// Banners -----------------------------------------------------------------------------------

/** Inline error row on a surface card, with optional retry and dismiss. */
@Composable
fun OrbitErrorBanner(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = OrbitTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(OrbitShapes.GroupedList)
            .background(colors.surface)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.accentText)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(error.messageRes()),
            style = OrbitTheme.type.secondary,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        if (onRetry != null) OrbitTextButton(stringResource(R.string.action_retry), onRetry)
        if (onDismiss != null) {
            OrbitIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_dismiss_error),
                onClick = onDismiss,
                tint = colors.textSecondary,
            )
        }
    }
}

/** Shown while offline: everything visible comes from the local cache. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val colors = OrbitTheme.colors
    Row(
        modifier = modifier
            .clip(OrbitShapes.Pill)
            .background(colors.surfaceRaised)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.offline_banner), style = OrbitTheme.type.smallLabel, color = colors.textSecondary)
    }
}

// Section header ------------------------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = OrbitDimens.ScreenPadding, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = OrbitTheme.type.sectionTitle,
            color = OrbitTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

// Loading ------------------------------------------------------------------------------------

/** Moving highlight over a surfaceRaised block. */
fun Modifier.shimmer(progress: () -> Float, base: Color, highlight: Color): Modifier =
    background(base).drawWithContent {
        drawContent()
        val x = progress() * (size.width * 2) - size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(highlight.copy(alpha = 0f), highlight, highlight.copy(alpha = 0f)),
                start = Offset(x, 0f),
                end = Offset(x + size.width, size.height),
            ),
        )
    }

/** Skeleton rows plus the orbit loader while the first load is in flight. */
@Composable
fun SongListSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
    val colors = OrbitTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "shimmerProgress",
    )
    val shimmerProgress = if (OrbitTheme.reduceMotion) 0.5f else progress
    val base = colors.surfaceRaised
    val highlight = colors.outline.copy(alpha = 0.18f)
    val loading = stringResource(R.string.cd_loading)
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { OrbitLoader(size = 40.dp) }
        repeat(rows) {
            Row(Modifier.padding(horizontal = OrbitDimens.ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(OrbitShapes.SmallArtwork).shimmer({ shimmerProgress }, base, highlight))
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(180.dp, { shimmerProgress }, base, highlight)
                    SkeletonLine(110.dp, { shimmerProgress }, base, highlight)
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(width: Dp, progress: () -> Float, base: Color, highlight: Color) {
    Box(Modifier.width(width).height(12.dp).clip(OrbitShapes.Pill).shimmer(progress, base, highlight))
}

// Motion --------------------------------------------------------------------------------------

/**
 * Staggered fade-in on first load only: once an item has appeared (tracked with
 * rememberSaveable) it is not animated again when scrolled back or after rotation.
 * Skipped entirely when the system "Remove animations" setting is on.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = OrbitTheme.reduceMotion
    var appeared by rememberSaveable { mutableStateOf(reduceMotion) }
    val progress = remember { Animatable(if (appeared) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!appeared) {
            delay(index.coerceAtMost(8) * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow))
            appeared = true
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 16.dp.toPx()
        },
        content = content,
    )
}

@OrbitPreviews
@Composable
private fun FeedbackPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            Modifier.background(OrbitTheme.colors.background).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(stringResource(R.string.home_section_trending))
            OrbitErrorBanner(AppError.QuotaExceeded, Modifier.padding(horizontal = 20.dp), onRetry = {}, onDismiss = {})
            OfflineBanner(Modifier.padding(horizontal = 20.dp))
            SongListSkeleton(rows = 2)
        }
    }
}
