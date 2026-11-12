package com.example.orbitmusic.ui

import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.ui.navigation.LoginRoute
import com.example.orbitmusic.ui.navigation.MainRoute
import com.example.orbitmusic.ui.navigation.OnboardingRoute
import com.example.orbitmusic.ui.screens.login.LoginScreen
import com.example.orbitmusic.ui.screens.main.MainScreen
import com.example.orbitmusic.ui.screens.onboarding.OnboardingScreen

private val slideSpec = spring<IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

/**
 * Root navigation graph. Rendered only once [AppUiState.isReady] is true, so the start
 * destination is known (the splash screen covers the loading time).
 */
@Composable
fun OrbitApp(
    startDestination: StartDestination,
    authState: AuthState,
) {
    val navController = rememberNavController()
    val start: Any = remember {
        when (startDestination) {
            StartDestination.Onboarding -> OnboardingRoute
            StartDestination.Login -> LoginRoute
            StartDestination.Main -> MainRoute
        }
    }

    // Session guard: signing out, or YouTube access being revoked, returns to Login.
    LaunchedEffect(authState) {
        val lostAccess = authState is AuthState.SignedOut ||
            (authState is AuthState.SignedIn && !authState.hasYouTubeAccess())
        val destination = navController.currentDestination
        val insideApp = destination?.hasRoute<MainRoute>() == true
        if (lostAccess && insideApp) {
            navController.navigate(LoginRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = start,
        modifier = Modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.background),
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec) + fadeIn()
        },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec) + fadeOut()
        },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(LoginRoute) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<LoginRoute> {
            LoginScreen(
                onSignedIn = {
                    navController.navigate(MainRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<MainRoute> { MainScreen() }
    }
}
