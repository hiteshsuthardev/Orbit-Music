package com.example.orbitmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.Playlist
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme

@Composable
fun playlistSubtitle(playlist: Playlist): String {
    val count = pluralStringResource(R.plurals.track_count, playlist.itemCount, playlist.itemCount)
    return playlist.ownerName?.takeIf { it.isNotBlank() }?.let { "$count · $it" } ?: count
}

/** Carousel / grid card for a playlist. */
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 150.dp,
) = ArtworkCard(
    title = playlist.title,
    subtitle = playlistSubtitle(playlist),
    imageUrl = playlist.thumbnailUrl,
    placeholderKey = playlist.id,
    onClick = onClick,
    modifier = modifier,
    width = width,
)

/** List row for playlists (search results, pickers). */
@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrbitTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitArtwork(
            url = playlist.thumbnailUrl,
            contentDescription = null,
            placeholderKey = playlist.id,
            shape = OrbitShapes.SmallArtwork,
            placeholderIcon = Icons.AutoMirrored.Rounded.QueueMusic,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, style = OrbitTheme.type.songTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlistSubtitle(playlist), style = OrbitTheme.type.secondary, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OrbitPreviews
@Composable
private fun PlaylistItemsPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        val playlist = Playlist("PL", "Late night lofi", "", null, 42, "Nova")
        Column(Modifier.background(OrbitTheme.colors.background).padding(vertical = 20.dp)) {
            PlaylistCard(playlist = playlist, onClick = {}, modifier = Modifier.padding(start = 20.dp))
            PlaylistRow(playlist = playlist, onClick = {})
        }
    }
}
