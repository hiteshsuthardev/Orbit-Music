package com.example.orbitmusic.domain.player

import com.example.orbitmusic.di.ApplicationScope
import com.example.orbitmusic.di.MainDispatcher
import com.example.orbitmusic.util.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Running(val remainingMs: Long, val totalMs: Long) : SleepTimerState
    data object EndOfTrack : SleepTimerState
}

/** Durations offered in the UI, in minutes. */
val SleepTimerOptionsMinutes: List<Int> = listOf(15, 30, 45, 60)

/**
 * Pauses playback after a delay, or when the current song ends. Remaining time is
 * published once per second for the UI.
 */
@Singleton
class SleepTimer @Inject constructor(
    private val controller: PlayerController,
    private val timeProvider: TimeProvider,
    @ApplicationScope private val scope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(minutes: Int) {
        cancel()
        val totalMs = minutes * 60_000L
        val endsAt = timeProvider.currentTimeMillis() + totalMs
        job = scope.launch {
            while (true) {
                val remaining = endsAt - timeProvider.currentTimeMillis()
                if (remaining <= 0) break
                _state.value = SleepTimerState.Running(remaining, totalMs)
                delay(minOf(TICK_MS, remaining))
            }
            withContext(mainDispatcher) { controller.pause() }
            _state.value = SleepTimerState.Off
        }
    }

    fun startEndOfTrack() {
        cancel()
        _state.value = SleepTimerState.EndOfTrack
        job = scope.launch {
            withContext(mainDispatcher) { controller.setPauseAtEndOfTrack(true) }
            // PlayerManager clears the flag once it has paused at the end of the song.
            controller.state.map { it.pauseAtEndOfTrack }.drop(1).first { !it }
            _state.value = SleepTimerState.Off
        }
    }

    fun cancel() {
        val wasEndOfTrack = _state.value == SleepTimerState.EndOfTrack
        job?.cancel()
        job = null
        _state.value = SleepTimerState.Off
        if (wasEndOfTrack) scope.launch(mainDispatcher) { controller.setPauseAtEndOfTrack(false) }
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
