package com.example.orbitmusic.data.repository

import com.example.orbitmusic.data.remote.ai.AiConfig
import com.example.orbitmusic.data.remote.ai.AiPrompts
import com.example.orbitmusic.data.remote.ai.ChatChoiceDto
import com.example.orbitmusic.data.remote.ai.ChatCompletionsApi
import com.example.orbitmusic.data.remote.ai.ChatReplyDto
import com.example.orbitmusic.data.remote.ai.ChatRequestDto
import com.example.orbitmusic.data.remote.ai.ChatResponseDto
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.update.RemoteConfig
import com.example.orbitmusic.domain.update.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/** Remote config with nothing set (no model override). */
private object NoRemoteConfig : RemoteConfigRepository {
    override val config: Flow<RemoteConfig> = flowOf(RemoteConfig())
    override suspend fun refresh() = Unit
    override val dismissedAnnouncement: Flow<String?> = flowOf(null)
    override suspend fun dismissAnnouncement(text: String) = Unit
}

class GroqAiRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val config = AiConfig(endpoint = "https://example.test/chat", apiKey = "test-key", model = "test-model", sendsApiKey = true)
    private val validReply = """{"playlistName":"Gym","description":"Loud","songs":[{"title":"Stronger","artist":"Kanye West"}]}"""

    /** Fake API answering each call with the next scripted reply (a String or a Throwable). */
    private class ScriptedApi(vararg replies: Any) : ChatCompletionsApi {
        private val queue = ArrayDeque(replies.toList())
        val requests = mutableListOf<ChatRequestDto>()

        override suspend fun complete(endpoint: String, request: ChatRequestDto): ChatResponseDto {
            requests += request
            return when (val next = queue.removeFirst()) {
                is Throwable -> throw next
                else -> ChatResponseDto(listOf(ChatChoiceDto(ChatReplyDto(next as String))))
            }
        }
    }

    private fun httpError(code: Int, body: String = "{}", headers: Map<String, String> = emptyMap()): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.test/chat").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .headers(headersOf(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray()))
            .build()
        return HttpException(Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw))
    }

    @Test
    fun parsesStructuredReply() = runTest {
        val api = ScriptedApi(validReply)
        val result = GroqAiRepository(api, config, NoRemoteConfig, json).curatePlaylist("gym songs", emptyList(), allowExplicit = false)
        assertEquals("Gym", (result as Resource.Success).data.playlist.name)
        assertEquals(AiPrompts.jsonSchemaFormat, api.requests.single().responseFormat)
        assertTrue(api.requests.single().messages.first().content.contains("NOT allowed"))
    }

    @Test
    fun fallsBackToJsonObjectWhenSchemaUnsupported() = runTest {
        val api = ScriptedApi(
            httpError(400, """{"error":{"message":"This model does not support response format `json_schema`"}}"""),
            validReply,
        )
        val result = GroqAiRepository(api, config, NoRemoteConfig, json).curatePlaylist("gym songs", emptyList(), allowExplicit = true)
        assertTrue(result is Resource.Success)
        assertEquals(AiPrompts.jsonObjectFormat, api.requests.last().responseFormat)
    }

    @Test
    fun invalidJsonRejectionIsRetriedWithReminder() = runTest {
        val api = ScriptedApi(
            httpError(400, """{"error":{"message":"Failed to validate JSON.","code":"json_validate_failed"}}"""),
            validReply,
        )
        val result = GroqAiRepository(api, config, NoRemoteConfig, json).curatePlaylist("30 songs", emptyList(), allowExplicit = false)
        assertTrue(result is Resource.Success)
        assertEquals(AiPrompts.JSON_REMINDER, api.requests.last().messages.last().content)
        // Still on structured output: this was not a "format unsupported" rejection.
        assertEquals(AiPrompts.jsonSchemaFormat, api.requests.last().responseFormat)
    }

    @Test
    fun reasoningEffortOnlyForGptOss() = runTest {
        val gptOss = ScriptedApi(validReply)
        GroqAiRepository(gptOss, config.copy(model = "openai/gpt-oss-120b"), NoRemoteConfig, json).curatePlaylist("x", emptyList(), false)
        assertEquals("low", gptOss.requests.single().reasoningEffort)
        val other = ScriptedApi(validReply)
        GroqAiRepository(other, config.copy(model = "qwen/qwen3.8-27b"), NoRemoteConfig, json).curatePlaylist("x", emptyList(), false)
        assertEquals(null, other.requests.single().reasoningEffort)
    }

    @Test
    fun retriesOnceWithReminderThenFails() = runTest {
        val api = ScriptedApi("not json", "still not json")
        val result = GroqAiRepository(api, config, NoRemoteConfig, json).curatePlaylist("anything", emptyList(), allowExplicit = false)
        assertEquals(AppError.AiBadResponse, (result as Resource.Error).error)
        assertEquals(AiPrompts.JSON_REMINDER, api.requests.last().messages.last().content)
    }

    @Test
    fun reminderRetryCanSucceed() = runTest {
        val api = ScriptedApi("oops", validReply)
        val result = GroqAiRepository(api, config, NoRemoteConfig, json).curatePlaylist("anything", emptyList(), allowExplicit = false)
        assertTrue(result is Resource.Success)
    }

    @Test
    fun missingConfigurationIsReportedWithoutCalling() = runTest {
        val api = ScriptedApi()
        val result = GroqAiRepository(api, config.copy(apiKey = ""), NoRemoteConfig, json).curatePlaylist("x", emptyList(), false)
        assertEquals(AppError.AiNotConfigured, (result as Resource.Error).error)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun remoteConfigCanSwitchTheModel() = runTest {
        val api = ScriptedApi(validReply)
        val remote = object : RemoteConfigRepository by NoRemoteConfig {
            override val config: Flow<RemoteConfig> = flowOf(RemoteConfig(aiModel = "openai/gpt-oss-20b"))
        }
        GroqAiRepository(api, config, remote, json).curatePlaylist("x", emptyList(), false)
        assertEquals("openai/gpt-oss-20b", api.requests.single().model)
    }

    @Test
    fun mapsErrors() {
        assertEquals(AppError.AiRateLimited(7), GroqAiRepository.mapError(httpError(429, headers = mapOf("Retry-After" to "7"))))
        assertEquals(AppError.AiRateLimited(10), GroqAiRepository.mapError(httpError(429)))
        assertEquals(AppError.AiInvalidKey, GroqAiRepository.mapError(httpError(401)))
        assertEquals(AppError.AiUnavailable, GroqAiRepository.mapError(httpError(503)))
        assertEquals(AppError.AiTimeout, GroqAiRepository.mapError(SocketTimeoutException()))
        assertEquals(AppError.NoInternet, GroqAiRepository.mapError(IOException()))
        assertEquals(2, GroqAiRepository.retryAfterSeconds("1.2"))
    }
}
