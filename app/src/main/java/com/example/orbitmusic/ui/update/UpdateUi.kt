package com.example.orbitmusic.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.orbitmusic.R
import com.example.orbitmusic.data.update.DownloadFailure
import com.example.orbitmusic.domain.update.AppUpdate
import com.example.orbitmusic.ui.common.findActivity
import com.example.orbitmusic.ui.components.orbit.LogoStyle
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.orbit.OrbitLogo
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitPrimaryButton
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import java.io.File

/** Callbacks shared by the dialog and the blocking screen. */
internal class UpdateCallbacks(
    val onUpdate: (AppUpdate) -> Unit,
    val onInstall: (File) -> Unit,
    val onCancelDownload: () -> Unit,
    val onOpenInstallSettings: () -> Unit,
    val onLater: () -> Unit,
    val onSkip: () -> Unit,
)

/** Optional update: version, notes, and Update / Later / Skip this version. */
@Composable
internal fun UpdateDialog(state: UpdateUiState, update: AppUpdate, callbacks: UpdateCallbacks) {
    val busy = state.download is DownloadState.Downloading || state.download is DownloadState.Verifying
    Dialog(
        onDismissRequest = { if (!busy) callbacks.onLater() },
        properties = DialogProperties(dismissOnClickOutside = !busy),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(OrbitShapes.HeroCard)
                .background(OrbitTheme.colors.surface)
                .padding(22.dp),
        ) {
            OrbitLogo(size = 48.dp, style = LogoStyle.Brand, contentDescription = null)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.update_available_title),
                style = OrbitTheme.type.sectionTitle,
                color = OrbitTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.update_version, update.versionName),
                style = OrbitTheme.type.smallLabel,
                color = OrbitTheme.colors.accentText,
            )
            ReleaseNotes(update.notes, Modifier.heightIn(max = 220.dp))
            Spacer(Modifier.height(16.dp))
            UpdateActions(state = state, update = update, callbacks = callbacks, optional = true)
        }
    }
}

/** Blocking screen: this version can no longer be used until it is updated. */
@Composable
internal fun UpdateRequiredScreen(state: UpdateUiState, update: AppUpdate, callbacks: UpdateCallbacks) {
    val activity = LocalContext.current.findActivity()
    // Back leaves the app instead of slipping past the screen.
    BackHandler { activity?.finish() }
    Box(
        Modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OrbitLogo(size = 96.dp, style = LogoStyle.Brand, contentDescription = null)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.update_required_title),
                style = OrbitTheme.type.screenTitle,
                color = OrbitTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.update_required_body, update.versionName),
                style = OrbitTheme.type.body,
                color = OrbitTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ReleaseNotes(update.notes)
            Spacer(Modifier.height(24.dp))
            UpdateActions(state = state, update = update, callbacks = callbacks, optional = false)
        }
    }
}

@Composable
private fun ReleaseNotes(notes: String, modifier: Modifier = Modifier) {
    if (notes.isBlank()) return
    Column(modifier.padding(top = 14.dp)) {
        Text(
            stringResource(R.string.update_whats_new),
            style = OrbitTheme.type.smallLabel,
            color = OrbitTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            notes,
            style = OrbitTheme.type.secondary,
            color = OrbitTheme.colors.textSecondary,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

/** Buttons and progress for every step: permission, download, verify, install, errors. */
@Composable
private fun UpdateActions(state: UpdateUiState, update: AppUpdate, callbacks: UpdateCallbacks, optional: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.needsInstallPermission -> {
                StatusText(stringResource(R.string.update_permission_explanation))
                OrbitPrimaryButton(
                    text = stringResource(R.string.update_open_settings),
                    onClick = callbacks.onOpenInstallSettings,
                    leadingIcon = Icons.Rounded.Settings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> when (val download = state.download) {
                DownloadState.Idle -> OrbitPrimaryButton(
                    text = stringResource(R.string.update_now),
                    onClick = { callbacks.onUpdate(update) },
                    leadingIcon = Icons.Rounded.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
                is DownloadState.Downloading -> {
                    DownloadProgress(download.fraction)
                    OrbitTextButton(stringResource(R.string.update_cancel), callbacks.onCancelDownload)
                }
                DownloadState.Verifying -> {
                    OrbitLoader(size = 36.dp)
                    StatusText(stringResource(R.string.update_verifying))
                }
                is DownloadState.ReadyToInstall -> {
                    StatusText(
                        stringResource(
                            if (download.installerWasClosed) R.string.update_install_not_finished else R.string.update_ready,
                        ),
                    )
                    OrbitPrimaryButton(
                        text = stringResource(R.string.update_install),
                        onClick = { callbacks.onInstall(download.file) },
                        leadingIcon = Icons.Rounded.InstallMobile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is DownloadState.Installing -> {
                    StatusText(stringResource(R.string.update_installer_open))
                    OrbitPrimaryButton(
                        text = stringResource(R.string.update_install),
                        onClick = { callbacks.onInstall(download.file) },
                        leadingIcon = Icons.Rounded.InstallMobile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is DownloadState.Failed -> {
                    StatusText(stringResource(download.reason.messageRes()), isError = true)
                    OrbitPrimaryButton(
                        text = stringResource(R.string.action_retry),
                        onClick = { callbacks.onUpdate(update) },
                        leadingIcon = Icons.Rounded.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        val busy = state.download is DownloadState.Downloading || state.download is DownloadState.Verifying
        if (optional && !busy) {
            OrbitTextButton(stringResource(R.string.update_later), callbacks.onLater)
            OrbitTextButton(stringResource(R.string.update_skip_version), callbacks.onSkip, color = OrbitTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun StatusText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = OrbitTheme.type.secondary,
        color = if (isError) OrbitTheme.colors.accentText else OrbitTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Brand-gradient progress bar with the percentage (or "Downloading…" while size is unknown). */
@Composable
private fun DownloadProgress(fraction: Float?) {
    val colors = OrbitTheme.colors
    val label = if (fraction != null) {
        stringResource(R.string.update_downloading_percent, (fraction * 100).toInt())
    } else {
        stringResource(R.string.update_downloading)
    }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(OrbitShapes.Pill)
                .background(colors.surfaceRaised)
                .semantics {
                    progressBarRangeInfo = if (fraction != null) {
                        ProgressBarRangeInfo(fraction, 0f..1f)
                    } else {
                        ProgressBarRangeInfo.Indeterminate
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction ?: INDETERMINATE_FILL)
                    .clip(OrbitShapes.Pill)
                    .background(BrandColors.Gradient),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = OrbitTheme.type.smallLabel, color = colors.textSecondary)
    }
}

private const val INDETERMINATE_FILL = 0.15f

private fun DownloadFailure.messageRes(): Int = when (this) {
    DownloadFailure.NoInternet -> R.string.update_error_no_internet
    DownloadFailure.LowStorage -> R.string.update_error_low_storage
    DownloadFailure.Network -> R.string.update_error_download
    DownloadFailure.VerificationFailed -> R.string.update_error_verification
}

internal val noUpdateCallbacks = UpdateCallbacks({}, {}, {}, {}, {}, {})

private val previewUpdate = AppUpdate(
    versionCode = 9,
    versionName = "1.7.0",
    apkUrl = "https://example.com/orbit-music.apk",
    sha256 = "0".repeat(64),
    minVersionCode = 1,
    forceUpdate = false,
    notes = "• OrbitNova AI remembers your favourite moods\n• Faster search\n• Bug fixes",
)

@OrbitPreviews
@Composable
private fun UpdateRequiredPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        UpdateRequiredScreen(
            state = UpdateUiState(download = DownloadState.Downloading(0.42f)),
            update = previewUpdate,
            callbacks = noUpdateCallbacks,
        )
    }
}
