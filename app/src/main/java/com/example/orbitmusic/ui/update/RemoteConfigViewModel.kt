package com.example.orbitmusic.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.update.RemoteConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Remote switches for the main shell, from the cached remote config (so they also apply
 * offline). The config is refreshed together with the update check.
 */
@HiltViewModel
class RemoteConfigViewModel @Inject constructor(
    private val repository: RemoteConfigRepository,
) : ViewModel() {

    /** OrbitNova AI can be switched off remotely (hides its button). */
    val aiEnabled: StateFlow<Boolean> = repository.config
        .map { it.featureAiEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The announcement to show on Home, unless the user already closed this one. */
    val announcement: StateFlow<String?> = combine(repository.config, repository.dismissedAnnouncement) { config, dismissed ->
        config.announcementText?.takeIf { it != dismissed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissAnnouncement() {
        val text = announcement.value ?: return
        viewModelScope.launch { repository.dismissAnnouncement(text) }
    }
}
