package com.example.orbitmusic.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.example.orbitmusic.data.local.secure.KeystoreSecretCipher
import com.example.orbitmusic.data.local.secure.SecretCipher
import com.example.orbitmusic.data.repository.AuthRepositoryImpl
import com.example.orbitmusic.data.repository.PreferencesRepositoryImpl
import com.example.orbitmusic.domain.repository.AuthRepository
import com.example.orbitmusic.domain.repository.PreferencesRepository
import com.example.orbitmusic.domain.repository.SessionCleaner
import com.example.orbitmusic.util.SystemTimeProvider
import com.example.orbitmusic.util.TimeProvider
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.Identity
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager =
        CredentialManager.create(context)

    /**
     * Created with the application context: silent authorization works without UI, and
     * consent screens are returned as a PendingIntent the Activity launches.
     */
    @Provides
    @Singleton
    fun provideAuthorizationClient(@ApplicationContext context: Context): AuthorizationClient =
        Identity.getAuthorizationClient(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    abstract fun bindSecretCipher(impl: KeystoreSecretCipher): SecretCipher

    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    /** Declares the (initially empty) set; later phases add cleaners with @IntoSet. */
    @Multibinds
    abstract fun sessionCleaners(): Set<SessionCleaner>
}
