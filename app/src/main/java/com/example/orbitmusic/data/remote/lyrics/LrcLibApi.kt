package com.example.orbitmusic.data.remote.lyrics

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * LRCLIB (https://lrclib.net), a free, open lyrics database with time-synced lyrics. No key
 * or account is needed; only the song title and artist are sent. Used only while the
 * Lyrics view is open.
 */
interface LrcLibApi {

    /** Lookup by track (and optionally artist) name. */
    @GET("api/search")
    suspend fun search(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String?,
    ): List<LrcLibTrackDto>

    /** Free-text search across title, artist and album. */
    @GET("api/search")
    suspend fun searchText(@Query("q") query: String): List<LrcLibTrackDto>

    companion object {
        const val BASE_URL = "https://lrclib.net/"
        const val SOURCE_NAME = "LRCLIB"
    }
}

@Serializable
data class LrcLibTrackDto(
    val id: Long,
    val trackName: String? = null,
    val artistName: String? = null,
    /** Seconds. */
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
