package com.example.orbitmusic.util

/**
 * Parses YouTube's ISO-8601 durations (e.g. `PT4M13S`, `PT1H2M`, `P1DT3H`, `P0D`).
 *
 * `java.time.Duration.parse` rejects week designators and some YouTube variants, so this
 * small parser is used instead. Returns 0 for live streams / unparseable input.
 */
object IsoDuration {

    private val pattern = Regex(
        "^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
    )

    fun toMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        val match = pattern.matchEntire(iso.trim().uppercase()) ?: return 0L
        val (weeks, days, hours, minutes, seconds) = match.destructured
        val totalSeconds =
            (weeks.toLongOrNull() ?: 0L) * 7 * 24 * 3600 +
                (days.toLongOrNull() ?: 0L) * 24 * 3600 +
                (hours.toLongOrNull() ?: 0L) * 3600 +
                (minutes.toLongOrNull() ?: 0L) * 60
        val secondsMillis = ((seconds.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        return totalSeconds * 1000 + secondsMillis
    }
}

/** Formats milliseconds as `m:ss` or `h:mm:ss` for track times. */
fun formatTrackTime(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
