package com.example.orbitmusic.service

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.example.orbitmusic.di.MainDispatcher
import com.example.orbitmusic.domain.player.PlayerController
import com.example.orbitmusic.domain.player.PlayerState
import com.example.orbitmusic.domain.player.RepeatMode
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Adapts [PlayerController] to Media3's [Player] so a MediaSession can expose it. Media3
 * then provides the media-style notification, lock-screen controls and Bluetooth /
 * headset media buttons; every command coming from those surfaces is forwarded here.
 */
@OptIn(UnstableApi::class)
@Singleton
class MediaSessionPlayer @Inject constructor(
    private val controller: PlayerController,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var lastPublishedPositionMs = 0L

    init {
        // Republish on every meaningful change. Plain position ticks are not needed (the
        // session extrapolates while playing), but seeks / jumps are.
        scope.launch {
            controller.state
                .map { it.toSnapshot() }
                .distinctUntilChanged()
                .collect { invalidateState() }
        }
        scope.launch {
            controller.state
                .filter { abs(it.positionMs - expectedPositionMs(it)) > POSITION_JUMP_MS }
                .collect { invalidateState() }
        }
    }

    /** Fields whose change must reach the notification (position excluded). */
    private data class Snapshot(
        val queueIds: List<String>,
        val currentIndex: Int,
        val playWhenReady: Boolean,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val hasEnded: Boolean,
        val durationMs: Long,
        val speed: Float,
        val shuffle: Boolean,
        val repeat: RepeatMode,
    )

    private fun PlayerState.toSnapshot() = Snapshot(
        queueIds = queue.items.map { it.queueId },
        currentIndex = queue.currentIndex,
        playWhenReady = playWhenReady,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        hasEnded = hasEnded,
        durationMs = durationMs,
        speed = playbackSpeed,
        shuffle = queue.shuffleEnabled,
        repeat = queue.repeatMode,
    )

    private var publishedAtElapsed = 0L

    private fun expectedPositionMs(state: PlayerState): Long {
        if (!state.isPlaying) return lastPublishedPositionMs
        val elapsed = android.os.SystemClock.elapsedRealtime() - publishedAtElapsed
        return lastPublishedPositionMs + (elapsed * state.playbackSpeed).toLong()
    }

    override fun getState(): State {
        val s = controller.state.value
        lastPublishedPositionMs = s.positionMs
        publishedAtElapsed = android.os.SystemClock.elapsedRealtime()

        val playlist = s.queue.items.mapIndexed { index, item ->
            val isCurrent = index == s.queue.currentIndex
            val durationMs = if (isCurrent && s.durationMs > 0) s.durationMs else item.song.durationMs
            MediaItemData.Builder(item.queueId)
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(item.song.videoId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(item.song.title)
                                .setArtist(item.song.artist)
                                .setArtworkUri(item.song.thumbnailUrl?.let(Uri::parse))
                                .setIsPlayable(item.song.isPlayable)
                                .build(),
                        )
                        .build(),
                )
                .setDurationUs(if (durationMs > 0) durationMs * 1000 else C.TIME_UNSET)
                .build()
        }

        val playbackState = when {
            playlist.isEmpty() -> Player.STATE_IDLE
            s.hasEnded -> Player.STATE_ENDED
            s.isBuffering && s.playWhenReady -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }

        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(s.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlaylist(playlist)
            .apply { if (playlist.isNotEmpty()) setCurrentMediaItemIndex(s.queue.currentIndex.coerceIn(0, playlist.lastIndex)) }
            .setContentPositionMs(
                PositionSupplier.getExtrapolating(s.positionMs, if (s.isPlaying) s.playbackSpeed else 0f),
            )
            .setShuffleModeEnabled(s.queue.shuffleEnabled)
            .setRepeatMode(
                when (s.queue.repeatMode) {
                    RepeatMode.Off -> Player.REPEAT_MODE_OFF
                    RepeatMode.All -> Player.REPEAT_MODE_ALL
                    RepeatMode.One -> Player.REPEAT_MODE_ONE
                },
            )
            .setPlaybackParameters(PlaybackParameters(s.playbackSpeed))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) controller.play() else controller.pause()
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> controller.next()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> controller.previous()
            Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> {
                val queueState = controller.state.value.queue
                val target = queueState.items.getOrNull(mediaItemIndex)
                if (target != null && mediaItemIndex != queueState.currentIndex) {
                    controller.skipToQueueItem(target.queueId)
                } else {
                    controller.seekTo(positionMs.coerceAtLeast(0L))
                }
            }
            else -> controller.seekTo(positionMs.coerceAtLeast(0L))
        }
        return done()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        controller.setShuffle(shuffleModeEnabled)
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        controller.setRepeatMode(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                else -> RepeatMode.Off
            },
        )
        return done()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        controller.setPlaybackSpeed(playbackParameters.speed)
        return done()
    }

    /** "Stop" from the notification / a controller pauses but keeps the queue. */
    override fun handleStop(): ListenableFuture<*> {
        controller.pause()
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> = done()

    /** The player is an app-wide singleton; releasing the session must not tear it down. */
    override fun handleRelease(): ListenableFuture<*> = done()

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val POSITION_JUMP_MS = 2_000L

        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
