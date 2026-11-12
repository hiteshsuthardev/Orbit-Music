package com.example.orbitmusic.domain.usecase

import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.PlaylistRepository
import com.example.orbitmusic.domain.repository.SearchRepository
import com.example.orbitmusic.domain.usecase.library.buildQuickPicks
import com.example.orbitmusic.domain.usecase.playlist.GetPlaylistQueueUseCase
import com.example.orbitmusic.domain.usecase.search.SearchUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class UseCaseLogicTest {

    private fun song(id: String, playable: Boolean = true) =
        Song(id, "Title $id", "Artist", null, null, 180_000, playable)

    @Test
    fun `quick picks merge sources, skip unplayable and dedupe`() {
        val picks = buildQuickPicks(
            mostPlayed = listOf(song("a"), song("b", playable = false)),
            liked = listOf(song("a"), song("c")),
            trending = listOf(song("d"), song("e")),
            limit = 3,
        )
        assertEquals(listOf("a", "c", "d"), picks.map { it.videoId })
    }

    @Test
    fun `playlist queue drops unplayable songs and can shuffle deterministically`() = runTest {
        val repository = mockk<PlaylistRepository>()
        coEvery { repository.getAllPlaylistSongs("PL", any()) } returns
            Resource.Success(listOf(song("1"), song("2", playable = false), song("3"), song("4")))
        val useCase = GetPlaylistQueueUseCase(repository)

        val ordered = useCase("PL", shuffle = false) as Resource.Success
        assertEquals(listOf("1", "3", "4"), ordered.data.map { it.videoId })

        val shuffled = useCase("PL", shuffle = true, random = Random(42)) as Resource.Success
        assertEquals(setOf("1", "3", "4"), shuffled.data.map { it.videoId }.toSet())
        assertEquals(listOf("1", "3", "4").shuffled(Random(42)), shuffled.data.map { it.videoId })
    }

    @Test
    fun `playlist with nothing playable is reported as empty`() = runTest {
        val repository = mockk<PlaylistRepository>()
        coEvery { repository.getAllPlaylistSongs("PL", any()) } returns Resource.Success(listOf(song("x", playable = false)))
        assertEquals(Resource.Error(AppError.EmptyPlaylist), GetPlaylistQueueUseCase(repository)("PL", shuffle = false))
    }

    @Test
    fun `search saves history only on success and ignores tiny queries`() = runTest {
        val repository = mockk<SearchRepository>(relaxUnitFun = true)
        coEvery { repository.search("lofi", SearchFilter.Songs, false) } returns
            Resource.Success(SearchResults("lofi", SearchFilter.Songs))
        coEvery { repository.search("jazz", SearchFilter.Songs, false) } returns Resource.Error(AppError.QuotaExceeded)
        val useCase = SearchUseCase(repository)

        useCase("  lofi ", SearchFilter.Songs)
        useCase("jazz", SearchFilter.Songs)
        useCase("a", SearchFilter.Songs)

        coVerify(exactly = 1) { repository.saveSearch("lofi") }
        coVerify(exactly = 0) { repository.saveSearch("jazz") }
        coVerify(exactly = 0) { repository.search("a", any(), any()) }
    }
}
