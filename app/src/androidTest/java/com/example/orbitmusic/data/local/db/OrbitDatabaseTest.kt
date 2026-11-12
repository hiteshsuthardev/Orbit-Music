package com.example.orbitmusic.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.orbitmusic.data.local.db.entity.CollectionItemEntity
import com.example.orbitmusic.data.local.db.entity.CollectionItemType
import com.example.orbitmusic.data.local.db.entity.FavoriteEntity
import com.example.orbitmusic.data.local.db.entity.SearchHistoryEntity
import com.example.orbitmusic.data.local.db.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbitDatabaseTest {

    private lateinit var db: OrbitDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OrbitDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun song(id: String, title: String = "Song $id") = SongEntity(
        videoId = id, title = title, artist = "Artist", rawTitle = title, channelId = null,
        channelTitle = null, thumbnailUrl = null, durationMs = 1000, isPlayable = true, updatedAt = 0,
    )

    private fun item(key: String, position: Int, id: String) =
        CollectionItemEntity(key, position, id, CollectionItemType.SONG)

    @Test
    fun collectionsKeepOrderAndReplaceAtomically() = runBlocking {
        db.songDao().upsertAll(listOf(song("a"), song("b"), song("c")))
        val dao = db.collectionDao()
        dao.replace("k", listOf(item("k", 0, "b"), item("k", 1, "a")))
        assertEquals(listOf("b", "a"), dao.getSongs("k").map { it.videoId })
        assertEquals(2, dao.nextPosition("k"))

        dao.insertAll(listOf(item("k", 2, "c")))
        assertEquals(listOf("b", "a", "c"), dao.observeSongs("k").first().map { it.videoId })

        dao.replace("k", listOf(item("k", 0, "c")))
        assertEquals(listOf("c"), dao.getSongs("k").map { it.videoId })
    }

    @Test
    fun historyCountsPlaysAndOrdersByRecency() = runBlocking {
        db.songDao().upsertAll(listOf(song("a"), song("b")))
        val history = db.historyDao()
        history.recordPlay("a", playedAt = 1)
        history.recordPlay("b", playedAt = 2)
        history.recordPlay("a", playedAt = 3)

        assertEquals(listOf("a", "b"), history.observeRecent(10).first().map { it.videoId })
        assertEquals(2, history.get("a")!!.playCount)
        assertEquals(listOf("a", "b"), history.observeMostPlayed(10).first().map { it.videoId })
    }

    @Test
    fun likeQueriesTreatWildcardsLiterally() = runBlocking {
        db.songDao().upsertAll(listOf(song("1", "100% Pure"), song("2", "100 Days")))
        assertEquals(listOf("100% Pure"), db.songDao().titlesStartingWith("100\\%", 10))

        db.searchHistoryDao().upsert(SearchHistoryEntity("lo_fi", "lo_fi", 1))
        db.searchHistoryDao().upsert(SearchHistoryEntity("loxfi", "loxfi", 2))
        assertEquals(listOf("lo_fi"), db.searchHistoryDao().matching("lo\\_", 10).map { it.query })
    }

    @Test
    fun clearAllTablesWipesUserData() = runBlocking {
        db.songDao().upsertAll(listOf(song("a")))
        db.favoriteDao().insert(FavoriteEntity("a", 1))
        assertTrue(db.favoriteDao().isFavorite("a"))
        db.clearAllTables()
        assertEquals(emptyList<SongEntity>(), db.favoriteDao().observeFavoriteSongs().first())
    }
}
