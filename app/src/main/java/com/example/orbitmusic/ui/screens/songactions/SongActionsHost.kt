package com.example.orbitmusic.ui.screens.songactions

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.common.ObserveAsEvents
import com.example.orbitmusic.ui.components.OrbitArtwork
import com.example.orbitmusic.ui.components.PlaylistRow
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.songSubtitle
import com.example.orbitmusic.ui.components.previewSong
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSheet
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import kotlinx.coroutines.launch

/** What screens can do with songs. Provided once by the main shell. */
interface SongActions {
    fun play(songs: List<Song>, startSong: Song?, shuffle: Boolean = false)
    fun showMenu(song: Song)
}

val LocalSongActions = staticCompositionLocalOf<SongActions> {
    error("SongActions not provided; wrap content in SongActionsHost")
}

/**
 * Provides [LocalSongActions] to [content] and renders the shared song menu sheet,
 * playlist picker, snackbars, consent launcher and share sheet.
 */
@Composable
fun SongActionsHost(
    viewModel: SongActionsViewModel,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.resultCode, result.data) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SongActionsEvent.Message -> {
                val text = if (event.formatArg != null) {
                    context.getString(event.messageRes, event.formatArg)
                } else {
                    context.getString(event.messageRes)
                }
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(text)
                }
            }
            is SongActionsEvent.LaunchConsent -> consentLauncher.launch(
                IntentSenderRequest.Builder(event.pendingIntent.intentSender).build(),
            )
            is SongActionsEvent.Share -> {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, event.song.title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        context.getString(R.string.share_song_text, event.song.title, event.song.artist, event.song.shareUrl),
                    )
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
            }
        }
    }

    val actions = remember(viewModel) {
        object : SongActions {
            override fun play(songs: List<Song>, startSong: Song?, shuffle: Boolean) =
                viewModel.play(songs, startSong, shuffle)

            override fun showMenu(song: Song) = viewModel.showMenu(song)
        }
    }

    CompositionLocalProvider(LocalSongActions provides actions) {
        content()
    }

    state.menuSong?.let { song ->
        SongMenuSheet(
            song = song,
            isFavorite = song.videoId in state.favoriteIds,
            onDismiss = viewModel::dismissMenu,
            onPlayNext = { viewModel.onPlayNext(song) },
            onAddToQueue = { viewModel.onAddToQueue(song) },
            onAddToPlaylist = { viewModel.onAddToPlaylist(song) },
            onToggleFavorite = { viewModel.onToggleFavorite(song) },
            onShare = { viewModel.onShare(song) },
        )
    }

    state.addToPlaylistSong?.let { song ->
        PlaylistPickerSheet(
            song = song,
            playlists = state.myPlaylists,
            isLoading = state.isLoadingPlaylists && state.myPlaylists.isEmpty(),
            addingToPlaylistId = state.addingToPlaylistId,
            onChoose = viewModel::onPlaylistChosen,
            onDismiss = viewModel::dismissPlaylistPicker,
        )
    }
}

/** Song menu: header (art, title, artist), then Play next, Add to queue, Add to playlist, Favorite, Share. */
@Composable
private fun SongMenuSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    OrbitSheet(onDismissRequest = onDismiss) {
        SongMenuContent(song, isFavorite, onPlayNext, onAddToQueue, onAddToPlaylist, onToggleFavorite, onShare)
    }
}

@Composable
internal fun ColumnScope.SongMenuContent(
    song: Song,
    isFavorite: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    SheetSongHeader(song)
    HorizontalDivider(
        color = OrbitTheme.colors.divider,
        modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp),
    )
    SheetAction(Icons.Rounded.SkipNext, stringResource(R.string.action_play_next), onPlayNext, enabled = song.isPlayable)
    SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.action_add_to_queue), onAddToQueue, enabled = song.isPlayable)
    SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.action_add_to_playlist), onAddToPlaylist)
    SheetAction(
        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        label = stringResource(if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
        onClick = onToggleFavorite,
        iconTint = if (isFavorite) OrbitTheme.colors.accentText else null,
    )
    SheetAction(Icons.Rounded.Share, stringResource(R.string.action_share), onShare)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SheetSongHeader(song: Song) {
    val colors = OrbitTheme.colors
    Row(
        modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitArtwork(
            url = song.thumbnailUrl,
            contentDescription = null,
            placeholderKey = song.videoId,
            shape = OrbitShapes.SmallArtwork,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = OrbitTheme.type.songTitle,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(songSubtitle(song), style = OrbitTheme.type.secondary, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconTint: Color? = null,
) {
    val colors = OrbitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = OrbitDimens.ScreenPadding)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint ?: colors.textPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = OrbitTheme.type.songTitle, color = colors.textPrimary)
    }
}

private const val DISABLED_ALPHA = 0.4f

@Composable
private fun PlaylistPickerSheet(
    song: Song,
    playlists: List<Playlist>,
    isLoading: Boolean,
    addingToPlaylistId: String?,
    onChoose: (Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrbitTheme.colors
    OrbitSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.add_to_playlist_title, song.title),
            style = OrbitTheme.type.sectionTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp)
                .semantics { heading() },
        )
        when {
            isLoading -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) { OrbitLoader() }
            playlists.isEmpty() -> Text(
                text = stringResource(R.string.add_to_playlist_empty),
                style = OrbitTheme.type.secondary,
                color = colors.textSecondary,
                modifier = Modifier.padding(OrbitDimens.ScreenPadding),
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { if (addingToPlaylistId == null) onChoose(playlist) },
                        )
                        if (addingToPlaylistId == playlist.id) {
                            OrbitLoader(size = 32.dp, modifier = Modifier.padding(end = OrbitDimens.ScreenPadding))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OrbitPreviews
@Composable
private fun SongMenuPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Column(Modifier.background(OrbitTheme.colors.surface)) {
            SongMenuContent(previewSong, isFavorite = true, onPlayNext = {}, onAddToQueue = {}, onAddToPlaylist = {}, onToggleFavorite = {}, onShare = {})
        }
    }
}
