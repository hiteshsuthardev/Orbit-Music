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
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Looks lyrics up on LRCLIB by title and artist and picks the best match: lyrics present,
 * length close to the video's, time-synced preferred. Results (including "none found") are
 * kept in memory per video so reopening the view doesn't repeat the request.
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
            val artist = song.artist.takeIf { it.isNotBlank() }
            var candidates = api.search(trackName = song.title, artistName = artist)
            // Artist names parsed from video titles are sometimes off; retry by title alone.
            if (candidates.none { it.hasLyrics() } && artist != null) {
                candidates = api.search(trackName = song.title, artistName = null)
            }
            val lyrics = bestMatch(candidates, song.durationMs)?.toLyrics()
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

    private fun bestMatch(candidates: List<LrcLibTrackDto>, durationMs: Long): LrcLibTrackDto? {
        val withLyrics = candidates.filter { it.hasLyrics() }
        if (withLyrics.isEmpty()) return null
        val seconds = durationMs / 1_000.0
        // Music videos often run longer than the audio (intros / outros), so allow slack.
        val close = withLyrics.filter { track ->
            durationMs <= 0 || track.duration == null || abs(track.duration - seconds) <= DURATION_TOLERANCE_S
        }
        val pool = close.ifEmpty { withLyrics }
        return pool.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: pool.first()
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
