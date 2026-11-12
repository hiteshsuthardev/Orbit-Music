package com.example.orbitmusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.orbitmusic.R
import com.example.orbitmusic.ui.theme.orbit.OrbitTypeScale

/**
 * Figtree (weights 400-900) via Google Fonts downloadable fonts. Falls back to the system
 * sans-serif if the font provider is unavailable.
 */
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val figtree = GoogleFont("Figtree")

val FigtreeFamily = FontFamily(
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    ).map { weight -> Font(googleFont = figtree, fontProvider = fontProvider, weight = weight) },
)

private fun style(
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = 0.sp,
    tabularNumbers: Boolean = false,
) = TextStyle(
    fontFamily = FigtreeFamily,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = if (tabularNumbers) "tnum" else null,
)

/** Named styles from the spec, plus a few for full-bleed screens. */
val OrbitTypeScaleDefault = OrbitTypeScale(
    screenTitle = style(FontWeight.ExtraBold, 32.sp, 38.sp, (-0.9).sp),
    sectionTitle = style(FontWeight.ExtraBold, 21.sp, 26.sp, (-0.3).sp),
    songTitle = style(FontWeight.SemiBold, 15.5.sp, 20.sp),
    secondary = style(FontWeight.Medium, 13.5.sp, 18.sp),
    smallLabel = style(FontWeight.Bold, 13.5.sp, 18.sp),
    tabLabel = style(FontWeight.SemiBold, 11.sp, 14.sp),
    duration = style(FontWeight.Medium, 13.5.sp, 18.sp, tabularNumbers = true),
    heroHeadline = style(FontWeight.Black, 48.sp, 52.sp, (-1.4).sp),
    onboardingTitle = style(FontWeight.ExtraBold, 36.sp, 40.sp, (-1).sp),
    nowPlayingTitle = style(FontWeight.ExtraBold, 26.sp, 32.sp, (-0.5).sp),
    nowPlayingArtist = style(FontWeight.Medium, 17.sp, 22.sp),
    button = style(FontWeight.Bold, 16.sp, 20.sp),
    body = style(FontWeight.Medium, 16.sp, 23.sp),
)

/** Material typography mapped onto the Figtree scale so stock components match. */
val OrbitTypography = Typography(
    displayLarge = OrbitTypeScaleDefault.heroHeadline,
    displayMedium = style(FontWeight.ExtraBold, 40.sp, 46.sp, (-1).sp),
    displaySmall = OrbitTypeScaleDefault.onboardingTitle,
    headlineLarge = OrbitTypeScaleDefault.screenTitle,
    headlineMedium = style(FontWeight.ExtraBold, 28.sp, 34.sp, (-0.6).sp),
    headlineSmall = OrbitTypeScaleDefault.nowPlayingTitle,
    titleLarge = OrbitTypeScaleDefault.sectionTitle,
    titleMedium = OrbitTypeScaleDefault.songTitle,
    titleSmall = style(FontWeight.SemiBold, 14.sp, 19.sp),
    bodyLarge = OrbitTypeScaleDefault.body,
    bodyMedium = OrbitTypeScaleDefault.secondary,
    bodySmall = style(FontWeight.Medium, 12.sp, 16.sp),
    labelLarge = OrbitTypeScaleDefault.smallLabel,
    labelMedium = style(FontWeight.SemiBold, 12.sp, 16.sp),
    labelSmall = OrbitTypeScaleDefault.tabLabel,
)
