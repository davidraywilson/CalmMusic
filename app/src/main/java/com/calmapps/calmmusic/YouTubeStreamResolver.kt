package com.calmapps.calmmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * NewPipe Extractor.
 */
class YouTubeStreamResolver(private val client: OkHttpClient = OkHttpClient()) {

    init {
        ensureInitialized(client)
    }

    companion object {
        // Default User-Agent to start with; match NewPipeExtractor's Firefox ESR 140 UA.
        const val NEWPIPE_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        @Volatile
        private var initialized: Boolean = false

        fun ensureInitialized(client: OkHttpClient) {
            if (!initialized) {
                synchronized(this) {
                    if (!initialized) {
                        NewPipe.init(OkHttpDownloader(client), Localization("en", "US"))
                        initialized = true
                    }
                }
            }
        }
    }

    suspend fun getBestAudioUrl(videoId: String): String =
        getAudioUrlWithRetry(videoId = videoId, maxBitrateKbps = null)

    suspend fun getDownloadAudioUrl(videoId: String, maxBitrateKbps: Int = 160): String =
        getAudioUrlWithRetry(videoId = videoId, maxBitrateKbps = maxBitrateKbps)

    private suspend fun getAudioUrlWithRetry(videoId: String, maxBitrateKbps: Int?): String {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return getAudioUrl(videoId, maxBitrateKbps)
            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: ""
                if (msg.contains("reloaded", ignoreCase = true) ||
                    msg.contains("ContentNotAvailable", ignoreCase = true)) {
                    synchronized(Companion) {
                        initialized = false
                    }
                    delay(500L * (attempt + 1))
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: ExtractionException("Failed to resolve audio after retries")
    }

    private suspend fun getAudioUrl(videoId: String, maxBitrateKbps: Int?): String =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/watch?v=$videoId"
            ensureInitialized(client)

            val service = NewPipe.getServiceByUrl(url) as YoutubeService
            val streamInfo = service.getStreamExtractor(url).apply { fetchPage() }

            val audioStreams = streamInfo.audioStreams
            if (audioStreams.isEmpty()) error("No audio streams found")

            val candidates = audioStreams.filter { it.averageBitrate > 0 }
            if (candidates.isEmpty()) error("No audio streams with valid bitrate")

            val chosen = if (maxBitrateKbps == null) {
                candidates.maxByOrNull { it.averageBitrate }!!
            } else {
                val capped = candidates.filter { it.averageBitrate <= maxBitrateKbps * 1000 }
                (capped.maxByOrNull { it.averageBitrate }
                    ?: candidates.maxByOrNull { it.averageBitrate }
                    ?: candidates.first())
            }

            chosen.url ?: error("No URL for chosen audio stream")
        }

        private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
            override fun execute(request: Request): Response {
                val httpMethod = request.httpMethod()
                val url = request.url()
                val headers = request.headers()
                val dataToSend = request.dataToSend()

                val requestBody = dataToSend?.let { RequestBody.create(null, it) }

                // Start with our default User-Agent, then let extractor-provided headers
                // (including cookies, referer, or a different User-Agent) override as needed.
                val builder = okhttp3.Request.Builder()
                    .method(httpMethod, requestBody)
                    .url(url)
                    .header("User-Agent", NEWPIPE_USER_AGENT)

                for ((name, values) in headers) {
                    builder.removeHeader(name)
                    for (value in values) {
                        builder.addHeader(name, value)
                    }
                }

                val response = client.newCall(builder.build()).execute()

                if (response.code == 429) {
                    response.close()
                    throw ReCaptchaException("reCaptcha Challenge requested", url)
                }

                val body = response.body?.string()
                val latestUrl = response.request.url.toString()

                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    body,
                    latestUrl,
                )
            }
        }
}
