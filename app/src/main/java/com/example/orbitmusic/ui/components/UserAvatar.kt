package com.example.orbitmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme

/** Circular avatar: profile photo over a brand-gradient circle with the initial. */
@Composable
fun UserAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Box(
        modifier = modifier
            .size(if (onClick != null) maxOf(size, OrbitDimens.MinTouchTarget) else size)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(OrbitShapes.Circle)
                .background(BrandColors.Gradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercase().orEmpty(),
                style = OrbitTheme.type.button.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold),
                color = BrandColors.OnBrand,
            )
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = stringResource(R.string.cd_profile_photo, name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(OrbitShapes.Circle),
                )
            }
        }
    }
}

@OrbitPreviews
@Composable
private fun UserAvatarPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.background(OrbitTheme.colors.background).padding(16.dp)) {
            UserAvatar(name = "Nova", photoUrl = null, size = 56.dp)
        }
    }
}
