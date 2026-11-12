package com.example.orbitmusic.domain.player

import com.example.orbitmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Everything the UI, media session and notification need to render playback. */
data class PlayerState(
    val queue: QueueState = QueueState(),
    /** The user wants playback to run (may still be buffering). */
    val playWhenReady: Boolean = false,
    /** Audio is actually playing right now. */
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** The last track finished and the queue has nothing left (repeat off). */
    val hasEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    /** Sleep timer "end of song": pause when the current track finishes. */
    val pauseAtEndOfTrack: Boolean = false,
) {
    val currentSong: Song? get() = queue.current?.song
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

sealed interface PlayerEvent {
    /** A private / removed / non-embeddable video was skipped automatically. */
    data class SkippedUnavailable(val title: String) : PlayerEvent

    /** Every song in the queue failed to play. */
    data object NothingPlayable : PlayerEvent

    /** The player could not start at all (e.g. embedded player misconfiguration). */
    data object PlaybackFailed : PlayerEvent
}

/** Playback speeds offered in the UI (YouTube supports these rates). */
val SupportedPlaybackSpeeds: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Playback commands and state. Implemented by the PlayerManager singleton; consumed by
 * ViewModels and the media session bridge.
 */
interface PlayerController {
    val state: StateFlow<PlayerState>
    val events: Flow<PlayerEvent>

    fun play()
    fun pause()
    fun togglePlayPause()

    /** Next button: always moves on (also with Repeat One). */
    fun next()

    /** Restarts the song if more than 3 s in, otherwise goes to the previous song. */
    fun previous()

    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun toggleShuffle()
    fun setShuffle(enabled: Boolean)
    fun cycleRepeatMode()
    fun setRepeatMode(mode: RepeatMode)
    fun skipToQueueItem(queueId: String)
    fun removeFromQueue(queueId: String)
    fun moveQueueItem(from: Int, to: Int)

    /** Stops playback and clears the queue. */
    fun stop()

    /** When true, playback pauses once the current track ends (the next one is cued). */
    fun setPauseAtEndOfTrack(enabled: Boolean)

    /**
     * Restores a saved session without starting playback: the current song is cued at
     * [positionMs] so pressing play continues where the user left off. Ignored if
     * something is already queued.
     */
    fun restoreSession(queue: QueueState, positionMs: Long)
}
