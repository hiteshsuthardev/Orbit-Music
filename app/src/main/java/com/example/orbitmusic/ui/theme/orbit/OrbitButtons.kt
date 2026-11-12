package com.example.orbitmusic.ui.theme.orbit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme

private const val DISABLED_ALPHA = 0.4f

/** Shared pill button: [fill] brush, [content] colour, spring press, min [height]. */
@Composable
private fun PillButton(
    onClick: () -> Unit,
    fill: Brush,
    contentColor: Color,
    height: Dp,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled)
    Row(
        modifier = modifier
            .scaled { scale.value }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(OrbitShapes.Pill)
            .background(fill)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = height)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides OrbitTheme.type.button.copy(color = contentColor),
        ) { content() }
    }
}

@Composable
private fun RowScope.ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Brand gradient fill, dark content, 54dp. */
@Composable
fun OrbitPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = PillButton(onClick, BrandColors.Gradient, BrandColors.OnBrand, 54.dp, modifier, enabled) {
    ButtonLabel(text, leadingIcon)
}

/** textPrimary fill with background-coloured content, 58dp. Main CTA on sheets / Login. */
@Composable
fun OrbitWhiteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onColorful: Boolean = false,
) {
    val colors = OrbitTheme.colors
    // On full-bleed colour (Login) the button is always white with dark content.
    val fill = if (onColorful) OnColor.Primary else colors.textPrimary
    val content = if (onColorful) BrandColors.OnWhitePlay else colors.background
    PillButton(onClick, SolidColor(fill), content, 58.dp, modifier, enabled) { ButtonLabel(text, leadingIcon) }
}

/** surfaceRaised fill, 54dp. */
@Composable
fun OrbitTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = OrbitTheme.colors
    PillButton(onClick, SolidColor(colors.surfaceRaised), colors.textPrimary, 54.dp, modifier, enabled) {
        ButtonLabel(text, leadingIcon)
    }
}

/** Plain accent-coloured text action ("Clear", "See all", "Sign out"). */
@Composable
fun OrbitTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = OrbitTheme.colors.accentText,
) {
    Box(
        modifier = modifier
            .clip(OrbitShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = OrbitDimens.MinTouchTarget, minHeight = OrbitDimens.MinTouchTarget)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OrbitTheme.type.smallLabel, color = color)
    }
}

enum class PlayButtonStyle {
    /** Brand gradient with dark icon (default everywhere). */
    Gradient,

    /** White with near-black icon (Now Playing, Home hero). */
    White,
}

/** Circular play / pause button. */
@Composable
fun OrbitPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    style: PlayButtonStyle = PlayButtonStyle.Gradient,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled, pressedScale = 0.92f)
    val fill: Brush = when (style) {
        PlayButtonStyle.Gradient -> BrandColors.Gradient
        PlayButtonStyle.White -> SolidColor(OnColor.Primary)
    }
    val iconColor = when (style) {
        PlayButtonStyle.Gradient -> BrandColors.OnBrand
        PlayButtonStyle.White -> BrandColors.OnWhitePlay
    }
    Box(
        modifier = modifier
            .size(maxOf(size, OrbitDimens.MinTouchTarget))
            .scaled { scale.value }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(OrbitShapes.Circle)
            .background(fill)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = isPlaying, label = "playPause") { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                tint = iconColor,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

enum class IconButtonStyle {
    /** No container. */
    Plain,

    /** surfaceRaised circle. */
    Tonal,

    /** White at 14% (on artwork / full-bleed colour). */
    Translucent,

    /** Brand gradient circle. */
    Gradient,
}

/** Round icon button, never smaller than the 48dp touch target. */
@Composable
fun OrbitIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.Plain,
    size: Dp = OrbitDimens.MinTouchTarget,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    val colors = OrbitTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled, pressedScale = 0.9f)
    val (fill, defaultTint) = when (style) {
        IconButtonStyle.Plain -> SolidColor(Color.Transparent) to colors.textPrimary
        IconButtonStyle.Tonal -> SolidColor(colors.surfaceRaised) to colors.textPrimary
        IconButtonStyle.Translucent -> SolidColor(OnColor.ChipFill) to OnColor.Primary
        IconButtonStyle.Gradient -> BrandColors.Gradient to BrandColors.OnBrand
    }
    Box(
        modifier = modifier
            .size(maxOf(size, OrbitDimens.MinTouchTarget))
            .scaled { scale.value }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(OrbitShapes.Circle)
            .background(fill)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (tint == Color.Unspecified) defaultTint else tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OrbitPreviews
@Composable
private fun OrbitButtonsPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            Modifier
                .background(OrbitTheme.colors.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OrbitPrimaryButton(stringResource(R.string.playlist_play_all), {}, leadingIcon = Icons.Rounded.PlayArrow)
            OrbitWhiteButton(stringResource(R.string.login_continue_with_google), {})
            OrbitTonalButton(stringResource(R.string.playlist_shuffle), {}, leadingIcon = Icons.Rounded.Shuffle)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OrbitPlayButton(isPlaying = false, onClick = {})
                OrbitIconButton(Icons.Rounded.Favorite, stringResource(R.string.cd_like), {}, style = IconButtonStyle.Tonal)
                OrbitTextButton(stringResource(R.string.search_clear_all), {})
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
