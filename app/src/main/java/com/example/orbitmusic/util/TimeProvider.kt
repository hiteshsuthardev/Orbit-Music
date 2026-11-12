package com.example.orbitmusic.util

import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable clock so time-dependent logic (token expiry, greetings) is testable. */
interface TimeProvider {
    fun currentTimeMillis(): Long
    fun localTime(): LocalTime
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun localTime(): LocalTime = LocalTime.now()
}
