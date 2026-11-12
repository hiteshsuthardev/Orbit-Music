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

/** Design-system showcase, reachable from Settings in debug builds. */
@Serializable
data object DesignSystemRoute

// Bottom-navigation tabs ------------------------------------------------------------

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data object LibraryRoute

@Serializable
data object SettingsRoute

/** Playlist detail, pushed inside the tab shell (bottom bar stays visible). */
@Serializable
data class PlaylistDetailRoute(val playlistId: String)
