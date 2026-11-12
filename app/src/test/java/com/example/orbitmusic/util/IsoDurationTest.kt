package com.example.orbitmusic.util

import org.junit.Assert.assertEquals
import org.junit.Test

class IsoDurationTest {

    @Test
    fun `parses common YouTube durations`() {
        assertEquals(253_000L, IsoDuration.toMillis("PT4M13S"))
        assertEquals(45_000L, IsoDuration.toMillis("PT45S"))
        assertEquals(3_720_000L, IsoDuration.toMillis("PT1H2M"))
        assertEquals(3_600_000L, IsoDuration.toMillis("PT1H"))
        assertEquals(97_200_000L, IsoDuration.toMillis("P1DT3H"))
        assertEquals(604_800_000L, IsoDuration.toMillis("P1W"))
        assertEquals(1_500L, IsoDuration.toMillis("PT1.5S"))
    }

    @Test
    fun `live streams and garbage are zero`() {
        assertEquals(0L, IsoDuration.toMillis("P0D"))
        assertEquals(0L, IsoDuration.toMillis(null))
        assertEquals(0L, IsoDuration.toMillis(""))
        assertEquals(0L, IsoDuration.toMillis("4:13"))
    }

    @Test
    fun `formats track times`() {
        assertEquals("0:00", formatTrackTime(0))
        assertEquals("3:05", formatTrackTime(185_000))
        assertEquals("1:02:03", formatTrackTime(3_723_000))
        assertEquals("0:00", formatTrackTime(-5))
    }
}
