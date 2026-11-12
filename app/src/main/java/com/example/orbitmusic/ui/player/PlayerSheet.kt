package com.example.orbitmusic.ui.player

import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.contentColorOn
import com.example.orbitmusic.ui.components.OrbitArtwork
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import com.example.orbitmusic.ui.theme.orbit.floatingShadow
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.example.orbitmusic.domain.model.NowPlayingView
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.orbitmusic.ui.common.findActivity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private val CollapsedHorizontalMargin = 8.dp
private val CollapsedCorner = 16.dp
private val MiniTileCorner = 6.dp
private val FullArtCorner = 18.dp
private val VideoTileCorner = 12.dp

/** Below this the live player counts as hidden and moves behind the content. */
private const val HIDDEN_ALPHA = 0.02f

/** How far a vertical swipe must travel to open or close the player. */
private val SwipeThreshold = 48.dp
private const val PAUSED_COVER_SCALE = 0.9f

/**
 * The player as one morphing surface with exactly two resting states: the floating mini
 * player docked above the bottom bar, and full-screen Now Playing. Tap or swipe up opens
 * it; swipe down or Back closes it. A swipe only chooses the state - the sheet never
 * follows the finger, so it can't be left part-way. The transition animates the sheet's
 * bounds and cross-fades the two layouts, while the one live YouTube player glides between
 * the mini tile and the large cover in Video view (in Poster / Lyrics view it keeps playing
 * hidden) without ever detaching the WebView.
 */
@Composable
fun PlayerSheet(
    state: PlayerUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    collapsedBottomOffset: Dp,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val colors = OrbitTheme.colors
    val density = LocalDensity.current
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    val settleSpec = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    val instantTransitions = OrbitTheme.reduceMotion
    // Always settles fully open or fully closed.
    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        if (instantTransitions) progress.snapTo(target) else progress.animateTo(target, settleSpec)
    }
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    // Now Playing is always dark, so use light system-bar icons while it is open (matters in
    // the light theme) and restore the theme's own setting when it closes.
    val view = LocalView.current
    val themeIsDark = colors.isDark
    DisposableEffect(expanded, themeIsDark) {
        val window = view.context.findActivity()?.window
        val controller = if (window != null && !view.isInEditMode) WindowCompat.getInsetsController(window, view) else null
        if (expanded) {
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
        }
        onDispose {
            controller?.isAppearanceLightStatusBars = !themeIsDark
            controller?.isAppearanceLightNavigationBars = !themeIsDark
        }
    }

    val tileSize = MiniPlayerArtSize
    val miniHeight = MiniPlayerHeight
    val song = state.player.currentSong
    val palette = rememberArtworkPalette(song?.thumbnailUrl)
    val tint = palette.tint
    // Cover shrinks to 0.9 while paused and springs back on play.
    val reduceMotion = OrbitTheme.reduceMotion
    val coverScale by animateFloatAsState(
        targetValue = if (state.player.playWhenReady || reduceMotion) 1f else PAUSED_COVER_SCALE,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "coverScale",
    )
    // Mini player: artwork tint blended 46% into surfaceRaised; content colour follows its
    // luminance so text keeps contrast for any album colour.
    val miniBackground = lerp(colors.surfaceRaised, tint, 0.46f)
    val miniContent = contentColorOn(miniBackground)

    var sheetCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var miniSlot by remember { mutableStateOf<Rect?>(null) }
    var videoSlot by remember { mutableStateOf<Rect?>(null) }
    var videoTileSlot by remember { mutableStateOf<Rect?>(null) }
    // 1 = the live player sits in the large Video slot, 0 = in the small tile (Poster/Lyrics).
    val videoFraction by animateFloatAsState(
        targetValue = if (state.nowPlayingView == NowPlayingView.Video) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "videoSlot",
    )
    val swipeThresholdPx = with(density) { SwipeThreshold.toPx() }
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        val collapsedTop = maxHeight - collapsedBottomOffset - miniHeight
        val f = progress.value.coerceIn(0f, 1f)

        val sheetShape = RoundedCornerShape(lerp(CollapsedCorner, 0.dp, f))
        Box(
            modifier = Modifier
                .offset { IntOffset(0, lerp(collapsedTop, 0.dp, f).roundToPx()) }
                .padding(horizontal = lerp(CollapsedHorizontalMargin, 0.dp, f))
                .fillMaxWidth()
                .height(lerp(miniHeight, fullHeight, f))
                .floatingShadow(lerp(14.dp, 0.dp, f), sheetShape, alpha = 0.45f)
                .clip(sheetShape)
                .background(lerp(miniBackground, BrandColors.NowPlayingBase, f))
                .pointerInput(swipeThresholdPx) {
                    var travelled = 0f
                    detectVerticalDragGestures(
                        onDragStart = { travelled = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            travelled += amount
                        },
                        onDragEnd = {
                            when {
                                !currentExpanded && travelled < -swipeThresholdPx -> currentOnExpandedChange(true)
                                currentExpanded && travelled > swipeThresholdPx -> currentOnExpandedChange(false)
                            }
                        },
                    )
                }
                .onPlaced { sheetCoordinates = it },
        ) {
            // Artwork blobs + scrim fade in as the sheet opens.
            if (f > 0.001f) {
                NowPlayingBackground(palette, Modifier.graphicsLayer { alpha = f })
            }

            if (f < 0.999f) {
                MiniPlayerContent(
                    state = state.player,
                    tileSize = tileSize,
                    contentColor = miniContent,
                    onExpand = { onExpandedChange(true) },
                    onTogglePlay = actions::togglePlayPause,
                    onNext = actions::next,
                    tileSlot = { slotModifier ->
                        Box(slotModifier.onPlaced { coords ->
                            sheetCoordinates?.let { miniSlot = it.localBoundingBoxOf(coords, clipBounds = false) }
                        })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = (1f - f * 3f).coerceIn(0f, 1f) },
                )
            }

            if (f > 0.001f) {
                NowPlayingContent(
                    state = state,
                    actions = actions,
                    onCollapse = { onExpandedChange(false) },
                    coverScale = coverScale,
                    videoSlot = { slotModifier ->
                        Box(slotModifier.onPlaced { coords ->
                            sheetCoordinates?.let { videoSlot = it.localBoundingBoxOf(coords, clipBounds = false) }
                        })
                    },
                    videoTileSlot = { slotModifier ->
                        Box(slotModifier.onPlaced { coords ->
                            sheetCoordinates?.let { videoTileSlot = it.localBoundingBoxOf(coords, clipBounds = false) }
                        })
                    },
                    modifier = Modifier
                        // Lay out at full height from the top so the art slot doesn't jump
                        // while the sheet grows; the sheet clips what doesn't fit yet.
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .height(fullHeight)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .graphicsLayer { alpha = ((f - 0.35f) / 0.65f).coerceIn(0f, 1f) },
                )
            }

            // The single live player, morphing between the mini tile and its Now Playing slot.
            // In Poster / Lyrics view it keeps playing in a hidden slot: fully transparent and
            // drawn (and hit-tested) behind the content, so only the poster or lyrics show.
            val from = miniSlot
            if (from != null) {
                val large = videoSlot
                val small = videoTileSlot
                val target = when {
                    large != null && small != null -> lerp(small, large, videoFraction)
                    else -> large ?: small
                }
                val rect = if (target == null) from else lerp(from, target, f)
                val openCorner = lerp(VideoTileCorner, FullArtCorner, videoFraction)
                val coverShape = RoundedCornerShape(lerp(MiniTileCorner, openCorner, f))
                // Only the large cover breathes with play/pause.
                val breathing = f * videoFraction
                val playerAlpha = 1f - f * (1f - videoFraction)
                Box(
                    Modifier
                        .zIndex(if (playerAlpha < HIDDEN_ALPHA) -1f else 0f)
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                        .graphicsLayer {
                            val scale = 1f + (coverScale - 1f) * breathing
                            scaleX = scale
                            scaleY = scale
                            alpha = playerAlpha
                        }
                        .floatingShadow(lerp(0.dp, lerp(16.dp, 28.dp, videoFraction), f), coverShape, alpha = 0.5f)
                        .clip(coverShape)
                        .background(BrandColors.Black),
                ) {
                    YouTubePlayerSurface(Modifier.fillMaxSize())
                    // Collapsed, the 44dp slot shows the album art (centre-cropped) over the
                    // live player; it fades out as the sheet opens to reveal the video.
                    val artAlpha = (1f - f * 2.5f).coerceIn(0f, 1f)
                    if (artAlpha > 0f && song != null) {
                        OrbitArtwork(
                            url = song.thumbnailUrl,
                            contentDescription = null,
                            placeholderKey = song.videoId,
                            shape = RectangleShape,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = artAlpha },
                        )
                    }
                }
            }
        }
    }
}
