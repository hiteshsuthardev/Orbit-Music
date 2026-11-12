package com.example.orbitmusic.data.mapper

import com.example.orbitmusic.data.remote.youtube.dto.PlaylistContentDetailsDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemContentDetailsDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistItemSnippetDto
import com.example.orbitmusic.data.remote.youtube.dto.PlaylistSnippetDto
import com.example.orbitmusic.data.remote.youtube.dto.ResourceIdDto
import com.example.orbitmusic.data.remote.youtube.dto.SearchResultDto
import com.example.orbitmusic.data.remote.youtube.dto.SearchSnippetDto
import com.example.orbitmusic.data.remote.youtube.dto.ThumbnailDto
import com.example.orbitmusic.data.remote.youtube.dto.ThumbnailsDto
import com.example.orbitmusic.data.remote.youtube.dto.VideoContentDetailsDto
import com.example.orbitmusic.data.remote.youtube.dto.VideoDto
import com.example.orbitmusic.data.remote.youtube.dto.VideoSnippetDto
import com.example.orbitmusic.data.remote.youtube.dto.VideoStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeMappersTest {

    private val now = 1_000L

    private fun video(
        privacy: String? = "public",
        embeddable: Boolean? = true,
        live: String? = "none",
        thumbnails: ThumbnailsDto? = ThumbnailsDto(
            medium = ThumbnailDto("https://img/medium.jpg"),
            high = ThumbnailDto("https://img/high.jpg"),
        ),
    ) = VideoDto(
        id = "abc123",
        snippet = VideoSnippetDto(
            title = "Daft Punk - Get Lucky (Official Audio) ft. Pharrell",
            channelId = "UC1",
            channelTitle = "Daft Punk - Topic",
            thumbnails = thumbnails,
            liveBroadcastContent = live,
        ),
        contentDetails = VideoContentDetailsDto(duration = "PT4M8S"),
        status = VideoStatusDto(privacyStatus = privacy, embeddable = embeddable, uploadStatus = "processed"),
    )

    @Test
    fun `video maps to a clean song entity`() {
        val entity = video().toSongEntity(now)
        assertEquals("abc123", entity.videoId)
        assertEquals("Daft Punk", entity.artist)
        assertEquals(248_000L, entity.durationMs)
        assertEquals("https://img/high.jpg", entity.thumbnailUrl)
        assertTrue(entity.isPlayable)
        assertEquals(now, entity.updatedAt)
        val song = entity.toDomain()
        assertEquals("https://music.youtube.com/watch?v=abc123", song.shareUrl)
    }

    @Test
    fun `non embeddable private and upcoming videos are unplayable`() {
        assertFalse(video(embeddable = false).toSongEntity(now).isPlayable)
        assertFalse(video(privacy = "private").toSongEntity(now).isPlayable)
        assertFalse(video(live = "upcoming").toSongEntity(now).isPlayable)
        assertTrue(video(privacy = "unlisted").toSongEntity(now).isPlayable)
    }

    @Test
    fun `missing thumbnails fall back to the ytimg url`() {
        assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", video(thumbnails = null).toSongEntity(now).thumbnailUrl)
    }

    @Test
    fun `deleted playlist item becomes an unplayable placeholder`() {
        val item = PlaylistItemDto(
            id = "item1",
            snippet = PlaylistItemSnippetDto(title = "Deleted video", resourceId = ResourceIdDto(videoId = "gone")),
            contentDetails = PlaylistItemContentDetailsDto(videoId = "gone"),
        )
        val entity = item.toUnavailableSongEntity(now)!!
        assertEquals("gone", entity.videoId)
        assertFalse(entity.isPlayable)
        assertEquals(0L, entity.durationMs)
    }

    @Test
    fun `playlist item without a video id is dropped`() {
        assertNull(PlaylistItemDto(id = "x").toUnavailableSongEntity(now))
    }

    @Test
    fun `playlist maps item count and cleans owner`() {
        val playlist = PlaylistDto(
            id = "PL1",
            snippet = PlaylistSnippetDto(title = "Chill &amp; Focus", channelTitle = "Lofi Girl - Topic"),
            contentDetails = PlaylistContentDetailsDto(itemCount = 42),
        ).toEntity(now).toDomain()
        assertEquals("Chill & Focus", playlist.title)
        assertEquals(42, playlist.itemCount)
        assertEquals("Lofi Girl", playlist.ownerName)
    }

    @Test
    fun `search hits map to partial songs and playlists`() {
        val songHit = SearchResultDto(
            id = ResourceIdDto(kind = "youtube#video", videoId = "v1"),
            snippet = SearchSnippetDto(title = "Song [Lyrics]", channelTitle = "Artist"),
        )
        val song = songHit.toPartialSongEntity(now)!!
        assertEquals("Song", song.title)
        assertEquals("Artist", song.artist)
        assertEquals(0L, song.durationMs)
        assertNull(songHit.toPlaylistEntity(now))

        val playlistHit = SearchResultDto(
            id = ResourceIdDto(kind = "youtube#playlist", playlistId = "PL9"),
            snippet = SearchSnippetDto(title = "Mix"),
        )
        assertEquals("PL9", playlistHit.toPlaylistEntity(now)!!.id)
        assertNull(playlistHit.toPartialSongEntity(now))
    }

    private fun musicCandidate(
        category: String? = "10",
        duration: String? = "PT3M45S",
        title: String = "Daft Punk - Get Lucky (Official Video)",
        description: String? = null,
        live: String? = "none",
    ) = VideoDto(
        id = "m1",
        snippet = VideoSnippetDto(
            title = title,
            description = description,
            categoryId = category,
            liveBroadcastContent = live,
        ),
        contentDetails = VideoContentDetailsDto(duration = duration),
    )

    @Test
    fun musicVideoInMusicCategoryIsKept() {
        assertTrue(musicCandidate().isMusicVideo())
    }

    @Test
    fun otherCategoriesAreRejected() {
        assertFalse(musicCandidate(category = "22").isMusicVideo())
        assertFalse(musicCandidate(category = null).isMusicVideo())
    }

    @Test
    fun shortsAreRejected() {
        assertFalse(musicCandidate(duration = "PT45S").isMusicVideo())
        assertFalse(musicCandidate(duration = "PT1M").isMusicVideo())
        assertFalse(musicCandidate(title = "New song teaser #Shorts").isMusicVideo())
        assertFalse(musicCandidate(description = "Full song out now #short").isMusicVideo())
        assertTrue(musicCandidate(title = "Shortstop - Live Session").isMusicVideo())
    }

    @Test
    fun liveStreamsAndUnknownLengthsAreRejected() {
        assertFalse(musicCandidate(live = "live", duration = "P0D").isMusicVideo())
        assertFalse(musicCandidate(duration = null).isMusicVideo())
    }
}
