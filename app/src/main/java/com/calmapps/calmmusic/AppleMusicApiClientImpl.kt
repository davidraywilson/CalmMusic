package com.calmapps.calmmusic

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit-based sketch implementation of [AppleMusicApiClient].
 *
 * This is intentionally minimal and focuses on:
 * - How to construct requests to Apple Music REST API
 * - How to wire authentication headers using developer + music user token
 *
 * You still need to:
 * - Provide a valid base URL (usually "https://api.music.apple.com/")
 * - Supply a short-lived developer token from your backend via [SimpleTokenProvider]
 * - Optionally attach the music user token for user-specific endpoints
 */
internal class AppleMusicApiClientImpl private constructor(
    private val retrofitService: AppleMusicService,
    private val storefront: String,
) : AppleMusicApiClient {

    override suspend fun searchSongs(
        term: String,
        storefront: String,
        limit: Int,
    ): List<AppleMusicSong> {
        val response = retrofitService.search(
            storefront = storefront,
            term = term,
            types = "songs",
            songLimit = limit,
            playlistLimit = 0,
        )
        val songs = response.results?.songs?.data.orEmpty()
        return songs.map { it.toDomainSong() }
    }

    override suspend fun searchPlaylists(
        term: String,
        storefront: String,
        limit: Int,
    ): List<AppleMusicPlaylist> {
        val response = retrofitService.search(
            storefront = storefront,
            term = term,
            types = "playlists",
            songLimit = 0,
            playlistLimit = limit,
        )
        val playlists = response.results?.playlists?.data.orEmpty()
        return playlists.map { it.toDomainPlaylist() }
    }

    override suspend fun getPlaylistTracks(
        playlistId: String,
        storefront: String,
        limit: Int,
    ): List<AppleMusicSong> {
        val response = retrofitService.getPlaylistTracks(
            storefront = storefront,
            playlistId = playlistId,
            limit = limit,
        )
        val tracks = response.data.orEmpty()
        return tracks.mapNotNull { it.attributes?.toDomainSong(it.id) }
    }

    override suspend fun getLibrarySongs(limit: Int): List<AppleMusicSong> {
        val response = retrofitService.getLibrarySongs(limit = limit)
        return response.data.orEmpty().map { it.toDomainSong() }
    }

    override suspend fun getLibraryPlaylists(limit: Int): List<AppleMusicPlaylist> {
        val response = retrofitService.getLibraryPlaylists(limit = limit)
        return response.data.orEmpty().map { it.toDomainPlaylist() }
    }

    override suspend fun searchAll(
        term: String,
        storefront: String,
        songLimit: Int,
        playlistLimit: Int,
    ): AppleMusicSearchResult {
        val response = retrofitService.search(
            storefront = storefront,
            term = term,
            types = "songs,playlists",
            songLimit = songLimit,
            playlistLimit = playlistLimit,
        )
        val songs = response.results?.songs?.data.orEmpty().map { it.toDomainSong() }
        val playlists = response.results?.playlists?.data.orEmpty().map { it.toDomainPlaylist() }
        return AppleMusicSearchResult(
            songs = songs,
            playlists = playlists,
        )
    }

    companion object {
        /**
         * Build an [AppleMusicApiClient] with a basic OkHttp + Retrofit stack.
         */
        fun create(
            tokenProvider: SimpleTokenProvider,
            baseUrl: String = "https://api.music.apple.com/",
            storefront: String = "us",
        ): AppleMusicApiClient {
            val authInterceptor = AppleMusicAuthInterceptor(tokenProvider)

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            val service = retrofit.create(AppleMusicService::class.java)
            return AppleMusicApiClientImpl(service, storefront)
        }
    }
}

/**
 * Interceptor that adds Apple Music auth headers to every request.
 *
 * - Authorization: Bearer <developer_token>
 * - Music-User-Token: <music_user_token> (optional, only when available)
 */
class AppleMusicAuthInterceptor(
    private val tokenProvider: SimpleTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original: Request = chain.request()
        val devToken = tokenProvider.getDeveloperToken()
        val musicUserToken = tokenProvider.getUserToken().takeIf { it.isNotBlank() }

        val builder = original.newBuilder()
        if (devToken.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $devToken")
        }
        if (!musicUserToken.isNullOrBlank()) {
            builder.addHeader("Music-User-Token", musicUserToken)
        }

        return chain.proceed(builder.build())
    }
}

// --- Retrofit DTOs ---

private interface AppleMusicService {

    // Example: GET /v1/catalog/{storefront}/search?term=...&types=songs,playlists&limit=...
    @GET("v1/catalog/{storefront}/search")
    suspend fun search(
        @Path("storefront") storefront: String,
        @Query("term") term: String,
        @Query("types") types: String,
        @Query("limit") songLimit: Int? = null,
        @Query("playlistLimit") playlistLimit: Int? = null,
    ): SearchResponse

    // Example: GET /v1/catalog/{storefront}/playlists/{id}/tracks
    @GET("v1/catalog/{storefront}/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("storefront") storefront: String,
        @Path("id") playlistId: String,
        @Query("limit") limit: Int? = null,
    ): TracksResponse

    // Example: GET /v1/me/library/songs
    @GET("v1/me/library/songs")
    suspend fun getLibrarySongs(
        @Query("limit") limit: Int? = null,
    ): LibrarySongsResponse

    // Example: GET /v1/me/library/playlists
    @GET("v1/me/library/playlists")
    suspend fun getLibraryPlaylists(
        @Query("limit") limit: Int? = null,
    ): LibraryPlaylistsResponse
}

// These DTOs are intentionally incomplete; extend as needed based on Apple Music API docs.

private data class SearchResponse(
    val results: SearchResults? = null,
)

private data class SearchResults(
    val songs: SongContainer? = null,
    val playlists: PlaylistContainer? = null,
)

private data class SongContainer(
    val data: List<SongResource> = emptyList(),
)

private data class PlaylistContainer(
    val data: List<PlaylistResource> = emptyList(),
)

private data class SongResource(
    val id: String,
    val attributes: SongAttributes? = null,
)

private data class PlaylistResource(
    val id: String,
    val attributes: PlaylistAttributes? = null,
)

private data class SongAttributes(
    val name: String? = null,
    val artistName: String? = null,
    val artwork: Artwork? = null,
    val durationInMillis: Long? = null,
    val albumName: String? = null,
)

private data class PlaylistAttributes(
    val name: String? = null,
    val description: DescriptionAttribute? = null,
    val artwork: Artwork? = null,
    val trackCount: Int? = null,
)

private data class DescriptionAttribute(
    val standard: String? = null,
)

private data class Artwork(
    val url: String? = null,
)

private data class TracksResponse(
    val data: List<TrackResource> = emptyList(),
)

private data class TrackResource(
    val id: String,
    val attributes: SongAttributes? = null,
)

private data class LibrarySongsResponse(
    val data: List<SongResource> = emptyList(),
)

private data class LibraryPlaylistsResponse(
    val data: List<PlaylistResource> = emptyList(),
)

// --- Mapping extensions ---

private fun SongResource.toDomainSong(): AppleMusicSong =
    AppleMusicSong(
        id = id,
        name = attributes?.name.orEmpty(),
        artistName = attributes?.artistName.orEmpty(),
        artworkUrl = attributes?.artwork?.url,
        durationMillis = attributes?.durationInMillis,
        albumName = attributes?.albumName,
    )

private fun PlaylistResource.toDomainPlaylist(): AppleMusicPlaylist =
    AppleMusicPlaylist(
        id = id,
        name = attributes?.name.orEmpty(),
        description = attributes?.description?.standard,
        artworkUrl = attributes?.artwork?.url,
        trackCount = attributes?.trackCount,
    )

private fun SongAttributes.toDomainSong(id: String): AppleMusicSong =
    AppleMusicSong(
        id = id,
        name = name.orEmpty(),
        artistName = artistName.orEmpty(),
        artworkUrl = artwork?.url,
        durationMillis = durationInMillis,
        albumName = albumName,
    )
