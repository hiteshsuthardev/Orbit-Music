package com.example.orbitmusic.domain.usecase.lyrics

import com.example.orbitmusic.domain.model.Lyrics
import com.example.orbitmusic.domain.model.Resource
import com.example.orbitmusic.domain.model.Song
import com.example.orbitmusic.domain.repository.LyricsRepository
import javax.inject.Inject

class GetLyricsUseCase @Inject constructor(private val repository: LyricsRepository) {
    suspend operator fun invoke(song: Song): Resource<Lyrics?> = repository.getLyrics(song)
}
