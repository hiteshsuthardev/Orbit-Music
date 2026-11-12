package com.example.orbitmusic.di

import com.example.orbitmusic.domain.player.PlayerController
import com.example.orbitmusic.domain.player.QueueManager
import com.example.orbitmusic.data.repository.PlaybackSessionRepositoryImpl
import com.example.orbitmusic.domain.repository.PlaybackSessionRepository
import com.example.orbitmusic.domain.repository.SessionCleaner
import com.example.orbitmusic.player.AndroidAudioSessionGuard
import com.example.orbitmusic.player.AudioSessionGuard
import com.example.orbitmusic.player.PlaybackServiceStarter
import com.example.orbitmusic.player.PlayerManager
import com.example.orbitmusic.player.engine.VideoPlaybackEngine
import com.example.orbitmusic.player.engine.YouTubePlaybackEngine
import com.example.orbitmusic.service.AndroidPlaybackServiceStarter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    abstract fun bindPlayerController(impl: PlayerManager): PlayerController

    @Binds
    abstract fun bindVideoPlaybackEngine(impl: YouTubePlaybackEngine): VideoPlaybackEngine

    @Binds
    abstract fun bindAudioSessionGuard(impl: AndroidAudioSessionGuard): AudioSessionGuard

    @Binds
    abstract fun bindPlaybackSessionRepository(impl: PlaybackSessionRepositoryImpl): PlaybackSessionRepository

    @Binds
    abstract fun bindPlaybackServiceStarter(impl: AndroidPlaybackServiceStarter): PlaybackServiceStarter

    companion object {
        /** Signing out stops playback and empties the queue. */
        @Provides
        @IntoSet
        fun providePlayerSessionCleaner(
            controller: PlayerController,
            @MainDispatcher mainDispatcher: CoroutineDispatcher,
        ): SessionCleaner = SessionCleaner { withContext(mainDispatcher) { controller.stop() } }

        /** ...and forgets the saved session so the next account starts fresh. */
        @Provides
        @IntoSet
        fun providePlaybackSessionCleaner(repository: PlaybackSessionRepository): SessionCleaner =
            SessionCleaner { repository.clear() }
    }
}

/** Lets the player surface composable reach the single YouTube player view. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerSurfaceEntryPoint {
    fun youTubePlaybackEngine(): YouTubePlaybackEngine
}

/** Access to the playback singletons outside Hilt-injected classes (e.g. instrumentation tests). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackEntryPoint {
    fun playerController(): PlayerController
    fun queueManager(): QueueManager
}
