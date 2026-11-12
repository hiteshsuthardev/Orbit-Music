package com.example.orbitmusic.ui.screens.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.ai.AiAllowance
import com.example.orbitmusic.domain.ai.AiQueueFeeder
import com.example.orbitmusic.domain.ai.AiRequestGate
import com.example.orbitmusic.domain.ai.OrbitAiSession
import com.example.orbitmusic.domain.ai.OrbitAiState
import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the Orbit AI conversation. */
sealed interface AiChatEntry {
    val id: Int

    data class Request(override val id: Int, val text: String) : AiChatEntry
    data class Answer(override val id: Int, val playlist: AiPlaylist) : AiChatEntry
}

data class OrbitAiUiState(
    val entries: List<AiChatEntry> = emptyList(),
    val session: OrbitAiState = OrbitAiState.Idle,
    val allowExplicit: Boolean = false,
    /** Answer whose songs are being looked up to start playback. */
    val startingAnswerId: Int? = null,
    /** A song lookup failed (e.g. YouTube quota exceeded). */
    val playbackError: AppError? = null,
    /** Request limits for the signed-in user (null until known). */
    val allowance: AiAllowance? = null,
) {
    val isWorking: Boolean get() = session is OrbitAiState.Thinking || session is OrbitAiState.Busy
}

@HiltViewModel
class OrbitAiViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val session: OrbitAiSession,
    private val feeder: AiQueueFeeder,
    private val gate: AiRequestGate,
) : ViewModel() {

    private val local = MutableStateFlow(OrbitAiUiState(allowExplicit = savedStateHandle[KEY_EXPLICIT] ?: false))
    private var nextId = 0
    private var lastRequest: String? = null
    private var request: Job? = null

    val uiState: StateFlow<OrbitAiUiState> = combine(local, session.state, gate.allowance) { state, sessionState, allowance ->
        state.copy(session = sessionState, allowance = allowance)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch {
            feeder.errors.collect { error -> local.update { it.copy(playbackError = error) } }
        }
    }

    /** Sends a request if the user's limits allow it (each message counts once). */
    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || uiState.value.isWorking) return
        ask(prompt, isNewMessage = true)
    }

    /** Asks the last request again after an error (counts as a request). */
    fun retry() {
        lastRequest?.let { ask(it, isNewMessage = false) }
    }

    private fun ask(prompt: String, isNewMessage: Boolean) {
        request?.cancel()
        request = viewModelScope.launch {
            // Blocked: the screen already shows why (limit notice or contact-developer screen).
            if (gate.tryConsume() != null) return@launch
            if (isNewMessage) {
                lastRequest = prompt
                local.update { it.copy(entries = it.entries + AiChatEntry.Request(nextId++, prompt)) }
            }
            session.send(prompt, allowExplicit = local.value.allowExplicit)
            val result = session.state.value
            if (result is OrbitAiState.Ready) {
                local.update { it.copy(entries = it.entries + AiChatEntry.Answer(nextId++, result.playlist)) }
            }
        }
    }

    fun setAllowExplicit(allow: Boolean) {
        savedStateHandle[KEY_EXPLICIT] = allow
        local.update { it.copy(allowExplicit = allow) }
    }

    fun newChat() {
        request?.cancel()
        session.reset()
        lastRequest = null
        local.update { it.copy(entries = emptyList(), playbackError = null) }
    }

    /** Plays an answer from [startIndex]; each song is looked up on YouTube only when reached. */
    fun play(answer: AiChatEntry.Answer, startIndex: Int = 0, shuffle: Boolean = false) {
        if (local.value.startingAnswerId != null) return
        local.update { it.copy(startingAnswerId = answer.id, playbackError = null) }
        viewModelScope.launch {
            val result = feeder.play(answer.playlist.songs, startIndex, shuffle)
            local.update {
                it.copy(
                    startingAnswerId = null,
                    playbackError = (result as? Resource.Error)?.error,
                )
            }
        }
    }

    fun dismissPlaybackError() = local.update { it.copy(playbackError = null) }

    private companion object {
        const val KEY_EXPLICIT = "allow_explicit"
    }
}
