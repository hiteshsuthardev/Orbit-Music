package com.example.orbitmusic

import android.app.Application
import com.example.orbitmusic.player.PlaybackSessionKeeper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OrbitApplication : Application() {

    @Inject lateinit var playbackSessionKeeper: PlaybackSessionKeeper

    override fun onCreate() {
        super.onCreate()
        // Restores the last queue (cued, not playing) and keeps it saved from now on.
        playbackSessionKeeper.start()
    }
}
