package com.example.orbitmusic.data.repository

import android.util.LruCache
import com.example.orbitmusic.data.remote.lyrics.LrcLibApi
import com.example.orbitmusic.data.remote.lyrics.LrcLibTrackDto
import com.example.orbitmusic.domain.model.AppError
import com.example.orbitmusic.domain.model.Lyrics
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.LyricsRepository
import com.example.orbitmusic.util.LrcParser
import com.example.orbitmusic.util.LyricsQueries
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Finds lyrics on LRCLIB. Music video titles are noisy, so it tries, in order, until a
 * result whose track name fits the song is found:
 * 1. exact track + artist lookup (works for clean titles / "- Topic" channels);
 * 2. free-text search for the core song name ("Tum Hi Ho" from "Tum Hi Ho Full Song | ...");
 * 3. free-text search for its first few words.
 * Among fitting results it prefers time-synced lyrics with a length close to the video's.
 * Results (including "none found") are kept in memory per video.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val api: LrcLibApi,
) : LyricsRepository {

    /** Wrapper so "no lyrics" can be cached too (LruCache doesn't accept null values). */
    private class Entry(val lyrics: Lyrics?)

    private val cache = LruCache<String, Entry>(CACHE_SIZE)

    override suspend fun getLyrics(song: Song): Resource<Lyrics?> {
        cache.get(song.videoId)?.let { return Resource.Success(it.lyrics) }
        return try {
            val lyrics = find(song)
            cache.put(song.videoId, Entry(lyrics))
            Resource.Success(lyrics)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Resource.Error(AppError.NoInternet)
        } catch (error: Exception) {
            Resource.Error(AppError.Unknown(error.message))
        }
    }

    private suspend fun find(song: Song): Lyrics? {
        val attempts: List<suspend () -> List<LrcLibTrackDto>> = buildList {
            add { api.search(trackName = song.title, artistName = song.artist.takeIf { it.isNotBlank() }) }
            LyricsQueries.freeTextQueries(song.title).forEach { query -> add { api.searchText(query) } }
        }
        for (attempt in attempts) {
            val match = bestMatch(attempt(), song)
            if (match != null) return match.toLyrics()
        }
        return null
    }

    private fun bestMatch(candidates: List<LrcLibTrackDto>, song: Song): LrcLibTrackDto? {
        val fitting = candidates.filter { it.hasLyrics() && LyricsQueries.titleMatches(it.trackName, song.title) }
        if (fitting.isEmpty()) return null
        val seconds = song.durationMs / 1_000.0
        // Music videos often run longer than the audio (intros / outros), so allow slack.
        val close = fitting.filter { track ->
            song.durationMs <= 0 || track.duration == null || abs(track.duration - seconds) <= DURATION_TOLERANCE_S
        }
        val pool = close.ifEmpty { fitting }
        val byLength = pool.sortedBy { track -> track.duration?.let { abs(it - seconds) } ?: Double.MAX_VALUE }
        return byLength.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: byLength.first()
    }

    private fun LrcLibTrackDto.hasLyrics(): Boolean =
        !instrumental && (!syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank())

    private fun LrcLibTrackDto.toLyrics(): Lyrics? {
        val lines = syncedLyrics?.takeIf { it.isNotBlank() }?.let(LrcParser::parseSynced)?.takeIf { it.isNotEmpty() }
            ?: plainLyrics?.takeIf { it.isNotBlank() }?.let(LrcParser::parsePlain)
            ?: return null
        return Lyrics(lines = lines, source = LrcLibApi.SOURCE_NAME)
    }

    private companion object {
        const val CACHE_SIZE = 50
        const val DURATION_TOLERANCE_S = 45.0
    }
}
