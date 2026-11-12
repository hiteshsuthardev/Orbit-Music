package com.example.orbitmusic.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.data.update.ApkDownloader
import com.example.orbitmusic.data.update.ApkInstaller
import com.example.orbitmusic.data.update.DownloadEvent
import com.example.orbitmusic.data.update.DownloadFailure
import com.example.orbitmusic.domain.update.AppUpdate
import com.example.orbitmusic.domain.update.RemoteConfigRepository
import com.example.orbitmusic.domain.update.UpdatePolicy
import com.example.orbitmusic.domain.update.UpdateRepository
import com.example.orbitmusic.domain.update.UpdateStatus
import com.example.orbitmusic.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val fraction: Float?) : DownloadState
    data object Verifying : DownloadState

    /** Verified and waiting to be installed (also after the installer was backed out of). */
    data class ReadyToInstall(val file: File, val installerWasClosed: Boolean = false) : DownloadState

    /** The system installer is open. */
    data class Installing(val file: File) : DownloadState
    data class Failed(val reason: DownloadFailure) : DownloadState
}

enum class ManualCheck { Idle, Checking, UpToDate, Failed }

data class UpdateUiState(
    /** Blocking "Update required" screen. */
    val required: AppUpdate? = null,
    /** Optional update dialog. */
    val offered: AppUpdate? = null,
    val download: DownloadState = DownloadState.Idle,
    /** Android needs the user to allow installs from Orbit Music first. */
    val needsInstallPermission: Boolean = false,
    val manualCheck: ManualCheck = ManualCheck.Idle,
)

/**
 * Self-hosted updates (no Google Play), shared by the whole app (activity-scoped):
 * checks version.json on start and resume (at most every 6 hours, failures ignored) or when
 * asked from Profile, shows the optional dialog or the blocking screen, downloads and verifies
 * the APK, and opens the system installer. Remote config is refreshed alongside.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val remoteConfig: RemoteConfigRepository,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val time: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _intents = Channel<Intent>(Channel.BUFFERED)

    /** System screens to open (installer, "install unknown apps" settings). */
    val intents: Flow<Intent> = _intents.receiveAsFlow()

    /** Main-thread only (viewModelScope), so a plain flag is enough. */
    private var isChecking = false
    private var downloading: Job? = null

    init {
        // Old APKs are useless once their version (or a newer one) is installed.
        viewModelScope.launch { downloader.cleanUp(BuildConfig.VERSION_CODE) }
    }

    /** App started or came back to the foreground. */
    fun onAppResumed() {
        val current = _state.value
        // Back from the installer but still on the old version: it was cancelled.
        (current.download as? DownloadState.Installing)?.let { installing ->
            _state.update { it.copy(download = DownloadState.ReadyToInstall(installing.file, installerWasClosed = true)) }
        }
        // Back from "install unknown apps" settings with permission granted: carry on.
        if (current.needsInstallPermission && installer.canInstall()) {
            _state.update { it.copy(needsInstallPermission = false) }
            (current.required ?: current.offered)?.let(::startUpdate)
        }
        viewModelScope.launch {
            if (UpdatePolicy.isAutomaticCheckDue(updates.history.first(), time.currentTimeMillis())) check(manual = false)
        }
    }

    /** "Check for updates" in Profile. */
    fun checkNow() {
        _state.update { it.copy(manualCheck = ManualCheck.Checking) }
        viewModelScope.launch { check(manual = true) }
    }

    private suspend fun check(manual: Boolean) {
        if (isChecking) return
        isChecking = true
        try {
            runCheck(manual)
        } finally {
            isChecking = false
        }
    }

    private suspend fun runCheck(requestedManually: Boolean) {
        viewModelScope.launch { remoteConfig.refresh() }
        val update = updates.fetchLatest()
        // Also report to a "Check for updates" tap made while this check was running.
        val manual = requestedManually || _state.value.manualCheck == ManualCheck.Checking
        val now = time.currentTimeMillis()
        if (update == null) {
            if (manual) _state.update { it.copy(manualCheck = ManualCheck.Failed) }
            return
        }
        updates.recordCheck(now)
        when (val status = UpdatePolicy.status(update, BuildConfig.VERSION_CODE)) {
            UpdateStatus.UpToDate -> _state.update { it.copy(manualCheck = if (manual) ManualCheck.UpToDate else it.manualCheck) }
            is UpdateStatus.Required -> _state.update { it.copy(required = status.update, manualCheck = ManualCheck.Idle) }
            is UpdateStatus.Available -> {
                val history = updates.history.first()
                if (UpdatePolicy.shouldPrompt(status.update, history, now, manual)) {
                    if (!manual) updates.recordPrompt(status.update.versionCode, now)
                    _state.update { it.copy(offered = status.update, manualCheck = ManualCheck.Idle) }
                } else {
                    _state.update { it.copy(manualCheck = ManualCheck.Idle) }
                }
            }
        }
    }

    fun clearManualCheck() = _state.update { it.copy(manualCheck = ManualCheck.Idle) }

    /** Update / Try again. Asks for install permission first if Android needs it. */
    fun startUpdate(update: AppUpdate) {
        if (!installer.canInstall()) {
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }
        downloading?.cancel()
        downloading = viewModelScope.launch {
            downloader.download(update).collect { event ->
                val next = when (event) {
                    is DownloadEvent.Progress -> DownloadState.Downloading(event.fraction)
                    DownloadEvent.Verifying -> DownloadState.Verifying
                    is DownloadEvent.Failed -> DownloadState.Failed(event.reason)
                    is DownloadEvent.Ready -> DownloadState.ReadyToInstall(event.file)
                }
                _state.update { it.copy(download = next) }
                if (event is DownloadEvent.Ready) install(event.file)
            }
        }
    }

    fun install(file: File) {
        _state.update { it.copy(download = DownloadState.Installing(file)) }
        _intents.trySend(installer.installIntent(file))
    }

    fun openInstallPermissionSettings() {
        _intents.trySend(installer.allowInstallsIntent())
    }

    fun cancelDownload() {
        downloading?.cancel()
        _state.update { it.copy(download = DownloadState.Idle) }
    }

    /** "Later": close the optional dialog (it may come back tomorrow). */
    fun dismissOffer() {
        downloading?.cancel()
        _state.update { it.copy(offered = null, download = DownloadState.Idle, needsInstallPermission = false) }
    }

    fun skipOfferedVersion() {
        val update = _state.value.offered ?: return
        viewModelScope.launch { updates.skipVersion(update.versionCode) }
        dismissOffer()
    }
}
