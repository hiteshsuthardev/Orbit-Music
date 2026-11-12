package com.example.orbitmusic.ui.screens.ai

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.ai.AiAllowance
import com.example.orbitmusic.domain.ai.AiQueueFeeder
import com.example.orbitmusic.domain.ai.AiRequestGate
import com.example.orbitmusic.domain.ai.OrbitAiSession
import com.example.orbitmusic.domain.ai.OrbitAiState
import com.example.orbitmusic.domain.ai.SaveAiPlaylistResult
import com.example.orbitmusic.domain.ai.SaveAiPlaylistUseCase
import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.usecase.auth.CompleteAuthorizationUseCase
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.domain.usecase.auth.RequestPlaylistEditAccessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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

/** Saving one answer to YouTube as a playlist. */
sealed interface YouTubeSave {
    data class Saving(val done: Int, val total: Int) : YouTubeSave
    data class Saved(val playlist: Playlist, val added: Int, val missing: Int) : YouTubeSave
    data class Failed(val error: AppError) : YouTubeSave
}

sealed interface OrbitAiEvent {
    /** Google must ask the user for permission to manage their YouTube playlists. */
    data class LaunchConsent(val pendingIntent: PendingIntent) : OrbitAiEvent
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
    /** "Add to YouTube" progress per answer id. */
    val saves: Map<Int, YouTubeSave> = emptyMap(),
) {
    val isWorking: Boolean get() = session is OrbitAiState.Thinking || session is OrbitAiState.Busy
}

@HiltViewModel
class OrbitAiViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val session: OrbitAiSession,
    private val feeder: AiQueueFeeder,
    private val gate: AiRequestGate,
    private val savePlaylist: SaveAiPlaylistUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val requestPlaylistEditAccess: RequestPlaylistEditAccessUseCase,
    private val completeAuthorization: CompleteAuthorizationUseCase,
) : ViewModel() {

    private val authState = observeAuthState()
    private val _events = Channel<OrbitAiEvent>(Channel.BUFFERED)
    val events: Flow<OrbitAiEvent> = _events.receiveAsFlow()

    /** Answer (and its fallback name) waiting for the YouTube permission screen to come back. */
    private var pendingSave: Pair<AiChatEntry.Answer, String>? = null

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
        local.update { it.copy(entries = emptyList(), playbackError = null, saves = emptyMap()) }
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

    /**
     * Saves [answer] to the user's YouTube account as a private playlist, asking for
     * permission to manage playlists first if needed. [fallbackName] is used for a nameless one.
     */
    fun saveToYouTube(answer: AiChatEntry.Answer, fallbackName: String) {
        if (local.value.saves[answer.id].let { it is YouTubeSave.Saving || it is YouTubeSave.Saved }) return
        setSave(answer.id, YouTubeSave.Saving(0, answer.playlist.songs.size))
        viewModelScope.launch {
            if ((authState.value as? AuthState.SignedIn)?.canEditPlaylists == true) {
                save(answer, fallbackName)
                return@launch
            }
            when (val outcome = requestPlaylistEditAccess()) {
                AuthorizationOutcome.Granted -> save(answer, fallbackName)
                is AuthorizationOutcome.NeedsConsent -> {
                    pendingSave = answer to fallbackName
                    _events.send(OrbitAiEvent.LaunchConsent(outcome.pendingIntent))
                }
                is AuthorizationOutcome.Failed -> setSave(answer.id, YouTubeSave.Failed(outcome.error))
            }
        }
    }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        val (answer, fallbackName) = pendingSave ?: return
        pendingSave = null
        if (resultCode != Activity.RESULT_OK) {
            setSave(answer.id, YouTubeSave.Failed(AppError.PermissionRequired))
            return
        }
        viewModelScope.launch {
            when (val result = completeAuthorization(data)) {
                is Resource.Success -> save(answer, fallbackName)
                is Resource.Error -> setSave(answer.id, YouTubeSave.Failed(result.error))
                Resource.Loading -> Unit
            }
        }
    }

    private suspend fun save(answer: AiChatEntry.Answer, fallbackName: String) {
        val result = savePlaylist(answer.playlist, fallbackName) { done, total ->
            setSave(answer.id, YouTubeSave.Saving(done, total))
        }
        setSave(
            answer.id,
            when (result) {
                is SaveAiPlaylistResult.Saved -> YouTubeSave.Saved(result.playlist, result.added, result.missing)
                is SaveAiPlaylistResult.Failed -> YouTubeSave.Failed(result.error)
            },
        )
    }

    private fun setSave(answerId: Int, save: YouTubeSave) = local.update { it.copy(saves = it.saves + (answerId to save)) }

    private companion object {
        const val KEY_EXPLICIT = "allow_explicit"
    }
}
