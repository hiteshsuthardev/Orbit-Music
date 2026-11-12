package com.example.orbitmusic.ui.theme.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme

/** Flat, borderless surface (no tonal elevation, no shadow). */
@Composable
fun OrbitSurface(
    modifier: Modifier = Modifier,
    shape: Shape = OrbitShapes.GroupedList,
    color: Color = OrbitTheme.colors.surface,
    contentColor: Color = OrbitTheme.colors.textPrimary,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** Flat clickable card with spring press feedback and optional long press. */
@Composable
fun OrbitCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = OrbitShapes.GroupedList,
    color: Color = OrbitTheme.colors.surface,
    contentColor: Color = OrbitTheme.colors.textPrimary,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(
        modifier = modifier
            .scaled { scale.value }
            .clip(shape)
            .background(color)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

// Sheet --------------------------------------------------------------------------------------

/** Bottom sheet: surface colour, 26dp top corners, drag handle, 60% black backdrop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = OrbitTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OrbitTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = OrbitShapes.Sheet,
        containerColor = containerColor,
        contentColor = colors.textPrimary,
        scrimColor = BrandColors.Black.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(OrbitShapes.Pill)
                    .background(colors.outline),
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding(), content = content)
    }
}

// Settings group -------------------------------------------------------------------------------

/** Surface card whose [rows] are separated by dividers. */
@Composable
fun SettingsGroup(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    val colors = OrbitTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(OrbitShapes.GroupedList)
            .background(colors.surface),
    ) {
        rows.forEachIndexed { index, row ->
            row()
            if (index < rows.lastIndex) {
                HorizontalDivider(color = colors.divider, thickness = 1.dp, modifier = Modifier.padding(start = 60.dp))
            }
        }
    }
}

/** Settings row: 32dp coloured icon tile with a white icon, title, optional subtitle / trailing. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    iconTileColor: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = OrbitTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.combinedClickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(OrbitShapes.IconTile)
                .background(iconTileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = OnColor.Primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = OrbitTheme.type.songTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = OrbitTheme.type.secondary, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}

@OrbitPreviews
@Composable
private fun OrbitContainersPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            Modifier
                .background(OrbitTheme.colors.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OrbitCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_section_playlists), style = OrbitTheme.type.sectionTitle)
            }
            SettingsGroup(
                rows = listOf(
                    {
                        SettingsRow(
                            icon = Icons.Rounded.Bedtime,
                            iconTileColor = BrandColors.Supporting[1],
                            title = stringResource(R.string.sleep_timer_title),
                            subtitle = stringResource(R.string.sleep_timer_off),
                            onClick = {},
                        )
                    },
                    {
                        SettingsRow(
                            icon = Icons.Rounded.History,
                            iconTileColor = BrandColors.Supporting[4],
                            title = stringResource(R.string.library_clear_history),
                            trailing = { OrbitSwitch(checked = true, onCheckedChange = {}) },
                        )
                    },
                ),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
