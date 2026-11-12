package com.example.orbitmusic.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfileAvatarViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
) : ViewModel() {
    val user: StateFlow<UserProfile?> = observeAuthState()
        .map { (it as? AuthState.SignedIn)?.user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Bottom-bar icon for Profile: the signed-in user's Google picture (or their gradient
 * initial), ringed in the accent colour when selected. Falls back to a person icon.
 * Decorative: the bar item's label ("Profile") describes it.
 */
@Composable
fun ProfileTabIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ProfileAvatarViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val colors = OrbitTheme.colors
    val profile = user
    if (profile == null) {
        Icon(
            imageVector = if (selected) Icons.Rounded.Person else Icons.Outlined.Person,
            contentDescription = null,
            tint = if (selected) colors.accentText else colors.textSecondary,
            modifier = modifier,
        )
        return
    }
    UserAvatar(
        name = profile.displayName ?: profile.email,
        photoUrl = profile.photoUrl,
        size = 25.dp,
        modifier = modifier
            .border(2.dp, if (selected) colors.accentText else Color.Transparent, OrbitShapes.Circle)
            .padding(if (selected) 2.dp else 0.dp),
    )
}
