package com.example.orbitmusic.domain.usecase

import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.QueueManager
import com.example.orbitmusic.domain.usecase.player.AddToQueueUseCase
import com.example.orbitmusic.domain.usecase.player.PlayNextUseCase
import com.example.orbitmusic.domain.usecase.player.PlaySongsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayUseCasesTest {

    private fun song(id: String, playable: Boolean = true) = Song(id, id, "Artist", null, null, 1000, playable)

    @Test
    fun `play skips unplayable songs and starts at the tapped one`() {
        val queue = QueueManager()
        val played = PlaySongsUseCase(queue)(
            songs = listOf(song("a"), song("x", playable = false), song("b"), song("c")),
            startSong = song("b"),
        )
        assertTrue(played)
        assertEquals(listOf("a", "b", "c"), queue.state.value.items.map { it.song.videoId })
        assertEquals("b", queue.state.value.current?.song?.videoId)
    }

    @Test
    fun `nothing playable leaves the queue untouched`() {
        val queue = QueueManager()
        assertFalse(PlaySongsUseCase(queue)(listOf(song("x", playable = false))))
        assertTrue(queue.state.value.isEmpty)
    }

    @Test
    fun `unplayable songs cannot be queued`() {
        val queue = QueueManager()
        assertFalse(PlayNextUseCase(queue)(song("x", playable = false)))
        assertFalse(AddToQueueUseCase(queue)(song("y", playable = false)))
        assertTrue(AddToQueueUseCase(queue)(song("z")))
        assertEquals("z", queue.state.value.current?.song?.videoId)
    }

    @Test
    fun `next reports the end of the queue`() {
        val queue = QueueManager()
        PlaySongsUseCase(queue)(listOf(song("a"), song("b")))
        assertTrue(queue.next(userInitiated = false))
        assertFalse(queue.next(userInitiated = false))
        assertEquals("b", queue.state.value.current?.song?.videoId)
    }
}
