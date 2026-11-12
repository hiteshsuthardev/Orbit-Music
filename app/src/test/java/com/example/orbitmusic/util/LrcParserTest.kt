package com.example.orbitmusic.util

import com.example.orbitmusic.domain.model.LyricLine
import com.example.orbitmusic.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parsesTimeTagsInOrderAndSkipsMetadata() {
        val lines = LrcParser.parseSynced(
            """
            [ar:Daft Punk]
            [00:12.50] Like the legend of the phoenix
            [00:05.00] Intro
            [01:02.123]All ends with beginnings
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                LyricLine(5_000, "Intro"),
                LyricLine(12_500, "Like the legend of the phoenix"),
                LyricLine(62_123, "All ends with beginnings"),
            ),
            lines,
        )
    }

    @Test
    fun repeatsLinesWithSeveralTimeTags() {
        val lines = LrcParser.parseSynced("[00:10.00][00:40.00] Chorus")
        assertEquals(listOf(LyricLine(10_000, "Chorus"), LyricLine(40_000, "Chorus")), lines)
    }

    @Test
    fun plainLyricsHaveNoTimesAndTrimmedEdges() {
        val lines = LrcParser.parsePlain("\n\nFirst line\n\nSecond line\n\n")
        assertEquals(listOf(LyricLine(null, "First line"), LyricLine(null, ""), LyricLine(null, "Second line")), lines)
        assertFalse(Lyrics(lines, "test").isSynced)
    }

    @Test
    fun activeLineFollowsPosition() {
        val lyrics = Lyrics(LrcParser.parseSynced("[00:05.00] A\n[00:10.00] B\n[00:20.00] C"), "test")
        assertTrue(lyrics.isSynced)
        assertEquals(-1, lyrics.activeLineIndex(1_000))
        assertEquals(0, lyrics.activeLineIndex(5_000))
        assertEquals(1, lyrics.activeLineIndex(19_999))
        assertEquals(2, lyrics.activeLineIndex(300_000))
    }
}
