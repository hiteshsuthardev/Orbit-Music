package com.example.orbitmusic.domain.ai

import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.repository.AiUsageRepository
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Applies [AiLimits] to the signed-in user. */
@OptIn(ExperimentalCoroutinesApi::class)
class AiRequestGate @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val usage: AiUsageRepository,
    private val time: TimeProvider,
) {
    private val authState = observeAuthState()

    private val userId: Flow<String?> = authState.map { (it as? AuthState.SignedIn)?.user?.id }.distinctUntilChanged()

    /** Re-evaluates periodically so a blocked window unlocks on its own. */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(time.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }

    /** The current user's allowance; null while nobody is signed in. */
    val allowance: Flow<AiAllowance?> = userId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(usage.observe(id), clock) { stored, now -> AiLimits.allowance(stored, now) }
        }
    }.distinctUntilChanged()

    /**
     * Counts one request if the user still has allowance. Returns null when it was counted
     * (go ahead), or the blocking allowance otherwise. Check and count happen atomically.
     */
    suspend fun tryConsume(): AiAllowance? {
        val id = (authState.value as? AuthState.SignedIn)?.user?.id ?: return AiAllowance(0, null, 0)
        val now = time.currentTimeMillis()
        var blocked: AiAllowance? = null
        usage.update(id) { stored ->
            val current = AiLimits.allowance(stored, now)
            if (current.canSend) {
                AiLimits.record(stored, now)
            } else {
                blocked = current
                stored
            }
        }
        return blocked
    }

    private companion object {
        const val CLOCK_TICK_MS = 30_000L
    }
}
