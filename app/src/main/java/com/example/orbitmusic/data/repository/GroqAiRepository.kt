package com.example.orbitmusic.data.repository

import com.example.orbitmusic.data.remote.ai.AiConfig
import com.example.orbitmusic.data.remote.ai.AiPrompts
import com.example.orbitmusic.data.remote.ai.AiReplyParser
import com.example.orbitmusic.data.remote.ai.ChatCompletionsApi
import com.example.orbitmusic.data.remote.ai.ChatMessageDto
import com.example.orbitmusic.data.remote.ai.ChatRequestDto
import com.example.orbitmusic.domain.model.AiCuration
import com.example.orbitmusic.domain.model.AiRole
import com.example.orbitmusic.domain.model.AiTurn
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.repository.AiRepository
import com.example.orbitmusic.domain.update.RemoteConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Orbit AI on Groq's OpenAI-compatible chat completions API.
 *
 * - Asks for structured output (json_schema). If the model rejects that (HTTP 400 mentioning
 *   the response format), it switches to {"type": "json_object"} for the rest of the session.
 * - If the reply can't be parsed (or Groq rejects the generation as invalid JSON with
 *   "json_validate_failed"), it asks once more with a "Return valid JSON only" reminder.
 * - Errors become [AppError]s: 429 -> AiRateLimited (with Retry-After), 401 -> AiInvalidKey,
 *   5xx -> AiUnavailable, timeouts -> AiTimeout, other I/O -> NoInternet.
 * The API key is only ever placed in the Authorization header (see NetworkModule); it is
 * never logged.
 */
@Singleton
class GroqAiRepository @Inject constructor(
    private val api: ChatCompletionsApi,
    private val config: AiConfig,
    private val remoteConfig: RemoteConfigRepository,
    json: Json,
) : AiRepository {

    private val parser = AiReplyParser(json)

    /** Models that rejected json_schema; they use json_object from then on. */
    private val modelsWithoutJsonSchema: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override suspend fun curatePlaylist(
        prompt: String,
        history: List<AiTurn>,
        allowExplicit: Boolean,
    ): Resource<AiCuration> {
        // The remote config may switch models without an app update (ai_model).
        val model = remoteConfig.config.first().aiModel ?: config.model
        if (!config.copy(model = model).isConfigured) return Resource.Error(AppError.AiNotConfigured)
        val messages = buildList {
            add(ChatMessageDto("system", AiPrompts.system(allowExplicit)))
            history.forEach { add(ChatMessageDto(it.role.apiName(), it.content)) }
            add(ChatMessageDto("user", prompt))
        }
        return try {
            val reply = complete(model, messages)
            parser.parse(reply)?.let { return Resource.Success(AiCuration(it, reply)) }
            // One retry with a reminder, then give up with a friendly error.
            val retryReply = complete(
                model,
                messages + ChatMessageDto("assistant", reply) + ChatMessageDto("user", AiPrompts.JSON_REMINDER),
            )
            parser.parse(retryReply)?.let { Resource.Success(AiCuration(it, retryReply)) }
                ?: Resource.Error(AppError.AiBadResponse)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Resource.Error(mapError(error))
        }
    }

    /** The model's reply text; empty when Groq rejected the generation as invalid JSON. */
    private suspend fun complete(model: String, messages: List<ChatMessageDto>): String {
        if (model !in modelsWithoutJsonSchema) {
            try {
                return request(model, messages, AiPrompts.jsonSchemaFormat)
            } catch (error: HttpException) {
                when (error.badRequestKind()) {
                    BadRequest.ResponseFormatUnsupported -> modelsWithoutJsonSchema += model
                    BadRequest.InvalidJsonGenerated -> return ""
                    null -> throw error
                }
            }
        }
        return try {
            request(model, messages, AiPrompts.jsonObjectFormat)
        } catch (error: HttpException) {
            if (error.badRequestKind() == BadRequest.InvalidJsonGenerated) "" else throw error
        }
    }

    private suspend fun request(model: String, messages: List<ChatMessageDto>, format: JsonObject): String {
        val response = api.complete(
            endpoint = config.endpoint,
            request = ChatRequestDto(
                model = model,
                messages = messages,
                temperature = AiPrompts.TEMPERATURE,
                maxTokens = AiPrompts.MAX_TOKENS,
                responseFormat = format,
                reasoningEffort = AiPrompts.reasoningEffortFor(model),
            ),
        )
        return response.choices.firstOrNull()?.message?.content.orEmpty()
    }

    private enum class BadRequest { ResponseFormatUnsupported, InvalidJsonGenerated }

    /** Classifies an HTTP 400 by its error body (which can be read only once). */
    private fun HttpException.badRequestKind(): BadRequest? {
        if (code() != HTTP_BAD_REQUEST) return null
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty().lowercase()
        return when {
            "json_validate_failed" in body -> BadRequest.InvalidJsonGenerated
            "json_schema" in body || "response_format" in body || "response format" in body ->
                BadRequest.ResponseFormatUnsupported
            else -> null
        }
    }

    private fun AiRole.apiName(): String = when (this) {
        AiRole.User -> "user"
        AiRole.Assistant -> "assistant"
    }

    companion object {
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val DEFAULT_RETRY_AFTER_SECONDS = 10

        /** Visible for tests. */
        fun mapError(error: Throwable): AppError = when (error) {
            is HttpException -> when (val code = error.code()) {
                HTTP_TOO_MANY_REQUESTS -> AppError.AiRateLimited(retryAfterSeconds(error.response()?.headers()?.get("Retry-After")))
                HTTP_UNAUTHORIZED -> AppError.AiInvalidKey
                // Unknown model or endpoint: a setup problem, not something to retry.
                HTTP_NOT_FOUND -> AppError.AiNotConfigured
                in 500..599 -> AppError.AiUnavailable
                else -> AppError.Unknown("AI request failed (HTTP $code)")
            }
            // SocketTimeoutException and OkHttp's call timeout are both InterruptedIOException.
            is InterruptedIOException -> AppError.AiTimeout
            is IOException -> AppError.NoInternet
            is SerializationException -> AppError.AiBadResponse
            else -> AppError.Unknown(error.message)
        }

        /** Retry-After in seconds (the delta form); missing or unreadable -> a short default. */
        fun retryAfterSeconds(header: String?): Int =
            header?.trim()?.toDoubleOrNull()?.let { ceil(it).toInt() }?.coerceAtLeast(1)
                ?: DEFAULT_RETRY_AFTER_SECONDS
    }
}
