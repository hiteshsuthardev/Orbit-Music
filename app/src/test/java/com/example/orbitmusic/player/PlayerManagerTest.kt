package com.example.orbitmusic.player

import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.PlayerEvent
import com.example.orbitmusic.domain.player.QueueManager
import com.example.orbitmusic.domain.player.RepeatMode
import com.example.orbitmusic.domain.repository.SongRepository
import com.example.orbitmusic.domain.repository.UserLibraryRepository
import com.example.orbitmusic.domain.usecase.library.RecordPlayUseCase
import com.example.orbitmusic.domain.usecase.song.MarkSongUnplayableUseCase
import com.example.orbitmusic.player.engine.EngineError
import com.example.orbitmusic.player.engine.EngineListener
import com.example.orbitmusic.player.engine.EngineState
import com.example.orbitmusic.player.engine.VideoPlaybackEngine
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerManagerTest {

    private class FakeEngine : VideoPlaybackEngine {
        var engineListener: EngineListener? = null
        val loads = mutableListOf<Pair<String, Boolean>>()
        val calls = mutableListOf<String>()

        override fun setListener(listener: EngineListener?) {
            engineListener = listener
        }
        override fun load(videoId: String, startPositionMs: Long, autoplay: Boolean) {
            loads += videoId to autoplay
        }
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
        override fun setPlaybackSpeed(speed: Float) { calls += "speed:$speed" }

        fun emit(state: EngineState) = engineListener!!.onEngineState(state)
    }

    private class FakeGuard : AudioSessionGuard {
        var guardListener: AudioSessionGuard.Listener? = null
        val calls = mutableListOf<String>()
        override fun setListener(listener: AudioSessionGuard.Listener?) {
            guardListener = listener
        }
        override fun onPlaybackStarted() { calls += "started" }
        override fun onPlaybackPaused() { calls += "paused" }
        override fun onPlaybackStopped() { calls += "stopped" }
    }

    private val engine = FakeEngine()
    private val guard = FakeGuard()
    private val queue = QueueManager()
    private val library = mockk<UserLibraryRepository>(relaxed = true)
    private val songs = mockk<SongRepository>(relaxed = true)
    private var serviceStarts = 0

    private fun song(id: String) = Song(id, "Title $id", "Artist", null, null, 200_000, true)

    private fun manager() = PlayerManager(
        engine = engine,
        queue = queue,
        recordPlay = RecordPlayUseCase(library),
        markUnplayable = MarkSongUnplayableUseCase(songs),
        audioGuard = guard,
        serviceStarter = { serviceStarts++ },
        mainDispatcher = UnconfinedTestDispatcher(),
    )

    private fun PlayerManager.currentId() = state.value.currentSong?.videoId

    @Test
    fun `setting a queue loads and autoplays the first song and starts the service`() {
        val player = manager()
        queue.setQueue(listOf(song("a"), song("b")))
        assertEquals(listOf("a" to true), engine.loads)
        assertEquals(1, serviceStarts)
        assertTrue(player.state.value.playWhenReady)
        assertTrue(player.state.value.isBuffering)
    }

    @Test
    fun `playing records history once and takes audio focus`() = runTest {
        val player = manager()
        queue.setQueue(listOf(song("a")))
        engine.emit(EngineState.Playing)
        engine.emit(EngineState.Paused)
        engine.emit(EngineState.Playing)

        assertTrue(player.state.value.isPlaying)
        assertEquals(listOf("started", "paused", "started"), guard.calls)
        coVerify(exactly = 1) { library.recordPlay(song("a")) }
    }

    @Test
    fun `track end advances and the last track ends playback`() {
        val player = manager()
        queue.setQueue(listOf(song("a"), song("b")))
        engine.emit(EngineState.Ended)
        assertEquals("b", player.currentId())
        assertEquals(listOf("a" to true, "b" to true), engine.loads)

        engine.emit(EngineState.Ended)
        assertTrue(player.state.value.hasEnded)
        assertFalse(player.state.value.playWhenReady)
        assertEquals("stopped", guard.calls.last())

        // Pressing play after the end restarts the last song.
        player.play()
        assertEquals(listOf("seek:0", "play"), engine.calls.takeLast(2))
        assertFalse(player.state.value.hasEnded)
    }

    @Test
    fun `repeat one replays the same song without reloading`() {
        val player = manager()
        queue.setQueue(listOf(song("a"), song("b")))
        player.setRepeatMode(RepeatMode.One)
        engine.emit(EngineState.Ended)
        assertEquals("a", player.currentId())
        assertEquals(1, engine.loads.size)
        assertEquals(listOf("seek:0", "play"), engine.calls.takeLast(2))

        // The Next button still moves on.
        player.next()
        assertEquals("b", player.currentId())
    }

    @Test
    fun `unavailable video is marked, announced and skipped`() = runTest {
        val player = manager()
        val events = mutableListOf<PlayerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { player.events.collect { events += it } }

        queue.setQueue(listOf(song("a"), song("b")))
        engine.engineListener!!.onEngineError(EngineError.VideoUnavailable)

        assertEquals("b", player.currentId())
        assertEquals(listOf(PlayerEvent.SkippedUnavailable("Title a")), events)
        coVerify { songs.markUnplayable("a") }
    }

    @Test
    fun `when every song fails playback stops`() = runTest {
        val player = manager()
        val events = mutableListOf<PlayerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { player.events.collect { events += it } }

        queue.setQueue(listOf(song("a"), song("b")))
        engine.engineListener!!.onEngineError(EngineError.VideoUnavailable)
        engine.engineListener!!.onEngineError(EngineError.VideoUnavailable)

        assertEquals(PlayerEvent.NothingPlayable, events.last())
        assertFalse(player.state.value.playWhenReady)
    }

    @Test
    fun `previous restarts after three seconds, otherwise goes back`() {
        val player = manager()
        queue.setQueue(listOf(song("a"), song("b")), startIndex = 1)
        engine.engineListener!!.onEnginePosition(10_000)
        player.previous()
        assertEquals("b", player.currentId())
        assertEquals("seek:0", engine.calls.last())

        engine.engineListener!!.onEnginePosition(1_000)
        player.previous()
        assertEquals("a", player.currentId())
    }

    @Test
    fun `pauses coming from the webview (e g focus loss) are reflected and resumable`() {
        val player = manager()
        queue.setQueue(listOf(song("a")))
        engine.emit(EngineState.Playing)

        // Chromium pauses the video itself on audio focus loss.
        engine.emit(EngineState.Paused)
        assertFalse(player.state.value.playWhenReady)
        assertFalse(player.state.value.isPlaying)

        // ...and resumes it after a transient loss.
        engine.emit(EngineState.Playing)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun `headphone unplug pauses`() {
        val player = manager()
        queue.setQueue(listOf(song("a")))
        engine.emit(EngineState.Playing)

        guard.guardListener!!.onBecomingNoisy()
        assertFalse(player.state.value.playWhenReady)
        assertEquals("pause", engine.calls.last())
    }

    @Test
    fun `end of song sleep timer cues the next song without playing it`() {
        val player = manager()
        queue.setQueue(listOf(song("a"), song("b")))
        engine.emit(EngineState.Playing)
        player.setPauseAtEndOfTrack(true)
        assertTrue(player.state.value.pauseAtEndOfTrack)

        engine.emit(EngineState.Ended)

        assertEquals("b", player.currentId())
        assertEquals("b" to false, engine.loads.last()) // cued, not autoplayed
        assertFalse(player.state.value.playWhenReady)
        assertFalse(player.state.value.pauseAtEndOfTrack)

        // The following track plays normally again.
        player.play()
        engine.emit(EngineState.Playing)
        engine.emit(EngineState.Ended)
        assertTrue(player.state.value.hasEnded)
    }

    @Test
    fun `restoring a session cues the saved song at its position without starting the service`() {
        val player = manager()
        val saved = com.example.orbitmusic.domain.player.QueueReducer.setQueue(
            listOf(song("a"), song("b")), startIndex = 1, shuffle = false,
            repeatMode = RepeatMode.All, newId = { java.util.UUID.randomUUID().toString() }, random = kotlin.random.Random(1),
        )
        player.restoreSession(saved, positionMs = 42_000)

        assertEquals("b", player.currentId())
        assertEquals(listOf("b" to false), engine.loads)
        assertEquals(42_000, player.state.value.positionMs)
        assertFalse(player.state.value.playWhenReady)
        assertEquals(RepeatMode.All, player.state.value.queue.repeatMode)
        assertEquals(0, serviceStarts)

        // Restoring again while something is queued is ignored.
        player.restoreSession(saved, positionMs = 0)
        assertEquals(1, engine.loads.size)
    }

    @Test
    fun `stop clears the queue`() {
        val player = manager()
        queue.setQueue(listOf(song("a")))
        player.stop()
        assertTrue(player.state.value.queue.isEmpty)
        assertEquals(null, player.state.value.currentSong)
    }

}
