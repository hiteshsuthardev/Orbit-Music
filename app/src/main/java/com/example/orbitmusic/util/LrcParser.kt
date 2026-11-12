package com.example.orbitmusic.util

import com.example.orbitmusic.domain.model.LyricLine

/**
 * Parses LRC ("[mm:ss.xx] line") time-synced lyrics. Lines with several time tags are
 * repeated at each time; metadata tags like [ar:...] and lines without a time are skipped.
 * Plain (unsynced) lyrics are split into lines with no times.
 */
object LrcParser {

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parseSynced(lrc: String): List<LyricLine> =
        lrc.lineSequence()
            .flatMap { raw ->
                val tags = timeTag.findAll(raw).toList()
                if (tags.isEmpty()) return@flatMap emptySequence()
                val text = raw.substring(tags.last().range.last + 1).trim()
                tags.asSequence().map { LyricLine(it.toMillis(), text) }
            }
            .sortedBy { it.timeMs }
            .toList()

    fun parsePlain(text: String): List<LyricLine> =
        text.lineSequence().map { LyricLine(null, it.trim()) }.toList().trimBlankEdges()

    private fun MatchResult.toMillis(): Long {
        val (minutes, seconds, fraction) = destructured
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    private fun List<LyricLine>.trimBlankEdges(): List<LyricLine> =
        dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
}
