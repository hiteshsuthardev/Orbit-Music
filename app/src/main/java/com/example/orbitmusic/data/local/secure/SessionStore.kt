package com.example.orbitmusic.data.local.secure

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.orbitmusic.di.IoDispatcher
import com.example.orbitmusic.di.SessionDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Everything we persist about the signed-in user. Stored encrypted. */
@Serializable
data class StoredSession(
    val userId: String,
    val email: String,
    val displayName: String? = null,
    val givenName: String? = null,
    val photoUrl: String? = null,
    val accessToken: String? = null,
    /** Wall-clock time after which [accessToken] must be renewed (includes a safety margin). */
    val tokenExpiresAtMillis: Long = 0L,
    val grantedScopes: List<String> = emptyList(),
    /** Set when silent renewal failed because the user must consent again. */
    val reauthorizationRequired: Boolean = false,
)

/**
 * Encrypted session persistence: the serialized [StoredSession] is AES-GCM encrypted with
 * a Keystore key and stored as Base64 in a dedicated DataStore file. That file is excluded
 * from backups (the Keystore key never leaves the device, so a restored copy could not be
 * decrypted anyway).
 */
@Singleton
class SessionStore @Inject constructor(
    @SessionDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    val session: Flow<StoredSession?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[SESSION_KEY]?.let(::decode) }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    suspend fun current(): StoredSession? = session.first()

    /** Atomically transforms the stored session; returning null deletes it. */
    suspend fun update(transform: (StoredSession?) -> StoredSession?) {
        dataStore.edit { prefs ->
            val updated = transform(prefs[SESSION_KEY]?.let(::decode))
            if (updated == null) prefs.remove(SESSION_KEY) else prefs[SESSION_KEY] = encode(updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(SESSION_KEY) }
    }

    private fun encode(session: StoredSession): String {
        val plain = json.encodeToString(StoredSession.serializer(), session).encodeToByteArray()
        return Base64.encodeToString(cipher.encrypt(plain), Base64.NO_WRAP)
    }

    /** Undecryptable data (e.g. key invalidated) is treated as "signed out". */
    private fun decode(encoded: String): StoredSession? = runCatching {
        val plain = cipher.decrypt(Base64.decode(encoded, Base64.NO_WRAP))
        json.decodeFromString(StoredSession.serializer(), plain.decodeToString())
    }.getOrNull()

    private companion object {
        val SESSION_KEY = stringPreferencesKey("encrypted_session")
    }
}
