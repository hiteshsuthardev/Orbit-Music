package com.example.orbitmusic.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.orbitmusic.data.local.db.dao.CacheMetadataDao
import com.example.orbitmusic.data.local.db.dao.ChannelDao
import com.example.orbitmusic.data.local.db.dao.CollectionDao
import com.example.orbitmusic.data.local.db.dao.FavoriteDao
import com.example.orbitmusic.data.local.db.dao.HistoryDao
import com.example.orbitmusic.data.local.db.dao.LocalPlaylistDao
import com.example.orbitmusic.data.local.db.dao.PlaylistDao
import com.example.orbitmusic.data.local.db.dao.SearchHistoryDao
import com.example.orbitmusic.data.local.db.dao.SongDao
import com.example.orbitmusic.data.local.db.entity.CacheMetadataEntity
import com.example.orbitmusic.data.local.db.entity.ChannelEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemEntity
import com.example.orbitmusic.data.local.db.entity.FavoriteEntity
import com.example.orbitmusic.data.local.db.entity.HistoryEntity
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistEntity
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistSongEntity
import com.example.orbitmusic.data.local.db.entity.PlaylistEntity
import com.example.orbitmusic.data.local.db.entity.SearchHistoryEntity
import com.example.orbitmusic.data.local.db.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        CollectionItemEntity::class,
        CacheMetadataEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        SearchHistoryEntity::class,
        ChannelEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistSongEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun collectionDao(): CollectionDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun localPlaylistDao(): LocalPlaylistDao

    companion object {
        const val NAME = "orbit_music.db"

        /**
         * 1 -> 2: OrbitNova Music became music-videos-only. The schema is unchanged; cached lists
         * (which could hold Shorts or other videos) are dropped so they refetch through the
         * music filter. Favorites, history and search history are kept.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM collection_items")
                db.execSQL("DELETE FROM cache_metadata")
            }
        }

        /** 2 -> 3: playlists stored in OrbitNova itself. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_playlists` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`youTubePlaylistId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_playlist_songs` (`playlistId` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `videoId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `position`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_playlist_songs_videoId` ON `local_playlist_songs` (`videoId`)",
                )
            }
        }
    }
}
