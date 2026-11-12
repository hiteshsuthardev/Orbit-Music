package com.example.orbitmusic.domain.ai

import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AiRole
import com.example.orbitmusic.domain.model.AiTurn
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.repository.AiRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface OrbitAiState {
    data object Idle : OrbitAiState
    data object Thinking : OrbitAiState

    /** Rate limited: "Orbit AI is busy, retrying in [secondsLeft] seconds". */
    data class Busy(val secondsLeft: Int) : OrbitAiState
    data class Ready(val playlist: AiPlaylist) : OrbitAiState
    data class Failed(val error: AppError) : OrbitAiState
}

/**
 * One Orbit AI conversation (create one per screen). Keeps the last few messages so
 * follow-ups like "remove the slow ones" or "add 10 more" refer to the previous answer, and
 * waits out rate limits: on HTTP 429 it counts down the Retry-After seconds, then retries
 * (up to [MAX_RATE_LIMIT_RETRIES] times) before reporting the error.
 */
class OrbitAiSession @Inject constructor(
    private val repository: AiRepository,
) {
    private val _state = MutableStateFlow<OrbitAiState>(OrbitAiState.Idle)
    val state: StateFlow<OrbitAiState> = _state.asStateFlow()

    private val history = ArrayDeque<AiTurn>()

    /** Turns currently sent as context (oldest first). Visible for tests. */
    val historySnapshot: List<AiTurn> get() = history.toList()

    suspend fun send(prompt: String, allowExplicit: Boolean) {
        val request = prompt.trim()
        if (request.isEmpty()) return
        _state.value = OrbitAiState.Thinking
        var rateLimitRetries = 0
        while (true) {
            when (val result = repository.curatePlaylist(request, history.toList(), allowExplicit)) {
                is Resource.Success -> {
                    remember(AiTurn(AiRole.User, request))
                    remember(AiTurn(AiRole.Assistant, result.data.assistantReply))
                    _state.value = OrbitAiState.Ready(result.data.playlist)
                    return
                }
                is Resource.Error -> {
                    val error = result.error
                    if (error is AppError.AiRateLimited && rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                        rateLimitRetries++
                        countDown(error.retryAfterSeconds)
                        _state.value = OrbitAiState.Thinking
                    } else {
                        _state.value = OrbitAiState.Failed(error)
                        return
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Starts a new conversation. */
    fun reset() {
        history.clear()
        _state.value = OrbitAiState.Idle
    }

    private suspend fun countDown(seconds: Int) {
        for (left in seconds.coerceIn(1, MAX_WAIT_SECONDS) downTo 1) {
            _state.value = OrbitAiState.Busy(left)
            delay(1_000)
        }
    }

    private fun remember(turn: AiTurn) {
        history.addLast(turn)
        while (history.size > MAX_HISTORY_MESSAGES) history.removeFirst()
    }

    companion object {
        /** Three exchanges (user + assistant) of context. */
        const val MAX_HISTORY_MESSAGES = 6
        const val MAX_RATE_LIMIT_RETRIES = 2

        /** Longer waits are capped; the error is shown if it is still busy after that. */
        const val MAX_WAIT_SECONDS = 60
    }
}
