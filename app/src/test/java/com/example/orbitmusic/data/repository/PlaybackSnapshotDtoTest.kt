package com.example.orbitmusic.data.repository

import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.QueueReducer
import com.example.orbitmusic.domain.player.RepeatMode
import com.example.orbitmusic.domain.repository.PlaybackSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PlaybackSnapshotDtoTest {

    private var id = 0
    private fun song(v: String) = Song(v, "Title $v", "Artist", "UC", "https://img/$v.jpg", 180_000, true)

    @Test
    fun `shuffled queue survives a json round trip`() {
        val queue = QueueReducer.setQueue(
            songs = listOf("a", "b", "c", "d").map(::song),
            startIndex = 2,
            shuffle = true,
            repeatMode = RepeatMode.One,
            newId = { "q${id++}" },
            random = Random(3),
        )
        val snapshot = PlaybackSnapshot(queue, positionMs = 61_000)
        val json = Json.encodeToString(PlaybackSnapshotDto.serializer(), snapshot.toDto())
        val restored = Json.decodeFromString(PlaybackSnapshotDto.serializer(), json).toDomain()

        assertEquals(snapshot, restored)
    }

    @Test
    fun `corrupt current index is rejected`() {
        val dto = PlaybackSnapshotDto(
            items = listOf(QueueItemDto("q", SongDto("v", "t", "a"))),
            unshuffledIds = listOf("q"),
            currentIndex = 5,
            shuffleEnabled = false,
            repeatMode = "Off",
            positionMs = 0,
        )
        assertNull(dto.toDomain())
    }
}
