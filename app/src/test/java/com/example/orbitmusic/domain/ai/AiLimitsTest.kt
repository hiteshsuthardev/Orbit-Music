package com.example.orbitmusic.domain.ai

import com.example.orbitmusic.domain.model.AuthState
import com.example.orbitmusic.domain.model.UserProfile
import com.example.orbitmusic.domain.model.YouTubeAccess
import com.example.orbitmusic.domain.repository.AiUsageRepository
import com.example.orbitmusic.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.orbitmusic.util.TimeProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class AiLimitsTest {

    private val hour = 60 * 60 * 1_000L

    @Test
    fun allowsTwentyPerThreeHours() {
        val now = 10 * hour
        val nineteen = AiUsage(List(19) { now - it * 60_000L }, totalRequests = 19)
        assertEquals(1, AiLimits.allowance(nineteen, now).remainingInWindow)

        val twenty = AiLimits.record(nineteen, now)
        val blocked = AiLimits.allowance(twenty, now)
        assertTrue(blocked.isWindowExhausted)
        assertFalse(blocked.canSend)
        // The oldest of the 20 was 18 minutes ago, so a slot frees up 3h after it.
        assertEquals(now - 18 * 60_000L + 3 * hour, blocked.nextRequestAt)
    }

    @Test
    fun oldRequestsLeaveTheWindow() {
        val now = 10 * hour
        val usage = AiUsage(List(20) { now - 3 * hour - it }, totalRequests = 20)
        val allowance = AiLimits.allowance(usage, now)
        assertEquals(20, allowance.remainingInWindow)
        assertTrue(allowance.canSend)
        assertEquals(listOf(now), AiLimits.record(usage, now).recentRequestTimes)
    }

    @Test
    fun lifetimeLimitBlocksEvenWithWindowLeft() {
        val allowance = AiLimits.allowance(AiUsage(emptyList(), totalRequests = AiLimits.TOTAL_REQUESTS), 0L)
        assertEquals(AiLimits.REQUESTS_PER_WINDOW, allowance.remainingInWindow)
        assertTrue(allowance.isLifetimeExhausted)
        assertFalse(allowance.canSend)
    }

    private class MemoryUsage : AiUsageRepository {
        val stored = MutableStateFlow<Map<String, AiUsage>>(emptyMap())
        override fun observe(userId: String): Flow<AiUsage> = stored.map { it[userId] ?: AiUsage() }
        override suspend fun update(userId: String, transform: (AiUsage) -> AiUsage) {
            stored.value = stored.value + (userId to transform(stored.value[userId] ?: AiUsage()))
        }
    }

    private fun gate(usage: AiUsageRepository, now: Long): AiRequestGate {
        val auth = mockk<ObserveAuthStateUseCase>()
        every { auth() } returns MutableStateFlow(
            AuthState.SignedIn(
                user = UserProfile("u1", "a@b.c", "A", "A", null),
                youTubeAccess = YouTubeAccess.Granted,
                canEditPlaylists = false,
            ),
        )
        val time = object : TimeProvider {
            override fun currentTimeMillis() = now
            override fun localTime(): LocalTime = LocalTime.NOON
        }
        return AiRequestGate(auth, usage, time)
    }

    @Test
    fun gateCountsUntilTheWindowIsFull() = runTest {
        val usage = MemoryUsage()
        val gate = gate(usage, now = 5 * hour)
        repeat(AiLimits.REQUESTS_PER_WINDOW) { assertNull(gate.tryConsume()) }
        val blocked = gate.tryConsume()
        assertTrue(blocked!!.isWindowExhausted)
        assertEquals(AiLimits.REQUESTS_PER_WINDOW, usage.stored.value.getValue("u1").totalRequests)
    }
}
