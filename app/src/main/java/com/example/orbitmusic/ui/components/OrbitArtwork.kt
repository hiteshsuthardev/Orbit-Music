package com.example.orbitmusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.placeholderMesh

/**
 * Square album art. YouTube thumbnails are 16:9 (often letterboxed 4:3), so they are
 * always centre-cropped to a square. While loading, without a URL, or on failure, a mesh
 * gradient generated from [placeholderKey] (usually the video id) shows instead.
 */
@Composable
fun OrbitArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = OrbitShapes.SmallArtwork,
    placeholderKey: String = url ?: "orbit",
    placeholderIcon: ImageVector = Icons.Rounded.MusicNote,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .placeholderMesh(placeholderKey),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholderIcon,
            contentDescription = null,
            tint = OnColor.Secondary,
            modifier = Modifier.fillMaxSize(0.38f),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
