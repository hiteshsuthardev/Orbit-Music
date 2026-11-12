package com.example.orbitmusic.ui.theme.orbit

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

/** Named text styles from the spec. Values come from ui/theme/Type.kt. */
@Immutable
data class OrbitTypeScale(
    /** 32sp ExtraBold, -0.9sp tracking. */
    val screenTitle: TextStyle = TextStyle.Default,
    /** 21sp ExtraBold. */
    val sectionTitle: TextStyle = TextStyle.Default,
    /** 15.5sp SemiBold. */
    val songTitle: TextStyle = TextStyle.Default,
    /** 13.5sp Medium. */
    val secondary: TextStyle = TextStyle.Default,
    /** 13.5sp Bold, used in textSecondary. */
    val smallLabel: TextStyle = TextStyle.Default,
    /** 11sp SemiBold. */
    val tabLabel: TextStyle = TextStyle.Default,
    /** Durations: tabular numbers. */
    val duration: TextStyle = TextStyle.Default,
    /** 48sp Login headline. */
    val heroHeadline: TextStyle = TextStyle.Default,
    /** 36sp onboarding title. */
    val onboardingTitle: TextStyle = TextStyle.Default,
    /** 26sp Now Playing title. */
    val nowPlayingTitle: TextStyle = TextStyle.Default,
    /** 17sp Now Playing artist. */
    val nowPlayingArtist: TextStyle = TextStyle.Default,
    val button: TextStyle = TextStyle.Default,
    val body: TextStyle = TextStyle.Default,
)
