package com.example.orbitmusic.ui.screens.ai

import android.content.Intent
import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.ai.OrbitAiState
import com.example.orbitmusic.domain.model.AiPlaylist
import com.example.orbitmusic.domain.model.AiSongSuggestion
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.ui.components.OrbitEmptyState
import com.example.orbitmusic.ui.components.OrbitErrorBanner
import com.example.orbitmusic.ui.components.ScreenHeader
import com.example.orbitmusic.ui.components.orbit.LogoStyle
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.orbit.OrbitLogo
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.IconButtonStyle
import com.example.orbitmusic.ui.theme.orbit.OrbitChip
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPlayButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSurface
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import java.util.Date

private val suggestions = listOf(
    R.string.ai_suggestion_1,
    R.string.ai_suggestion_2,
    R.string.ai_suggestion_3,
    R.string.ai_suggestion_4,
    R.string.ai_suggestion_5,
)

@Composable
fun OrbitAiScreen(
    contentPadding: PaddingValues,
    viewModel: OrbitAiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contactEmail = BuildConfig.DEVELOPER_CONTACT_EMAIL
    val contactSubject = stringResource(R.string.ai_limit_email_subject)
    OrbitAiContent(
        state = state,
        contentPadding = contentPadding,
        canContactDeveloper = contactEmail.isNotBlank(),
        callbacks = OrbitAiCallbacks(
            onSend = viewModel::send,
            onRetry = viewModel::retry,
            onNewChat = viewModel::newChat,
            onAllowExplicitChange = viewModel::setAllowExplicit,
            onPlay = { answer, index -> viewModel.play(answer, startIndex = index) },
            onShuffle = { answer -> viewModel.play(answer, shuffle = true) },
            onDismissPlaybackError = viewModel::dismissPlaybackError,
            onContactDeveloper = {
                val email = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(contactEmail))
                    putExtra(Intent.EXTRA_SUBJECT, contactSubject)
                }
                runCatching { context.startActivity(email) }
            },
        ),
    )
}

internal class OrbitAiCallbacks(
    val onSend: (String) -> Unit,
    val onRetry: () -> Unit,
    val onNewChat: () -> Unit,
    val onAllowExplicitChange: (Boolean) -> Unit,
    val onPlay: (AiChatEntry.Answer, Int) -> Unit,
    val onShuffle: (AiChatEntry.Answer) -> Unit,
    val onDismissPlaybackError: () -> Unit,
    val onContactDeveloper: () -> Unit,
)

/**
 * Orbit AI chat: describe a mood or moment, get a playlist card back, play or shuffle it,
 * and refine it with follow-ups ("remove the slow ones", "add 10 more").
 */
@Composable
internal fun OrbitAiContent(
    state: OrbitAiUiState,
    contentPadding: PaddingValues,
    callbacks: OrbitAiCallbacks,
    canContactDeveloper: Boolean = false,
) {
    val colors = OrbitTheme.colors
    val allowance = state.allowance
    if (allowance?.isLifetimeExhausted == true) {
        LimitReachedScreen(
            totalLimit = allowance.totalLimit,
            canContactDeveloper = canContactDeveloper,
            onContactDeveloper = callbacks.onContactDeveloper,
            contentPadding = contentPadding,
        )
        return
    }
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomInset = maxOf(contentPadding.calculateBottomPadding(), imeBottom)
    val itemCount = state.entries.size + if (state.session is OrbitAiState.Idle || state.session is OrbitAiState.Ready) 0 else 1

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = bottomInset),
    ) {
        ScreenHeader(
            title = stringResource(R.string.ai_title),
            trailing = {
                if (state.entries.isNotEmpty()) {
                    OrbitIconButton(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.ai_new_chat),
                        onClick = callbacks.onNewChat,
                        style = IconButtonStyle.Tonal,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            },
        )
        OrbitChip(
            label = stringResource(R.string.ai_explicit),
            selected = state.allowExplicit,
            onClick = { callbacks.onAllowExplicitChange(!state.allowExplicit) },
            modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.entries.isEmpty() && state.session is OrbitAiState.Idle) {
                Intro(onSuggestion = callbacks.onSend)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = OrbitDimens.ScreenPadding, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        when (entry) {
                            is AiChatEntry.Request -> RequestBubble(entry.text)
                            is AiChatEntry.Answer -> AnswerCard(
                                playlist = entry.playlist,
                                isStarting = state.startingAnswerId == entry.id,
                                onPlay = { index -> callbacks.onPlay(entry, index) },
                                onShuffle = { callbacks.onShuffle(entry) },
                            )
                        }
                    }
                    when (val session = state.session) {
                        OrbitAiState.Thinking -> item(key = "status") { StatusRow(stringResource(R.string.ai_thinking)) }
                        is OrbitAiState.Busy -> item(key = "status") {
                            StatusRow(stringResource(R.string.ai_busy_retrying, session.secondsLeft))
                        }
                        is OrbitAiState.Failed -> item(key = "status") {
                            OrbitErrorBanner(error = session.error, onRetry = callbacks.onRetry)
                        }
                        OrbitAiState.Idle, is OrbitAiState.Ready -> Unit
                    }
                }
            }
        }

        state.playbackError?.let { error ->
            OrbitErrorBanner(
                error = error,
                onDismiss = callbacks.onDismissPlaybackError,
                modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 6.dp),
            )
        }
        if (allowance?.isWindowExhausted == true) {
            WindowLimitNotice(windowLimit = allowance.windowLimit, nextRequestAt = allowance.nextRequestAt)
        } else {
            allowance?.let {
                Text(
                    text = stringResource(R.string.ai_requests_left, it.remainingInWindow, it.windowLimit),
                    style = OrbitTheme.type.tabLabel,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding + 4.dp),
                )
            }
            PromptInput(enabled = !state.isWorking, onSend = callbacks.onSend)
        }
    }
}

/** Shown instead of the input while the 3-hour request limit is used up. */
@Composable
private fun WindowLimitNotice(windowLimit: Int, nextRequestAt: Long?) {
    val context = LocalContext.current
    val colors = OrbitTheme.colors
    val time = nextRequestAt?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp)
            .clip(OrbitShapes.GroupedList)
            .background(colors.surface)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.ai_window_limit_title, windowLimit),
            style = OrbitTheme.type.songTitle,
            color = colors.textPrimary,
        )
        Text(
            text = if (time != null) {
                stringResource(R.string.ai_window_limit_body, windowLimit, time)
            } else {
                stringResource(R.string.ai_window_limit_body_soon, windowLimit)
            },
            style = OrbitTheme.type.secondary,
            color = colors.textSecondary,
        )
    }
}

/** All requests used: ask the user to contact the developer for more. */
@Composable
private fun LimitReachedScreen(
    totalLimit: Int,
    canContactDeveloper: Boolean,
    onContactDeveloper: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(contentPadding),
    ) {
        ScreenHeader(title = stringResource(R.string.ai_title))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OrbitEmptyState(
                icon = Icons.Rounded.MarkEmailUnread,
                title = stringResource(R.string.ai_limit_reached_title),
                body = stringResource(
                    if (canContactDeveloper) R.string.ai_limit_reached_body else R.string.ai_limit_reached_body_no_email,
                    totalLimit,
                ),
                actionLabel = if (canContactDeveloper) stringResource(R.string.ai_contact_developer) else null,
                onAction = if (canContactDeveloper) onContactDeveloper else null,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(onSuggestion: (String) -> Unit) {
    val colors = OrbitTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OrbitDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OrbitLogo(size = 88.dp, style = LogoStyle.Brand, contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.ai_intro_title),
            style = OrbitTheme.type.sectionTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ai_intro_body),
            style = OrbitTheme.type.secondary,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                val text = stringResource(suggestion)
                OrbitChip(label = text, selected = false, onClick = { onSuggestion(text) })
            }
        }
    }
}

@Composable
private fun RequestBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            style = OrbitTheme.type.body,
            color = OrbitTheme.colors.textPrimary,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(OrbitShapes.GroupedList)
                .background(OrbitTheme.colors.surfaceRaised)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StatusRow(text: String) {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitLoader(size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = OrbitTheme.type.smallLabel, color = OrbitTheme.colors.textSecondary)
    }
}

/** The suggested playlist: name, description, play / shuffle, and the numbered songs. */
@Composable
private fun AnswerCard(
    playlist: AiPlaylist,
    isStarting: Boolean,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
) {
    val colors = OrbitTheme.colors
    OrbitSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = playlist.name.ifBlank { stringResource(R.string.ai_default_playlist_name) },
                        style = OrbitTheme.type.sectionTitle,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (playlist.description.isNotBlank()) {
                        Text(playlist.description, style = OrbitTheme.type.secondary, color = colors.textSecondary)
                    }
                    Text(
                        pluralStringResource(R.plurals.track_count, playlist.songs.size, playlist.songs.size),
                        style = OrbitTheme.type.smallLabel,
                        color = colors.textSecondary,
                    )
                }
                OrbitIconButton(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.playlist_shuffle),
                    onClick = onShuffle,
                    style = IconButtonStyle.Tonal,
                    enabled = !isStarting,
                )
                Spacer(Modifier.width(8.dp))
                if (isStarting) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { OrbitLoader(size = 36.dp) }
                } else {
                    OrbitPlayButton(isPlaying = false, onClick = { onPlay(0) }, size = 52.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            playlist.songs.forEachIndexed { index, song ->
                SuggestionRow(index = index, song = song, enabled = !isStarting, onClick = { onPlay(index) })
            }
            Text(
                stringResource(R.string.ai_lookup_note),
                style = OrbitTheme.type.tabLabel,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun SuggestionRow(index: Int, song: AiSongSuggestion, enabled: Boolean, onClick: () -> Unit) {
    val colors = OrbitTheme.colors
    val label = stringResource(R.string.ai_play_song, song.title, song.artist)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString(),
            style = OrbitTheme.type.duration,
            color = colors.textSecondary,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(song.title, style = OrbitTheme.type.songTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = OrbitTheme.type.secondary, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PromptInput(enabled: Boolean, onSend: (String) -> Unit) {
    val colors = OrbitTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    var text by rememberSaveable { mutableStateOf("") }
    val hint = stringResource(R.string.ai_input_hint)
    val submit = {
        if (enabled && text.isNotBlank()) {
            onSend(text)
            text = ""
            keyboard?.hide()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(OrbitShapes.SearchField)
                .background(colors.surfaceRaised)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text(hint, style = OrbitTheme.type.body, color = colors.textSecondary)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(MAX_PROMPT_LENGTH) },
                textStyle = OrbitTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentText),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint },
            )
        }
        Spacer(Modifier.width(8.dp))
        OrbitIconButton(
            imageVector = Icons.AutoMirrored.Rounded.Send,
            contentDescription = stringResource(R.string.ai_send),
            onClick = { submit() },
            style = IconButtonStyle.Gradient,
            enabled = enabled && text.isNotBlank(),
        )
    }
}

private const val MAX_PROMPT_LENGTH = 300

internal val noAiCallbacks = OrbitAiCallbacks({}, {}, {}, {}, { _, _ -> }, {}, {}, {})

@OrbitPreviews
@Composable
private fun OrbitAiPreview() {
    val playlist = AiPlaylist(
        name = "Late night drive",
        description = "Smooth synths for empty roads",
        songs = listOf(AiSongSuggestion("Midnight City", "M83"), AiSongSuggestion("Nightcall", "Kavinsky")),
    )
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        OrbitAiContent(
            state = OrbitAiUiState(
                entries = listOf(AiChatEntry.Request(0, "Songs for a late night drive"), AiChatEntry.Answer(1, playlist)),
                session = OrbitAiState.Busy(secondsLeft = 8),
                playbackError = AppError.QuotaExceeded,
            ),
            contentPadding = PaddingValues(),
            callbacks = noAiCallbacks,
        )
    }
}
