package com.example.orbitmusic.player

import com.example.orbitmusic.di.ApplicationScope
import com.example.orbitmusic.di.MainDispatcher
import com.example.orbitmusic.domain.player.PlayerController
import com.example.orbitmusic.domain.player.PlayerState
import com.example.orbitmusic.domain.repository.PlaybackSessionRepository
import com.example.orbitmusic.domain.repository.PlaybackSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Resume last session": restores the saved queue (cued, not playing) when the app
 * starts, then keeps the saved copy up to date: immediately on structural changes
 * (queue, current song, shuffle/repeat, play/pause) and every [POSITION_SAVE_MS] while
 * playing, so the position survives the process being killed.
 */
@OptIn(FlowPreview::class)
@Singleton
class PlaybackSessionKeeper @Inject constructor(
    private val controller: PlayerController,
    private val repository: PlaybackSessionRepository,
    @ApplicationScope private val scope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    private var started = false

    private data class StructuralKey(
        val queueIds: List<String>,
        val currentIndex: Int,
        val shuffle: Boolean,
        val repeat: String,
        val playWhenReady: Boolean,
    )

    fun start() {
        if (started) return
        started = true
        scope.launch {
            repository.load()?.let { snapshot ->
                withContext(mainDispatcher) { controller.restoreSession(snapshot.queue, snapshot.positionMs) }
            }
            launch {
                controller.state
                    .map { it.structuralKey() }
                    .distinctUntilChanged()
                    .drop(1) // The initial (restored or empty) state needs no write.
                    .debounce(SAVE_DEBOUNCE_MS)
                    .collect { persist(controller.state.value) }
            }
            launch {
                while (true) {
                    delay(POSITION_SAVE_MS)
                    val state = controller.state.value
                    if (state.isPlaying) persist(state)
                }
            }
        }
    }

    private suspend fun persist(state: PlayerState) {
        if (state.queue.isEmpty) {
            repository.clear()
        } else {
            repository.save(PlaybackSnapshot(state.queue, state.positionMs))
        }
    }

    private fun PlayerState.structuralKey() = StructuralKey(
        queueIds = queue.items.map { it.queueId },
        currentIndex = queue.currentIndex,
        shuffle = queue.shuffleEnabled,
        repeat = queue.repeatMode.name,
        playWhenReady = playWhenReady,
    )

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
        const val POSITION_SAVE_MS = 15_000L
    }
}
