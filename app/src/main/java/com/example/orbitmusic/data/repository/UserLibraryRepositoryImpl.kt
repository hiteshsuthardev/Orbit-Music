package com.example.orbitmusic.data.repository

import androidx.room.withTransaction
import com.example.orbitmusic.data.local.db.OrbitDatabase
import com.example.orbitmusic.data.local.db.entity.FavoriteEntity
import com.example.orbitmusic.data.mapper.toEntity
import com.example.orbitmusic.data.mapper.toSongs
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.UserLibraryRepository
import com.example.orbitmusic.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLibraryRepositoryImpl @Inject constructor(
    private val db: OrbitDatabase,
    private val timeProvider: TimeProvider,
) : UserLibraryRepository {

    private val songDao = db.songDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()

    override fun observeFavorites(): Flow<List<Song>> = favoriteDao.observeFavoriteSongs().map { it.toSongs() }

    override fun observeFavoriteIds(): Flow<Set<String>> = favoriteDao.observeFavoriteIds().map { it.toSet() }

    override suspend fun setFavorite(song: Song, favorite: Boolean) {
        db.withTransaction {
            if (favorite) {
                ensureSongStored(song)
                favoriteDao.insert(FavoriteEntity(song.videoId, timeProvider.currentTimeMillis()))
            } else {
                favoriteDao.delete(song.videoId)
            }
        }
    }

    override suspend fun isFavorite(videoId: String): Boolean = favoriteDao.isFavorite(videoId)

    override fun observeRecentlyPlayed(limit: Int): Flow<List<Song>> =
        historyDao.observeRecent(limit).map { it.toSongs() }

    override fun observeMostPlayed(limit: Int): Flow<List<Song>> =
        historyDao.observeMostPlayed(limit).map { it.toSongs() }

    override suspend fun recordPlay(song: Song) {
        db.withTransaction {
            ensureSongStored(song)
            historyDao.recordPlay(song.videoId, timeProvider.currentTimeMillis())
        }
    }

    override suspend fun clearHistory() = historyDao.clear()

    /** Keeps richer cached data (raw title, channel) if the song is already stored. */
    private suspend fun ensureSongStored(song: Song) {
        if (songDao.getById(song.videoId) == null) {
            songDao.upsertAll(listOf(song.toEntity(timeProvider.currentTimeMillis())))
        }
    }
}
