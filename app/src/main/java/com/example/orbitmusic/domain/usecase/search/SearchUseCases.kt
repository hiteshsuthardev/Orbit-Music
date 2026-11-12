package com.example.orbitmusic.domain.usecase.search

import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Runs a search and records it in the recent-search history on success.
 * Queries shorter than [MIN_QUERY_LENGTH] are ignored to save quota.
 */
class SearchUseCase @Inject constructor(private val repository: SearchRepository) {
    suspend operator fun invoke(
        query: String,
        filter: SearchFilter,
        forceRefresh: Boolean = false,
    ): Resource<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return Resource.Success(SearchResults(trimmed, filter))
        val result = repository.search(trimmed, filter, forceRefresh)
        if (result is Resource.Success) repository.saveSearch(trimmed)
        return result
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

class LoadMoreSearchResultsUseCase @Inject constructor(private val repository: SearchRepository) {
    suspend operator fun invoke(query: String, filter: SearchFilter): Resource<SearchResults> =
        repository.loadMore(query.trim(), filter)
}

class ObserveRecentSearchesUseCase @Inject constructor(private val repository: SearchRepository) {
    operator fun invoke(limit: Int = 10): Flow<List<String>> = repository.observeRecentSearches(limit)
}

class DeleteRecentSearchUseCase @Inject constructor(private val repository: SearchRepository) {
    suspend operator fun invoke(query: String) = repository.deleteSearch(query)
}

class ClearSearchHistoryUseCase @Inject constructor(private val repository: SearchRepository) {
    suspend operator fun invoke() = repository.clearSearchHistory()
}

/** Free lookup of an earlier search while the user is still typing. */
class GetCachedSearchResultsUseCase @Inject constructor(private val repository: SearchRepository) {
    suspend operator fun invoke(query: String, filter: SearchFilter): SearchResults? =
        if (query.trim().length < SearchUseCase.MIN_QUERY_LENGTH) null else repository.cachedResults(query.trim(), filter)
}

/** Live suggestions while typing; never spends API quota. */
class GetSearchSuggestionsUseCase @Inject constructor(private val repository: SearchRepository) {
    suspend operator fun invoke(prefix: String): List<String> =
        if (prefix.isBlank()) emptyList() else repository.suggestions(prefix)
}
