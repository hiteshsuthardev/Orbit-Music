package com.example.orbitmusic.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTitleParserTest {

    @Test
    fun `strips bracketed noise`() {
        assertEquals("Blinding Lights", MusicTitleParser.cleanTitle("Blinding Lights (Official Video)"))
        assertEquals("Blinding Lights", MusicTitleParser.cleanTitle("Blinding Lights [Official Music Video]"))
        assertEquals("Levitating", MusicTitleParser.cleanTitle("Levitating (Lyrics)"))
        assertEquals("Song", MusicTitleParser.cleanTitle("Song (Official Lyric Video) [4K]"))
        assertEquals("Song", MusicTitleParser.cleanTitle("Song 【MV】"))
        assertEquals("Song", MusicTitleParser.cleanTitle("Song (Audio)"))
        assertEquals("Song", MusicTitleParser.cleanTitle("Song (HD)"))
    }

    @Test
    fun `strips trailing noise and hashtags`() {
        assertEquals("Song", MusicTitleParser.cleanTitle("Song | Official Video"))
        assertEquals("Song", MusicTitleParser.cleanTitle("Song - Official Audio"))
        assertEquals("Song", MusicTitleParser.cleanTitle("Song Official Music Video"))
        assertEquals("Song", MusicTitleParser.cleanTitle("Song #shorts #music"))
    }

    @Test
    fun `keeps meaningful qualifiers`() {
        assertEquals("Song (Remix)", MusicTitleParser.cleanTitle("Song (Remix) (Official Video)"))
        assertEquals("Song (Live at Wembley)", MusicTitleParser.cleanTitle("Song (Live at Wembley)"))
        assertEquals("Song (feat. Someone)", MusicTitleParser.cleanTitle("Song (feat. Someone) [Official Video]"))
    }

    @Test
    fun `decodes html entities`() {
        assertEquals("Rock & Roll", MusicTitleParser.cleanTitle("Rock &amp; Roll"))
        assertEquals("Don't Stop", MusicTitleParser.cleanTitle("Don&#39;t Stop"))
        assertEquals("\"Quoted\"", MusicTitleParser.cleanTitle("&quot;Quoted&quot;"))
    }

    @Test
    fun `topic channel gives clean artist and keeps title`() {
        val parsed = MusicTitleParser.parse("Shape of You", "Ed Sheeran - Topic")
        assertEquals(ParsedTrack(title = "Shape of You", artist = "Ed Sheeran"), parsed)
    }

    @Test
    fun `artist dash title is split for regular channels`() {
        val parsed = MusicTitleParser.parse("Adele - Hello (Official Music Video)", "AdeleVEVO")
        assertEquals(ParsedTrack(title = "Hello", artist = "Adele"), parsed)
    }

    @Test
    fun `falls back to channel as artist`() {
        val parsed = MusicTitleParser.parse("Midnight City [Official Video]", "M83VEVO")
        assertEquals(ParsedTrack(title = "Midnight City", artist = "M83"), parsed)
    }

    @Test
    fun `never returns a blank title`() {
        assertEquals("(Official Video)", MusicTitleParser.cleanTitle("(Official Video)"))
    }
}
