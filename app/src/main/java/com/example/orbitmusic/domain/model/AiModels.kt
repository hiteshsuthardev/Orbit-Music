package com.example.orbitmusic.domain.model

/** A song the AI suggested: names only, never a video id or URL. */
data class AiSongSuggestion(val title: String, val artist: String)

/** A playlist suggested by OrbitNova AI. */
data class AiPlaylist(
    val name: String,
    val description: String,
    val songs: List<AiSongSuggestion>,
)

enum class AiRole { User, Assistant }

/** One chat message kept as history so follow-ups ("add 10 more") have context. */
data class AiTurn(val role: AiRole, val content: String)

/**
 * Result of one request: the parsed playlist plus the assistant's reply text, which is kept
 * in the chat history for follow-up requests.
 */
data class AiCuration(val playlist: AiPlaylist, val assistantReply: String)
