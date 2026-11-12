package com.example.orbitmusic.ui.player

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TINT_ANIMATION_MS = 600

/** Swatches pulled from one artwork. [tint] = vibrant, else dominant, else the fallback. */
@Immutable
data class ArtworkPalette(val tint: Color, val second: Color, val third: Color) {
    val blobs: List<Color> get() = listOf(tint, second, third)

    companion object {
        val Fallback = ArtworkPalette(BrandColors.FallbackTint, BrandColors.Violet, BrandColors.Rose)
    }
}

/**
 * The current artwork's "tint" colour via AndroidX Palette: the vibrant swatch, else the
 * dominant one, else [BrandColors.FallbackTint]. Changes animate over ~600ms. Screens blend
 * this into their own backgrounds (mini player, playlist header, queue).
 */
@Composable
fun rememberArtworkTint(artworkUrl: String?): Color = rememberArtworkPalette(artworkUrl).tint

/** Three animated swatches for the Now Playing blobs (and [ArtworkPalette.tint] for the rest). */
@Composable
fun rememberArtworkPalette(artworkUrl: String?): ArtworkPalette {
    val context = LocalContext.current
    var extracted by remember(artworkUrl) { mutableStateOf<ArtworkPalette?>(null) }
    LaunchedEffect(artworkUrl) {
        extracted = artworkUrl?.let { extractPalette(context, it) }
    }
    val target = extracted ?: ArtworkPalette.Fallback
    val spec = tween<Color>(if (OrbitTheme.reduceMotion) 0 else TINT_ANIMATION_MS)
    val tint by animateColorAsState(target.tint, spec, label = "artworkTint")
    val second by animateColorAsState(target.second, spec, label = "artworkSecond")
    val third by animateColorAsState(target.third, spec, label = "artworkThird")
    return ArtworkPalette(tint, second, third)
}

private suspend fun extractPalette(context: Context, url: String): ArtworkPalette? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(128)
            .allowHardware(false) // Palette needs to read the pixels.
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
        val palette = Palette.from(result.image.toBitmap()).generate()
        val tint = (palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb?.let(::Color) ?: return@runCatching null
        val second = (palette.darkVibrantSwatch ?: palette.dominantSwatch)?.rgb?.let(::Color) ?: tint
        val third = (palette.mutedSwatch ?: palette.lightVibrantSwatch)?.rgb?.let(::Color) ?: second
        ArtworkPalette(tint, second, third)
    }.getOrNull()
}
