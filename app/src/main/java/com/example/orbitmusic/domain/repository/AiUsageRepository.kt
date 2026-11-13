package com.example.orbitmusic.domain.repository

import com.example.orbitmusic.domain.ai.AiUsage
import kotlinx.coroutines.flow.Flow

/** Persists OrbitNova AI usage per user (kept across sign-out so limits can't be reset that way). */
interface AiUsageRepository {
    fun observe(userId: String): Flow<AiUsage>

    /** Atomically replaces the user's usage with [transform] of the stored value. */
    suspend fun update(userId: String, transform: (AiUsage) -> AiUsage)
}
