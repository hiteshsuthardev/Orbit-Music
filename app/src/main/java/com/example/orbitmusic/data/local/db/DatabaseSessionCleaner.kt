package com.example.orbitmusic.data.local.db

import com.example.orbitmusic.di.IoDispatcher
import com.example.orbitmusic.domain.repository.SessionCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Clears every table on sign-out so no data from one account leaks into the next. */
class DatabaseSessionCleaner @Inject constructor(
    private val database: OrbitDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SessionCleaner {
    override suspend fun clear() = withContext(ioDispatcher) {
        // clearAllTables must not run on the main thread.
        database.clearAllTables()
    }
}
