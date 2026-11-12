package com.example.orbitmusic.domain.repository

import com.example.orbitmusic.domain.model.AiCuration
import com.example.orbitmusic.domain.model.AiTurn
import com.example.orbitmusic.domain.model.Resource

/**
 * The AI playlist curator. Provider-agnostic: today Groq (OpenAI-compatible API), swappable
 * by binding another implementation.
 */
interface AiRepository {
    /**
     * Asks for a playlist for [prompt]. [history] holds the earlier turns of this chat
     * (oldest first) so follow-ups like "remove the slow ones" work.
     */
    suspend fun curatePlaylist(
        prompt: String,
        history: List<AiTurn>,
        allowExplicit: Boolean,
    ): Resource<AiCuration>
}
