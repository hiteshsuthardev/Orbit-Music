package com.example.orbitmusic.domain.ai

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
import com.example.orbitmusic.domain.usecase.playlist.AddSongToPlaylistUseCase
import com.example.orbitmusic.domain.usecase.playlist.CreatePlaylistUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveAiPlaylistUseCaseTest {

    private val created = Playlist("PL1", "Gym", "", null, 0, ownerName = null)
    private val playlists = mockk<PlaylistRepository>()
    private val search = mockk<SearchRepository>()
    private val useCase = SaveAiPlaylistUseCase(
        CreatePlaylistUseCase(playlists),
        AddSongToPlaylistUseCase(playlists),
        ResolveAiSongUseCase(search),
    )

    private fun song(id: String, title: String) = Song(id, title, "Artist", null, null, 200_000L, isPlayable = true)

    private fun searchFinds(query: String, vararg songs: Song) {
        coEvery { search.search(query, SearchFilter.Songs, false) } returns
            Resource.Success(SearchResults(query, SearchFilter.Songs, songs = songs.toList()))
    }

    private val playlist = AiPlaylist(
        name = "Gym",
        description = "Loud",
        songs = listOf(AiSongSuggestion("One", "A"), AiSongSuggestion("Lost", "B"), AiSongSuggestion("Two", "C")),
    )

    @Test
    fun createsPrivatePlaylistAndAddsMatchedSongs() = runTest {
        coEvery { playlists.createPlaylist("Gym", "Loud\n\nMade with Orbit AI", PlaylistPrivacy.Private) } returns
            Resource.Success(created)
        coEvery { playlists.addSongToPlaylist("PL1", any()) } returns Resource.Success(Unit)
        searchFinds("One A", song("v1", "One"))
        searchFinds("Lost B")
        searchFinds("Two C", song("v2", "Two"))
        val progress = mutableListOf<Int>()

        val result = useCase(playlist, fallbackName = "Mix") { done, _ -> progress += done }

        assertEquals(SaveAiPlaylistResult.Saved(created, added = 2, missing = 1), result)
        assertEquals(listOf(0, 1, 2, 3), progress)
        coVerify(exactly = 2) { playlists.addSongToPlaylist("PL1", any()) }
    }

    @Test
    fun stopsOnQuotaErrorAndReportsWhatWasSaved() = runTest {
        coEvery { playlists.createPlaylist(any(), any(), any()) } returns Resource.Success(created)
        coEvery { playlists.addSongToPlaylist("PL1", any()) } returns Resource.Success(Unit)
        searchFinds("One A", song("v1", "One"))
        coEvery { search.search("Lost B", SearchFilter.Songs, false) } returns Resource.Error(AppError.QuotaExceeded)

        val result = useCase(playlist, fallbackName = "Mix")

        assertEquals(SaveAiPlaylistResult.Failed(AppError.QuotaExceeded, created, added = 1), result)
        coVerify(exactly = 0) { search.search("Two C", any(), any()) }
    }

    @Test
    fun failedCreationAddsNothingAndBlankNameUsesFallback() = runTest {
        coEvery { playlists.createPlaylist("Mix", any(), any()) } returns Resource.Error(AppError.NoInternet)

        val result = useCase(playlist.copy(name = " "), fallbackName = "Mix")

        assertTrue(result is SaveAiPlaylistResult.Failed)
        assertNull((result as SaveAiPlaylistResult.Failed).playlist)
        coVerify(exactly = 0) { playlists.addSongToPlaylist(any(), any()) }
    }
}
