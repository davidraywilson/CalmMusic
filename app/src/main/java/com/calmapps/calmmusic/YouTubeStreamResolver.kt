package com.calmapps.calmmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeService

/**
 * Resolves a playable audio URL for a YouTube video (by videoId) using
 * NewPipe Extractor. This keeps the rest of the app unaware of NewPipe
 * specifics and exposes a simple suspend function.
 */
class YouTubeStreamResolver(private val client: OkHttpClient = OkHttpClient()) {

    init {
        // Initialize NewPipe once with an OkHttp-backed Downloader.
        ensureInitialized(client)
    }

    companion object {
        private const val USER_AGENT: String = "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0"

        @Volatile
        private var initialized: Boolean = false

        fun ensureInitialized(client: OkHttpClient) {
            if (!initialized) {
                synchronized(this) {
                    if (!initialized) {
                        NewPipe.init(OkHttpDownloader(client), Localization.DEFAULT)
                        initialized = true
                    }
                }
            }
        }
    }

    /**
     * Resolve the best available audio stream URL for the given [videoId].
     * This is intended for playback, so it prefers the highest available
     * bitrate.
     */
    suspend fun getBestAudioUrl(videoId: String): String =
        getAudioUrl(videoId = videoId, maxBitrateKbps = null)

    /**
     * Resolve a download-friendly audio stream URL for [videoId], capping the
     * bitrate to keep file sizes reasonable (and reduce download time) while
     * still preferring higher-quality streams within that cap.
     */
    suspend fun getDownloadAudioUrl(videoId: String, maxBitrateKbps: Int = 160): String =
        getAudioUrl(videoId = videoId, maxBitrateKbps = maxBitrateKbps)

    private suspend fun getAudioUrl(videoId: String, maxBitrateKbps: Int?): String =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/watch?v=$videoId"
            try {
                val service = NewPipe.getServiceByUrl(url) as YoutubeService
                val streamInfo = service.getStreamExtractor(url).apply { fetchPage() }

                val audioStreams = streamInfo.audioStreams
                if (audioStreams.isEmpty()) error("No audio streams")

                val candidates = audioStreams.filter { it.averageBitrate > 0 }
                if (candidates.isEmpty()) error("No audio streams with bitrate")

                val chosen = if (maxBitrateKbps == null) {
                    // Playback: highest available bitrate
                    candidates.maxByOrNull { it.averageBitrate }!!
                } else {
                    // Downloads: try to pick the highest bitrate <= cap; if none,
                    // fall back to absolute highest.
                    val capped = candidates.filter { it.averageBitrate <= maxBitrateKbps * 1000 }
                    (capped.maxByOrNull { it.averageBitrate }
                        ?: candidates.maxByOrNull { it.averageBitrate }
                        ?: candidates.first())
                }

                chosen.url ?: error("No URL for chosen audio stream")
            } catch (e: ExtractionException) {
                throw e
            } catch (e: Exception) {
                throw e
            }
        }

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

        override fun execute(request: Request): Response {
            val httpMethod = request.httpMethod()
            val url = request.url()
            val headers = request.headers()
            val dataToSend = request.dataToSend()

            var requestBody: RequestBody? = null
            if (dataToSend != null) {
                requestBody = RequestBody.create(null, dataToSend)
            }

            val builder = okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .addHeader("User-Agent", USER_AGENT)

            // Copy headers from NewPipe request
            for ((headerName, headerValues) in headers) {
                if (headerValues.size > 1) {
                    builder.removeHeader(headerName)
                    for (value in headerValues) {
                        builder.addHeader(headerName, value)
                    }
                } else if (headerValues.size == 1) {
                    builder.header(headerName, headerValues[0])
                }
            }

            val response = client.newCall(builder.build()).execute()
            if (response.code == 429) {
                response.close()
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }

            val body = response.body
            val responseBodyToReturn = body?.string()
            val latestUrl = response.request.url.toString()

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBodyToReturn,
                latestUrl,
            )
        }
    }
}
