package com.example.orbitmusic.ui.theme.orbit

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Theme-dependent colour tokens. Screens read colours only from here, [BrandColors] and
 * [OnColor]; no hex values appear in screen code.
 */
@Immutable
data class OrbitColors(
    val background: Color,
    /** Grouped lists, sheets, cards. */
    val surface: Color,
    /** Chips, search field, quick-pick tiles. */
    val surfaceRaised: Color,
    val divider: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Selected tab, links, "Clear", now-playing title. */
    val accentText: Color,
    val isDark: Boolean,
)

private val Ink = Color(0xFF0B0B10)
private val White = Color(0xFFFFFFFF)

val DarkOrbitColors = OrbitColors(
    background = Color(0xFF08080C),
    surface = Color(0xFF16161D),
    surfaceRaised = Color(0xFF22222C),
    divider = White.copy(alpha = 0.09f),
    outline = White.copy(alpha = 0.26f),
    textPrimary = White,
    textSecondary = Color(0xFFA6A6B8),
    accentText = Color(0xFFFF5C87),
    isDark = true,
)

val LightOrbitColors = OrbitColors(
    background = White,
    surface = Color(0xFFF4F4F7),
    surfaceRaised = Color(0xFFEBEBF1),
    divider = Ink.copy(alpha = 0.09f),
    outline = Ink.copy(alpha = 0.26f),
    textPrimary = Ink,
    textSecondary = Color(0xFF5C5C6B),
    accentText = Color(0xFFE11D55),
    isDark = false,
)

/** Brand colours shared by both themes. */
object BrandColors {
    val Rose = Color(0xFFFF3D71)
    val Amber = Color(0xFFFF8A3D)

    /** Icons and text on the brand gradient (never white). */
    val OnBrand = Color(0xFF16050A)

    /** Swipe-to-remove; content on it is [OnColor.Primary]. */
    val Destructive = Color(0xFFE5173F)

    /** Active shuffle / repeat on Now Playing. */
    val ActiveControl = Color(0xFFFF8FAB)

    /** Artwork tint when Palette finds nothing usable. */
    val FallbackTint = Color(0xFF8A1E7A)

    /** Icon colour on the white play button (Now Playing, Home hero). */
    val OnWhitePlay = Ink

    /** Dark plum base for Splash / Login mesh gradients. */
    val PlumBase = Color(0xFF1A0820)

    /** Base under the Now Playing artwork blobs. */
    val NowPlayingBase = Color(0xFF0A0A10)

    val Violet = Color(0xFF7B2FF7)

    /** Artwork placeholders, category tiles and settings icon tiles only. */
    val Supporting: List<Color> = listOf(
        Color(0xFFFF3D9A),
        Violet,
        Color(0xFF1E3CFF),
        Color(0xFF00E5C3),
        Color(0xFF0077FF),
        Color(0xFFFFB347),
        Color(0xFFFFD166),
        Color(0xFFC6FF3D),
        Color(0xFF00C97B),
        Color(0xFFFF2E2E),
        Color(0xFFC9B6FF),
        Color(0xFFFF9ECF),
        Color(0xFF6EC5FF),
        Color(0xFFB23DFF),
    )

    /** Stable supporting colour for a key (e.g. a video id). */
    fun supportingFor(key: String, offset: Int = 0): Color {
        val index = Math.floorMod(key.hashCode() + offset * 7, Supporting.size)
        return Supporting[index]
    }

    /** 135deg Rose -> Amber (top-left to bottom-right). */
    val Gradient: Brush = Brush.linearGradient(listOf(Rose, Amber))

    val Black = Color(0xFF000000)
}

/**
 * Content colours for full-bleed colourful screens (Splash, Login, Now Playing), used in
 * BOTH themes.
 */
object OnColor {
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Primary.copy(alpha = 0.74f)
    val ChipFill = Primary.copy(alpha = 0.14f)
    val Scrim = BrandColors.Black
}

/**
 * Readable content colour on an arbitrary (e.g. artwork-tinted) background: whichever of
 * near-black or white has the higher contrast ratio. Above ~0.18 relative luminance the
 * dark ink wins, so mid-tone tints (e.g. the light-theme mini player) get dark text.
 */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > CONTRAST_CROSSOVER_LUMINANCE) LightOrbitColors.textPrimary else OnColor.Primary

/** Luminance where white and near-black text have equal WCAG contrast. */
private const val CONTRAST_CROSSOVER_LUMINANCE = 0.18f

fun OrbitColors.toMaterialColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = accentText,
        onPrimary = background,
        secondary = BrandColors.Amber,
        onSecondary = BrandColors.OnBrand,
        tertiary = accentText,
        onTertiary = background,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceRaised,
        surfaceTint = background,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        inversePrimary = accentText,
        outline = outline,
        outlineVariant = divider,
        error = BrandColors.Destructive,
        onError = OnColor.Primary,
        scrim = BrandColors.Black,
    )
} else {
    lightColorScheme(
        primary = accentText,
        onPrimary = background,
        secondary = BrandColors.Amber,
        onSecondary = BrandColors.OnBrand,
        tertiary = accentText,
        onTertiary = background,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceRaised,
        surfaceTint = background,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        inversePrimary = accentText,
        outline = outline,
        outlineVariant = divider,
        error = BrandColors.Destructive,
        onError = OnColor.Primary,
        scrim = BrandColors.Black,
    )
}
