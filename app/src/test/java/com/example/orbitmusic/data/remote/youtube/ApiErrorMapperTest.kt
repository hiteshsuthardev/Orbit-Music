package com.example.orbitmusic.data.remote.youtube

import com.example.orbitmusic.domain.model.AppError
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ApiErrorMapperTest {

    private val mapper = ApiErrorMapper(Json { ignoreUnknownKeys = true })

    private fun httpError(code: Int, body: String): HttpException =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `quota exceeded is detected from the error body`() {
        val body = """{"error":{"code":403,"message":"quota","errors":[{"reason":"quotaExceeded","domain":"youtube.quota"}]}}"""
        assertEquals(AppError.QuotaExceeded, mapper.map(httpError(403, body)))
    }

    @Test
    fun `missing write scope is a permission error`() {
        val body = """{"error":{"code":403,"errors":[{"reason":"insufficientPermissions"}]}}"""
        assertEquals(AppError.PermissionRequired, mapper.map(httpError(403, body)))
    }

    @Test
    fun `status codes map to friendly errors`() {
        assertEquals(AppError.AuthorizationRequired, mapper.mapStatus(401, emptyList()))
        assertEquals(AppError.NotFound, mapper.mapStatus(404, listOf("playlistNotFound")))
        assertEquals(AppError.VideoUnavailable, mapper.mapStatus(403, listOf("videoNotFound")))
        assertTrue(mapper.mapStatus(503, emptyList()) is AppError.Unknown)
    }

    @Test
    fun `unparseable bodies still map by status`() {
        assertEquals(AppError.AuthorizationRequired, mapper.map(httpError(401, "<html>nope</html>")))
    }

    @Test
    fun `io failures mean offline`() {
        assertEquals(AppError.NoInternet, mapper.map(IOException("timeout")))
    }
}
