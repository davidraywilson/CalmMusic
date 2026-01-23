package com.calmapps.calmmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Minimal YouTube Music search client using the Innertube JSON API.
 *
 * This is intentionally small and focused: it only implements anonymous
 * search for songs/albums and returns just the metadata CalmMusic needs
 * (videoId, title, artist, optional album, duration).
 */
interface YouTubeMusicInnertubeClient {
    suspend fun searchSongs(query: String, limit: Int = 25): List<InnertubeSongResult>
    suspend fun searchAlbums(query: String, limit: Int = 25): List<InnertubeAlbumResult>

    suspend fun getBestAudioUrl(videoId: String): String
}


data class InnertubeSongResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMillis: Long?,
)

data class InnertubeAlbumResult(
    val albumId: String,
    val title: String,
    val artist: String?,
    val year: Int?,
)

data class InnertubeSearchResults(
    val songs: List<InnertubeSongResult>,
    val albums: List<InnertubeAlbumResult>,
)

private enum class MusicSearchFilter {
    NONE,
    SONGS,
    ALBUMS,
}

private const val PARAMS_SONGS: String = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
private const val PARAMS_ALBUMS: String = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"

internal class YouTubeMusicInnertubeClientImpl(
    private val httpClient: OkHttpClient,
) : YouTubeMusicInnertubeClient {

    private val apiKey: String = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private val baseUrl: String = "https://youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false&key=$apiKey"

    private val pipedBaseUrl: String = "https://pipedapi.kavin.rocks/streams/"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun searchSongs(query: String, limit: Int): List<InnertubeSongResult> {
        if (query.isBlank() || limit <= 0) return emptyList()
        return searchInternal(
            query = query,
            filter = MusicSearchFilter.SONGS,
            limitSongs = limit,
            limitAlbums = 0,
        ).songs
    }

    override suspend fun searchAlbums(query: String, limit: Int): List<InnertubeAlbumResult> {
        if (query.isBlank() || limit <= 0) return emptyList()
        return searchInternal(
            query = query,
            filter = MusicSearchFilter.ALBUMS,
            limitSongs = 0,
            limitAlbums = limit,
        ).albums
    }

    private suspend fun searchInternal(
        query: String,
        filter: MusicSearchFilter,
        limitSongs: Int,
        limitAlbums: Int,
    ): InnertubeSearchResults {
        if (query.isBlank() || (limitSongs <= 0 && limitAlbums <= 0)) {
            return InnertubeSearchResults(emptyList(), emptyList())
        }

        return withContext(Dispatchers.IO) {
            val bodyJson = buildSearchRequestBody(query, filter)
            val requestBody = bodyJson.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext InnertubeSearchResults(emptyList(), emptyList())
                val bodyString = response.body?.string() ?: return@withContext InnertubeSearchResults(emptyList(), emptyList())

                parseSearchResults(JSONObject(bodyString), limitSongs, limitAlbums)
            }
        }
    }

    private fun buildSearchRequestBody(
        query: String,
        filter: MusicSearchFilter,
    ): JSONObject {
        val client = JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20250101.01.00")
            put("hl", "en")
            put("gl", "US")
        }

        val context = JSONObject().apply {
            put("client", client)
        }

        val params = when (filter) {
            MusicSearchFilter.SONGS -> PARAMS_SONGS
            MusicSearchFilter.ALBUMS -> PARAMS_ALBUMS
            MusicSearchFilter.NONE -> null
        }

        return JSONObject().apply {
            put("context", context)
            put("query", query)
            if (!params.isNullOrBlank()) {
                put("params", params)
            }
        }
    }

    private fun parseSearchResults(
        root: JSONObject,
        limitSongs: Int,
        limitAlbums: Int,
    ): InnertubeSearchResults {
        val songs = mutableListOf<InnertubeSongResult>()
        val albums = mutableListOf<InnertubeAlbumResult>()

        val contentsRoot = root.optJSONObject("contents")
            ?: return InnertubeSearchResults(emptyList(), emptyList())

        val directSectionList = contentsRoot.optJSONObject("sectionListRenderer")

        val sectionList = directSectionList ?: run {
            val tabbed = contentsRoot.optJSONObject("tabbedSearchResultsRenderer")
                ?: return InnertubeSearchResults(emptyList(), emptyList())
            val tabs = tabbed.optJSONArray("tabs")
                ?: return InnertubeSearchResults(emptyList(), emptyList())

            var found: JSONObject? = null
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i) ?: continue
                val tabRenderer = tab.optJSONObject("tabRenderer") ?: continue
                val selected = tabRenderer.optBoolean("selected", false)
                val content = tabRenderer.optJSONObject("content") ?: continue
                val candidate = content.optJSONObject("sectionListRenderer")
                if (candidate != null && (selected || found == null)) {
                    found = candidate
                    if (selected) break
                }
            }
            found ?: return InnertubeSearchResults(emptyList(), emptyList())
        }

        val contents = sectionList.optJSONArray("contents")
            ?: return InnertubeSearchResults(emptyList(), emptyList())

        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i)
                ?.optJSONObject("musicShelfRenderer") ?: continue

            val items = section.optJSONArray("contents") ?: continue

            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j)
                    ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue

                if (songs.size < limitSongs) {
                    val song = parseSongItem(item)
                    if (song != null) {
                        songs += song
                    }
                }

                if (albums.size < limitAlbums) {
                    val album = parseAlbumItem(item)
                    if (album != null) {
                        albums += album
                    }
                }

                if (songs.size >= limitSongs && albums.size >= limitAlbums) {
                    break
                }
            }

            if (songs.size >= limitSongs && albums.size >= limitAlbums) {
                break
            }
        }

        return InnertubeSearchResults(songs, albums)
    }

    override suspend fun getBestAudioUrl(videoId: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(pipedBaseUrl + videoId)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Piped request failed: ${response.code}")
            }
            val bodyString = response.body?.string() ?: error("Empty Piped body")
            val root = JSONObject(bodyString)
            val audioStreams = root.optJSONArray("audioStreams")
                ?: error("No audioStreams in Piped response")

            var bestAudio: JSONObject? = null
            for (i in 0 until audioStreams.length()) {
                val stream = audioStreams.optJSONObject(i) ?: continue
                val url = stream.optString("url")
                if (url.isNullOrBlank()) continue

                if (bestAudio == null) {
                    bestAudio = stream
                    continue
                }
                val currentBitrate = stream.optInt("bitrate", 0)
                val bestBitrate = bestAudio?.optInt("bitrate", 0) ?: 0
                if (currentBitrate > bestBitrate) {
                    bestAudio = stream
                }
            }

            val url = bestAudio?.optString("url")?.takeIf { it.isNotBlank() }
                ?: error("No audio URL in Piped response")
            url
        }
    }

    private fun parseSongItem(item: JSONObject): InnertubeSongResult? {
        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        if (flexColumns.length() == 0) return null

        val mainColumn = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?: return null

        val text = mainColumn.optJSONObject("text")
        val titleRuns = text?.optJSONArray("runs")
        if (titleRuns == null || titleRuns.length() == 0) return null

        val titleRun = titleRuns.optJSONObject(0)
        val title = titleRun?.optString("text").orEmpty()
        if (title.isBlank()) return null

        val videoId = titleRun
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            .takeUnless { it.isNullOrBlank() }
            ?: item.optJSONObject("playlistItemData")
                ?.optString("videoId")
                .takeUnless { it.isNullOrBlank() }
            ?: return null

        var artist: String? = null
        var album: String? = null
        var durationText: String? = null

        for (i in 1 until flexColumns.length()) {
            val subtitleColumn = flexColumns.optJSONObject(i)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?: continue

            val subtitleText = subtitleColumn.optJSONObject("text") ?: continue
            val subtitleRuns = subtitleText.optJSONArray("runs") ?: continue

            for (k in 0 until subtitleRuns.length()) {
                val run = subtitleRuns.optJSONObject(k) ?: continue
                val textValue = run.optString("text").orEmpty().trim()
                if (textValue.isEmpty()) continue

                val navEndpoint = run.optJSONObject("navigationEndpoint")
                val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
                val browseId = browseEndpoint?.optString("browseId")

                when {
                    artist == null && browseId != null && browseId.startsWith("UC") -> {
                        artist = textValue
                    }
                    album == null && browseId != null && browseId.startsWith("MPRE") -> {
                        album = textValue
                    }
                    artist == null && browseId.isNullOrBlank() -> {
                        artist = textValue
                    }
                }

                if (durationText == null && textValue.contains(":")) {
                    durationText = textValue
                }
            }
        }

        val safeArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"
        val durationMillis = durationText?.let { parseDurationToMillis(it) }

        return InnertubeSongResult(
            videoId = videoId,
            title = title,
            artist = safeArtist,
            album = album,
            durationMillis = durationMillis,
        )
    }

    private fun parseAlbumItem(item: JSONObject): InnertubeAlbumResult? {
        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        if (flexColumns.length() == 0) return null

        val mainColumn = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?: return null

        val text = mainColumn.optJSONObject("text")
        val titleRuns = text?.optJSONArray("runs")
        if (titleRuns == null || titleRuns.length() == 0) return null

        val titleRun = titleRuns.optJSONObject(0)
        val title = titleRun?.optString("text").orEmpty()
        if (title.isBlank()) return null

        val topNavEndpoint = item.optJSONObject("navigationEndpoint")
        val topBrowseEndpoint = topNavEndpoint?.optJSONObject("browseEndpoint")

        val runBrowseEndpoint = titleRun
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")

        val albumId = (topBrowseEndpoint?.optString("browseId")
            ?: runBrowseEndpoint?.optString("browseId"))
            .takeUnless { it.isNullOrBlank() }
            ?: return null

        var artist: String? = null
        var year: Int? = null

        for (i in 1 until flexColumns.length()) {
            val subtitleColumn = flexColumns.optJSONObject(i)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?: continue

            val subtitleText = subtitleColumn.optJSONObject("text") ?: continue
            val subtitleRuns = subtitleText.optJSONArray("runs") ?: continue

            for (k in 0 until subtitleRuns.length()) {
                val run = subtitleRuns.optJSONObject(k) ?: continue
                val textValue = run.optString("text").orEmpty().trim()
                if (textValue.isEmpty()) continue

                val navEndpoint = run.optJSONObject("navigationEndpoint")
                val subBrowseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
                val subBrowseId = subBrowseEndpoint?.optString("browseId")

                if (artist == null && subBrowseId != null && subBrowseId.startsWith("UC")) {
                    artist = textValue
                } else if (artist == null && subBrowseId.isNullOrBlank()) {
                    artist = textValue
                }

                if (year == null) {
                    val maybeYear = textValue.toIntOrNull()
                    if (maybeYear != null && maybeYear in 1900..2100) {
                        year = maybeYear
                    }
                }
            }
        }

        return InnertubeAlbumResult(
            albumId = albumId,
            title = title,
            artist = artist,
            year = year,
        )
    }

    private fun parseDurationToMillis(text: String): Long? {
        val parts = text.trim().split(":")
        if (parts.size < 2) return null
        val numbers = parts.mapNotNull { it.toIntOrNull() }
        if (numbers.size != parts.size) return null

        val seconds = when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            else -> return null
        }
        return (seconds * 1000L).coerceAtLeast(0L)
    }
}
