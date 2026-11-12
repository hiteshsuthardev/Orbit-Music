package com.example.orbitmusic.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.SearchFilter
import com.example.orbitmusic.domain.model.SearchResults
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.common.LoadMoreEffect
import com.example.orbitmusic.ui.components.CategoryTile
import com.example.orbitmusic.ui.components.OfflineBanner
import com.example.orbitmusic.ui.components.OrbitEmptyState
import com.example.orbitmusic.ui.components.OrbitErrorBanner
import com.example.orbitmusic.ui.components.OrbitErrorState
import com.example.orbitmusic.ui.components.PlaylistRow
import com.example.orbitmusic.ui.components.ScreenHeader
import com.example.orbitmusic.ui.components.SectionHeader
import com.example.orbitmusic.ui.components.SongListSkeleton
import com.example.orbitmusic.ui.components.SongRow
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.orbit.OrbitSpin
import com.example.orbitmusic.ui.screens.songactions.LocalSongActions
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitChip
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitSearchField
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme

/**
 * A Browse tile: its label, the search it runs (results are cached in Room for 24h), and its
 * look: an icon and gradient, with a song's artwork once that search has been cached.
 */
private class BrowseCategory(
    @param:StringRes val label: Int,
    @param:StringRes val query: Int,
    val seed: String,
    val icon: ImageVector,
    val gradient: List<Color>,
)

private val browseCategories = listOf(
    BrowseCategory(
        R.string.search_category_trending, R.string.search_category_trending_query, "browse-trending",
        Icons.Rounded.Whatshot, listOf(Color(0xFFFF3D71), Color(0xFFFF8A3D)),
    ),
    BrowseCategory(
        R.string.search_category_chill, R.string.search_category_chill_query, "browse-chill",
        Icons.Rounded.Spa, listOf(Color(0xFF1FA2B8), Color(0xFF3A47D5)),
    ),
    BrowseCategory(
        R.string.search_category_workout, R.string.search_category_workout_query, "browse-workout",
        Icons.Rounded.FitnessCenter, listOf(Color(0xFFE5413B), Color(0xFFB0186F)),
    ),
    BrowseCategory(
        R.string.search_category_focus, R.string.search_category_focus_query, "browse-focus",
        Icons.Rounded.Headphones, listOf(Color(0xFF4B6CB7), Color(0xFF1B2A55)),
    ),
    BrowseCategory(
        R.string.search_category_party, R.string.search_category_party_query, "browse-party",
        Icons.Rounded.Celebration, listOf(Color(0xFF9B1FE8), Color(0xFF2A7FE0)),
    ),
    BrowseCategory(
        R.string.search_category_indie, R.string.search_category_indie_query, "browse-indie",
        Icons.Rounded.Album, listOf(Color(0xFF0F8F80), Color(0xFF1E4F6A)),
    ),
)

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenPlaylist: (Playlist) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val browseArt by viewModel.browseArt.collectAsStateWithLifecycle()
    val actions = LocalSongActions.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) focusRequester.requestFocus()
    }
    if (state.mode == SearchMode.Results) LoadMoreEffect(listState, viewModel::onLoadMore)
    // Browse artwork comes from cached results only (no quota); refreshed whenever Browse shows.
    val browseQueries = browseCategories.map { stringResource(it.query) }
    val showingBrowse = state.mode == SearchMode.Recent
    LaunchedEffect(showingBrowse) {
        if (showingBrowse) viewModel.loadBrowseArt(browseQueries)
    }

    SearchContent(
        state = state,
        contentPadding = contentPadding,
        browseArt = browseArt,
        listState = listState,
        fieldModifier = Modifier.focusRequester(focusRequester),
        callbacks = SearchCallbacks(
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onSubmit = { text ->
                keyboard?.hide()
                viewModel.onSubmit(text)
            },
            onFilterChange = viewModel::onFilterChange,
            onDeleteRecent = viewModel::onDeleteRecent,
            onClearRecents = viewModel::onClearRecents,
            onRetry = viewModel::onRetry,
            onDismissError = viewModel::dismissError,
            onPlay = { songs, song -> actions.play(songs, song) },
            onSongMenu = actions::showMenu,
            onOpenPlaylist = onOpenPlaylist,
        ),
    )
}

internal class SearchCallbacks(
    val onQueryChange: (String) -> Unit,
    val onClearQuery: () -> Unit,
    val onSubmit: (String) -> Unit,
    val onFilterChange: (SearchFilter) -> Unit,
    val onDeleteRecent: (String) -> Unit,
    val onClearRecents: () -> Unit,
    val onRetry: () -> Unit,
    val onDismissError: () -> Unit,
    val onPlay: (List<Song>, Song) -> Unit,
    val onSongMenu: (Song) -> Unit,
    val onOpenPlaylist: (Playlist) -> Unit,
)

/**
 * Title and search field, then: recent chips + Browse tiles (empty query), bold-matched
 * suggestions + a saved Top result (typing), or Songs/Playlists results (submitted).
 */
@Composable
internal fun SearchContent(
    state: SearchUiState,
    contentPadding: PaddingValues,
    callbacks: SearchCallbacks,
    browseArt: Map<String, String> = emptyMap(),
    listState: LazyListState = rememberLazyListState(),
    fieldModifier: Modifier = Modifier,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ScreenHeader(
            title = stringResource(R.string.search_title),
            trailing = { OrbitSpin(modifier = Modifier.padding(end = 8.dp)) },
        )
        OrbitSearchField(
            value = state.query,
            onValueChange = callbacks.onQueryChange,
            placeholder = stringResource(R.string.search_hint),
            modifier = fieldModifier.padding(horizontal = OrbitDimens.ScreenPadding),
            onSearch = { callbacks.onSubmit(state.query) },
            onClear = callbacks.onClearQuery,
        )
        if (state.mode == SearchMode.Results) {
            Row(
                modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OrbitChip(
                    label = stringResource(R.string.search_filter_songs),
                    selected = state.filter == SearchFilter.Songs,
                    onClick = { callbacks.onFilterChange(SearchFilter.Songs) },
                )
                OrbitChip(
                    label = stringResource(R.string.search_filter_playlists),
                    selected = state.filter == SearchFilter.Playlists,
                    onClick = { callbacks.onFilterChange(SearchFilter.Playlists) },
                )
            }
        }
        if (state.isOffline) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { OfflineBanner() }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = contentPadding.calculateBottomPadding()),
        ) {
            when (state.mode) {
                SearchMode.Recent -> {
                    recentSearches(state.recentSearches, callbacks)
                    browse(browseArt, callbacks.onSubmit)
                }
                SearchMode.Typing -> {
                    suggestionRows(state.query, state.suggestions, callbacks.onSubmit)
                    state.previewResults?.takeUnless { it.isEmpty }?.let { saved ->
                        item(key = "topResultHeader") {
                            SectionHeader(
                                title = stringResource(R.string.search_top_result),
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        resultRows(saved, callbacks)
                    }
                }
                SearchMode.Results -> results(state, callbacks)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.recentSearches(recents: List<String>, callbacks: SearchCallbacks) {
    if (recents.isEmpty()) return
    item(key = "recentHeader") {
        SectionHeader(
            title = stringResource(R.string.search_recent_title),
            action = { OrbitTextButton(text = stringResource(R.string.search_clear), onClick = callbacks.onClearRecents) },
        )
    }
    item(key = "recentChips") {
        FlowRow(
            modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding).padding(top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recents.forEach { recent ->
                val removeLabel = stringResource(R.string.cd_remove_recent, recent)
                OrbitChip(
                    label = recent,
                    selected = false,
                    onClick = { callbacks.onSubmit(recent) },
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(removeLabel) {
                                callbacks.onDeleteRecent(recent)
                                true
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun LazyListScope.browse(art: Map<String, String>, onSubmit: (String) -> Unit) {
    item(key = "browseHeader") {
        SectionHeader(title = stringResource(R.string.search_browse), modifier = Modifier.padding(bottom = 12.dp))
    }
    item(key = "browseGrid") {
        Column(
            modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            browseCategories.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { category ->
                        val query = stringResource(category.query)
                        CategoryTile(
                            label = stringResource(category.label),
                            seed = category.seed,
                            onClick = { onSubmit(query) },
                            icon = category.icon,
                            artworkUrl = art[query],
                            gradient = category.gradient,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.suggestionRows(query: String, suggestions: List<String>, onSubmit: (String) -> Unit) {
    items(suggestions, key = { "suggestion:$it" }) { suggestion ->
        SuggestionRow(
            text = highlightMatch(suggestion, query),
            onClick = { onSubmit(suggestion) },
            onClickLabel = stringResource(R.string.cd_use_suggestion, suggestion),
        )
    }
}

private fun LazyListScope.results(state: SearchUiState, callbacks: SearchCallbacks) {
    if (state.error != null && state.results == null && !state.isSearching) {
        item(key = "errorState") { OrbitErrorState(error = state.error, onRetry = callbacks.onRetry) }
        return
    }
    state.error?.let { error ->
        item(key = "error") {
            OrbitErrorBanner(
                error = error,
                onRetry = callbacks.onRetry,
                onDismiss = callbacks.onDismissError,
                modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp),
            )
        }
    }
    val results = state.results
    when {
        state.isSearching && results == null -> item(key = "skeleton") { SongListSkeleton() }
        results == null -> Unit
        results.isEmpty -> item(key = "noResults") {
            OrbitEmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.search_no_results_title),
                body = stringResource(R.string.search_no_results_body, results.query),
            )
        }
        else -> {
            if (results.fromCache) {
                item(key = "cachedHint") {
                    Text(
                        text = stringResource(R.string.search_cached_hint),
                        style = OrbitTheme.type.smallLabel,
                        color = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 4.dp),
                    )
                }
            }
            resultRows(results, callbacks)
            if (state.isLoadingMore) {
                item(key = "loadingMore") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        OrbitLoader(size = 40.dp)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.resultRows(results: SearchResults, callbacks: SearchCallbacks) {
    when (results.filter) {
        SearchFilter.Songs -> items(results.songs, key = { "song:${it.videoId}" }) { song ->
            SongRow(
                song = song,
                onClick = { callbacks.onPlay(results.songs, song) },
                onMoreClick = { callbacks.onSongMenu(song) },
                modifier = Modifier.animateItem(),
            )
        }
        SearchFilter.Playlists -> items(results.playlists, key = { "playlist:${it.id}" }) { playlist ->
            PlaylistRow(playlist = playlist, onClick = { callbacks.onOpenPlaylist(playlist) }, modifier = Modifier.animateItem())
        }
    }
}

/** The typed part in bold primary, the rest of the suggestion in secondary. */
@Composable
private fun highlightMatch(suggestion: String, query: String): AnnotatedString {
    val colors = OrbitTheme.colors
    val start = if (query.isBlank()) -1 else suggestion.indexOf(query.trim(), ignoreCase = true)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = colors.textSecondary)) {
            if (start < 0) {
                append(suggestion)
            } else {
                val end = start + query.trim().length
                append(suggestion.substring(0, start))
                withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.ExtraBold)) {
                    append(suggestion.substring(start, end))
                }
                append(suggestion.substring(end))
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: AnnotatedString, onClick: () -> Unit, onClickLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = OrbitDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = OrbitTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = OrbitTheme.type.songTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val previewCallbacks = SearchCallbacks({}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})

@OrbitPreviews
@Composable
private fun SearchPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        SearchContent(
            state = SearchUiState(recentSearches = listOf("lofi beats", "Daft Punk", "synthwave")),
            contentPadding = PaddingValues(),
            callbacks = previewCallbacks,
        )
    }
}

@OrbitPreviews
@Composable
private fun SearchTypingPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        SearchContent(
            state = SearchUiState(query = "lo", mode = SearchMode.Typing, suggestions = listOf("lofi beats", "lofi hip hop", "love songs")),
            contentPadding = PaddingValues(),
            callbacks = previewCallbacks,
        )
    }
}
