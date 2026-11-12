package com.example.orbitmusic.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.SearchRepository
import com.example.orbitmusic.domain.usecase.search.ClearSearchHistoryUseCase
import com.example.orbitmusic.domain.usecase.search.DeleteRecentSearchUseCase
import com.example.orbitmusic.domain.usecase.search.GetCachedSearchResultsUseCase
import com.example.orbitmusic.domain.usecase.search.GetSearchSuggestionsUseCase
import com.example.orbitmusic.domain.usecase.search.LoadMoreSearchResultsUseCase
import com.example.orbitmusic.domain.usecase.search.ObserveRecentSearchesUseCase
import com.example.orbitmusic.domain.usecase.search.SearchUseCase
import com.example.orbitmusic.fakes.MainDispatcherRule
import com.example.orbitmusic.util.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private class FakeSearchRepository : SearchRepository {
        val suggestionCalls = mutableListOf<String>()
        val apiSearches = mutableListOf<Pair<String, SearchFilter>>()
        var nextResult: Resource<SearchResults>? = null
        val recents = MutableStateFlow<List<String>>(emptyList())

        override suspend fun search(query: String, filter: SearchFilter, forceRefresh: Boolean): Resource<SearchResults> {
            apiSearches += query to filter
            return nextResult ?: Resource.Success(
                SearchResults(query, filter, songs = listOf(Song("v", query, "Artist", null, null, 1000, true))),
            )
        }

        override suspend fun loadMore(query: String, filter: SearchFilter) = Resource.Success(SearchResults(query, filter))
        override suspend fun cachedResults(query: String, filter: SearchFilter): SearchResults? = null
        override fun observeRecentSearches(limit: Int): Flow<List<String>> = recents
        override suspend fun saveSearch(query: String) {
            recents.value = listOf(query) + recents.value
        }
        override suspend fun deleteSearch(query: String) = Unit
        override suspend fun clearSearchHistory() = Unit
        override suspend fun suggestions(prefix: String, limit: Int): List<String> {
            suggestionCalls += prefix
            return listOf("$prefix suggestion")
        }
    }

    private val repository = FakeSearchRepository()
    private val online = object : NetworkMonitor {
        override val isOnline: Flow<Boolean> = flowOf(true)
        override fun isCurrentlyOnline() = true
    }

    private fun viewModel() = SearchViewModel(
        savedStateHandle = SavedStateHandle(),
        search = SearchUseCase(repository),
        loadMoreResults = LoadMoreSearchResultsUseCase(repository),
        cachedResults = GetCachedSearchResultsUseCase(repository),
        suggestions = GetSearchSuggestionsUseCase(repository),
        observeRecentSearches = ObserveRecentSearchesUseCase(repository),
        deleteRecentSearch = DeleteRecentSearchUseCase(repository),
        clearSearchHistory = ClearSearchHistoryUseCase(repository),
        networkMonitor = online,
    )

    @Test
    fun `typing is debounced and never calls the search API`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        runCurrent()

        vm.onQueryChange("d")
        advanceTimeBy(100)
        vm.onQueryChange("da")
        advanceTimeBy(100)
        vm.onQueryChange("daf")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()

        assertEquals(listOf("daf"), repository.suggestionCalls)
        assertEquals(emptyList<Pair<String, SearchFilter>>(), repository.apiSearches)
        assertEquals(SearchMode.Typing, vm.uiState.value.mode)
        assertEquals(listOf("daf suggestion"), vm.uiState.value.suggestions)
    }

    @Test
    fun `submitting searches once and shows results`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }

        vm.onQueryChange("daft punk ")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("daft punk" to SearchFilter.Songs), repository.apiSearches)
        val state = vm.uiState.value
        assertEquals(SearchMode.Results, state.mode)
        assertEquals("daft punk", state.results?.query)
        assertEquals(listOf("daft punk"), state.recentSearches)
    }

    @Test
    fun `changing filter after submit re-runs the search for that filter`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        vm.onSubmit("lofi")
        advanceUntilIdle()

        vm.onFilterChange(SearchFilter.Playlists)
        advanceUntilIdle()

        assertEquals(
            listOf("lofi" to SearchFilter.Songs, "lofi" to SearchFilter.Playlists),
            repository.apiSearches,
        )
    }

    @Test
    fun `errors are shown and clearing the query resets`() = runTest {
        repository.nextResult = Resource.Error(AppError.QuotaExceeded)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        vm.onSubmit("jazz")
        advanceUntilIdle()
        assertEquals(AppError.QuotaExceeded, vm.uiState.value.error)

        vm.onClearQuery()
        advanceUntilIdle()
        assertEquals(SearchMode.Recent, vm.uiState.value.mode)
        assertNull(vm.uiState.value.error)
    }
}
