package com.example.orbitmusic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.orbitmusic.data.local.db.entity.CacheMetadataEntity
import com.example.orbitmusic.data.local.db.entity.ChannelEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemEntity
import com.example.orbitmusic.data.local.db.entity.FavoriteEntity
import com.example.orbitmusic.data.local.db.entity.HistoryEntity
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistEntity
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistRow
import com.example.orbitmusic.data.local.db.entity.LocalPlaylistSongEntity
import com.example.orbitmusic.data.local.db.entity.PlaylistEntity
import com.example.orbitmusic.data.local.db.entity.SearchHistoryEntity
import com.example.orbitmusic.data.local.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE videoId = :videoId")
    suspend fun getById(videoId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE videoId = :videoId")
    fun observeById(videoId: String): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE videoId IN (:videoIds)")
    suspend fun getByIds(videoIds: List<String>): List<SongEntity>

    @Query("UPDATE songs SET isPlayable = 0 WHERE videoId = :videoId")
    suspend fun markUnplayable(videoId: String)

    /** Quota-free title suggestions from everything cached locally. */
    @Query(
        "SELECT DISTINCT title FROM songs WHERE title LIKE :prefix || '%' ESCAPE '\\' " +
            "ORDER BY updatedAt DESC LIMIT :limit",
    )
    suspend fun titlesStartingWith(prefix: String, limit: Int): List<String>
}

@Dao
interface PlaylistDao {
    @Upsert
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    /** Used for search results so they never overwrite richer data (e.g. itemCount). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(playlists: List<PlaylistEntity>)

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?
}

@Dao
abstract class CollectionDao {

    @Query(
        "SELECT s.* FROM collection_items c INNER JOIN songs s ON s.videoId = c.itemId " +
            "WHERE c.collectionKey = :key AND c.itemType = 0 ORDER BY c.position",
    )
    abstract fun observeSongs(key: String): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* FROM collection_items c INNER JOIN songs s ON s.videoId = c.itemId " +
            "WHERE c.collectionKey = :key AND c.itemType = 0 ORDER BY c.position",
    )
    abstract suspend fun getSongs(key: String): List<SongEntity>

    @Query(
        "SELECT p.* FROM collection_items c INNER JOIN playlists p ON p.id = c.itemId " +
            "WHERE c.collectionKey = :key AND c.itemType = 1 ORDER BY c.position",
    )
    abstract fun observePlaylists(key: String): Flow<List<PlaylistEntity>>

    @Query(
        "SELECT p.* FROM collection_items c INNER JOIN playlists p ON p.id = c.itemId " +
            "WHERE c.collectionKey = :key AND c.itemType = 1 ORDER BY c.position",
    )
    abstract suspend fun getPlaylists(key: String): List<PlaylistEntity>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM collection_items WHERE collectionKey = :key")
    abstract suspend fun nextPosition(key: String): Int

    @Query("SELECT COUNT(*) FROM collection_items WHERE collectionKey = :key")
    abstract suspend fun count(key: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<CollectionItemEntity>)

    @Query("DELETE FROM collection_items WHERE collectionKey = :key")
    abstract suspend fun clear(key: String)

    /** Replaces a whole collection atomically (first page / full refresh). */
    @Transaction
    open suspend fun replace(key: String, items: List<CollectionItemEntity>) {
        clear(key)
        insertAll(items)
    }
}

@Dao
interface CacheMetadataDao {
    @Query("SELECT * FROM cache_metadata WHERE `key` = :key")
    suspend fun get(key: String): CacheMetadataEntity?

    @Query("SELECT * FROM cache_metadata WHERE `key` = :key")
    fun observe(key: String): Flow<CacheMetadataEntity?>

    @Upsert
    suspend fun upsert(metadata: CacheMetadataEntity)

    @Query("DELETE FROM cache_metadata WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface FavoriteDao {
    @Query(
        "SELECT s.* FROM favorites f INNER JOIN songs s ON s.videoId = f.videoId ORDER BY f.addedAt DESC",
    )
    fun observeFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT videoId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun delete(videoId: String)
}

@Dao
abstract class HistoryDao {

    @Query(
        "SELECT s.* FROM history h INNER JOIN songs s ON s.videoId = h.videoId " +
            "ORDER BY h.lastPlayedAt DESC LIMIT :limit",
    )
    abstract fun observeRecent(limit: Int): Flow<List<SongEntity>>

    /** Most played first; used for "Quick picks". */
    @Query(
        "SELECT s.* FROM history h INNER JOIN songs s ON s.videoId = h.videoId " +
            "WHERE s.isPlayable = 1 ORDER BY h.playCount DESC, h.lastPlayedAt DESC LIMIT :limit",
    )
    abstract fun observeMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM history WHERE videoId = :videoId")
    abstract suspend fun get(videoId: String): HistoryEntity?

    @Upsert
    abstract suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history")
    abstract suspend fun clear()

    /**
     * Insert-or-increment. Done in a transaction instead of SQLite UPSERT syntax, which
     * needs SQLite 3.24 (not available on API 26-29).
     */
    @Transaction
    open suspend fun recordPlay(videoId: String, playedAt: Long) {
        val existing = get(videoId)
        upsert(
            HistoryEntity(
                videoId = videoId,
                lastPlayedAt = playedAt,
                playCount = (existing?.playCount ?: 0) + 1,
            ),
        )
    }
}

@Dao
interface SearchHistoryDao {
    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query(
        "SELECT * FROM search_history WHERE normalizedQuery LIKE :prefix || '%' ESCAPE '\\' " +
            "ORDER BY searchedAt DESC LIMIT :limit",
    )
    suspend fun matching(prefix: String, limit: Int): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history WHERE normalizedQuery = :normalizedQuery")
    suspend fun delete(normalizedQuery: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface ChannelDao {
    @Upsert
    suspend fun upsert(channel: ChannelEntity)

    @Query("SELECT * FROM channels WHERE isMine = 1 LIMIT 1")
    fun observeMine(): Flow<ChannelEntity?>
}

private const val LOCAL_PLAYLIST_ROW =
    "SELECT p.*, " +
        "(SELECT COUNT(*) FROM local_playlist_songs ls WHERE ls.playlistId = p.id) AS itemCount, " +
        "(SELECT s.thumbnailUrl FROM local_playlist_songs ls INNER JOIN songs s ON s.videoId = ls.videoId " +
        "WHERE ls.playlistId = p.id ORDER BY ls.position LIMIT 1) AS thumbnailUrl " +
        "FROM local_playlists p"

@Dao
abstract class LocalPlaylistDao {
    @Query("$LOCAL_PLAYLIST_ROW ORDER BY p.updatedAt DESC")
    abstract fun observeAll(): Flow<List<LocalPlaylistRow>>

    @Query("$LOCAL_PLAYLIST_ROW WHERE p.id = :id")
    abstract fun observeById(id: String): Flow<LocalPlaylistRow?>

    @Query("SELECT * FROM local_playlists WHERE id = :id")
    abstract suspend fun getById(id: String): LocalPlaylistEntity?

    @Query(
        "SELECT s.* FROM local_playlist_songs ls INNER JOIN songs s ON s.videoId = ls.videoId " +
            "WHERE ls.playlistId = :id ORDER BY ls.position",
    )
    abstract fun observeSongs(id: String): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* FROM local_playlist_songs ls INNER JOIN songs s ON s.videoId = ls.videoId " +
            "WHERE ls.playlistId = :id ORDER BY ls.position",
    )
    abstract suspend fun getSongs(id: String): List<SongEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM local_playlist_songs WHERE playlistId = :id AND videoId = :videoId)")
    abstract suspend fun contains(id: String, videoId: String): Boolean

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM local_playlist_songs WHERE playlistId = :id")
    abstract suspend fun nextPosition(id: String): Int

    @Upsert
    abstract suspend fun upsert(playlist: LocalPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSongs(songs: List<LocalPlaylistSongEntity>)

    @Query("UPDATE local_playlists SET youTubePlaylistId = :youTubeId, updatedAt = :now WHERE id = :id")
    abstract suspend fun link(id: String, youTubeId: String, now: Long)

    @Query("UPDATE local_playlists SET updatedAt = :now WHERE id = :id")
    abstract suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM local_playlist_songs WHERE playlistId = :id")
    abstract suspend fun deleteSongs(id: String)

    @Query("DELETE FROM local_playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: String)

    @Transaction
    open suspend fun delete(id: String) {
        deleteSongs(id)
        deletePlaylist(id)
    }
}
