package com.calmapps.calmmusic

/**
 * Minimal abstraction for searching YouTube for music tracks using the
 * official YouTube Data API.
 */
data class YouTubeSongResult(
    val videoId: String,
    val title: String,
    val artist: String?,
    val durationMillis: Long?,
)

interface YouTubeMusicSearchClient {
    suspend fun searchSongs(term: String, limit: Int = 25): List<YouTubeSongResult>
}
