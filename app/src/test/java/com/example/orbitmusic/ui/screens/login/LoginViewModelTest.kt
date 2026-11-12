package com.example.orbitmusic.ui.screens.login

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import app.cash.turbine.test
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.AuthorizationOutcome
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.usecase.auth.CompleteAuthorizationUseCase
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.domain.usecase.auth.SignInAndAuthorizeUseCase
import com.example.orbitmusic.domain.usecase.auth.SignOutUseCase
import com.example.orbitmusic.fakes.FakeAuthRepository
import com.example.orbitmusic.fakes.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val context: Context = mockk(relaxed = true)

    private fun viewModel() = LoginViewModel(
        observeAuthState = ObserveAuthStateUseCase(repository),
        signInAndAuthorize = SignInAndAuthorizeUseCase(repository),
        completeAuthorization = CompleteAuthorizationUseCase(repository),
        signOut = SignOutUseCase(repository),
    )

    @Test
    fun `granted authorization emits SignedIn`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onContinueWithGoogle(context)
            assertEquals(LoginEvent.SignedIn, awaitItem())
        }
        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `consent required launches consent and denial shows error`() = runTest {
        val pendingIntent: PendingIntent = mockk()
        repository.authorizeOutcome = AuthorizationOutcome.NeedsConsent(pendingIntent)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }

        vm.events.test {
            vm.onContinueWithGoogle(context)
            assertEquals(LoginEvent.LaunchConsent(pendingIntent), awaitItem())
        }
        vm.onConsentResult(Activity.RESULT_CANCELED, null)

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(AppError.ConsentDenied, state.error)
    }

    @Test
    fun `consent approved completes authorization`() = runTest {
        repository.authorizeOutcome = AuthorizationOutcome.NeedsConsent(mockk())
        val vm = viewModel()
        vm.events.test {
            vm.onContinueWithGoogle(context)
            assertTrue(awaitItem() is LoginEvent.LaunchConsent)
            vm.onConsentResult(Activity.RESULT_OK, mockk())
            assertEquals(LoginEvent.SignedIn, awaitItem())
        }
    }

    @Test
    fun `cancelling the account picker shows no error`() = runTest {
        repository.signInResult = Resource.Error(AppError.SignInCancelled)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }

        vm.onContinueWithGoogle(context)

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `reconnect skips the account picker`() = runTest {
        repository.state.value = com.example.orbitmusic.domain.model.AuthState.SignedIn(
            repository.user,
            com.example.orbitmusic.domain.model.YouTubeAccess.Required,
            canEditPlaylists = false,
        )
        val vm = viewModel()
        vm.events.test {
            vm.onContinueWithGoogle(context)
            assertEquals(LoginEvent.SignedIn, awaitItem())
        }
        assertEquals(0, repository.signInCalls)
    }
}
