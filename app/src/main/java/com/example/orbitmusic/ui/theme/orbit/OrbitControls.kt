package com.example.orbitmusic.ui.theme.orbit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import kotlin.math.roundToInt

// Chip ------------------------------------------------------------------------------------

/** 36dp pill. Selected = textPrimary fill with background-coloured text. */
@Composable
fun OrbitChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onColorful: Boolean = false,
) {
    val colors = OrbitTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    val fill by animateColorAsState(
        when {
            selected && onColorful -> OnColor.Primary
            selected -> colors.textPrimary
            onColorful -> OnColor.ChipFill
            else -> colors.surfaceRaised
        },
        label = "chipFill",
    )
    val content = when {
        selected && onColorful -> BrandColors.OnWhitePlay
        selected -> colors.background
        onColorful -> OnColor.Primary
        else -> colors.textPrimary
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .scaled { scale.value }
            .selectable(selected = selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(OrbitShapes.Pill)
                .background(fill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = OrbitTheme.type.smallLabel, color = content, maxLines = 1)
        }
    }
}

// Search field ------------------------------------------------------------------------------

/** 48dp, surfaceRaised, leading search icon, trailing clear button. */
@Composable
fun OrbitSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    onClear: () -> Unit = { onValueChange("") },
) {
    val colors = OrbitTheme.colors
    val textStyle = OrbitTheme.type.songTitle.copy(color = colors.textPrimary)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.accentText),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(OrbitShapes.SearchField)
                    .background(colors.surfaceRaised)
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    OrbitIconButton(
                        imageVector = Icons.Rounded.Cancel,
                        contentDescription = stringResource(R.string.cd_clear_text),
                        onClick = onClear,
                        tint = colors.textSecondary,
                    )
                }
            }
        },
    )
}

// Seek bar ------------------------------------------------------------------------------------

/**
 * 6dp track in the outline colour, filled part and 14dp thumb in [activeColor] (white on
 * Now Playing), with a 48dp invisible touch area. Supports tap, drag and TalkBack.
 */
@Composable
fun OrbitSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    activeColor: Color = OnColor.Primary,
    trackColor: Color = OrbitTheme.colors.outline,
    contentDescription: String = stringResource(R.string.cd_seek_bar),
) {
    val density = LocalDensity.current
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val currentRange by rememberUpdatedState(valueRange)
    var widthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val thumbRadiusPx = with(density) { 7.dp.toPx() }

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val thumbScale by animateFloatAsState(if (dragging) 1.35f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "thumb")

    fun valueAt(x: Float): Float {
        val f = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f)
        return currentRange.start + f * (currentRange.endInclusive - currentRange.start)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(OrbitDimens.MinTouchTarget)
            .onSizeChanged { widthPx = it.width }
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                if (!enabled) disabled() else setProgress { target ->
                    currentOnChange(target.coerceIn(currentRange)); currentOnFinished?.invoke(); true
                }
            }
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> currentOnChange(valueAt(offset.x)); currentOnFinished?.invoke() }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true; currentOnChange(valueAt(it.x)) },
                            onDragEnd = { dragging = false; currentOnFinished?.invoke() },
                            onDragCancel = { dragging = false; currentOnFinished?.invoke() },
                            onHorizontalDrag = { change, _ -> change.consume(); currentOnChange(valueAt(change.position.x)) },
                        )
                    },
            )
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(trackColor, cornerRadius = r)
                    drawRoundRect(activeColor, size = size.copy(width = size.width * fraction), cornerRadius = r)
                },
        )
        Box(
            Modifier
                .offset { IntOffset((widthPx * fraction - thumbRadiusPx).roundToInt(), 0) }
                .size(14.dp)
                .scaled { thumbScale }
                .clip(OrbitShapes.Circle)
                .background(activeColor),
        )
    }
}

// Switch ----------------------------------------------------------------------------------------

/** Brand gradient track when on, outline when off; white thumb. */
@Composable
fun OrbitSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val colors = OrbitTheme.colors
    val knobOffset by animateDpAsState(
        if (checked) 22.dp else 3.dp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "switchKnob",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(51.dp, 32.dp)
                .clip(OrbitShapes.Pill)
                .background(if (checked) BrandColors.Gradient else SolidColor(colors.outline)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = knobOffset)
                    .size(26.dp)
                    .floatingShadow(2.dp, OrbitShapes.Circle, alpha = 0.3f)
                    .clip(OrbitShapes.Circle)
                    .background(OnColor.Primary),
            )
        }
    }
}

// Segmented ------------------------------------------------------------------------------------

/** Theme picker style control: surfaceRaised container, selected segment raised. */
@Composable
fun OrbitSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrbitTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(OrbitShapes.Pill)
            .background(colors.surfaceRaised)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(
            segmentWidth * selectedIndex.coerceIn(0, options.lastIndex.coerceAtLeast(0)),
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .floatingShadow(3.dp, OrbitShapes.Pill, alpha = 0.25f)
                .clip(OrbitShapes.Pill)
                .background(if (colors.isDark) colors.outline else colors.background),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                Box(
                    Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .selectable(selected = index == selectedIndex, role = Role.RadioButton, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = OrbitTheme.type.smallLabel,
                        color = if (index == selectedIndex) colors.textPrimary else colors.textSecondary,
                    )
                }
            }
        }
    }
}

// Previews ------------------------------------------------------------------------------------------

@OrbitPreviews
@Composable
private fun OrbitControlsPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        var query by remember { mutableStateOf("lofi") }
        var progress by remember { mutableFloatStateOf(0.4f) }
        var on by remember { mutableStateOf(true) }
        var segment by remember { mutableIntStateOf(2) }
        Column(
            Modifier
                .background(OrbitTheme.colors.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrbitChip(stringResource(R.string.search_filter_songs), selected = true, onClick = {}, leadingIcon = Icons.Rounded.Check)
                OrbitChip(stringResource(R.string.search_filter_playlists), selected = false, onClick = {})
            }
            OrbitSearchField(query, { query = it }, stringResource(R.string.search_hint))
            Box(Modifier.background(BrandColors.NowPlayingBase).padding(12.dp)) {
                OrbitSeekBar(progress, { progress = it }, trackColor = OnColor.ChipFill)
            }
            OrbitSwitch(checked = on, onCheckedChange = { on = it })
            OrbitSegmented(
                options = listOf(
                    stringResource(R.string.settings_theme_light),
                    stringResource(R.string.settings_theme_dark),
                    stringResource(R.string.settings_theme_system),
                ),
                selectedIndex = segment,
                onSelect = { segment = it },
            )
        }
    }
}
