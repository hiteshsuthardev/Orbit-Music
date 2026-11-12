package com.example.orbitmusic.domain.usecase.player

import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.QueueManager
import com.example.orbitmusic.domain.player.QueueState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveQueueUseCase @Inject constructor(private val queue: QueueManager) {
    operator fun invoke(): StateFlow<QueueState> = queue.state
}

/**
 * Plays [songs] starting at [startSong]. Unplayable songs are dropped up front so the
 * player never stalls on them; returns false if nothing in the list can be played.
 */
class PlaySongsUseCase @Inject constructor(private val queue: QueueManager) {
    operator fun invoke(songs: List<Song>, startSong: Song? = null, shuffle: Boolean = false): Boolean {
        val playable = songs.filter { it.isPlayable }
        if (playable.isEmpty()) return false
        val start = startSong?.let { s -> playable.indexOfFirst { it.videoId == s.videoId } }?.takeIf { it >= 0 } ?: 0
        queue.setQueue(playable, startIndex = start, shuffle = shuffle)
        return true
    }
}

class PlayNextUseCase @Inject constructor(private val queue: QueueManager) {
    operator fun invoke(song: Song): Boolean {
        if (!song.isPlayable) return false
        queue.playNext(song)
        return true
    }
}

class AddToQueueUseCase @Inject constructor(private val queue: QueueManager) {
    operator fun invoke(song: Song): Boolean {
        if (!song.isPlayable) return false
        queue.addToQueue(song)
        return true
    }
}
