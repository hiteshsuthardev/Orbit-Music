package com.example.orbitmusic.data.remote.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.orbitmusic.BuildConfig
import com.example.orbitmusic.domain.model.AppError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown by the auth data sources; carries the user-facing [AppError]. */
class AuthException(val error: AppError, cause: Throwable? = null) : Exception(cause)

data class GoogleAccount(
    val id: String,
    val email: String,
    val displayName: String?,
    val givenName: String?,
    val photoUrl: String?,
)

/**
 * "Continue with Google" through Credential Manager.
 *
 * Strategy: first try accounts that already authorized this app (auto-select gives a
 * one-tap return), then all Google accounts on the device, and finally the explicit
 * "Sign in with Google" sheet which also lets the user add an account.
 */
@Singleton
class GoogleCredentialDataSource @Inject constructor(
    private val credentialManager: CredentialManager,
) {

    suspend fun requestAccount(activityContext: Context): GoogleAccount {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (serverClientId.isBlank()) throw AuthException(AppError.MissingConfiguration)

        val attempts: List<() -> CredentialOption> = listOf(
            { googleIdOption(serverClientId, authorizedOnly = true) },
            { googleIdOption(serverClientId, authorizedOnly = false) },
            { GetSignInWithGoogleOption.Builder(serverClientId).setNonce(newNonce()).build() },
        )

        var lastError: Throwable? = null
        for (attempt in attempts) {
            try {
                return getAccount(activityContext, attempt())
            } catch (e: NoCredentialException) {
                lastError = e // Try the next, broader option.
            }
        }
        throw AuthException(AppError.NoGoogleAccount, lastError)
    }

    suspend fun clearCredentialState() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private fun googleIdOption(serverClientId: String, authorizedOnly: Boolean) =
        GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setAutoSelectEnabled(authorizedOnly)
            .setNonce(newNonce())
            .build()

    private suspend fun getAccount(activityContext: Context, option: CredentialOption): GoogleAccount {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (e: NoCredentialException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            throw AuthException(AppError.SignInCancelled, e)
        } catch (e: GetCredentialProviderConfigurationException) {
            throw AuthException(AppError.PlayServicesUnavailable, e)
        } catch (e: GetCredentialUnsupportedException) {
            throw AuthException(AppError.PlayServicesUnavailable, e)
        } catch (e: GetCredentialException) {
            throw AuthException(AppError.Unknown(e.type), e)
        }

        val isGoogleCredential = credential is CustomCredential && (
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
            )
        if (!isGoogleCredential) throw AuthException(AppError.Unknown("Unexpected credential type"))

        val google = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw AuthException(AppError.Unknown("Invalid Google ID token"), e)
        }
        // For Google accounts the credential id is the account email.
        val email = google.email?.takeIf { it.isNotBlank() } ?: google.id
        return GoogleAccount(
            id = google.uniqueId.takeIf { it.isNotBlank() } ?: email,
            email = email,
            displayName = google.displayName,
            givenName = google.givenName,
            photoUrl = google.profilePictureUri?.toString(),
        )
    }

    private fun newNonce(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
