package com.example.orbitmusic.domain.usecase.playlist

import com.example.orbitmusic.domain.ai.ResolveAiSongUseCase
import com.example.orbitmusic.domain.ai.SaveAiPlaylistResult
import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AiSongSuggestion
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.PlaylistRepository
import com.example.orbitmusic.domain.repository.SearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaylistUseCasesTest {

    private val playlists = mockk<PlaylistRepository>(relaxUnitFun = true)
    private val search = mockk<SearchRepository>()

    private fun song(id: String) = Song(id, "Song $id", "Artist", null, null, 200_000L, isPlayable = true)

    private val aiPlaylist = AiPlaylist(
        name = "Gym",
        description = "Loud",
        songs = listOf(AiSongSuggestion("One", "A"), AiSongSuggestion("Two", "B"), AiSongSuggestion("Three", "C")),
    )

    private val local = Playlist("${Playlist.LOCAL_ID_PREFIX}1", "Gym", "Loud", null, 2, "OrbitNova")

    @Test
    fun localIdsAreRecognised() {
        assertTrue(local.isLocal)
        assertEquals(null, local.shareUrl)
        assertEquals("PL9", local.copy(linkedYouTubeId = "PL9").youTubeId)
        assertFalse(Playlist("PL1", "x", "", null, 0, null).isLocal)
    }

    @Test
    fun aiPlaylistIsSavedInOrbitNovaWithTheSongsFoundBeforeAnError() = runTest {
        coEvery { search.search("One A", SearchFilter.Songs, false) } returns
            Resource.Success(SearchResults("One A", SearchFilter.Songs, songs = listOf(song("v1"))))
        coEvery { search.search("Two B", SearchFilter.Songs, false) } returns Resource.Error(AppError.QuotaExceeded)
        coEvery { playlists.createLocalPlaylist("Gym", "Loud", listOf(song("v1"))) } returns local

        val result = SaveAiPlaylistToOrbitNovaUseCase(playlists, ResolveAiSongUseCase(search))(aiPlaylist, "Mix")

        assertEquals(SaveAiPlaylistResult.Saved(local, added = 1, missing = 2), result)
        coVerify(exactly = 0) { search.search("Three C", any(), any()) }
    }

    @Test
    fun nothingFoundSavesNothing() = runTest {
        coEvery { search.search(any(), SearchFilter.Songs, false) } returns Resource.Error(AppError.NoInternet)

        val result = SaveAiPlaylistToOrbitNovaUseCase(playlists, ResolveAiSongUseCase(search))(aiPlaylist, "Mix")

        assertEquals(SaveAiPlaylistResult.Failed(AppError.NoInternet, null, 0), result)
        coVerify(exactly = 0) { playlists.createLocalPlaylist(any(), any(), any()) }
    }

    @Test
    fun copiesToYouTubeLinksAndResumesAfterAFailure() = runTest {
        val youTube = Playlist("PL9", "Gym", "Loud", null, 0, "Me")
        coEvery { playlists.getAllPlaylistSongs(local.id, any()) } returns Resource.Success(listOf(song("v1"), song("v2")))
        coEvery { playlists.createPlaylist("Gym", "Loud", PlaylistPrivacy.Private) } returns Resource.Success(youTube)
        coEvery { playlists.addSongToPlaylist("PL9", song("v1")) } returns Resource.Success(Unit)
        coEvery { playlists.addSongToPlaylist("PL9", song("v2")) } returns Resource.Error(AppError.QuotaExceeded)
        val useCase = SavePlaylistToYouTubeUseCase(playlists, CreatePlaylistUseCase(playlists))

        val first = useCase(local)
        assertEquals(SaveAiPlaylistResult.Failed(AppError.QuotaExceeded, youTube, added = 1), first)
        coVerify(exactly = 0) { playlists.linkToYouTube(any(), any()) }

        coEvery { playlists.addSongToPlaylist("PL9", song("v2")) } returns Resource.Success(Unit)
        val second = useCase(local, resumeYouTubeId = "PL9", alreadyAdded = 1)

        assertTrue(second is SaveAiPlaylistResult.Saved)
        coVerify(exactly = 1) { playlists.createPlaylist(any(), any(), any()) }
        coVerify(exactly = 1) { playlists.addSongToPlaylist("PL9", song("v1")) }
        coVerify { playlists.linkToYouTube(local.id, "PL9") }
    }
}
