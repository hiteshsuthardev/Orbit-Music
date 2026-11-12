package com.example.orbitmusic.domain.ai

import com.example.orbitmusic.domain.model.AiSongSuggestion
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.player.QueueManager
import com.example.orbitmusic.domain.repository.SearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiQueueFeederTest {

    private val suggestions = listOf(
        AiSongSuggestion("One", "A"),
        AiSongSuggestion("Two", "B"),
        AiSongSuggestion("Three", "C"),
    )

    private fun song(id: String, title: String) = Song(id, title, "Artist", null, null, 200_000L, isPlayable = true)

    private fun searchReturning(byQuery: Map<String, Resource<SearchResults>>): SearchRepository {
        val repository = mockk<SearchRepository>()
        byQuery.forEach { (query, result) ->
            coEvery { repository.search(query, SearchFilter.Songs, false) } returns result
        }
        return repository
    }

    private fun found(query: String, vararg songs: Song) =
        Resource.Success(SearchResults(query, SearchFilter.Songs, songs = songs.toList()))

    @Test
    fun resolvesOnlyTheFirstSongUpFrontThenOneAheadAsTheQueueRunsOut() = runTest {
        val search = searchReturning(
            mapOf(
                "One A" to found("One A", song("v1", "One")),
                "Two B" to found("Two B", song("v2", "Two")),
                "Three C" to found("Three C", song("v3", "Three")),
            ),
        )
        val queue = QueueManager()
        val feederScope = TestScope(StandardTestDispatcher(testScheduler))
        val feeder = AiQueueFeeder(queue, ResolveAiSongUseCase(search), feederScope)

        assertTrue(feeder.play(suggestions) is Resource.Success)
        assertEquals(listOf("v1"), queue.state.value.items.map { it.song.videoId })

        // The queue has nothing up next, so exactly one more song is looked up.
        feederScope.runCurrent()
        assertEquals(listOf("v1", "v2"), queue.state.value.items.map { it.song.videoId })
        coVerify(exactly = 0) { search.search("Three C", SearchFilter.Songs, false) }

        // Moving on to song 2 empties "up next" again, so song 3 is looked up.
        queue.next(userInitiated = true)
        feederScope.runCurrent()
        assertEquals(listOf("v1", "v2", "v3"), queue.state.value.items.map { it.song.videoId })
    }

    @Test
    fun skipsSongsYouTubeCannotMatch() = runTest {
        val search = searchReturning(
            mapOf(
                "One A" to found("One A"),
                "Two B" to found("Two B", song("v2", "Two")),
            ),
        )
        val queue = QueueManager()
        val feeder = AiQueueFeeder(queue, ResolveAiSongUseCase(search), TestScope(StandardTestDispatcher(testScheduler)))
        feeder.play(suggestions.take(2))
        assertEquals(listOf("v2"), queue.state.value.items.map { it.song.videoId })
    }

    @Test
    fun reportsWhenTheFirstSongCannotBeLookedUp() = runTest {
        val search = searchReturning(mapOf("One A" to Resource.Error(AppError.QuotaExceeded)))
        val queue = QueueManager()
        val feeder = AiQueueFeeder(queue, ResolveAiSongUseCase(search), TestScope(StandardTestDispatcher(testScheduler)))
        assertEquals(AppError.QuotaExceeded, (feeder.play(suggestions) as Resource.Error).error)
        assertTrue(queue.state.value.isEmpty)
    }

    @Test
    fun stopsFeedingWhenTheUserPlaysSomethingElse() = runTest {
        val search = searchReturning(
            mapOf(
                "One A" to found("One A", song("v1", "One")),
                "Two B" to found("Two B", song("v2", "Two")),
            ),
        )
        val queue = QueueManager()
        val feederScope = TestScope(StandardTestDispatcher(testScheduler))
        val feeder = AiQueueFeeder(queue, ResolveAiSongUseCase(search), feederScope)
        feeder.play(suggestions.take(2))
        queue.setQueue(listOf(song("other", "Other song")))
        feederScope.runCurrent()
        assertEquals(listOf("other"), queue.state.value.items.map { it.song.videoId })
        coVerify(exactly = 0) { search.search("Two B", SearchFilter.Songs, false) }
    }
}
