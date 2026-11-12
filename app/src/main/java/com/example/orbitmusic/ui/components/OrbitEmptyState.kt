package com.example.orbitmusic.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.ui.common.messageRes
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.OrbitWhiteButton
import com.example.orbitmusic.ui.theme.orbit.meshGradient

/**
 * Empty / offline / quota state: a 120dp mesh-gradient circle with an icon, a dashed
 * orbit ring around it, a title, one line of help text and one white action button.
 */
@Composable
fun OrbitEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = OrbitTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
            val ringColor = colors.outline
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2f - 2.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 7.dp.toPx())),
                    ),
                )
                drawCircle(
                    brush = BrandColors.Gradient,
                    radius = 5.dp.toPx(),
                    center = center.copy(y = 2.dp.toPx() + 3.dp.toPx()),
                )
            }
            Box(
                Modifier
                    .size(120.dp)
                    .clip(OrbitShapes.Circle)
                    .meshGradient(BrandColors.Violet, listOf(BrandColors.Rose, BrandColors.Amber), seed = title.hashCode()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = OnColor.Primary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = OrbitTheme.type.sectionTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(body, style = OrbitTheme.type.secondary, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OrbitWhiteButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * Full-screen state for a failure with nothing cached to show: offline, quota exceeded, or
 * anything else, each with its own icon and title, the error's help text and a Retry button.
 */
@Composable
fun OrbitErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes fallbackTitle: Int = R.string.state_error_title,
) {
    val (icon, title) = when (error) {
        AppError.NoInternet -> Icons.Rounded.CloudOff to R.string.state_offline_title
        AppError.QuotaExceeded -> Icons.Rounded.HourglassTop to R.string.state_quota_title
        else -> Icons.Rounded.ErrorOutline to fallbackTitle
    }
    OrbitEmptyState(
        icon = icon,
        title = stringResource(title),
        body = stringResource(
            when (error) {
                AppError.QuotaExceeded -> R.string.state_quota_body
                AppError.NoInternet -> R.string.state_offline_body
                else -> error.messageRes()
            },
        ),
        modifier = modifier,
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRetry,
    )
}

@OrbitPreviews
@Composable
private fun OrbitErrorStatePreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.background(OrbitTheme.colors.background)) {
            OrbitErrorState(error = AppError.QuotaExceeded, onRetry = {})
        }
    }
}

@OrbitPreviews
@Composable
private fun OrbitEmptyStatePreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.background(OrbitTheme.colors.background)) {
            OrbitEmptyState(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(R.string.error_no_internet),
                body = stringResource(R.string.offline_banner),
                actionLabel = stringResource(R.string.action_retry),
                onAction = {},
            )
        }
    }
}
