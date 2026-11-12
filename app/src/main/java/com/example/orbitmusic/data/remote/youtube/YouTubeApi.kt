package com.example.orbitmusic.data.remote.youtube

import com.example.orbitmusic.data.remote.youtube.dto.ChannelDto
import com.example.orbitmusic.data.remote.youtube.dto.ListResponseDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistInsertRequestDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemInsertRequestDto
import com.example.orbitmusic.data.remote.youtube.dto.SearchResultDto
import com.example.orbitmusic.data.remote.youtube.dto.VideoDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * YouTube Data API v3. Quota cost per call is noted on each method (daily default
 * quota is 10,000 units), which is why the repositories cache aggressively.
 */
interface YouTubeApi {

    /** Cost: 1. The signed-in user's playlists. */
    @GET("playlists")
    suspend fun myPlaylists(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = MAX_PAGE_SIZE,
        @Query("pageToken") pageToken: String? = null,
    ): ListResponseDto<PlaylistDto>

    /** Cost: 1. Metadata for specific playlists (e.g. one opened from search). */
    @GET("playlists")
    suspend fun playlistsById(
        @Query("id") ids: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("maxResults") maxResults: Int = MAX_PAGE_SIZE,
    ): ListResponseDto<PlaylistDto>

    /** Cost: 1. Items of a playlist (no durations; see [videosById]). */
    @GET("playlistItems")
    suspend fun playlistItems(
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("maxResults") maxResults: Int = MAX_PAGE_SIZE,
        @Query("pageToken") pageToken: String? = null,
    ): ListResponseDto<PlaylistItemDto>

    /** Cost: 1. Videos the user liked. */
    @GET("videos")
    suspend fun likedVideos(
        @Query("myRating") myRating: String = "like",
        @Query("part") part: String = VIDEO_PARTS,
        @Query("maxResults") maxResults: Int = MAX_PAGE_SIZE,
        @Query("pageToken") pageToken: String? = null,
    ): ListResponseDto<VideoDto>

    /** Cost: 1. Details (duration, embeddability) for up to 50 comma-separated ids. */
    @GET("videos")
    suspend fun videosById(
        @Query("id") ids: String,
        @Query("part") part: String = VIDEO_PARTS,
        @Query("maxResults") maxResults: Int = MAX_PAGE_SIZE,
    ): ListResponseDto<VideoDto>

    /** Cost: 1. Most popular music videos in a region. */
    @GET("videos")
    suspend fun trendingMusic(
        @Query("regionCode") regionCode: String,
        @Query("chart") chart: String = "mostPopular",
        @Query("videoCategoryId") videoCategoryId: String = MUSIC_CATEGORY_ID,
        @Query("part") part: String = VIDEO_PARTS,
        @Query("maxResults") maxResults: Int = 25,
        @Query("pageToken") pageToken: String? = null,
    ): ListResponseDto<VideoDto>

    /**
     * Cost: 100 (!). Search. For songs use type=video with the Music category;
     * for playlists use type=playlist (category filters only apply to videos).
     */
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("videoCategoryId") videoCategoryId: String? = null,
        @Query("part") part: String = "snippet",
        @Query("maxResults") maxResults: Int = SEARCH_PAGE_SIZE,
        @Query("pageToken") pageToken: String? = null,
        @Query("safeSearch") safeSearch: String = "moderate",
    ): ListResponseDto<SearchResultDto>

    /** Cost: 1. The signed-in user's channel. */
    @GET("channels")
    suspend fun myChannel(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
    ): ListResponseDto<ChannelDto>

    /** Cost: 50. Requires the youtube scope. */
    @POST("playlists")
    suspend fun createPlaylist(
        @Body body: PlaylistInsertRequestDto,
        @Query("part") part: String = "snippet,status,contentDetails",
    ): PlaylistDto

    /** Cost: 50. Requires the youtube scope. */
    @POST("playlistItems")
    suspend fun addPlaylistItem(
        @Body body: PlaylistItemInsertRequestDto,
        @Query("part") part: String = "snippet",
    ): PlaylistItemDto

    /** Cost: 50. Requires the youtube scope. rating = "like" | "none". */
    @POST("videos/rate")
    suspend fun rateVideo(
        @Query("id") videoId: String,
        @Query("rating") rating: String,
    )

    companion object {
        const val BASE_URL = "https://www.googleapis.com/youtube/v3/"
        const val MAX_PAGE_SIZE = 50
        const val SEARCH_PAGE_SIZE = 25
        const val MUSIC_CATEGORY_ID = "10"
        const val VIDEO_PARTS = "snippet,contentDetails,status"
    }
}
