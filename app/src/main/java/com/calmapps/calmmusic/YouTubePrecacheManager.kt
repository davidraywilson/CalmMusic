package com.calmapps.calmmusic

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manager responsible for pre-resolving YouTube audio stream URLs via
 * NewPipe and keeping a small logical window of URLs cached in memory.
 *
 * This keeps the most relevant YouTube songs "warm" so playback can start
 * quickly without blocking on NewPipe extraction.
 */
class YouTubePrecacheManager(private val app: CalmMusic) {

    private data class Entry(
        val url: String,
        val resolverLabel: String?,
        val expiresAtMillis: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefetchBytes: Long = 3L * 1024L * 1024L // 3 MB

    private val urlCache = mutableMapOf<String, Entry>()

    private val searchWindowIds = mutableSetOf<String>()
    private val queueWindowIds = mutableSetOf<String>()

    private val ttlMs: Long = 30L * 60L * 1000L // 30 minutes

    fun getCachedUrl(videoId: String, now: Long = System.currentTimeMillis()): String? {
        synchronized(urlCache) {
            val entry = urlCache[videoId] ?: return null
            if (entry.expiresAtMillis <= now) {
                urlCache.remove(videoId)
                return null
            }
            return entry.url
        }
    }

    fun getCachedWithLabel(videoId: String, now: Long = System.currentTimeMillis()): Pair<String, String?>? {
        synchronized(urlCache) {
            val entry = urlCache[videoId] ?: return null
            if (entry.expiresAtMillis <= now) {
                urlCache.remove(videoId)
                return null
            }
            return entry.url to entry.resolverLabel
        }
    }

    fun putUrl(videoId: String, url: String, resolverLabel: String?, now: Long = System.currentTimeMillis()) {
        val entry = Entry(url = url, resolverLabel = resolverLabel, expiresAtMillis = now + ttlMs)
        synchronized(urlCache) {
            urlCache[videoId] = entry
        }
    }

    fun precacheSearchResults(videoIds: List<String>) {
        synchronized(this) {
            searchWindowIds.clear()
            searchWindowIds.addAll(videoIds)
        }
        precache(videoIds)
        trimToLogicalWindow()
    }

    fun clearSearchWindow() {
        synchronized(this) {
            searchWindowIds.clear()
        }
        trimToLogicalWindow()
    }

    fun updateQueueWindow(videoIds: List<String>) {
        synchronized(this) {
            queueWindowIds.clear()
            queueWindowIds.addAll(videoIds)
        }
        precache(videoIds)
        trimToLogicalWindow()
    }

    private fun trimToLogicalWindow() {
        val now = System.currentTimeMillis()
        val keep: Set<String> = synchronized(this) {
            (searchWindowIds + queueWindowIds).toSet()
        }
        synchronized(urlCache) {
            val it = urlCache.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.value.expiresAtMillis <= now) {
                    it.remove()
                    continue
                }
                if (keep.isNotEmpty() && entry.key !in keep) {
                    it.remove()
                }
            }
        }
    }

    private fun precache(videoIds: List<String>) {
        videoIds.forEach { videoId ->
            scope.launch {
                try {
                    val now = System.currentTimeMillis()
                    if (getCachedUrl(videoId, now) != null) {
                        return@launch
                    }

                    val (url, resolverLabel) = try {
                        app.youTubeInnertubeClient.getBestAudioUrl(videoId) to "Innertube/Piped"
                    } catch (_: Exception) {
                        app.youTubeStreamResolver.getBestAudioUrl(videoId) to "NewPipe"
                    }
                    putUrl(videoId, url, resolverLabel, now)

                    prefetchBytesIntoCache(videoId, url)
                } catch (_: Exception) {
                    // Ignore prefetch failures; playback will resolve on demand.
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun prefetchBytesIntoCache(videoId: String, url: String) {
        val dataSource = app.cacheDataSourceFactory.createDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(url.toUri())
            .setKey(videoId)
            .setPosition(0L)
            .setLength(prefetchBytes)
            .build()

        try {
            var remaining = prefetchBytes
            val buffer = ByteArray(32 * 1024)
            dataSource.open(dataSpec)
            while (remaining > 0) {
                val toRead = if (remaining >= buffer.size) buffer.size else remaining.toInt()
                val read = dataSource.read(buffer, 0, toRead)
                if (read == C.RESULT_END_OF_INPUT || read <= 0) break
                remaining -= read
            }
        } catch (_: Exception) {
            // Best-effort only; ignore failures.
        } finally {
            try {
                dataSource.close()
            } catch (_: Exception) {
            }
        }
    }
}
