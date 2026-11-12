package com.example.orbitmusic

import kotlinx.coroutines.delay
import com.example.orbitmusic.ui.screens.splash.SplashScreen
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.Crossfade
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbitmusic.domain.model.ThemeMode
import com.example.orbitmusic.ui.MainViewModel
import com.example.orbitmusic.ui.OrbitApp
import com.example.orbitmusic.ui.theme.OrbitMusicTheme
import com.example.orbitmusic.ui.update.AppUpdateHost
import com.example.orbitmusic.ui.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Activity-scoped so Profile's "Check for updates" shares it (see SettingsScreen). */
    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android's own splash (plum + white logo) hands over to the Compose splash, whose first
        // frame is identical, so only one splash is ever seen; it then animates while
        // preferences and the encrypted session load.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            // Show the branded splash at least briefly (once per launch, not on rotation).
            var splashShown by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(SPLASH_MIN_MILLIS)
                splashShown = true
            }

            OrbitMusicTheme(darkTheme = dark) {
                Crossfade(targetState = state.isReady && splashShown, label = "splash") { ready ->
                    if (ready) {
                        AppUpdateHost(updateViewModel) {
                            OrbitApp(
                                startDestination = state.startDestination,
                                authState = state.authState,
                            )
                        }
                    } else {
                        SplashScreen()
                    }
                }
            }
        }
    }

    private companion object {
        /** Long enough for the splash animation (logo, orbit ring, name) to finish. */
        const val SPLASH_MIN_MILLIS = 1_600L
    }
}
