package com.example.orbitmusic.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.usecase.search.ClearSearchHistoryUseCase
import com.example.orbitmusic.domain.usecase.search.DeleteRecentSearchUseCase
import com.example.orbitmusic.domain.usecase.search.GetCachedSearchResultsUseCase
import com.example.orbitmusic.domain.usecase.search.GetSearchSuggestionsUseCase
import com.example.orbitmusic.domain.usecase.search.LoadMoreSearchResultsUseCase
import com.example.orbitmusic.domain.usecase.search.ObserveRecentSearchesUseCase
import com.example.orbitmusic.domain.usecase.search.SearchUseCase
import com.example.orbitmusic.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode {
    /** Empty query: recent searches. */
    Recent,

    /** Typing: quota-free suggestions + cached results for the current text. */
    Typing,

    /** A submitted search (API results). */
    Results,
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.Songs,
    val mode: SearchMode = SearchMode.Recent,
    val recentSearches: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    /** Earlier results for what is being typed (free, from Room). */
    val previewResults: SearchResults? = null,
    val results: SearchResults? = null,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: AppError? = null,
    val isOffline: Boolean = false,
)

/**
 * Search costs 100 quota units per request, so typing never hits the API: input is
 * debounced (400 ms) and drives local suggestions plus cached results only. The network
 * search runs when the user submits (IME action or tapping a suggestion / recent search).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val search: SearchUseCase,
    private val loadMoreResults: LoadMoreSearchResultsUseCase,
    private val cachedResults: GetCachedSearchResultsUseCase,
    private val suggestions: GetSearchSuggestionsUseCase,
    observeRecentSearches: ObserveRecentSearchesUseCase,
    private val deleteRecentSearch: DeleteRecentSearchUseCase,
    private val clearSearchHistory: ClearSearchHistoryUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, SearchFilter.Songs.name)
    private val submitted = savedStateHandle.getStateFlow<String?>(KEY_SUBMITTED, null)

    private val results = MutableStateFlow<SearchResults?>(null)
    private val searching = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private var searchJob: Job? = null

    private val typingPreview: Flow<TypingPreview> = combine(query, filter) { q, f -> q.trim() to SearchFilter.valueOf(f) }
        .debounce(DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .mapLatest { (q, f) -> TypingPreview(suggestions(q), cachedResults(q, f)) }
        .onStart { emit(TypingPreview()) }

    private val inputs = combine(query, filter, submitted) { q, f, s -> Inputs(q, SearchFilter.valueOf(f), s) }
    private val status = combine(results, searching, loadingMore, error) { r, s, l, e -> Status(r, s, l, e) }

    val uiState: StateFlow<SearchUiState> = combine(
        inputs,
        status,
        typingPreview,
        observeRecentSearches(),
        networkMonitor.isOnline,
    ) { input, s, preview, recents, online ->
        val trimmed = input.query.trim()
        val mode = when {
            trimmed.isEmpty() -> SearchMode.Recent
            input.submitted != null && input.submitted == trimmed -> SearchMode.Results
            else -> SearchMode.Typing
        }
        SearchUiState(
            query = input.query,
            filter = input.filter,
            mode = mode,
            recentSearches = recents,
            suggestions = preview.suggestions,
            previewResults = preview.cached?.takeUnless { it.isEmpty },
            results = s.results,
            isSearching = s.searching,
            isLoadingMore = s.loadingMore,
            error = s.error,
            isOffline = !online,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        // Restore results after process death (served from cache, no quota).
        submitted.value?.let { runSearch(it, SearchFilter.valueOf(filter.value)) }
    }

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun onClearQuery() {
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle[KEY_SUBMITTED] = null
        searchJob?.cancel()
        results.value = null
        error.value = null
        searching.value = false
    }

    fun onSubmit(text: String = query.value) {
        val trimmed = text.trim()
        if (trimmed.length < SearchUseCase.MIN_QUERY_LENGTH) return
        savedStateHandle[KEY_QUERY] = trimmed
        savedStateHandle[KEY_SUBMITTED] = trimmed
        runSearch(trimmed, SearchFilter.valueOf(filter.value))
    }

    fun onFilterChange(newFilter: SearchFilter) {
        if (newFilter.name == filter.value) return
        savedStateHandle[KEY_FILTER] = newFilter.name
        submitted.value?.takeIf { it == query.value.trim() }?.let { runSearch(it, newFilter) }
    }

    fun onRetry() {
        submitted.value?.let { runSearch(it, SearchFilter.valueOf(filter.value), force = true) }
    }

    fun onLoadMore() {
        val current = results.value ?: return
        if (!current.hasMore || loadingMore.value || searching.value) return
        loadingMore.value = true
        viewModelScope.launch {
            when (val result = loadMoreResults(current.query, current.filter)) {
                is Resource.Success -> results.value = result.data
                is Resource.Error -> error.value = result.error
                Resource.Loading -> Unit
            }
            loadingMore.value = false
        }
    }

    fun onDeleteRecent(query: String) {
        viewModelScope.launch { deleteRecentSearch(query) }
    }

    fun onClearRecents() {
        viewModelScope.launch { clearSearchHistory() }
    }

    fun dismissError() {
        error.value = null
    }

    private fun runSearch(text: String, searchFilter: SearchFilter, force: Boolean = false) {
        searchJob?.cancel()
        searching.value = true
        error.value = null
        if (results.value?.filter != searchFilter || results.value?.query != text) results.value = null
        searchJob = viewModelScope.launch {
            when (val result = search(text, searchFilter, forceRefresh = force)) {
                is Resource.Success -> results.value = result.data
                is Resource.Error -> error.value = result.error
                Resource.Loading -> Unit
            }
            searching.value = false
        }
    }

    private data class TypingPreview(val suggestions: List<String> = emptyList(), val cached: SearchResults? = null)
    private data class Inputs(val query: String, val filter: SearchFilter, val submitted: String?)
    private data class Status(val results: SearchResults?, val searching: Boolean, val loadingMore: Boolean, val error: AppError?)

    companion object {
        const val DEBOUNCE_MILLIS = 400L
        private const val KEY_QUERY = "search_query"
        private const val KEY_FILTER = "search_filter"
        private const val KEY_SUBMITTED = "search_submitted"
    }
}
