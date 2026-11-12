package com.example.orbitmusic.data.remote.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI-compatible chat completions. The full endpoint comes from [AiConfig]: Groq directly
 * in debug builds, the AI proxy in release builds.
 */
interface ChatCompletionsApi {
    @POST
    suspend fun complete(@Url endpoint: String, @Body request: ChatRequestDto): ChatResponseDto
}

/**
 * Where and how to call the AI. In debug builds [apiKey] is GROQ_API_KEY from local.properties
 * and [endpoint] is Groq; release builds have no key in the app and call AI_PROXY_URL, which
 * holds the key server-side.
 */
data class AiConfig(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    /** True when the app itself must send the key (debug, calling Groq directly). */
    val sendsApiKey: Boolean,
) {
    val isConfigured: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank() && (!sendsApiKey || apiKey.isNotBlank())
}

@Serializable
data class ChatRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
    @SerialName("response_format") val responseFormat: JsonObject,
    /** Only for reasoning models (gpt-oss); left out of the request when null. */
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ChatResponseDto(val choices: List<ChatChoiceDto> = emptyList())

@Serializable
data class ChatChoiceDto(val message: ChatReplyDto? = null)

@Serializable
data class ChatReplyDto(val content: String? = null)

/** The JSON shape the model is asked to reply with. */
@Serializable
data class AiPlaylistDto(
    val playlistName: String = "",
    val description: String = "",
    val songs: List<AiSongDto> = emptyList(),
)

@Serializable
data class AiSongDto(val title: String = "", val artist: String = "")
