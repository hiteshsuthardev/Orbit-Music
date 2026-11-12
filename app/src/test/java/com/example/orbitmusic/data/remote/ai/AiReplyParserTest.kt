package com.example.orbitmusic.data.remote.ai

import com.example.orbitmusic.domain.model.AiSongSuggestion
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiReplyParserTest {

    private val parser = AiReplyParser(Json { ignoreUnknownKeys = true })

    @Test
    fun parsesPlainJson() {
        val playlist = parser.parse(
            """{"playlistName":"Late night drive","description":"Smooth synths","songs":[{"title":"Midnight City","artist":"M83"}]}""",
        )!!
        assertEquals("Late night drive", playlist.name)
        assertEquals("Smooth synths", playlist.description)
        assertEquals(listOf(AiSongSuggestion("Midnight City", "M83")), playlist.songs)
    }

    @Test
    fun toleratesCodeFencesAndExtraText() {
        val reply = "Here you go!\n```json\n{\"playlistName\":\"Focus\",\"description\":\"\",\"songs\":[{\"title\":\"Weightless\",\"artist\":\"Marconi Union\"}]}\n```"
        assertEquals("Focus", parser.parse(reply)!!.name)
    }

    @Test
    fun dropsBlankLinkedAndDuplicateSongs() {
        val playlist = parser.parse(
            """
            {"playlistName":"Mix","description":"","songs":[
              {"title":" Blinding Lights ","artist":"The Weeknd"},
              {"title":"blinding lights","artist":"the weeknd"},
              {"title":"","artist":"Nobody"},
              {"title":"Watch here https://youtu.be/abc","artist":"Someone"},
              {"title":"Levitating","artist":"Dua Lipa","videoId":"xyz"}
            ]}
            """.trimIndent(),
        )!!
        assertEquals(
            listOf(AiSongSuggestion("Blinding Lights", "The Weeknd"), AiSongSuggestion("Levitating", "Dua Lipa")),
            playlist.songs,
        )
    }

    @Test
    fun normalizesUnusualSpaces() {
        val playlist = parser.parse("""{"playlistName":"Gym","description":"","songs":[{"title":"Eye of the Tiger","artist":"Survivor"}]}""")!!
        assertEquals("Eye of the Tiger", playlist.songs.single().title)
    }

    @Test
    fun unusableRepliesAreNull() {
        assertNull(parser.parse(null))
        assertNull(parser.parse("Sorry, I can't help with that."))
        assertNull(parser.parse("""{"playlistName":"Empty","description":"","songs":[]}"""))
        assertNull(parser.parse("""{"playlistName": "Broken", "songs": [ {"title": """))
    }
}
