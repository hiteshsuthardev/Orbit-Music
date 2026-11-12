package com.example.orbitmusic.player

import com.example.orbitmusic.di.MainDispatcher
import com.example.orbitmusic.domain.model.QueueItem
import com.example.orbitmusic.domain.player.PlayerController
import com.example.orbitmusic.domain.player.PlayerEvent
import com.example.orbitmusic.domain.player.PlayerState
import com.example.orbitmusic.domain.player.QueueManager
import com.example.orbitmusic.domain.player.QueueState
import com.example.orbitmusic.domain.player.RepeatMode
import com.example.orbitmusic.domain.usecase.library.RecordPlayUseCase
import com.example.orbitmusic.domain.usecase.song.MarkSongUnplayableUseCase
import com.example.orbitmusic.player.engine.EngineError
import com.example.orbitmusic.player.engine.EngineListener
import com.example.orbitmusic.player.engine.EngineState
import com.example.orbitmusic.player.engine.VideoPlaybackEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Starts the media foreground service (abstracted for tests). */
fun interface PlaybackServiceStarter {
    fun start()
}

/**
 * The single owner of playback. It follows [QueueManager] (loading whatever becomes the
 * current item), drives the [VideoPlaybackEngine], and exposes [PlayerState].
 *
 * Behaviour:
 * - Auto-advances when a track ends; Repeat One / a one-song Repeat All replays it.
 * - Unavailable videos (removed, private, embedding disabled) are remembered as
 *   unplayable, announced via [PlayerEvent.SkippedUnavailable], and skipped. If every
 *   song fails in a row, playback stops with [PlayerEvent.NothingPlayable].
 * - Records a song in listening history the first time it actually starts playing.
 * - Pauses when headphones are unplugged. Audio focus (pause / resume / duck around calls
 *   and other apps) is handled by the WebView's own Chromium media stack; those pauses
 *   arrive here as engine Paused / Playing states (see AudioSessionGuard).
 *
 * All methods must be called on the main thread (the IFrame player lives in a WebView).
 */
@Singleton
class PlayerManager @Inject constructor(
    private val engine: VideoPlaybackEngine,
    private val queue: QueueManager,
    private val recordPlay: RecordPlayUseCase,
    private val markUnplayable: MarkSongUnplayableUseCase,
    private val audioGuard: AudioSessionGuard,
    private val serviceStarter: PlaybackServiceStarter,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private data class Status(
        val playWhenReady: Boolean = false,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val hasEnded: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: Float = 1f,
        val pauseAtEndOfTrack: Boolean = false,
    )

    private val status = MutableStateFlow(Status())
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    override val state: StateFlow<PlayerState> = combine(status, queue.state) { s, q ->
        PlayerState(
            queue = q,
            playWhenReady = s.playWhenReady,
            isPlaying = s.isPlaying,
            isBuffering = s.isBuffering,
            hasEnded = s.hasEnded,
            positionMs = s.positionMs,
            durationMs = s.durationMs,
            playbackSpeed = s.speed,
            pauseAtEndOfTrack = s.pauseAtEndOfTrack,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlayerState())

    private var loadedQueueId: String? = null
    private var historyRecordedFor: String? = null
    private var consecutiveErrors = 0

    /** One-shot overrides for the next load (session restore / pause-at-end cueing). */
    private var autoplayNextLoad = true
    private var nextLoadStartMs = 0L

    private val engineListener = object : EngineListener {
        override fun onEngineState(state: EngineState) = handleEngineState(state)
        override fun onEnginePosition(positionMs: Long) = status.update { it.copy(positionMs = positionMs) }
        override fun onEngineDuration(durationMs: Long) {
            if (durationMs > 0) status.update { it.copy(durationMs = durationMs) }
        }
        override fun onEngineError(error: EngineError) = handleError(error)
        override fun onEnginePlaybackSpeed(speed: Float) = status.update { it.copy(speed = speed) }
    }

    private val audioListener = object : AudioSessionGuard.Listener {
        override fun onBecomingNoisy() = pause()
    }

    init {
        engine.setListener(engineListener)
        audioGuard.setListener(audioListener)
        scope.launch {
            queue.state
                .map { it.current }
                .distinctUntilChanged { old, new -> old?.queueId == new?.queueId }
                .collect(::onCurrentItemChanged)
        }
    }

    // Queue -> engine ---------------------------------------------------------------------

    private fun onCurrentItemChanged(item: QueueItem?) {
        if (item == null) {
            if (loadedQueueId == null) return // Nothing was loaded yet (e.g. app start).
            loadedQueueId = null
            engine.pause()
            status.update { Status(speed = it.speed) }
            audioGuard.onPlaybackStopped()
            return
        }
        if (item.queueId == loadedQueueId) return
        loadedQueueId = item.queueId
        historyRecordedFor = null
        val autoplay = autoplayNextLoad
        val start = nextLoadStartMs
        autoplayNextLoad = true
        nextLoadStartMs = 0L
        status.update {
            it.copy(
                playWhenReady = autoplay,
                isPlaying = false,
                isBuffering = autoplay,
                hasEnded = false,
                positionMs = start,
                durationMs = item.song.durationMs,
            )
        }
        engine.load(item.song.videoId, startPositionMs = start, autoplay = autoplay)
        if (autoplay) serviceStarter.start()
    }

    // Engine -> state ---------------------------------------------------------------------

    private fun handleEngineState(engineState: EngineState) {
        when (engineState) {
            EngineState.Playing -> {
                consecutiveErrors = 0
                status.update { it.copy(isPlaying = true, isBuffering = false, playWhenReady = true, hasEnded = false) }
                audioGuard.onPlaybackStarted()
                recordHistoryOnce()
            }
            EngineState.Paused -> {
                status.update { it.copy(isPlaying = false, isBuffering = false, playWhenReady = false) }
                audioGuard.onPlaybackPaused()
            }
            EngineState.Buffering -> status.update { it.copy(isBuffering = true) }
            EngineState.Cued -> status.update { it.copy(isPlaying = false, isBuffering = false) }
            EngineState.Unstarted -> status.update { it.copy(isBuffering = it.playWhenReady) }
            EngineState.Ended -> handleEnded()
        }
    }

    private fun handleEnded() {
        status.update { it.copy(positionMs = it.durationMs, isPlaying = false) }
        if (status.value.pauseAtEndOfTrack) {
            pauseAfterTrack()
            return
        }
        if (!advance(userInitiated = false)) {
            status.update { it.copy(playWhenReady = false, isBuffering = false, hasEnded = true) }
            audioGuard.onPlaybackStopped()
        }
    }

    private fun handleError(error: EngineError) {
        val item = queue.state.value.current ?: return
        consecutiveErrors++
        if (error == EngineError.Configuration) {
            stopPlayback()
            _events.tryEmit(PlayerEvent.PlaybackFailed)
            return
        }
        if (error == EngineError.VideoUnavailable) {
            scope.launch { markUnplayable(item.song.videoId) }
        }
        val queueSize = queue.state.value.items.size
        if (consecutiveErrors >= queueSize) {
            stopPlayback()
            _events.tryEmit(PlayerEvent.NothingPlayable)
            return
        }
        _events.tryEmit(PlayerEvent.SkippedUnavailable(item.song.title))
        val advanced = queue.next(userInitiated = true)
        if (!advanced || queue.state.value.current?.queueId == loadedQueueId) stopPlayback()
    }

    private fun recordHistoryOnce() {
        val item = queue.state.value.current ?: return
        if (historyRecordedFor == item.queueId) return
        historyRecordedFor = item.queueId
        scope.launch { recordPlay(item.song) }
    }

    /**
     * Moves to the next item. When the queue "advances" onto the same entry (Repeat One,
     * or Repeat All with a single song) the observer won't reload it, so restart it here.
     */
    private fun advance(userInitiated: Boolean): Boolean {
        val before = loadedQueueId
        val advanced = queue.next(userInitiated)
        if (advanced && queue.state.value.current?.queueId == before) restartCurrent()
        return advanced
    }

    /** Sleep timer "end of song": move on to the next song but leave it cued, not playing. */
    private fun pauseAfterTrack() {
        status.update { it.copy(pauseAtEndOfTrack = false, playWhenReady = false, isBuffering = false) }
        audioGuard.onPlaybackPaused()
        val before = loadedQueueId
        autoplayNextLoad = false
        val advanced = queue.next(userInitiated = false)
        val current = queue.state.value.current?.queueId
        if (!advanced || current == before) {
            autoplayNextLoad = true
            if (advanced) {
                engine.seekTo(0L)
                status.update { it.copy(positionMs = 0L) }
            } else {
                status.update { it.copy(hasEnded = true) }
            }
        }
    }

    private fun restartCurrent() {
        historyRecordedFor = null
        status.update { it.copy(positionMs = 0L, hasEnded = false, playWhenReady = true) }
        engine.seekTo(0L)
        engine.play()
    }

    private fun stopPlayback() {
        engine.pause()
        status.update { it.copy(playWhenReady = false, isPlaying = false, isBuffering = false) }
        audioGuard.onPlaybackStopped()
    }

    // Commands ----------------------------------------------------------------------------

    override fun play() {
        if (queue.state.value.current == null) return
        if (status.value.hasEnded) {
            restartCurrent()
        } else {
            status.update { it.copy(playWhenReady = true) }
            engine.play()
        }
        serviceStarter.start()
    }

    override fun pause() {
        engine.pause()
        status.update { it.copy(playWhenReady = false) }
    }

    override fun togglePlayPause() {
        if (status.value.playWhenReady) pause() else play()
    }

    override fun next() {
        advance(userInitiated = true)
    }

    override fun previous() {
        if (status.value.positionMs > RESTART_THRESHOLD_MS) {
            seekTo(0L)
            return
        }
        val before = loadedQueueId
        queue.previous()
        if (queue.state.value.current?.queueId == before) seekTo(0L)
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        status.update { it.copy(positionMs = clamped, hasEnded = false) }
        engine.seekTo(clamped)
    }

    override fun setPlaybackSpeed(speed: Float) {
        status.update { it.copy(speed = speed) }
        engine.setPlaybackSpeed(speed)
    }

    override fun toggleShuffle() = queue.toggleShuffle()

    override fun setShuffle(enabled: Boolean) = queue.setShuffle(enabled)

    override fun cycleRepeatMode() = queue.cycleRepeat()

    override fun setRepeatMode(mode: RepeatMode) = queue.setRepeatMode(mode)

    override fun skipToQueueItem(queueId: String) {
        if (queueId == loadedQueueId) restartCurrent() else queue.skipTo(queueId)
    }

    override fun removeFromQueue(queueId: String) = queue.remove(queueId)

    override fun moveQueueItem(from: Int, to: Int) = queue.move(from, to)

    override fun stop() {
        stopPlayback()
        status.update { it.copy(pauseAtEndOfTrack = false) }
        queue.clear()
    }

    override fun setPauseAtEndOfTrack(enabled: Boolean) {
        status.update { it.copy(pauseAtEndOfTrack = enabled) }
    }

    override fun restoreSession(queue: QueueState, positionMs: Long) {
        if (!this.queue.state.value.isEmpty || queue.current == null) return
        autoplayNextLoad = false
        nextLoadStartMs = positionMs.coerceAtLeast(0L)
        this.queue.restore(queue)
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
