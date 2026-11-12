package com.example.orbitmusic.domain.ai

import com.example.orbitmusic.domain.model.AiCuration
import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AiRole
import com.example.orbitmusic.domain.model.AiSongSuggestion
import com.example.orbitmusic.domain.model.AiTurn
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.repository.AiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrbitAiSessionTest {

    private val playlist = AiPlaylist("Mix", "", listOf(AiSongSuggestion("Song", "Artist")))

    private class FakeRepository(vararg results: Resource<AiCuration>) : AiRepository {
        private val queue = ArrayDeque(results.toList())
        val histories = mutableListOf<List<AiTurn>>()

        override suspend fun curatePlaylist(prompt: String, history: List<AiTurn>, allowExplicit: Boolean): Resource<AiCuration> {
            histories += history
            return queue.removeFirst()
        }
    }

    @Test
    fun keepsHistoryForFollowUps() = runTest {
        val repository = FakeRepository(
            Resource.Success(AiCuration(playlist, "reply-1")),
            Resource.Success(AiCuration(playlist, "reply-2")),
        )
        val session = OrbitAiSession(repository)
        session.send("chill songs", allowExplicit = false)
        session.send("add 10 more", allowExplicit = false)

        assertEquals(listOf(AiTurn(AiRole.User, "chill songs"), AiTurn(AiRole.Assistant, "reply-1")), repository.histories[1])
        assertTrue(session.state.value is OrbitAiState.Ready)
    }

    @Test
    fun historyIsCapped() = runTest {
        val replies = Array(5) { Resource.Success(AiCuration(playlist, "r$it")) }
        val session = OrbitAiSession(FakeRepository(*replies))
        repeat(5) { session.send("request $it", allowExplicit = false) }
        assertEquals(OrbitAiSession.MAX_HISTORY_MESSAGES, session.historySnapshot.size)
        assertEquals("request 2", session.historySnapshot.first().content)
    }

    @Test
    fun countsDownAndRetriesWhenRateLimited() = runTest {
        val repository = FakeRepository(
            Resource.Error(AppError.AiRateLimited(retryAfterSeconds = 3)),
            Resource.Success(AiCuration(playlist, "reply")),
        )
        val session = OrbitAiSession(repository)
        val job = launch { session.send("party", allowExplicit = true) }
        runCurrent()
        assertEquals(OrbitAiState.Busy(3), session.state.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(OrbitAiState.Busy(2), session.state.value)
        advanceTimeBy(2_000)
        runCurrent()
        job.join()
        assertTrue(session.state.value is OrbitAiState.Ready)
    }

    @Test
    fun givesUpAfterRepeatedRateLimits() = runTest {
        val busy = Resource.Error(AppError.AiRateLimited(retryAfterSeconds = 1))
        val session = OrbitAiSession(FakeRepository(busy, busy, busy))
        session.send("party", allowExplicit = true)
        assertEquals(OrbitAiState.Failed(AppError.AiRateLimited(1)), session.state.value)
    }

    @Test
    fun otherErrorsFailImmediately() = runTest {
        val session = OrbitAiSession(FakeRepository(Resource.Error(AppError.AiInvalidKey)))
        session.send("x", allowExplicit = false)
        assertEquals(OrbitAiState.Failed(AppError.AiInvalidKey), session.state.value)
        assertTrue(session.historySnapshot.isEmpty())
    }
}
