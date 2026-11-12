package com.example.orbitmusic.data.remote.youtube.dto

import kotlinx.serialization.Serializable

// Shared --------------------------------------------------------------------------------

@Serializable
data class ListResponseDto<T>(
    val items: List<T> = emptyList(),
    val nextPageToken: String? = null,
    val pageInfo: PageInfoDto? = null,
)

@Serializable
data class PageInfoDto(
    val totalResults: Int = 0,
    val resultsPerPage: Int = 0,
)

@Serializable
data class ThumbnailDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ThumbnailsDto(
    val default: ThumbnailDto? = null,
    val medium: ThumbnailDto? = null,
    val high: ThumbnailDto? = null,
    val standard: ThumbnailDto? = null,
    val maxres: ThumbnailDto? = null,
) {
    /** Largest available thumbnail; high (480x360) is plenty for album art. */
    fun best(): String? = (maxres ?: standard ?: high ?: medium ?: default)?.url
}

@Serializable
data class ResourceIdDto(
    val kind: String? = null,
    val videoId: String? = null,
    val playlistId: String? = null,
    val channelId: String? = null,
)

// Playlists -----------------------------------------------------------------------------

@Serializable
data class PlaylistDto(
    val id: String,
    val snippet: PlaylistSnippetDto? = null,
    val contentDetails: PlaylistContentDetailsDto? = null,
)

@Serializable
data class PlaylistSnippetDto(
    val title: String = "",
    val description: String = "",
    val channelId: String? = null,
    val channelTitle: String? = null,
    val thumbnails: ThumbnailsDto? = null,
)

@Serializable
data class PlaylistContentDetailsDto(
    val itemCount: Int = 0,
)

// Playlist items ------------------------------------------------------------------------

@Serializable
data class PlaylistItemDto(
    val id: String,
    val snippet: PlaylistItemSnippetDto? = null,
    val contentDetails: PlaylistItemContentDetailsDto? = null,
)

@Serializable
data class PlaylistItemSnippetDto(
    val title: String = "",
    val channelTitle: String? = null,
    /** The channel that uploaded the video (absent for private/deleted videos). */
    val videoOwnerChannelTitle: String? = null,
    val videoOwnerChannelId: String? = null,
    val position: Int = 0,
    val resourceId: ResourceIdDto? = null,
    val thumbnails: ThumbnailsDto? = null,
)

@Serializable
data class PlaylistItemContentDetailsDto(
    val videoId: String? = null,
)

// Videos --------------------------------------------------------------------------------

@Serializable
data class VideoDto(
    val id: String,
    val snippet: VideoSnippetDto? = null,
    val contentDetails: VideoContentDetailsDto? = null,
    val status: VideoStatusDto? = null,
)

@Serializable
data class VideoSnippetDto(
    val title: String = "",
    val description: String? = null,
    val channelId: String? = null,
    val channelTitle: String? = null,
    val thumbnails: ThumbnailsDto? = null,
    val categoryId: String? = null,
    /** "none", "live" or "upcoming". */
    val liveBroadcastContent: String? = null,
)

@Serializable
data class VideoContentDetailsDto(
    /** ISO-8601, e.g. PT3M45S. */
    val duration: String? = null,
)

@Serializable
data class VideoStatusDto(
    val privacyStatus: String? = null,
    val embeddable: Boolean? = null,
    val uploadStatus: String? = null,
)

// Search --------------------------------------------------------------------------------

@Serializable
data class SearchResultDto(
    val id: ResourceIdDto,
    val snippet: SearchSnippetDto? = null,
)

@Serializable
data class SearchSnippetDto(
    val title: String = "",
    val description: String = "",
    val channelId: String? = null,
    val channelTitle: String? = null,
    val thumbnails: ThumbnailsDto? = null,
    val liveBroadcastContent: String? = null,
)

// Channels ------------------------------------------------------------------------------

@Serializable
data class ChannelDto(
    val id: String,
    val snippet: ChannelSnippetDto? = null,
)

@Serializable
data class ChannelSnippetDto(
    val title: String = "",
    val thumbnails: ThumbnailsDto? = null,
)

// Write requests (need the youtube scope) -----------------------------------------------

@Serializable
data class PlaylistInsertRequestDto(
    val snippet: PlaylistInsertSnippetDto,
    val status: PlaylistInsertStatusDto,
)

@Serializable
data class PlaylistInsertSnippetDto(
    val title: String,
    val description: String,
)

@Serializable
data class PlaylistInsertStatusDto(
    val privacyStatus: String,
)

@Serializable
data class PlaylistItemInsertRequestDto(
    val snippet: PlaylistItemInsertSnippetDto,
)

@Serializable
data class PlaylistItemInsertSnippetDto(
    val playlistId: String,
    val resourceId: ResourceIdDto,
)

// Errors --------------------------------------------------------------------------------

@Serializable
data class ApiErrorResponseDto(
    val error: ApiErrorBodyDto? = null,
)

@Serializable
data class ApiErrorBodyDto(
    val code: Int = 0,
    val message: String? = null,
    val errors: List<ApiErrorItemDto> = emptyList(),
)

@Serializable
data class ApiErrorItemDto(
    val reason: String? = null,
    val domain: String? = null,
    val message: String? = null,
)
