package com.example.orbitmusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.PlaylistPrivacy
import com.example.orbitmusic.ui.common.messageRes
import com.example.orbitmusic.ui.components.orbit.OrbitLoader
import com.example.orbitmusic.ui.player.SheetTitle
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitSegmented
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSheet
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitWhiteButton

private val privacyOptions = listOf(
    PlaylistPrivacy.Private to R.string.create_playlist_private,
    PlaylistPrivacy.Unlisted to R.string.create_playlist_unlisted,
    PlaylistPrivacy.Public to R.string.create_playlist_public,
)

@Composable
fun CreatePlaylistSheet(
    state: CreatePlaylistUiState,
    onCreate: (String, PlaylistPrivacy) -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitSheet(onDismissRequest = onDismiss) {
        CreatePlaylistContent(state, onCreate)
    }
}

/** Title field, privacy segmented control, a note about the extra permission, and Create. */
@Composable
internal fun ColumnScope.CreatePlaylistContent(
    state: CreatePlaylistUiState,
    onCreate: (String, PlaylistPrivacy) -> Unit,
) {
    val colors = OrbitTheme.colors
    var title by rememberSaveable { mutableStateOf("") }
    var privacyIndex by rememberSaveable { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val canCreate = title.isNotBlank() && !state.isCreating
    val submit = { if (canCreate) onCreate(title, privacyOptions[privacyIndex].first) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SheetTitle(stringResource(R.string.create_playlist_title))
    Column(Modifier.padding(horizontal = OrbitDimens.ScreenPadding)) {
        val label = stringResource(R.string.create_playlist_name_hint)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(OrbitShapes.SearchField)
                .background(colors.surfaceRaised)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (title.isEmpty()) Text(label, style = OrbitTheme.type.body, color = colors.textSecondary)
            BasicTextField(
                value = title,
                onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                singleLine = true,
                enabled = !state.isCreating,
                textStyle = OrbitTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label },
            )
        }
        Spacer(Modifier.height(14.dp))
        OrbitSegmented(
            options = privacyOptions.map { stringResource(it.second) },
            selectedIndex = privacyIndex,
            onSelect = { privacyIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.error?.let { stringResource(it.messageRes()) } ?: stringResource(R.string.create_playlist_permission_note),
            style = OrbitTheme.type.secondary,
            color = if (state.error != null) colors.accentText else colors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        if (state.isCreating) {
            Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) { OrbitLoader(size = 40.dp) }
        } else {
            OrbitWhiteButton(
                text = stringResource(R.string.create_playlist_create),
                onClick = submit,
                leadingIcon = Icons.Rounded.Add,
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** YouTube's playlist title limit. */
private const val MAX_TITLE_LENGTH = 150

@OrbitPreviews
@Composable
private fun CreatePlaylistPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Column(Modifier.background(OrbitTheme.colors.surface)) {
            CreatePlaylistContent(state = CreatePlaylistUiState(error = AppError.PermissionRequired), onCreate = { _, _ -> })
        }
    }
}
