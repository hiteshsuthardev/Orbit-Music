package com.example.orbitmusic.data.remote.ai

import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AiSongSuggestion
import kotlinx.serialization.json.Json

/**
 * Turns the model's reply text into an [AiPlaylist], or null when it isn't usable. Tolerates
 * Markdown code fences and text around the JSON object, trims fields, drops songs with blank
 * names or anything that looks like a link or video id, and removes duplicates.
 */
class AiReplyParser(private val json: Json) {

    fun parse(reply: String?): AiPlaylist? {
        val text = reply?.let(::extractJsonObject) ?: return null
        val dto = runCatching { json.decodeFromString(AiPlaylistDto.serializer(), text) }.getOrNull() ?: return null
        val songs = dto.songs
            .map { AiSongSuggestion(title = it.title.cleanSpaces(), artist = it.artist.cleanSpaces()) }
            .filter { it.title.isNotEmpty() && it.artist.isNotEmpty() }
            .filterNot { looksLikeLink(it.title) || looksLikeLink(it.artist) }
            .distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
            .take(MAX_SONGS)
        if (songs.isEmpty()) return null
        return AiPlaylist(name = dto.playlistName.cleanSpaces(), description = dto.description.cleanSpaces(), songs = songs)
    }

    private fun extractJsonObject(reply: String): String? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        return if (start in 0 until end) reply.substring(start, end + 1) else null
    }

    private fun looksLikeLink(text: String): Boolean = linkPattern.containsMatchIn(text)

    /** Models sometimes emit non-breaking / narrow spaces, which would hurt the YouTube search. */
    private fun String.cleanSpaces(): String = replace(anySpace, " ").trim()

    private companion object {
        const val MAX_SONGS = 50
        val anySpace = Regex("""[\p{Z}\s]+""")
        val linkPattern = Regex("""https?://|www\.|youtu\.?be|watch\?v=""", RegexOption.IGNORE_CASE)
    }
}
