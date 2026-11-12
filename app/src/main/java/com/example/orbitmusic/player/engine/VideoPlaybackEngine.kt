package com.example.orbitmusic.player.engine

/**
 * The low-level player PlayerManager drives. The production implementation wraps the
 * official YouTube IFrame player ([YouTubePlaybackEngine]); tests use a fake. All calls
 * and callbacks happen on the main thread.
 */
interface VideoPlaybackEngine {
    fun setListener(listener: EngineListener?)

    /** Loads [videoId]; starts playing immediately when [autoplay], otherwise just cues it. */
    fun load(videoId: String, startPositionMs: Long, autoplay: Boolean)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
}

enum class EngineState { Unstarted, Buffering, Playing, Paused, Ended, Cued }

enum class EngineError {
    /** Removed, private, not embeddable or not playable in HTML5: skip it and remember. */
    VideoUnavailable,

    /** The embedded player itself is misconfigured (e.g. missing HTTP referer). */
    Configuration,

    Unknown,
}

interface EngineListener {
    fun onEngineState(state: EngineState)
    fun onEnginePosition(positionMs: Long)
    fun onEngineDuration(durationMs: Long)
    fun onEngineError(error: EngineError)
    fun onEnginePlaybackSpeed(speed: Float)
}
