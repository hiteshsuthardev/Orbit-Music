package com.example.orbitmusic.domain.player

import com.example.orbitmusic.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Single source of truth for the play queue (thread-safe via atomic StateFlow updates).
 * The PlayerManager (Phase 5) observes [state] to load the current video and calls
 * [next] when a track ends; screens call the editing methods through use cases.
 */
@Singleton
class QueueManager @Inject constructor() {

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    private val newId: () -> String = { UUID.randomUUID().toString() }
    private val random: Random = Random.Default

    fun setQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) = _state.update {
        QueueReducer.setQueue(songs, startIndex, shuffle, it.repeatMode, newId, random)
    }

    fun playNext(song: Song) = _state.update { QueueReducer.playNext(it, song, newId) }

    fun addToQueue(song: Song) = _state.update { QueueReducer.addToQueue(it, song, newId) }

    fun remove(queueId: String) = _state.update { QueueReducer.remove(it, queueId) }

    fun move(from: Int, to: Int) = _state.update { QueueReducer.move(it, from, to) }

    fun skipTo(queueId: String) = _state.update { QueueReducer.skipTo(it, queueId) }

    /** Returns false when the end of the queue was reached (repeat off). */
    fun next(userInitiated: Boolean): Boolean {
        var advanced = false
        _state.update { current ->
            val next = QueueReducer.next(current, userInitiated)
            advanced = next != null
            next ?: current
        }
        return advanced
    }

    fun previous() = _state.update { QueueReducer.previous(it) }

    fun setShuffle(enabled: Boolean) = _state.update { QueueReducer.setShuffle(it, enabled, random) }

    fun toggleShuffle() = _state.update { QueueReducer.setShuffle(it, !it.shuffleEnabled, random) }

    fun cycleRepeat() = _state.update { QueueReducer.cycleRepeat(it) }

    fun setRepeatMode(mode: RepeatMode) = _state.update { it.copy(repeatMode = mode) }

    /** Restores a saved session (Phase 6 resume-last-session). */
    fun restore(state: QueueState) {
        _state.value = state
    }

    fun clear() = _state.update { QueueState(repeatMode = it.repeatMode) }
}
