package com.example.orbitmusic.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.orbitmusic.di.PlayerSurfaceEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Shows the app's single YouTube player view. The view is owned by the playback engine
 * (not by this composable) and is moved into this container, so playback continues
 * across navigation and configuration changes. If several surfaces exist, the most
 * recently composed one hosts the player.
 */
@Composable
fun YouTubePlayerSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerSurfaceEntryPoint::class.java)
            .youTubePlaybackEngine()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> FrameLayout(ctx) },
        update = { container ->
            val playerView = engine.view
            if (playerView.parent !== container) {
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                container.addView(
                    playerView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                // Fill the (square or 16:9) slot instead of forcing 16:9; the IFrame
                // letterboxes the video inside, so nothing is cropped or obscured.
                playerView.matchParent()
            }
        },
        onRelease = { container ->
            val playerView = engine.view
            if (playerView.parent === container) container.removeView(playerView)
        },
    )
}

/**
 * YouTube requires an embedded player of at least 200x200 *pixels*. Returns that size in
 * dp for the current screen, but never smaller than [floor].
 */
@Composable
fun minimumPlayerSize(floor: Dp = 64.dp): Dp {
    val density = LocalDensity.current
    val required = with(density) { (MIN_PLAYER_PX + 1).toDp() }
    return if (required > floor) required else floor
}

private const val MIN_PLAYER_PX = 200
