package com.example.orbitmusic.domain.repository

import com.example.orbitmusic.domain.player.QueueState

/** What "resume last session" needs: the full queue and where the current song was. */
data class PlaybackSnapshot(
    val queue: QueueState,
    val positionMs: Long,
)

interface PlaybackSessionRepository {
    suspend fun save(snapshot: PlaybackSnapshot)
    suspend fun load(): PlaybackSnapshot?
    suspend fun clear()
}
