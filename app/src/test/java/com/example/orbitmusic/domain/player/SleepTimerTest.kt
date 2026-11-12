package com.example.orbitmusic.domain.player

import com.example.orbitmusic.util.TimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private val playerState = MutableStateFlow(PlayerState())
    private val controller = mockk<PlayerController>(relaxed = true) {
        every { state } returns playerState
        every { setPauseAtEndOfTrack(any()) } answers { playerState.value = playerState.value.copy(pauseAtEndOfTrack = firstArg()) }
    }

    private fun TestScope.timer(): SleepTimer {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val time = object : TimeProvider {
            override fun currentTimeMillis() = testScheduler.currentTime
            override fun localTime(): LocalTime = LocalTime.NOON
        }
        return SleepTimer(controller, time, backgroundScope, dispatcher)
    }

    @Test
    fun `timer counts down and pauses when it runs out`() = runTest {
        val sleep = timer()
        sleep.start(15)
        runCurrent()
        assertEquals(SleepTimerState.Running(15 * 60_000L, 15 * 60_000L), sleep.state.value)

        advanceTimeBy(10 * 60_000L)
        runCurrent()
        val remaining = (sleep.state.value as SleepTimerState.Running).remainingMs
        assertTrue(remaining in 4 * 60_000L..5 * 60_000L)
        verify(exactly = 0) { controller.pause() }

        advanceTimeBy(5 * 60_000L + 1)
        runCurrent()
        verify(exactly = 1) { controller.pause() }
        assertEquals(SleepTimerState.Off, sleep.state.value)
    }

    @Test
    fun `cancel stops the countdown`() = runTest {
        val sleep = timer()
        sleep.start(30)
        runCurrent()
        sleep.cancel()
        advanceTimeBy(31 * 60_000L)
        runCurrent()
        verify(exactly = 0) { controller.pause() }
        assertEquals(SleepTimerState.Off, sleep.state.value)
    }

    @Test
    fun `end of song arms the player and turns off once it fires`() = runTest {
        val sleep = timer()
        sleep.startEndOfTrack()
        runCurrent()
        assertEquals(SleepTimerState.EndOfTrack, sleep.state.value)
        assertTrue(playerState.value.pauseAtEndOfTrack)

        // PlayerManager clears the flag after pausing at the end of the song.
        playerState.value = playerState.value.copy(pauseAtEndOfTrack = false)
        runCurrent()
        assertEquals(SleepTimerState.Off, sleep.state.value)
    }

    @Test
    fun `cancelling end of song disarms the player`() = runTest {
        val sleep = timer()
        sleep.startEndOfTrack()
        runCurrent()
        sleep.cancel()
        runCurrent()
        assertEquals(false, playerState.value.pauseAtEndOfTrack)
        assertEquals(SleepTimerState.Off, sleep.state.value)
    }
}
