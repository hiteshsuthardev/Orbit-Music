package com.example.orbitmusic.ui.navigation

import kotlinx.serialization.Serializable

// Root graph ------------------------------------------------------------------------

@Serializable
data object OnboardingRoute

@Serializable
data object LoginRoute

/** Hosts the bottom-navigation shell with its own nested NavHost. */
@Serializable
data object MainRoute

// Bottom-navigation tabs ------------------------------------------------------------

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data object LibraryRoute

/** OrbitNova AI chat, opened from the raised centre button of the bottom bar. */
@Serializable
data object OrbitAiRoute

/** Profile (account, appearance, storage, sign out), opened from the avatar on each tab. */
@Serializable
data object SettingsRoute

/** Playlist detail, pushed inside the tab shell (bottom bar stays visible). */
@Serializable
data class PlaylistDetailRoute(val playlistId: String)
