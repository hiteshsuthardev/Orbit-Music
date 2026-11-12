package com.example.orbitmusic.player

import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.orbitmusic.di.PlaybackEntryPoint
import com.example.orbitmusic.domain.model.NowPlayingView
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.player.PlayerActions
import com.example.orbitmusic.ui.player.PlayerSheet
import com.example.orbitmusic.ui.player.PlayerUiState
import com.example.orbitmusic.ui.player.QueueSheet
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Drives the real player sheet with real playback and captures screenshots for review. */
@RunWith(AndroidJUnit4::class)
class PlayerSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun song(id: String, title: String, artist: String) =
        Song(id, title, artist, null, "https://i.ytimg.com/vi/$id/hqdefault.jpg", 19_000, true)

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("renders")!!
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun miniPlayerExpandsToNowPlayingAndCollapsesBack() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(app, PlaybackEntryPoint::class.java)
        val controller = entryPoint.playerController()
        val queue = entryPoint.queueManager()
        var expandedState = false

        compose.setContent {
            OrbitMusicTheme(darkTheme = false) {
                val state by controller.state.collectAsState()
                var expanded by remember { mutableStateOf(false) }
                var showQueue by remember { mutableStateOf(false) }
                expandedState = expanded
                val actions = remember {
                    object : PlayerActions {
                        override fun togglePlayPause() = controller.togglePlayPause()
                        override fun next() = controller.next()
                        override fun previous() = controller.previous()
                        override fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
                        override fun toggleShuffle() = controller.toggleShuffle()
                        override fun cycleRepeat() = controller.cycleRepeatMode()
                        override fun toggleFavorite() = Unit
                        override fun share() = Unit
                        override fun openMenu() = Unit
                        override fun selectView(view: NowPlayingView) = Unit
                        override fun retryLyrics() = Unit
                        override fun openQueue() { showQueue = true }
                        override fun openSleepTimer() = Unit
                        override fun openSpeed() = Unit
                    }
                }
                Box(Modifier.fillMaxSize().background(OrbitTheme.colors.background)) {
                    PlayerSheet(
                        state = PlayerUiState(player = state),
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        collapsedBottomOffset = 110.dp,
                        actions = actions,
                    )
                }
                if (showQueue) {
                    QueueSheet(
                        queue = state.queue,
                        onDismiss = { showQueue = false },
                        onSkipTo = controller::skipToQueueItem,
                        onRemove = controller::removeFromQueue,
                        onMove = controller::moveQueueItem,
                    )
                }
            }
        }

        compose.runOnUiThread {
            queue.setQueue(
                listOf(
                    song("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley"),
                    song("jNQXAC9IVRw", "Me at the zoo", "jawed"),
                    song("9bZkp7q19f0", "Gangnam Style", "PSY"),
                ),
            )
        }
        compose.waitUntil(timeoutMillis = 60_000) {
            controller.state.value.isPlaying && controller.state.value.positionMs > 2_000
        }
        compose.waitForIdle()
        screenshot("sheet_collapsed")

        compose.onNodeWithText("Never Gonna Give You Up").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { expandedState }
        Thread.sleep(1_500) // Let the spring settle and the artwork tint fade in.
        compose.waitForIdle()
        screenshot("sheet_expanded")
        assertTrue(controller.state.value.playWhenReady)

        compose.onNodeWithContentDescription("Queue").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Next up").assertExists() }.isSuccess
        }
        Thread.sleep(800)
        screenshot("queue_sheet")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
        )
        Thread.sleep(800)

        // Swipe down on the expanded sheet collapses it.
        compose.onRoot().performTouchInput {
            swipeDown(startY = height * 0.25f, endY = height * 0.9f, durationMillis = 300)
        }
        compose.waitUntil(timeoutMillis = 5_000) { !expandedState }
        assertFalse(expandedState)

        compose.runOnUiThread { controller.stop() }
    }
}
