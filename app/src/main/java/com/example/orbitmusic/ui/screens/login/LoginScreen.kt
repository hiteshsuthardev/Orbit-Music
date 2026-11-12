package com.example.orbitmusic.ui.screens.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.ui.common.ObserveAsEvents
import com.example.orbitmusic.ui.common.findActivity
import com.example.orbitmusic.ui.common.messageRes
import com.example.orbitmusic.ui.components.UserAvatar
import com.example.orbitmusic.ui.components.orbit.LogoStyle
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.components.orbit.OrbitLogo
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitIconButton
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitWhiteButton
import com.example.orbitmusic.ui.theme.orbit.brandMesh

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.resultCode, result.data) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LoginEvent.LaunchConsent -> consentLauncher.launch(
                IntentSenderRequest.Builder(event.pendingIntent.intentSender).build(),
            )
            LoginEvent.SignedIn -> onSignedIn()
        }
    }

    LoginContent(
        state = state,
        onContinue = { viewModel.onContinueWithGoogle(context.findActivity() ?: context) },
        onUseDifferentAccount = viewModel::onUseDifferentAccount,
        onDismissError = viewModel::onDismissError,
    )
}

/**
 * Full-bleed brand mesh (same as Splash), white logo top-left, big headline, and the
 * white "Continue with Google" button at the bottom with the read-only privacy note.
 * Text on the mesh always uses the white on-colour tokens, in both themes.
 */
@Composable
internal fun LoginContent(
    state: LoginUiState,
    onContinue: () -> Unit,
    onUseDifferentAccount: () -> Unit,
    onDismissError: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .brandMesh()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // At least screen-tall so the CTA sits at the bottom, but scrollable on small screens.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            LoginHeadline()
            Column(Modifier.padding(top = 32.dp)) {
                if (!state.isLoading) state.reconnectUser?.let { ReconnectCard(it) }
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    state.error?.let { ErrorBanner(it, onDismissError) }
                }
                LoginAction(state, onContinue, onUseDifferentAccount)
                Spacer(Modifier.height(16.dp))
                PrivacyNotes()
            }
        }
    }
}

@Composable
private fun LoginHeadline() {
    Column {
        OrbitLogo(size = 56.dp, style = LogoStyle.White)
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.login_headline),
            style = OrbitTheme.type.heroHeadline,
            color = OnColor.Primary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.login_tagline),
            style = OrbitTheme.type.body,
            color = OnColor.Secondary,
        )
    }
}

@Composable
private fun LoginAction(state: LoginUiState, onContinue: () -> Unit, onUseDifferentAccount: () -> Unit) {
    Column {
        AnimatedContent(
            targetState = state.isLoading,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = Alignment.Center,
            label = "loginAction",
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        ) { loading ->
            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OrbitLoader(ringColor = OnColor.ChipFill, dotColor = OnColor.Primary)
                }
            } else {
                val reconnect = state.reconnectUser != null
                OrbitWhiteButton(
                    text = stringResource(if (reconnect) R.string.login_reconnect_button else R.string.login_continue_with_google),
                    onClick = onContinue,
                    leadingIcon = if (reconnect) Icons.Rounded.Refresh else Icons.Rounded.AccountCircle,
                    onColorful = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.reconnectUser != null && !state.isLoading) {
            OrbitTextButton(
                text = stringResource(R.string.login_use_another_account),
                onClick = onUseDifferentAccount,
                color = OnColor.Primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PrivacyNotes() {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = OnColor.Secondary,
                modifier = Modifier.padding(top = 2.dp).size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.login_permission_note), style = OrbitTheme.type.secondary, color = OnColor.Secondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.login_terms_note),
            style = OrbitTheme.type.secondary,
            color = OnColor.Secondary,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun ReconnectCard(user: UserProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(OrbitShapes.GroupedList)
            .background(OnColor.ChipFill)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(name = user.displayName ?: user.email, photoUrl = user.photoUrl, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.login_reconnect_title), style = OrbitTheme.type.songTitle, color = OnColor.Primary)
            Text(stringResource(R.string.login_signed_in_as, user.email), style = OrbitTheme.type.secondary, color = OnColor.Secondary)
            Text(stringResource(R.string.login_reconnect_body), style = OrbitTheme.type.secondary, color = OnColor.Secondary)
        }
    }
}

@Composable
private fun ErrorBanner(error: AppError, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(OrbitShapes.GroupedList)
            .background(OnColor.ChipFill)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = OnColor.Primary)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(error.messageRes()),
            style = OrbitTheme.type.secondary,
            color = OnColor.Primary,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        OrbitIconButton(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.cd_dismiss_error),
            onClick = onDismiss,
            tint = OnColor.Primary,
        )
    }
}

@Preview(name = "Login", showBackground = true, backgroundColor = 0xFF1A0820, heightDp = 800)
@Composable
private fun LoginPreview() {
    OrbitMusicTheme {
        LoginContent(state = LoginUiState(error = AppError.NoInternet), onContinue = {}, onUseDifferentAccount = {}, onDismissError = {})
    }
}
