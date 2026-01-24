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
import java.io.IOException

/**
 * Resolves a playable audio URL for a YouTube video (by videoId) using
 * NewPipe Extractor.
 */
class YouTubeStreamResolver(private val client: OkHttpClient = OkHttpClient()) {

    init {
        ensureInitialized(client)
    }

    companion object {
        private const val USER_AGENT: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        private const val MAGIC_COOKIES: String = "SOCS=CAI; VISITOR_INFO1_LIVE=i7Sm6Qgj0lE; CONSENT=YES+cb.20210328-17-p0.en+FX+475; PREF=tz=UTC&hl=en"

        @Volatile
        private var initialized: Boolean = false

        fun ensureInitialized(client: OkHttpClient) {
            if (!initialized) {
                synchronized(this) {
                    if (!initialized) {
                        val localization = Localization("US", "en")
                        NewPipe.init(OkHttpDownloader(client), localization)
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
        // is often temporary or clears up on a fresh connection attempt.
        repeat(3) { attempt ->
            try {
                return getAudioUrl(videoId, maxBitrateKbps)
            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: ""
                if (msg.contains("reloaded", ignoreCase = true) ||
                    msg.contains("ContentNotAvailable", ignoreCase = true)) {
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

            // Ensure initialization (safe to call multiple times)
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

            var requestBody: RequestBody? = null
            if (dataToSend != null) {
                requestBody = RequestBody.create(null, dataToSend)
            }

            val builder = okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                // Always overwrite these three headers to ensure the "Magic" bypass works
                .header("User-Agent", USER_AGENT)
                .header("Cookie", MAGIC_COOKIES)
                .header("Referer", "https://www.youtube.com/")

            // Copy other headers from NewPipe, but skip the ones we manually set
            for ((headerName, headerValues) in headers) {
                if (headerName.equals("User-Agent", ignoreCase = true) ||
                    headerName.equals("Cookie", ignoreCase = true) ||
                    headerName.equals("Referer", ignoreCase = true)) {
                    continue
                }

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