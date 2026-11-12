package com.example.orbitmusic.util

/**
 * Turns noisy YouTube titles into lyrics searches and checks that a lyrics result belongs to
 * the song. Music video titles often look like "Song | Movie | Singer" or
 * "Song (From X) Full Song With Lyrics | Actor, Actor", and the channel is frequently a label
 * (e.g. "T-Series") rather than the singer, so an exact title + artist lookup finds nothing.
 */
object LyricsQueries {

    private val brackets = Regex("""[(\[【「{].*?[)\]】」}]""")

    /** Where the song name usually ends in a video title. */
    private val segmentBreak = Regex("""\s*(?:\||｜|:|•|\s[-–—]\s|//)\s*""")

    /** Words that start the "noise" tail of a title (everything after them is dropped). */
    private val noiseTail = Regex(
        """\s+(?:full\s+(?:song|video|audio)|with\s+lyrics|lyrics?|lyrical|video\s+song|official|song|audio|ft\.?|feat\.?|featuring|prod\.?)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    private const val SHORT_QUERY_WORDS = 3

    /** The song name alone, e.g. "Tum Hi Ho" from "Tum Hi Ho Full Song With Lyrics | Aashiqui 2". */
    fun coreTitle(title: String): String {
        val noBrackets = title.replace(brackets, " ")
        val firstSegment = noBrackets.split(segmentBreak).firstOrNull { it.isNotBlank() } ?: noBrackets
        return firstSegment.replace(noiseTail, "").replace(Regex("\\s+"), " ").trim().ifBlank { title.trim() }
    }

    /** Free-text searches to try after the exact title + artist lookup, most specific first. */
    fun freeTextQueries(title: String): List<String> {
        val core = coreTitle(title)
        val words = core.split(' ').filter { it.isNotBlank() }
        val short = words.take(SHORT_QUERY_WORDS).joinToString(" ")
        return listOf(core, short).filter { it.isNotBlank() }.distinct()
    }

    /**
     * True when a lyrics result's track name fits the song: one normalized title contains the
     * other (after dropping bracketed parts like "(From "Movie")").
     */
    fun titleMatches(resultTrack: String?, songTitle: String): Boolean {
        val result = normalize(resultTrack.orEmpty().replace(brackets, " "))
        val song = normalize(songTitle)
        if (result.isBlank() || song.isBlank()) return false
        return song.contains(result) || result.contains(normalize(coreTitle(songTitle)))
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(nonWord, " ").trim().replace(Regex("\\s+"), " ")
}
