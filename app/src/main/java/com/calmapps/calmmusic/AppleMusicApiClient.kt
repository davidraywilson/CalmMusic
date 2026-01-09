package com.calmapps.calmmusic

/**
 * Minimal domain models used by the app for Apple Music catalog content.
 * These are intentionally simple and decoupled from the raw Apple Music API JSON.
 */
data class AppleMusicSong(
    val id: String,
    val name: String,
    val artistName: String,
    val artworkUrl: String?,
    val durationMillis: Long?,
    val albumName: String?,
    /** Optional release year for the track/album, when available. */
    val releaseYear: Int?,
)

data class AppleMusicPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val trackCount: Int?,
)

data class AppleMusicSearchResult(
    val songs: List<AppleMusicSong>,
    val playlists: List<AppleMusicPlaylist>,
)

/**
 * Abstraction over the Apple Music Web API that the UI layer can depend on.
 *
 * A concrete implementation can be built with Retrofit/OkHttp and should:
 * - Use a short-lived developer token (from your backend) and the music user token.
 * - Include the storefront (e.g. "us") for catalog queries.
 */
interface AppleMusicApiClient {

    /**
     * Search Apple Music catalog for songs matching [term].
     */
    suspend fun searchSongs(
        term: String,
        storefront: String,
        limit: Int = 25,
    ): List<AppleMusicSong>

    /**
     * Search Apple Music catalog for playlists matching [term].
     */
    suspend fun searchPlaylists(
        term: String,
        storefront: String,
        limit: Int = 25,
    ): List<AppleMusicPlaylist>

    /**
     * Fetch tracks for a given playlist from the catalog.
     */
    suspend fun getPlaylistTracks(
        playlistId: String,
        storefront: String,
        limit: Int = 100,
    ): List<AppleMusicSong>

    /**
     * Fetch songs from the signed-in user's Apple Music library.
     */
    suspend fun getLibrarySongs(
        limit: Int = 200,
    ): List<AppleMusicSong>

    /**
     * Fetch playlists from the signed-in user's Apple Music library.
     */
    suspend fun getLibraryPlaylists(
        limit: Int = 100,
    ): List<AppleMusicPlaylist>

    /**
     * Convenience combined search that returns both songs and playlists.
     */
    suspend fun searchAll(
        term: String,
        storefront: String,
        songLimit: Int = 25,
        playlistLimit: Int = 25,
    ): AppleMusicSearchResult
}
