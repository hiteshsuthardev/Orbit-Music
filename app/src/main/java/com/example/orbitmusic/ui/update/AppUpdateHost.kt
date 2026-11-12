package com.example.orbitmusic.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.ui.common.ObserveAsEvents

/**
 * Wraps the app with the self-hosted update flow: checks on start / resume, shows the blocking
 * "Update required" screen instead of [content] when needed, or the optional update dialog on
 * top of it, and opens the system installer / settings screens the view model asks for.
 */
@Composable
fun AppUpdateHost(viewModel: UpdateViewModel, content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAppResumed() }
    ObserveAsEvents(viewModel.intents) { intent -> runCatching { context.startActivity(intent) } }

    val callbacks = remember(viewModel) {
        UpdateCallbacks(
            onUpdate = viewModel::startUpdate,
            onInstall = viewModel::install,
            onCancelDownload = viewModel::cancelDownload,
            onOpenInstallSettings = viewModel::openInstallPermissionSettings,
            onLater = viewModel::dismissOffer,
            onSkip = viewModel::skipOfferedVersion,
        )
    }

    val required = state.required
    if (required != null) {
        UpdateRequiredScreen(state = state, update = required, callbacks = callbacks)
    } else {
        content()
        state.offered?.let { UpdateDialog(state = state, update = it, callbacks = callbacks) }
    }
}
