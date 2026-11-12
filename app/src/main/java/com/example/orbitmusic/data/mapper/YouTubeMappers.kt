package com.example.orbitmusic.data.mapper

import com.example.orbitmusic.data.local.db.entity.ChannelEntity
import com.example.orbitmusic.data.local.db.entity.PlaylistEntity
import com.example.orbitmusic.data.local.db.entity.SongEntity
import com.example.orbitmusic.data.remote.youtube.YouTubeApi
import com.example.orbitmusic.data.remote.youtube.dto.ChannelDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemDto
import com.example.orbitmusic.data.remote.youtube.dto.SearchResultDto
import com.example.orbitmusic.data.remote.youtube.dto.VideoDto
import com.example.orbitmusic.domain.model.Artist
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.util.HtmlEntities
import com.example.orbitmusic.util.IsoDuration
import com.example.orbitmusic.util.MusicTitleParser

// DTO -> entity ------------------------------------------------------------------------

/**
 * A video is playable in the embedded IFrame player only when it is not private, allows
 * embedding, finished processing and is not a scheduled (upcoming) premiere.
 */
fun VideoDto.isPlayableInEmbed(): Boolean {
    val privacy = status?.privacyStatus
    val upload = status?.uploadStatus
    return privacy != "private" &&
        status?.embeddable != false &&
        (upload == null || upload == "processed" || upload == "uploaded") &&
        snippet?.liveBroadcastContent != "upcoming"
}

/** Videos this long or shorter are treated as Shorts. */
private const val SHORTS_MAX_MS = 60_000L
private val ShortsTag = Regex("#shorts?\\b", RegexOption.IGNORE_CASE)

/**
 * Orbit Music only plays music videos: the YouTube "Music" category, longer than a minute,
 * not live, and not tagged as a Short. Everything else never enters the app.
 */
fun VideoDto.isMusicVideo(): Boolean {
    val snippet = snippet ?: return false
    if (snippet.categoryId != YouTubeApi.MUSIC_CATEGORY_ID) return false
    if (snippet.liveBroadcastContent == "live") return false
    if (IsoDuration.toMillis(contentDetails?.duration) <= SHORTS_MAX_MS) return false
    return !ShortsTag.containsMatchIn(snippet.title) && !ShortsTag.containsMatchIn(snippet.description.orEmpty())
}

fun VideoDto.toSongEntity(now: Long): SongEntity {
    val rawTitle = snippet?.title.orEmpty()
    val parsed = MusicTitleParser.parse(rawTitle, snippet?.channelTitle)
    return SongEntity(
        videoId = id,
        title = parsed.title,
        artist = parsed.artist,
        rawTitle = HtmlEntities.decode(rawTitle),
        channelId = snippet?.channelId,
        channelTitle = snippet?.channelTitle,
        thumbnailUrl = snippet?.thumbnails?.best() ?: fallbackThumbnail(id),
        durationMs = IsoDuration.toMillis(contentDetails?.duration),
        isPlayable = isPlayableInEmbed(),
        updatedAt = now,
    )
}

/** Video id of a playlist item (contentDetails is the most reliable source). */
fun PlaylistItemDto.videoId(): String? =
    contentDetails?.videoId ?: snippet?.resourceId?.videoId

/**
 * Placeholder for items whose video could not be fetched (deleted / private). Marked
 * unplayable so the queue skips it, but kept so playlist positions stay correct.
 */
fun PlaylistItemDto.toUnavailableSongEntity(now: Long): SongEntity? {
    val videoId = videoId() ?: return null
    val rawTitle = snippet?.title.orEmpty()
    val owner = snippet?.videoOwnerChannelTitle
    val parsed = MusicTitleParser.parse(rawTitle, owner)
    return SongEntity(
        videoId = videoId,
        title = parsed.title,
        artist = parsed.artist,
        rawTitle = HtmlEntities.decode(rawTitle),
        channelId = snippet?.videoOwnerChannelId,
        channelTitle = owner,
        thumbnailUrl = snippet?.thumbnails?.best(),
        durationMs = 0L,
        isPlayable = false,
        updatedAt = now,
    )
}

/** Search hits have no duration; they are enriched with videos.list right after. */
fun SearchResultDto.toPartialSongEntity(now: Long): SongEntity? {
    val videoId = id.videoId ?: return null
    val rawTitle = snippet?.title.orEmpty()
    val parsed = MusicTitleParser.parse(rawTitle, snippet?.channelTitle)
    return SongEntity(
        videoId = videoId,
        title = parsed.title,
        artist = parsed.artist,
        rawTitle = HtmlEntities.decode(rawTitle),
        channelId = snippet?.channelId,
        channelTitle = snippet?.channelTitle,
        thumbnailUrl = snippet?.thumbnails?.best() ?: fallbackThumbnail(videoId),
        durationMs = 0L,
        isPlayable = snippet?.liveBroadcastContent != "upcoming",
        updatedAt = now,
    )
}

fun PlaylistDto.toEntity(now: Long): PlaylistEntity = PlaylistEntity(
    id = id,
    title = HtmlEntities.decode(snippet?.title.orEmpty()),
    description = HtmlEntities.decode(snippet?.description.orEmpty()),
    thumbnailUrl = snippet?.thumbnails?.best(),
    itemCount = contentDetails?.itemCount ?: 0,
    channelTitle = snippet?.channelTitle?.let(MusicTitleParser::cleanArtist),
    updatedAt = now,
)

fun SearchResultDto.toPlaylistEntity(now: Long): PlaylistEntity? {
    val playlistId = id.playlistId ?: return null
    return PlaylistEntity(
        id = playlistId,
        title = HtmlEntities.decode(snippet?.title.orEmpty()),
        description = HtmlEntities.decode(snippet?.description.orEmpty()),
        thumbnailUrl = snippet?.thumbnails?.best(),
        itemCount = 0,
        channelTitle = snippet?.channelTitle?.let(MusicTitleParser::cleanArtist),
        updatedAt = now,
    )
}

fun ChannelDto.toEntity(now: Long, isMine: Boolean): ChannelEntity = ChannelEntity(
    id = id,
    title = HtmlEntities.decode(snippet?.title.orEmpty()),
    thumbnailUrl = snippet?.thumbnails?.best(),
    isMine = isMine,
    updatedAt = now,
)

/** YouTube serves a predictable thumbnail for every video id. */
fun fallbackThumbnail(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

// Entity -> domain ---------------------------------------------------------------------

fun SongEntity.toDomain(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    isPlayable = isPlayable,
)

fun Song.toEntity(now: Long): SongEntity = SongEntity(
    videoId = videoId,
    title = title,
    artist = artist,
    rawTitle = title,
    channelId = channelId,
    channelTitle = artist,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    isPlayable = isPlayable,
    updatedAt = now,
)

fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    itemCount = itemCount,
    ownerName = channelTitle,
)

fun ChannelEntity.toArtist(): Artist = Artist(id = id, name = title, thumbnailUrl = thumbnailUrl)

fun List<SongEntity>.toSongs(): List<Song> = map { it.toDomain() }

fun List<PlaylistEntity>.toPlaylists(): List<Playlist> = map { it.toDomain() }
