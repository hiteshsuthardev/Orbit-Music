package com.example.orbitmusic.player.engine

import android.content.Context
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * [VideoPlaybackEngine] backed by android-youtube-player, which wraps the official YouTube
 * IFrame Player in a WebView. Audio is never extracted, downloaded or re-streamed.
 *
 * Honest limitations of the IFrame approach:
 * - The IFrame player is designed to be visible. The single [view] instance is owned
 *   here (created with the application context) and re-parented into whatever screen
 *   shows it (mini player / Now Playing), so it survives navigation and rotation.
 * - Background playback (screen off, app in background) is NOT guaranteed: it depends
 *   on the device's WebView and power management. We keep the view attached while the
 *   UI exists, ask the library not to pause on visibility changes, and run a foreground
 *   media service to keep the process alive, but some devices may still pause.
 * - YouTube's policies require the player to be at least 200x200 px and not obscured;
 *   the UI sizes the player surface accordingly.
 */
@Singleton
class YouTubePlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoPlaybackEngine {

    private var listener: EngineListener? = null
    private var player: YouTubePlayer? = null
    private var pendingLoad: PendingLoad? = null
    private var pendingSpeed: Float? = null

    private data class PendingLoad(val videoId: String, val startPositionMs: Long, val autoplay: Boolean)

    /** The one player view. Created lazily on the main thread on first use. */
    val view: YouTubePlayerView by lazy(LazyThreadSafetyMode.NONE) { createView() }

    private fun createView(): YouTubePlayerView = YouTubePlayerView(context).apply {
        enableAutomaticInitialization = false
        // Keep playing when the view's window is hidden (see class docs for caveats).
        enableBackgroundPlayback(true)
        val options = IFramePlayerOptions.Builder(context)
            .controls(0) // Orbit Music draws its own controls; YouTube branding stays visible.
            .rel(0)
            .ivLoadPolicy(3)
            .ccLoadPolicy(0)
            .build()
        initialize(youTubeListener, true, options)
    }

    private val youTubeListener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
            pendingSpeed?.let { youTubePlayer.setPlaybackRate(it.toPlaybackRate()) }
            pendingLoad?.let { load -> youTubePlayer.loadOrCue(load) }
            pendingLoad = null
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            val mapped = when (state) {
                PlayerConstants.PlayerState.PLAYING -> EngineState.Playing
                PlayerConstants.PlayerState.PAUSED -> EngineState.Paused
                PlayerConstants.PlayerState.BUFFERING -> EngineState.Buffering
                PlayerConstants.PlayerState.ENDED -> EngineState.Ended
                PlayerConstants.PlayerState.VIDEO_CUED -> EngineState.Cued
                PlayerConstants.PlayerState.UNSTARTED, PlayerConstants.PlayerState.UNKNOWN -> EngineState.Unstarted
            }
            listener?.onEngineState(mapped)
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
            listener?.onEnginePosition((second * 1000).toLong())
        }

        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
            listener?.onEngineDuration((duration * 1000).toLong())
        }

        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
            val mapped = when (error) {
                PlayerConstants.PlayerError.VIDEO_NOT_FOUND,
                PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER,
                PlayerConstants.PlayerError.HTML_5_PLAYER,
                PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST,
                -> EngineError.VideoUnavailable
                PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER -> EngineError.Configuration
                PlayerConstants.PlayerError.UNKNOWN -> EngineError.Unknown
            }
            listener?.onEngineError(mapped)
        }

        override fun onPlaybackRateChange(youTubePlayer: YouTubePlayer, playbackRate: PlayerConstants.PlaybackRate) {
            playbackRate.toSpeed()?.let { listener?.onEnginePlaybackSpeed(it) }
        }
    }

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    override fun load(videoId: String, startPositionMs: Long, autoplay: Boolean) {
        val request = PendingLoad(videoId, startPositionMs, autoplay)
        val current = player
        if (current == null) {
            pendingLoad = request
            view // Make sure the WebView exists and is initializing.
        } else {
            current.loadOrCue(request)
        }
    }

    override fun play() {
        val current = player
        if (current == null) pendingLoad = pendingLoad?.copy(autoplay = true) else current.play()
    }

    override fun pause() {
        val current = player
        if (current == null) pendingLoad = pendingLoad?.copy(autoplay = false) else current.pause()
    }

    override fun seekTo(positionMs: Long) {
        val current = player
        if (current == null) {
            pendingLoad = pendingLoad?.copy(startPositionMs = positionMs)
        } else {
            current.seekTo(positionMs / 1000f)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        pendingSpeed = speed
        player?.setPlaybackRate(speed.toPlaybackRate())
    }

    private fun YouTubePlayer.loadOrCue(load: PendingLoad) {
        val start = load.startPositionMs / 1000f
        if (load.autoplay) loadVideo(load.videoId, start) else cueVideo(load.videoId, start)
    }

    private companion object {
        val rates = mapOf(
            0.25f to PlayerConstants.PlaybackRate.RATE_0_25,
            0.5f to PlayerConstants.PlaybackRate.RATE_0_5,
            0.75f to PlayerConstants.PlaybackRate.RATE_0_75,
            1f to PlayerConstants.PlaybackRate.RATE_1,
            1.25f to PlayerConstants.PlaybackRate.RATE_1_25,
            1.5f to PlayerConstants.PlaybackRate.RATE_1_5,
            1.75f to PlayerConstants.PlaybackRate.RATE_1_75,
            2f to PlayerConstants.PlaybackRate.RATE_2,
        )

        fun Float.toPlaybackRate(): PlayerConstants.PlaybackRate =
            rates.minBy { (speed, _) -> abs(speed - this) }.value

        fun PlayerConstants.PlaybackRate.toSpeed(): Float? =
            rates.entries.firstOrNull { it.value == this }?.key
    }
}
