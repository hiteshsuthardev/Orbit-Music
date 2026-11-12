package com.example.orbitmusic.ui.screens.settings

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.ui.components.ScreenHeader
import com.example.orbitmusic.ui.components.UserAvatar
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitSegmented
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSurface
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.SettingsGroup
import com.example.orbitmusic.ui.theme.orbit.SettingsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val themeOptions = listOf(
    ThemeMode.System to R.string.settings_theme_system,
    ThemeMode.Light to R.string.settings_theme_light,
    ThemeMode.Dark to R.string.settings_theme_dark,
)

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenDesignSystem: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Artwork image cache (Coil). The YouTube data cache is managed by its own TTLs.
    var imageCacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        imageCacheBytes = withContext(Dispatchers.IO) { SingletonImageLoader.get(context).diskCache?.size }
    }

    SettingsContent(
        state = state,
        contentPadding = contentPadding,
        imageCacheSize = imageCacheBytes?.let { Formatter.formatShortFileSize(context, it) },
        callbacks = SettingsCallbacks(
            onThemeSelected = viewModel::onThemeSelected,
            onClearImageCache = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val loader = SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                    }
                    imageCacheBytes = 0L
                }
            },
            onClearHistory = viewModel::onClearHistory,
            onRequestSignOut = viewModel::onRequestSignOut,
            onOpenDesignSystem = onOpenDesignSystem,
        ),
    )
    state.pendingConfirmation?.let { kind ->
        SignOutDialog(
            kind = kind,
            onConfirm = viewModel::onConfirmSignOut,
            onDismiss = viewModel::onDismissConfirmation,
        )
    }
}

internal class SettingsCallbacks(
    val onThemeSelected: (ThemeMode) -> Unit,
    val onClearImageCache: () -> Unit,
    val onClearHistory: () -> Unit,
    val onRequestSignOut: (SignOutConfirmation) -> Unit,
    val onOpenDesignSystem: () -> Unit,
)

/**
 * Account card, theme segmented control, "Playback" and "Storage and about" groups, then
 * Sign out as an accent text button.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    contentPadding: PaddingValues,
    imageCacheSize: String?,
    callbacks: SettingsCallbacks,
) {
    val colors = OrbitTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title))
        Column(
            modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.user?.let { AccountCard(user = it, canEditPlaylists = state.canEditPlaylists) }

            Section(R.string.settings_section_appearance) {
                OrbitSegmented(
                    options = themeOptions.map { stringResource(it.second) },
                    selectedIndex = themeOptions.indexOfFirst { it.first == state.themeMode }.coerceAtLeast(0),
                    onSelect = { callbacks.onThemeSelected(themeOptions[it].first) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(R.string.settings_section_playback) {
                SettingsGroup(
                    rows = listOf {
                        SettingsRow(
                            icon = Icons.Rounded.GraphicEq,
                            iconTileColor = BrandColors.Supporting[2],
                            title = stringResource(R.string.settings_audio_quality),
                            trailing = {
                                Text(
                                    stringResource(R.string.settings_audio_quality_value),
                                    style = OrbitTheme.type.secondary,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            },
                        )
                    },
                )
            }

            Section(R.string.settings_section_storage_about) {
                SettingsGroup(
                    rows = buildList {
                        add {
                            SettingsRow(
                                icon = Icons.Rounded.Image,
                                iconTileColor = BrandColors.Supporting[4],
                                title = stringResource(R.string.settings_clear_image_cache),
                                subtitle = imageCacheSize?.let { stringResource(R.string.settings_cache_size, it) },
                                onClick = callbacks.onClearImageCache,
                                trailing = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = colors.textSecondary) },
                            )
                        }
                        add {
                            SettingsRow(
                                icon = Icons.Rounded.History,
                                iconTileColor = BrandColors.Supporting[0],
                                title = stringResource(R.string.library_clear_history),
                                subtitle = if (state.historyCleared) stringResource(R.string.settings_history_cleared) else null,
                                onClick = callbacks.onClearHistory,
                                trailing = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = colors.textSecondary) },
                            )
                        }
                        if (BuildConfig.DEBUG) {
                            add {
                                SettingsRow(
                                    icon = Icons.Rounded.Palette,
                                    iconTileColor = BrandColors.Violet,
                                    title = stringResource(R.string.settings_design_system),
                                    subtitle = stringResource(R.string.settings_design_system_body),
                                    onClick = callbacks.onOpenDesignSystem,
                                )
                            }
                        }
                        add {
                            SettingsRow(
                                icon = Icons.Rounded.Info,
                                iconTileColor = BrandColors.Supporting[6],
                                title = stringResource(R.string.settings_about),
                                subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            )
                        }
                    },
                )
            }

            if (state.user != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    OrbitTextButton(
                        text = stringResource(R.string.settings_sign_out),
                        onClick = { if (!state.isSigningOut) callbacks.onRequestSignOut(SignOutConfirmation.SignOut) },
                    )
                    OrbitTextButton(
                        text = stringResource(R.string.settings_disconnect),
                        onClick = { if (!state.isSigningOut) callbacks.onRequestSignOut(SignOutConfirmation.Disconnect) },
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(title),
            style = OrbitTheme.type.smallLabel,
            color = OrbitTheme.colors.textSecondary,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { heading() },
        )
        content()
    }
}

/** Gradient avatar, name, email and the current YouTube access level. */
@Composable
private fun AccountCard(user: UserProfile, canEditPlaylists: Boolean) {
    val colors = OrbitTheme.colors
    OrbitSurface(modifier = Modifier.fillMaxWidth(), shape = OrbitShapes.GroupedList) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(name = user.displayName ?: user.email, photoUrl = user.photoUrl, size = 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName ?: user.firstName, style = OrbitTheme.type.sectionTitle, color = colors.textPrimary)
                Text(user.email, style = OrbitTheme.type.secondary, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (canEditPlaylists) R.string.settings_youtube_access_edit else R.string.settings_youtube_access_read,
                        ),
                        style = OrbitTheme.type.smallLabel,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignOutDialog(
    kind: SignOutConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrbitTheme.colors
    val (title, body) = when (kind) {
        SignOutConfirmation.SignOut ->
            R.string.settings_sign_out_confirm_title to R.string.settings_sign_out_confirm_body
        SignOutConfirmation.Disconnect ->
            R.string.settings_disconnect_confirm_title to R.string.settings_disconnect_confirm_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = OrbitShapes.GroupedList,
        title = { Text(stringResource(title), style = OrbitTheme.type.sectionTitle) },
        text = { Text(stringResource(body), style = OrbitTheme.type.body) },
        confirmButton = { OrbitTextButton(stringResource(R.string.settings_confirm), onConfirm) },
        dismissButton = { OrbitTextButton(stringResource(R.string.settings_cancel), onDismiss, color = colors.textSecondary) },
    )
}

internal val noSettingsCallbacks = SettingsCallbacks({}, {}, {}, {}, {})

@OrbitPreviews
@Composable
private fun SettingsPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        SettingsContent(
            state = SettingsUiState(
                user = UserProfile("1", "nova@example.com", "Nova Star", "Nova", null),
                themeMode = ThemeMode.Dark,
            ),
            contentPadding = PaddingValues(),
            imageCacheSize = "24 MB",
            callbacks = noSettingsCallbacks,
        )
    }
}
