package com.example.orbitmusic.domain.player

import com.example.orbitmusic.domain.model.QueueItem
import com.example.orbitmusic.domain.model.Song
import kotlin.random.Random

enum class RepeatMode { Off, All, One }

/**
 * Immutable play queue. [items] is the play order (shuffled when [shuffleEnabled]);
 * [unshuffledItems] remembers the original order so turning shuffle off restores it.
 */
data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val unshuffledItems: List<QueueItem> = emptyList(),
) {
    val current: QueueItem? get() = items.getOrNull(currentIndex)
    val isEmpty: Boolean get() = items.isEmpty()
    val upNext: List<QueueItem> get() = if (currentIndex < 0) items else items.drop(currentIndex + 1)
}

/**
 * Pure queue transitions. Kept free of Android and coroutines so every rule
 * (shuffle, repeat, reordering) is unit-tested directly. [newId] creates unique queue
 * entry ids so the same song can be queued more than once.
 */
object QueueReducer {

    fun setQueue(
        songs: List<Song>,
        startIndex: Int,
        shuffle: Boolean,
        repeatMode: RepeatMode,
        newId: () -> String,
        random: Random,
    ): QueueState {
        if (songs.isEmpty()) return QueueState(repeatMode = repeatMode)
        val items = songs.map { QueueItem(newId(), it) }
        val start = startIndex.coerceIn(0, items.lastIndex)
        if (!shuffle) {
            return QueueState(items = items, currentIndex = start, repeatMode = repeatMode, unshuffledItems = items)
        }
        val first = items[start]
        val rest = (items.take(start) + items.drop(start + 1)).shuffled(random)
        return QueueState(
            items = listOf(first) + rest,
            currentIndex = 0,
            shuffleEnabled = true,
            repeatMode = repeatMode,
            unshuffledItems = items,
        )
    }

    /** Inserts right after the current song (or starts a queue if empty). */
    fun playNext(state: QueueState, song: Song, newId: () -> String): QueueState {
        val item = QueueItem(newId(), song)
        if (state.isEmpty) return state.copy(items = listOf(item), currentIndex = 0, unshuffledItems = listOf(item))
        val insertAt = (state.currentIndex + 1).coerceIn(0, state.items.size)
        val currentInOriginal = state.unshuffledItems.indexOfFirst { it.queueId == state.current?.queueId }
        val originalInsertAt = if (currentInOriginal >= 0) currentInOriginal + 1 else state.unshuffledItems.size
        return state.copy(
            items = state.items.toMutableList().apply { add(insertAt, item) },
            unshuffledItems = state.unshuffledItems.toMutableList().apply { add(originalInsertAt, item) },
        )
    }

    fun addToQueue(state: QueueState, song: Song, newId: () -> String): QueueState {
        val item = QueueItem(newId(), song)
        if (state.isEmpty) return state.copy(items = listOf(item), currentIndex = 0, unshuffledItems = listOf(item))
        return state.copy(items = state.items + item, unshuffledItems = state.unshuffledItems + item)
    }

    /** Removing the current song makes the following one current. */
    fun remove(state: QueueState, queueId: String): QueueState {
        val index = state.items.indexOfFirst { it.queueId == queueId }
        if (index < 0) return state
        val items = state.items.toMutableList().apply { removeAt(index) }
        val unshuffled = state.unshuffledItems.filterNot { it.queueId == queueId }
        if (items.isEmpty()) return state.copy(items = emptyList(), currentIndex = -1, unshuffledItems = emptyList())
        val current = when {
            index < state.currentIndex -> state.currentIndex - 1
            index == state.currentIndex -> state.currentIndex.coerceAtMost(items.lastIndex)
            else -> state.currentIndex
        }
        return state.copy(items = items, currentIndex = current, unshuffledItems = unshuffled)
    }

    /** Drag-and-drop reorder; the current song stays current wherever it moves. */
    fun move(state: QueueState, from: Int, to: Int): QueueState {
        if (from !in state.items.indices || to !in state.items.indices || from == to) return state
        val currentId = state.current?.queueId
        val items = state.items.toMutableList().apply { add(to, removeAt(from)) }
        val current = items.indexOfFirst { it.queueId == currentId }
        // An explicit reorder defines the new order, also for when shuffle is turned off.
        return state.copy(items = items, currentIndex = current, unshuffledItems = if (state.shuffleEnabled) state.unshuffledItems else items)
    }

    fun skipTo(state: QueueState, queueId: String): QueueState {
        val index = state.items.indexOfFirst { it.queueId == queueId }
        return if (index < 0) state else state.copy(currentIndex = index)
    }

    /**
     * Advances the queue. [userInitiated] distinguishes the Next button (always moves)
     * from a track ending (honours Repeat One). Returns null at the end with repeat off.
     */
    fun next(state: QueueState, userInitiated: Boolean): QueueState? {
        if (state.isEmpty) return null
        if (state.repeatMode == RepeatMode.One && !userInitiated) return state
        return when {
            state.currentIndex < state.items.lastIndex -> state.copy(currentIndex = state.currentIndex + 1)
            state.repeatMode != RepeatMode.Off -> state.copy(currentIndex = 0)
            else -> null
        }
    }

    fun previous(state: QueueState): QueueState {
        if (state.isEmpty) return state
        return when {
            state.currentIndex > 0 -> state.copy(currentIndex = state.currentIndex - 1)
            state.repeatMode == RepeatMode.All -> state.copy(currentIndex = state.items.lastIndex)
            else -> state.copy(currentIndex = 0)
        }
    }

    /** Shuffle keeps the current song playing and reorders only what comes after it. */
    fun setShuffle(state: QueueState, enabled: Boolean, random: Random): QueueState {
        if (enabled == state.shuffleEnabled) return state
        val current = state.current
        if (enabled) {
            if (current == null) return state.copy(shuffleEnabled = true, items = state.items.shuffled(random))
            val rest = state.items.filterNot { it.queueId == current.queueId }.shuffled(random)
            return state.copy(
                items = listOf(current) + rest,
                currentIndex = 0,
                shuffleEnabled = true,
                unshuffledItems = state.items,
            )
        }
        val restored = state.unshuffledItems.ifEmpty { state.items }
        return state.copy(
            items = restored,
            currentIndex = restored.indexOfFirst { it.queueId == current?.queueId }.coerceAtLeast(if (restored.isEmpty()) -1 else 0),
            shuffleEnabled = false,
            unshuffledItems = restored,
        )
    }

    fun cycleRepeat(state: QueueState): QueueState = state.copy(
        repeatMode = when (state.repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        },
    )
}
