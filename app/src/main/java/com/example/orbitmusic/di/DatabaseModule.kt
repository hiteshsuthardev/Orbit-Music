package com.example.orbitmusic.di

import android.content.Context
import androidx.room.Room
import com.example.orbitmusic.data.local.db.OrbitDatabase
import com.example.orbitmusic.data.local.db.dao.CacheMetadataDao
import com.example.orbitmusic.data.local.db.dao.ChannelDao
import com.example.orbitmusic.data.local.db.dao.CollectionDao
import com.example.orbitmusic.data.local.db.dao.FavoriteDao
import com.example.orbitmusic.data.local.db.dao.HistoryDao
import com.example.orbitmusic.data.local.db.dao.PlaylistDao
import com.example.orbitmusic.data.local.db.dao.SearchHistoryDao
import com.example.orbitmusic.data.local.db.dao.SongDao
import com.example.orbitmusic.data.local.db.DatabaseSessionCleaner
import com.example.orbitmusic.data.repository.AiUsageRepositoryImpl
import com.example.orbitmusic.data.repository.ChannelRepositoryImpl
import com.example.orbitmusic.data.update.RemoteConfigRepositoryImpl
import com.example.orbitmusic.data.update.UpdateRepositoryImpl
import com.example.orbitmusic.domain.update.RemoteConfigRepository
import com.example.orbitmusic.domain.update.UpdateRepository
import com.example.orbitmusic.data.repository.GroqAiRepository
import com.example.orbitmusic.data.repository.LyricsRepositoryImpl
import com.example.orbitmusic.data.repository.PlaylistRepositoryImpl
import com.example.orbitmusic.data.repository.SearchRepositoryImpl
import com.example.orbitmusic.data.repository.SongRepositoryImpl
import com.example.orbitmusic.data.repository.UserLibraryRepositoryImpl
import com.example.orbitmusic.domain.repository.AiRepository
import com.example.orbitmusic.domain.repository.AiUsageRepository
import com.example.orbitmusic.domain.repository.ChannelRepository
import com.example.orbitmusic.domain.repository.LyricsRepository
import com.example.orbitmusic.domain.repository.PlaylistRepository
import com.example.orbitmusic.domain.repository.SearchRepository
import com.example.orbitmusic.domain.repository.SessionCleaner
import com.example.orbitmusic.domain.repository.SongRepository
import com.example.orbitmusic.domain.repository.UserLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Schema version 2 (see [OrbitDatabase.MIGRATION_1_2]). Future schema changes must ship a Migration: favorites and
     * listening history live here and must survive app updates.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OrbitDatabase =
        Room.databaseBuilder(context, OrbitDatabase::class.java, OrbitDatabase.NAME)
            .addMigrations(OrbitDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideSongDao(db: OrbitDatabase): SongDao = db.songDao()

    @Provides fun providePlaylistDao(db: OrbitDatabase): PlaylistDao = db.playlistDao()

    @Provides fun provideCollectionDao(db: OrbitDatabase): CollectionDao = db.collectionDao()

    @Provides fun provideCacheMetadataDao(db: OrbitDatabase): CacheMetadataDao = db.cacheMetadataDao()

    @Provides fun provideFavoriteDao(db: OrbitDatabase): FavoriteDao = db.favoriteDao()

    @Provides fun provideHistoryDao(db: OrbitDatabase): HistoryDao = db.historyDao()

    @Provides fun provideSearchHistoryDao(db: OrbitDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides fun provideChannelDao(db: OrbitDatabase): ChannelDao = db.channelDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    abstract fun bindSongRepository(impl: SongRepositoryImpl): SongRepository

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    abstract fun bindUserLibraryRepository(impl: UserLibraryRepositoryImpl): UserLibraryRepository

    @Binds
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    @Binds
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository

    @Binds
    abstract fun bindRemoteConfigRepository(impl: RemoteConfigRepositoryImpl): RemoteConfigRepository

    @Binds
    abstract fun bindAiUsageRepository(impl: AiUsageRepositoryImpl): AiUsageRepository

    /** Swap the AI provider by binding another AiRepository here. */
    @Binds
    abstract fun bindAiRepository(impl: GroqAiRepository): AiRepository

    /** Sign-out wipes the whole database (cache, favorites, history, searches). */
    @Binds
    @IntoSet
    abstract fun bindDatabaseSessionCleaner(impl: DatabaseSessionCleaner): SessionCleaner
}
