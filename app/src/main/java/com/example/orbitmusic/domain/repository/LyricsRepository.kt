package com.example.orbitmusic.domain.repository

import com.example.orbitmusic.domain.model.Lyrics
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song

interface LyricsRepository {
    /** Lyrics for [song]: Success(null) when none were found. Results are cached per song. */
    suspend fun getLyrics(song: Song): Resource<Lyrics?>
}
