package com.example.orbitmusic.ui.screens.home

import com.example.orbitmusic.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {

    @Test
    fun `greeting boundaries`() {
        assertEquals(Greeting.Night, greetingFor(LocalTime.of(4, 59)))
        assertEquals(Greeting.Morning, greetingFor(LocalTime.of(5, 0)))
        assertEquals(Greeting.Morning, greetingFor(LocalTime.of(11, 59)))
        assertEquals(Greeting.Afternoon, greetingFor(LocalTime.of(12, 0)))
        assertEquals(Greeting.Evening, greetingFor(LocalTime.of(17, 0)))
        assertEquals(Greeting.Night, greetingFor(LocalTime.of(22, 0)))
    }

    @Test
    fun `first name prefers given name then display name then email`() {
        assertEquals("Nova", UserProfile("1", "a@b.c", "Nova Star", "Nova", null).firstName)
        assertEquals("Nova", UserProfile("1", "a@b.c", "Nova Star", null, null).firstName)
        assertEquals("nova.star", UserProfile("1", "nova.star@b.c", null, " ", null).firstName)
    }
}
