package com.example.orbitmusic.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Being a good audio citizen around the WebView-based player.
 *
 * Audio focus: the YouTube IFrame player runs in a WebView, and Chromium's media stack
 * already requests audio focus for it (`org.chromium.content.browser.AudioFocusDelegate`)
 * and pauses / resumes / ducks the video on focus changes. We deliberately do NOT
 * request focus ourselves: doing so makes the WebView *lose* focus to our own request
 * and pause about a second after playback starts (observed on-device). Chromium's
 * pause/resume reaches PlayerManager as ordinary engine Paused / Playing states.
 *
 * What this class adds on top: pausing when audio becomes noisy (headphones unplugged,
 * Bluetooth disconnected) and a CPU wake lock while playing with the screen off.
 */
interface AudioSessionGuard {
    fun setListener(listener: Listener?)

    /** Playback started: watch for unplugging and keep the CPU awake. */
    fun onPlaybackStarted()

    /** Paused: stop watching and release the wake lock. */
    fun onPlaybackPaused()

    /** Finished or stopped. */
    fun onPlaybackStopped()

    interface Listener {
        fun onBecomingNoisy()
    }
}

@Singleton
class AndroidAudioSessionGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioSessionGuard {

    private var listener: AudioSessionGuard.Listener? = null
    private var noisyRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) listener?.onBecomingNoisy()
        }
    }

    private val wakeLock: PowerManager.WakeLock? =
        context.getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OrbitMusic:playback")
            ?.apply { setReferenceCounted(false) }

    override fun setListener(listener: AudioSessionGuard.Listener?) {
        this.listener = listener
    }

    override fun onPlaybackStarted() {
        registerNoisy()
        acquireWakeLock()
    }

    override fun onPlaybackPaused() {
        unregisterNoisy()
        releaseWakeLock()
    }

    override fun onPlaybackStopped() = onPlaybackPaused()

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        // Re-acquired on every play; released on pause/stop. Timeout is a safety net.
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private companion object {
        const val WAKE_LOCK_TIMEOUT_MS = 3L * 60 * 60 * 1000
    }
}
