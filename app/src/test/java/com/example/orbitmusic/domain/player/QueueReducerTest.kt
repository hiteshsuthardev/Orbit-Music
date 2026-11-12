package com.example.orbitmusic.domain.player

import com.example.orbitmusic.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueReducerTest {

    private var counter = 0
    private val newId: () -> String = { "q${counter++}" }
    private val random = Random(7)

    private fun song(id: String) = Song(id, "Title $id", "Artist", null, null, 1000, true)
    private val songs = listOf("a", "b", "c", "d", "e").map(::song)

    private fun QueueState.ids() = items.map { it.song.videoId }
    private fun QueueState.currentId() = current?.song?.videoId

    private fun queue(start: Int = 0, shuffle: Boolean = false, repeat: RepeatMode = RepeatMode.Off) =
        QueueReducer.setQueue(songs, start, shuffle, repeat, newId, random)

    @Test
    fun `set queue starts at the requested song`() {
        val state = queue(start = 2)
        assertEquals(listOf("a", "b", "c", "d", "e"), state.ids())
        assertEquals("c", state.currentId())
        assertEquals(listOf("d", "e"), state.upNext.map { it.song.videoId })
    }

    @Test
    fun `shuffled queue keeps the chosen song first and every song once`() {
        val state = queue(start = 3, shuffle = true)
        assertEquals("d", state.currentId())
        assertEquals(0, state.currentIndex)
        assertEquals(songs.map { it.videoId }.toSet(), state.ids().toSet())
        assertTrue(state.shuffleEnabled)
    }

    @Test
    fun `next and previous respect repeat modes`() {
        var state = queue(start = 4)
        assertNull(QueueReducer.next(state, userInitiated = false)) // end, repeat off

        state = state.copy(repeatMode = RepeatMode.All)
        assertEquals("a", QueueReducer.next(state, userInitiated = false)!!.currentId())

        state = state.copy(repeatMode = RepeatMode.One)
        assertEquals("e", QueueReducer.next(state, userInitiated = false)!!.currentId())
        // The Next button always moves on, even with Repeat One.
        assertEquals("a", QueueReducer.next(state, userInitiated = true)!!.currentId())

        val first = queue(start = 0)
        assertEquals("a", QueueReducer.previous(first).currentId())
        assertEquals("e", QueueReducer.previous(first.copy(repeatMode = RepeatMode.All)).currentId())
    }

    @Test
    fun `play next inserts after current and add to queue appends`() {
        var state = queue(start = 1)
        state = QueueReducer.playNext(state, song("x"), newId)
        state = QueueReducer.addToQueue(state, song("y"), newId)
        assertEquals(listOf("a", "b", "x", "c", "d", "e", "y"), state.ids())
        assertEquals("b", state.currentId())
    }

    @Test
    fun `queueing into an empty queue starts it`() {
        val state = QueueReducer.playNext(QueueState(), song("x"), newId)
        assertEquals("x", state.currentId())
    }

    @Test
    fun `removing items keeps the right song current`() {
        val start = queue(start = 2) // current = c
        val beforeCurrent = QueueReducer.remove(start, start.items[0].queueId)
        assertEquals("c", beforeCurrent.currentId())

        val removedCurrent = QueueReducer.remove(start, start.items[2].queueId)
        assertEquals("d", removedCurrent.currentId())

        val last = queue(start = 4)
        assertEquals("d", QueueReducer.remove(last, last.items[4].queueId).currentId())

        var single = QueueReducer.addToQueue(QueueState(), song("z"), newId)
        single = QueueReducer.remove(single, single.items[0].queueId)
        assertEquals(-1, single.currentIndex)
        assertTrue(single.isEmpty)
    }

    @Test
    fun `moving items follows the current song`() {
        val state = queue(start = 1) // current = b
        val moved = QueueReducer.move(state, from = 1, to = 4)
        assertEquals(listOf("a", "c", "d", "e", "b"), moved.ids())
        assertEquals("b", moved.currentId())

        val movedOther = QueueReducer.move(state, from = 4, to = 0)
        assertEquals(listOf("e", "a", "b", "c", "d"), movedOther.ids())
        assertEquals("b", movedOther.currentId())
    }

    @Test
    fun `toggling shuffle off restores the original order`() {
        val state = queue(start = 2)
        val shuffled = QueueReducer.setShuffle(state, enabled = true, random = random)
        assertEquals("c", shuffled.currentId())
        assertEquals(0, shuffled.currentIndex)

        val restored = QueueReducer.setShuffle(shuffled, enabled = false, random = random)
        assertEquals(listOf("a", "b", "c", "d", "e"), restored.ids())
        assertEquals("c", restored.currentId())
    }

    @Test
    fun `repeat cycles off all one`() {
        var state = QueueState()
        state = QueueReducer.cycleRepeat(state)
        assertEquals(RepeatMode.All, state.repeatMode)
        state = QueueReducer.cycleRepeat(state)
        assertEquals(RepeatMode.One, state.repeatMode)
        state = QueueReducer.cycleRepeat(state)
        assertEquals(RepeatMode.Off, state.repeatMode)
    }
}
