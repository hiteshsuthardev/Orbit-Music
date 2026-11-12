package com.example.orbitmusic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsQueriesTest {

    @Test
    fun coreTitleDropsMovieActorsAndNoise() {
        assertEquals("Tum Hi Ho", LyricsQueries.coreTitle("Tum Hi Ho Full Song With Lyrics | Aashiqui 2 | Aditya Roy Kapur"))
        assertEquals("Kesariya", LyricsQueries.coreTitle("Kesariya (From \"Brahmastra\") | Ranbir Kapoor | Pritam, Arijit Singh"))
        assertEquals("Shape of You", LyricsQueries.coreTitle("Shape of You"))
        assertEquals("Apna Bana Le", LyricsQueries.coreTitle("Apna Bana Le - Bhediya | Varun Dhawan"))
        assertEquals("Raataan Lambiyan", LyricsQueries.coreTitle("Raataan Lambiyan Lyrical Video | Shershaah"))
    }

    @Test
    fun freeTextQueriesGoFromCoreToShort() {
        assertEquals(listOf("Main Tera Boyfriend"), LyricsQueries.freeTextQueries("Main Tera Boyfriend Song Raabta"))
        assertEquals(
            listOf("Kal Ho Naa Ho Title Track", "Kal Ho Naa"),
            LyricsQueries.freeTextQueries("Kal Ho Naa Ho Title Track"),
        )
        assertEquals(listOf("Tum Hi Ho"), LyricsQueries.freeTextQueries("Tum Hi Ho | Aashiqui 2"))
    }

    @Test
    fun titleMatchAcceptsSameSongRejectsOthers() {
        val song = "Tum Hi Ho Full Song With Lyrics | Aashiqui 2"
        assertTrue(LyricsQueries.titleMatches("Tum Hi Ho", song))
        assertTrue(LyricsQueries.titleMatches("Tum Hi Ho (From \"Aashiqui 2\")", song))
        assertFalse(LyricsQueries.titleMatches("Tum Hi Tum Ho", song))
        assertTrue(LyricsQueries.titleMatches("Ed Sheeran - Shape Of You", "Shape of You"))
        assertFalse(LyricsQueries.titleMatches("Perfect", "Shape of You"))
        assertFalse(LyricsQueries.titleMatches(null, "Shape of You"))
    }
}
