package com.example.orbitmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.meshGradient
import com.example.orbitmusic.ui.theme.orbit.rememberPressScale
import com.example.orbitmusic.ui.theme.orbit.scaled
import com.example.orbitmusic.util.formatTrackTime

/** "Artist · 3:45" (duration omitted when unknown). */
@Composable
fun songSubtitle(song: Song): String =
    if (song.durationMs > 0) {
        stringResource(R.string.song_subtitle, song.artist, formatTrackTime(song.durationMs))
    } else {
        song.artist
    }

/**
 * Song list row: 48dp artwork, title, artist, duration and a horizontal three-dot menu,
 * with an optional leading rank number. Long-press opens the same menu.
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    rank: Int? = null,
) {
    val colors = OrbitTheme.colors
    val type = OrbitTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                enabled = song.isPlayable,
                onClickLabel = stringResource(R.string.cd_play),
                onLongClickLabel = stringResource(R.string.cd_more_options),
                onLongClick = onMoreClick,
                onClick = onClick,
            )
            .heightIn(min = 64.dp)
            .padding(start = OrbitDimens.ScreenPadding, end = 6.dp, top = 8.dp, bottom = 8.dp)
            .alpha(if (song.isPlayable) 1f else 0.42f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = type.sectionTitle,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 28.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        OrbitArtwork(
            url = song.thumbnailUrl,
            contentDescription = null,
            placeholderKey = song.videoId,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.cd_now_playing),
                        tint = colors.accentText,
                        modifier = Modifier.padding(end = 6.dp).size(16.dp),
                    )
                }
                Text(
                    text = song.title,
                    style = type.songTitle,
                    color = if (isCurrent) colors.accentText else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (song.isPlayable) song.artist else stringResource(R.string.song_unavailable),
                style = type.secondary,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.durationMs > 0) {
            Text(formatTrackTime(song.durationMs), style = type.duration, color = colors.textSecondary)
        }
        OrbitIconButton(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.cd_song_options, song.title),
            onClick = onMoreClick,
            tint = colors.textSecondary,
        )
    }
}

/** 150dp artwork card with title and subtitle underneath (carousels, grids). */
@Composable
fun ArtworkCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    placeholderKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp? = 150.dp,
) {
    val colors = OrbitTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    val description = stringResource(R.string.cd_song_card, title, subtitle)
    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .scaled { scale.value }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.cd_more_options),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrbitArtwork(
            url = imageUrl,
            contentDescription = null,
            placeholderKey = placeholderKey,
            shape = OrbitShapes.Artwork,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column {
            Text(title, style = OrbitTheme.type.songTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = OrbitTheme.type.secondary, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Song card for carousels ("Recently played", "Liked music"). */
@Composable
fun SongCard(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
) = ArtworkCard(
    title = song.title,
    subtitle = song.artist,
    imageUrl = song.thumbnailUrl,
    placeholderKey = song.videoId,
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
    width = width,
)

/** 56dp quick-pick tile: artwork flush left, title, on surfaceRaised. */
@Composable
fun QuickPickTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    placeholderKey: String = title,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = OrbitTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Row(
        modifier = modifier
            .height(56.dp)
            .scaled { scale.value }
            .clip(OrbitShapes.QuickPickTile)
            .background(colors.surfaceRaised)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp)) {
            if (leading != null) {
                leading()
            } else {
                OrbitArtwork(
                    url = imageUrl,
                    contentDescription = null,
                    placeholderKey = placeholderKey,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = OrbitTheme.type.smallLabel,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
    }
}

/** Quick-pick tile for a song (long-press opens its menu). */
@Composable
fun QuickPickTile(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) = QuickPickTile(
    title = song.title,
    onClick = onClick,
    modifier = modifier,
    imageUrl = song.thumbnailUrl,
    placeholderKey = song.videoId,
    onLongClick = onMoreClick,
)

/**
 * Browse / category tile: a gradient card with a bold white label top-left and, tilted in
 * the bottom-right corner, the category's [artworkUrl] (e.g. a song from its cached results)
 * or, without one, a frosted [icon] badge. [gradient] picks the colours; without it a mesh
 * gradient is generated from [seed].
 */
@Composable
fun CategoryTile(
    label: String,
    seed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    artworkUrl: String? = null,
    gradient: List<Color>? = null,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    val background = if (gradient != null) {
        Modifier.background(Brush.linearGradient(gradient))
    } else {
        Modifier.meshGradient(
            base = BrandColors.supportingFor(seed, 0),
            blobs = listOf(BrandColors.supportingFor(seed, 1), BrandColors.supportingFor(seed, 2)),
            seed = seed.hashCode(),
        )
    }
    Box(
        modifier = modifier
            .height(CategoryTileHeight)
            .scaled { scale.value }
            .clip(OrbitShapes.CategoryTile)
            .then(background)
            .combinedClickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
    ) {
        val tilt = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 14.dp, y = 10.dp)
            .graphicsLayer { rotationZ = CategoryArtTilt }
        when {
            !artworkUrl.isNullOrBlank() -> OrbitArtwork(
                url = artworkUrl,
                contentDescription = null,
                shape = OrbitShapes.QuickPickTile,
                placeholderKey = seed,
                modifier = tilt
                    .size(CategoryArtSize)
                    .shadow(10.dp, OrbitShapes.QuickPickTile),
            )
            icon != null -> Box(
                modifier = tilt
                    .size(CategoryArtSize)
                    .clip(OrbitShapes.Artwork)
                    .background(OnColor.ChipFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = OnColor.Primary, modifier = Modifier.size(40.dp))
            }
        }
        Text(
            text = label,
            style = OrbitTheme.type.sectionTitle,
            color = OnColor.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp, end = CategoryArtSize - 8.dp),
        )
    }
}

private val CategoryTileHeight = 104.dp
private val CategoryArtSize = 74.dp
private const val CategoryArtTilt = 22f

internal val previewSong = Song(
    videoId = "preview",
    title = "Midnight Drive",
    artist = "Nova Star",
    channelId = null,
    thumbnailUrl = null,
    durationMs = 215_000,
    isPlayable = true,
)

@OrbitPreviews
@Composable
private fun SongItemsPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            Modifier.background(OrbitTheme.colors.background).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SongRow(song = previewSong, onClick = {}, onMoreClick = {}, isCurrent = true)
            SongRow(song = previewSong.copy(videoId = "b", title = "Ocean Eyes"), onClick = {}, onMoreClick = {}, rank = 2)
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SongCard(song = previewSong, onClick = {}, onLongClick = {})
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickPickTile(song = previewSong, onClick = {}, onMoreClick = {}, modifier = Modifier.fillMaxWidth())
                    CategoryTile(stringResource(R.string.home_section_trending), "trending", {}, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
