package com.example.orbitmusic.domain.ai

import kotlin.time.Duration.Companion.hours

/** Stored Orbit AI usage for one user: send times within the current window, and the all-time count. */
data class AiUsage(
    val recentRequestTimes: List<Long> = emptyList(),
    val totalRequests: Int = 0,
)

/** What the user may do right now. */
data class AiAllowance(
    val remainingInWindow: Int,
    /** When blocked by the window: when the oldest counted request expires (epoch millis). */
    val nextRequestAt: Long?,
    val totalUsed: Int,
) {
    val totalLimit: Int get() = AiLimits.TOTAL_REQUESTS
    val windowLimit: Int get() = AiLimits.REQUESTS_PER_WINDOW
    val isLifetimeExhausted: Boolean get() = totalUsed >= AiLimits.TOTAL_REQUESTS
    val isWindowExhausted: Boolean get() = remainingInWindow <= 0
    val canSend: Boolean get() = !isLifetimeExhausted && !isWindowExhausted
}

/**
 * Orbit AI request limits per signed-in user: at most [REQUESTS_PER_WINDOW] requests in any
 * rolling [WINDOW_MS] period, and [TOTAL_REQUESTS] in total, after which the user is asked to
 * contact the developer. Every message the user sends counts once (automatic retries don't).
 */
object AiLimits {
    const val REQUESTS_PER_WINDOW = 20
    val WINDOW_MS: Long = 3.hours.inWholeMilliseconds
    const val TOTAL_REQUESTS = 220

    fun allowance(usage: AiUsage, now: Long): AiAllowance {
        val inWindow = usage.recentRequestTimes.filter { now - it < WINDOW_MS }.sorted()
        val remaining = (REQUESTS_PER_WINDOW - inWindow.size).coerceAtLeast(0)
        // A slot frees up when enough of the oldest requests leave the window.
        val nextAt = if (remaining == 0) inWindow[inWindow.size - REQUESTS_PER_WINDOW] + WINDOW_MS else null
        return AiAllowance(remainingInWindow = remaining, nextRequestAt = nextAt, totalUsed = usage.totalRequests)
    }

    /** [usage] with one more request at [now] (keeping only times still inside the window). */
    fun record(usage: AiUsage, now: Long): AiUsage = AiUsage(
        recentRequestTimes = usage.recentRequestTimes.filter { now - it < WINDOW_MS } + now,
        totalRequests = usage.totalRequests + 1,
    )
}
