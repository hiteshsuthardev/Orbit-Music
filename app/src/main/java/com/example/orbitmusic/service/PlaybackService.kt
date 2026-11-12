package com.example.orbitmusic.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.orbitmusic.MainActivity
import com.example.orbitmusic.R
import com.example.orbitmusic.player.PlaybackServiceStarter
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Foreground media service. Hosts the [MediaSession]; Media3 posts the media-style
 * notification (artwork, title, artist, previous / play-pause / next), shows lock-screen
 * controls, routes Bluetooth / headset buttons, and moves the service in and out of the
 * foreground as playback starts and stops.
 *
 * Note: the service keeps the process alive, but the audio itself comes from the YouTube
 * IFrame player in a WebView, so background behaviour can still vary by device (see
 * YouTubePlaybackEngine).
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var sessionPlayer: MediaSessionPlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_playback)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )
        val session = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(openApp)
            .build()
        mediaSession = session
        // No MediaController binds to us, so register the session explicitly; this is what
        // makes Media3 post the notification and manage foreground state.
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Swiping the app away: keep going only if something is actually playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            removeSession(session)
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/** Starts [PlaybackService] when playback begins (the app is in the foreground then). */
class AndroidPlaybackServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackServiceStarter {
    override fun start() {
        // Starting a plain service is only allowed from the foreground. If we are in the
        // background the service is already running (it is what keeps us alive).
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
    }
}
