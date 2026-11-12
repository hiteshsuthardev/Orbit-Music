package com.example.orbitmusic.util

/** Clean display metadata derived from a raw YouTube title + channel. */
data class ParsedTrack(val title: String, val artist: String)

/**
 * Turns noisy YouTube metadata into music-app metadata.
 *
 * - Removes bracketed noise such as "(Official Video)", "[Lyrics]", "【MV】", "(HD)".
 * - Removes trailing "| Official Audio" style suffixes and hashtags.
 * - Uses the channel as the artist, without " - Topic" / "VEVO" suffixes.
 * - For non-Topic channels, splits "Artist - Title" titles into their parts.
 *
 * Meaningful qualifiers ("Remix", "Live", "Acoustic", "feat. X") are kept.
 */
object MusicTitleParser {

    private const val NOISE =
        "official|video|audio|lyrics?|lyric video|visuali[sz]er|music video|mv|m/v|hd|hq|4k|" +
            "full song|full video|explicit|clean|color coded|eng sub|sub español|from .+ soundtrack"

    /** A bracketed group whose content is (mostly) noise words. */
    private val bracketNoise = Regex(
        "\\s*[(\\[【「]\\s*(?:(?:$NOISE)[\\s/&+,.-]*)+[)\\]】」]",
        RegexOption.IGNORE_CASE,
    )

    /** e.g. " | Official Video", " - Official Audio", " // Lyric Video" at the end. */
    private val trailingNoise = Regex(
        "\\s*(?:[|/]+|-)\\s*(?:(?:$NOISE)\\s*)+$",
        RegexOption.IGNORE_CASE,
    )

    /** "Official Music Video" without brackets at the very end. */
    private val bareTrailingNoise = Regex(
        "\\s+(?:official\\s+(?:music\\s+)?(?:video|audio)|lyric video|visuali[sz]er)$",
        RegexOption.IGNORE_CASE,
    )

    private val hashtags = Regex("(?:\\s+#[\\p{L}\\p{N}_]+)+$")
    private val whitespace = Regex("\\s+")
    private val artistSeparator = Regex("\\s+[-–—]\\s+")
    private const val MAX_ARTIST_LENGTH = 60

    fun cleanTitle(raw: String): String {
        var title = HtmlEntities.decode(raw)
        repeat(3) { // Titles can stack several noise groups.
            title = title
                .replace(bracketNoise, "")
                .replace(trailingNoise, "")
                .replace(bareTrailingNoise, "")
                .replace(hashtags, "")
        }
        return title
            .replace(whitespace, " ")
            .trim()
            .trim('-', '|', '–', '—', ' ')
            .ifBlank { HtmlEntities.decode(raw).trim() }
    }

    fun cleanArtist(channelTitle: String?): String {
        val decoded = HtmlEntities.decode(channelTitle.orEmpty()).trim()
        return decoded
            .removeSuffix(" - Topic")
            .replace(Regex("VEVO$"), "")
            .trim()
    }

    fun isTopicChannel(channelTitle: String?): Boolean =
        channelTitle?.trim()?.endsWith(" - Topic") == true

    fun parse(rawTitle: String, channelTitle: String?): ParsedTrack {
        val cleaned = cleanTitle(rawTitle)
        val channelArtist = cleanArtist(channelTitle)

        if (!isTopicChannel(channelTitle)) {
            val parts = cleaned.split(artistSeparator, limit = 2)
            if (parts.size == 2) {
                val artist = parts[0].trim()
                val title = parts[1].trim()
                if (artist.isNotEmpty() && title.isNotEmpty() && artist.length <= MAX_ARTIST_LENGTH) {
                    return ParsedTrack(title = title, artist = artist)
                }
            }
        }
        return ParsedTrack(title = cleaned, artist = channelArtist)
    }
}

/** Decodes the HTML entities YouTube leaves in snippet text (&amp;, &#39;, ...). */
object HtmlEntities {
    private val named = mapOf(
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "nbsp" to " ",
    )
    private val entity = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")

    fun decode(text: String): String = entity.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[body.lowercase()]
        } ?: match.value
    }
}
