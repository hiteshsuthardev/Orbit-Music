package com.example.orbitmusic.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.common.ObserveAsEvents
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitPreviews
import com.example.orbitmusic.ui.theme.orbit.OrbitPrimaryButton
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitTextButton
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val pillLabel: Int,
    val style: (pillLabel: String) -> OnboardingPanelStyle,
)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_title_1, R.string.onboarding_body_1, R.string.onboarding_pill_1) { pill ->
        OnboardingPanelStyle(
            base = BrandColors.Violet,
            blobs = listOf(BrandColors.Rose, BrandColors.Amber),
            centerIcon = Icons.Rounded.MusicNote,
            buttonIcon = Icons.Rounded.SkipNext,
            pillIcon = Icons.Rounded.Favorite,
            pillLabel = pill,
            coverKey = "onboarding-1",
        )
    },
    OnboardingPage(R.string.onboarding_title_2, R.string.onboarding_body_2, R.string.onboarding_pill_2) { pill ->
        OnboardingPanelStyle(
            base = BrandColors.Supporting[2],
            blobs = listOf(BrandColors.Supporting[3], BrandColors.Supporting[13]),
            centerIcon = Icons.AutoMirrored.Rounded.QueueMusic,
            buttonIcon = Icons.Rounded.Search,
            pillIcon = Icons.Rounded.Shuffle,
            pillLabel = pill,
            coverKey = "onboarding-2",
        )
    },
    OnboardingPage(R.string.onboarding_title_3, R.string.onboarding_body_3, R.string.onboarding_pill_3) { pill ->
        OnboardingPanelStyle(
            base = BrandColors.Rose,
            blobs = listOf(BrandColors.Amber, BrandColors.Supporting[0]),
            centerIcon = Icons.Rounded.Headphones,
            buttonIcon = Icons.Rounded.LibraryMusic,
            pillIcon = Icons.Rounded.Bedtime,
            pillLabel = pill,
            coverKey = "onboarding-3",
        )
    },
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OnboardingEvent.Finished -> onFinished()
        }
    }
    OnboardingContent(onFinish = viewModel::finish)
}

@Composable
internal fun OnboardingContent(onFinish: () -> Unit) {
    val colors = OrbitTheme.colors
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) { index ->
            OnboardingPageContent(page = pages[index], index = index, pagerState = pagerState, onSkip = onFinish)
        }

        PagerDots(
            pagerState = pagerState,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
        )

        AnimatedContent(
            targetState = isLastPage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboardingCta",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitDimens.ScreenPadding)
                .padding(bottom = 16.dp),
        ) { last ->
            OrbitPrimaryButton(
                text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next),
                onClick = {
                    if (last) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, index: Int, pagerState: PagerState, onSkip: () -> Unit) {
    val colors = OrbitTheme.colors
    val pillLabel = stringResource(page.pillLabel)
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = pagerState.getOffsetDistanceInPages(index).absoluteValue.coerceIn(0f, 1f)
                alpha = 1f - offset * 0.5f
            },
    ) {
        OnboardingPanel(
            style = page.style(pillLabel),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            overlay = {
                OrbitTextButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = onSkip,
                    color = OnColor.Primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(page.title),
            style = OrbitTheme.type.onboardingTitle,
            color = colors.textPrimary,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(page.body),
            style = OrbitTheme.type.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

/** Pager dots: inactive dots in the outline colour, the active one a short bar. */
@Composable
private fun PagerDots(pagerState: PagerState, modifier: Modifier = Modifier) {
    val colors = OrbitTheme.colors
    val description = stringResource(R.string.onboarding_page_indicator, pagerState.currentPage + 1, pagerState.pageCount)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pagerState.pageCount) { index ->
            val selected = index == pagerState.currentPage
            val width by animateDpAsState(
                if (selected) 22.dp else 8.dp,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth",
            )
            val color by animateColorAsState(if (selected) colors.textPrimary else colors.outline, label = "dotColor")
            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(OrbitShapes.Pill)
                    .background(color),
            )
        }
    }
}

@OrbitPreviews
@Composable
private fun OnboardingPreview() {
    OrbitMusicTheme(darkTheme = isSystemInDarkTheme()) {
        OnboardingContent(onFinish = {})
    }
}
