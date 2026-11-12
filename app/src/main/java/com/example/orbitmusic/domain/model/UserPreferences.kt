package com.example.orbitmusic.domain.model

enum class ThemeMode { System, Light, Dark }

/** What the Now Playing screen shows in its main area. */
enum class NowPlayingView { Poster, Video, Lyrics }

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    /** Dark is the app's default look. */
    val themeMode: ThemeMode = ThemeMode.Dark,
    val nowPlayingView: NowPlayingView = NowPlayingView.Video,
)
