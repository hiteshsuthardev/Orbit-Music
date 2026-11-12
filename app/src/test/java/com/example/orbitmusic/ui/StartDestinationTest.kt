package com.example.orbitmusic.ui

import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.UserPreferences
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.domain.model.YouTubeAccess
import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {

    private val user = UserProfile("id", "nova@example.com", "Nova Star", "Nova", null)
    private val onboarded = UserPreferences(onboardingCompleted = true)

    @Test
    fun `first launch shows onboarding even when signed in`() {
        val auth = AuthState.SignedIn(user, YouTubeAccess.Granted, canEditPlaylists = false)
        assertEquals(StartDestination.Onboarding, resolveStartDestination(UserPreferences(), auth))
    }

    @Test
    fun `signed out user goes to login`() {
        assertEquals(StartDestination.Login, resolveStartDestination(onboarded, AuthState.SignedOut))
    }

    @Test
    fun `signed in without youtube access goes to login to reconnect`() {
        val auth = AuthState.SignedIn(user, YouTubeAccess.Required, canEditPlaylists = false)
        assertEquals(StartDestination.Login, resolveStartDestination(onboarded, auth))
    }

    @Test
    fun `signed in with youtube access goes to main`() {
        val auth = AuthState.SignedIn(user, YouTubeAccess.Granted, canEditPlaylists = false)
        assertEquals(StartDestination.Main, resolveStartDestination(onboarded, auth))
    }
}
