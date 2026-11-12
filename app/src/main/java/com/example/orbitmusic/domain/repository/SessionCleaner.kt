package com.example.orbitmusic.domain.repository

/**
 * Anything that holds per-user data and must be wiped on sign-out (Room cache, search
 * history, player queue...). Implementations are contributed with Hilt `@IntoSet`.
 */
fun interface SessionCleaner {
    suspend fun clear()
}
