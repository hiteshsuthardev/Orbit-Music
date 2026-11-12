package com.example.orbitmusic.domain.model

/** One lyric line. [timeMs] is set for time-synced lyrics, null for plain text. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    /** Where the lyrics came from, shown as attribution. */
    val source: String,
) {
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.timeMs != null }

    /** Index of the line being sung at [positionMs] (-1 before the first line or when not synced). */
    fun activeLineIndex(positionMs: Long): Int {
        if (!isSynced) return -1
        return lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= positionMs }
    }
}
