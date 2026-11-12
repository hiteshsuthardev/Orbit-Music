package com.example.orbitmusic.player

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.orbitmusic.di.PlaybackEntryPoint
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.PlayerEvent
import com.example.orbitmusic.ui.player.YouTubePlayerSurface
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/**
 * End-to-end playback through the real stack: QueueManager -> PlayerManager -> YouTube
 * IFrame player (WebView) -> MediaSession in PlaybackService. Needs network access.
 */
@RunWith(AndroidJUnit4::class)
class RealPlaybackTest {

    @get:Rule
    val compose = createComposeRule()

    private fun song(id: String, title: String) = Song(id, title, "Test", null, null, 0, true)

    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("renders")!!
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun skipsBrokenVideoThenPlaysRealVideoWithMediaSession() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(app, PlaybackEntryPoint::class.java)
        val controller = entryPoint.playerController()
        val queue = entryPoint.queueManager()

        val events = Collections.synchronizedList(mutableListOf<PlayerEvent>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch { controller.events.collect { events += it } }

        compose.setContent { YouTubePlayerSurface(Modifier.size(320.dp, 200.dp)) }
        compose.runOnUiThread {
            queue.setQueue(
                listOf(
                    song("zzzzzzzzzzz", "Broken video"),
                    song("jNQXAC9IVRw", "Me at the zoo"),
                ),
            )
        }

        // The invalid id fails, is skipped, and the real video starts and advances.
        compose.waitUntil(timeoutMillis = 60_000) {
            val s = controller.state.value
            s.isPlaying && s.positionMs > 2_000
        }
        val state = controller.state.value
        assertEquals("jNQXAC9IVRw", state.currentSong?.videoId)
        assertTrue(state.durationMs > 10_000)
        assertTrue(events.any { it is PlayerEvent.SkippedUnavailable && it.title == "Broken video" })

        // The media session (notification / lock screen / Bluetooth) sees it playing.
        compose.waitUntil(timeoutMillis = 10_000) { shell("dumpsys media_session").contains("state=PLAYING") }
        val sessionDump = shell("dumpsys media_session")
        assertTrue(sessionDump.contains("com.example.orbitmusic"))

        screenshot("playing_app")
        shell("cmd statusbar expand-notifications")
        Thread.sleep(1_500)
        screenshot("playing_notification")
        shell("cmd statusbar collapse")

        // Pause from the controller is reflected by the engine.
        compose.runOnUiThread { controller.pause() }
        compose.waitUntil(timeoutMillis = 10_000) { !controller.state.value.isPlaying }

        compose.runOnUiThread { controller.stop() }
        scope.cancel()
    }
}
