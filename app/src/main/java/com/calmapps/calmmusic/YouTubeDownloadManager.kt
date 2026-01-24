package com.calmapps.calmmusic

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.data.ArtistEntity
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.LocalMusicScanner
import com.calmapps.calmmusic.data.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class YouTubeDownloadStatus(
    val id: String,
    val songId: String,
    val title: String,
    val artist: String,
    val progress: Float,
    val state: State,
    val errorMessage: String? = null,
) {
    enum class State { PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELED }
}

class YouTubeDownloadManager(
    private val app: CalmMusic,
    private val appScope: CoroutineScope,
) {
    private val client = OkHttpClient()

    private val _downloads = MutableStateFlow<List<YouTubeDownloadStatus>>(emptyList())
    val downloads: StateFlow<List<YouTubeDownloadStatus>> = _downloads.asStateFlow()

    private val jobsById = mutableMapOf<String, Job>()

    fun enqueueDownload(song: com.calmapps.calmmusic.ui.SongUiModel) {
        val id = UUID.randomUUID().toString()
        val initial = YouTubeDownloadStatus(
            id = id,
            songId = song.id,
            title = song.title,
            artist = song.artist,
            progress = 0f,
            state = YouTubeDownloadStatus.State.PENDING,
        )
        _downloads.value = _downloads.value + initial

        val job = appScope.launch {
            val context = app.applicationContext
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)

            if (musicDir == null) {
                updateDownload(id) { it.copy(state = YouTubeDownloadStatus.State.FAILED, errorMessage = "Storage inaccessible") }
                return@launch
            }

            if (!musicDir.exists()) musicDir.mkdirs()

            updateDownload(id) { it.copy(state = YouTubeDownloadStatus.State.IN_PROGRESS) }

            var errorMessage: String? = null
            val ok = try {
                performYouTubeDownloadInternal(
                    app = app,
                    song = song,
                    targetDir = musicDir,
                    context = context,
                    client = client,
                    onProgress = { progress ->
                        updateDownload(id) { status -> status.copy(progress = progress.coerceIn(0f, 1f)) }
                    },
                )
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: e.javaClass.simpleName ?: "Unknown error"
                false
            }

            updateDownload(id) { status ->
                status.copy(
                    progress = if (ok) 1f else status.progress,
                    state = if (ok) YouTubeDownloadStatus.State.COMPLETED else YouTubeDownloadStatus.State.FAILED,
                    errorMessage = if (ok) null else (errorMessage ?: status.errorMessage ?: "Unknown error"),
                )
            }
        }

        jobsById[id] = job
    }

    fun cancelDownload(id: String) {
        jobsById[id]?.cancel()
        jobsById.remove(id)
        updateDownload(id) { it.copy(state = YouTubeDownloadStatus.State.CANCELED) }
    }

    fun clearFinishedDownloads() {
        _downloads.value = _downloads.value.filterNot { status ->
            status.state == YouTubeDownloadStatus.State.COMPLETED ||
                    status.state == YouTubeDownloadStatus.State.FAILED ||
                    status.state == YouTubeDownloadStatus.State.CANCELED
        }
    }

    private fun updateDownload(id: String, transform: (YouTubeDownloadStatus) -> YouTubeDownloadStatus) {
        _downloads.value = _downloads.value.map { status ->
            if (status.id == id) transform(status) else status
        }
    }
}

/**
 * Shared internal implementation of the YouTube download pipeline.
 */
@OptIn(UnstableApi::class)
internal suspend fun performYouTubeDownloadInternal(
    app: CalmMusic,
    song: com.calmapps.calmmusic.ui.SongUiModel,
    targetDir: File,
    context: Context,
    client: OkHttpClient,
    onProgress: (Float) -> Unit,
): Boolean {
    var tmpFile: File? = null
    try {
        val videoId = song.id
        val TAG = "YouTubeDownload"

        val streamUrl = withContext(Dispatchers.IO) {
            try {
                val url = app.youTubeInnertubeClient.getBestAudioUrl(videoId)
                Log.i(TAG, "[$videoId] Resolved URL via InnerTube/Piped")
                url
            } catch (e: Exception) {
                Log.w(TAG, "[$videoId] InnerTube/Piped failed: ${e.message}. Falling back to NewPipe.")
                val url = app.youTubeStreamResolver.getDownloadAudioUrl(videoId)
                Log.i(TAG, "[$videoId] Resolved URL via NewPipe")
                url
            }
        }

        val safeTitle = (song.title.ifBlank { videoId })
            .replace(Regex("""[\\\\/:*?\"<>|]"""), "_")
        val fileName = "$safeTitle.m4a"
        val targetFile = File(targetDir, fileName)

        if (targetFile.exists()) {
            targetFile.delete()
        }

        tmpFile = withContext(Dispatchers.IO) {
            File.createTempFile("yt-$videoId-", ".m4a", context.cacheDir)
        }

        val downloadSuccess = withContext(Dispatchers.IO) {
            val userAgent = YouTubeStreamResolver.NEWPIPE_USER_AGENT

            val probeRequest = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", userAgent) // <--- CRITICAL FIX
                .head()
                .build()

            val (contentLength, supportsRanges) = client.newCall(probeRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Probe failed: ${response.code}")
                }
                val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                length to acceptRanges
            }

            if (contentLength > 0L && supportsRanges) {
                val chunkCount = 4.coerceAtMost(((contentLength / (5L * 1024 * 1024)).toInt() + 1).coerceAtLeast(2))
                val chunkSize = contentLength / chunkCount
                val downloaded = AtomicLong(0L)

                kotlinx.coroutines.coroutineScope {
                    repeat(chunkCount) { index ->
                        val start = index * chunkSize
                        val endExclusive = if (index == chunkCount - 1) contentLength else (start + chunkSize)
                        val end = endExclusive - 1

                        launch(Dispatchers.IO) {
                            val rangeRequest = Request.Builder()
                                .url(streamUrl)
                                .header("User-Agent", userAgent) // <--- CRITICAL FIX
                                .addHeader("Range", "bytes=$start-$end")
                                .build()

                            client.newCall(rangeRequest).execute().use { response ->
                                if (!response.isSuccessful) {
                                    throw IllegalStateException("Chunk download failed: ${response.code}")
                                }
                                val body = response.body ?: throw IllegalStateException("Empty body for chunk")

                                RandomAccessFile(tmpFile, "rw").use { raf ->
                                    val buffer = ByteArray(8 * 1024)
                                    var read: Int
                                    var offset = start
                                    while (body.byteStream().read(buffer).also { read = it } != -1) {
                                        if (read <= 0) continue
                                        synchronized(raf) {
                                            raf.seek(offset)
                                            raf.write(buffer, 0, read)
                                        }
                                        offset += read
                                        val totalSoFar = downloaded.addAndGet(read.toLong())
                                        onProgress((totalSoFar.toDouble() / contentLength.toDouble()).toFloat())
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", userAgent) // <--- CRITICAL FIX
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed: ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty body")
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L

                    FileOutputStream(tmpFile).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8 * 1024)
                            var read: Int
                            var readSoFar = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                if (total > 0) {
                                    readSoFar += read
                                    onProgress(readSoFar.toFloat() / total.toFloat())
                                }
                            }
                        }
                    }
                }
            }
            true
        }

        if (!downloadSuccess) return false

        withContext(Dispatchers.IO) {
            FileInputStream(tmpFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        withContext(Dispatchers.IO) {
            try {
                TagOptionSingleton.getInstance().isAndroid = true
                val audioFile = AudioFileIO.read(targetFile)
                val tag = audioFile.tagAndConvertOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, song.title)
                tag.setField(FieldKey.ARTIST, song.artist)
                if (!song.album.isNullOrBlank()) tag.setField(FieldKey.ALBUM, song.album)
                song.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
                song.discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }

                audioFile.commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onProgress(1f)

        withContext(Dispatchers.IO) {
            try {
                val settings = app.settingsManager
                if (!settings.includeLocalMusic.value) settings.setIncludeLocalMusic(true)

                val database = CalmMusicDatabase.getDatabase(app)
                val songDao = database.songDao()
                val albumDao = database.albumDao()
                val artistDao = database.artistDao()
                val playlistDao = database.playlistDao()

                val fileUri = android.net.Uri.fromFile(targetFile)
                val existingStreamingEntity = SongEntity(
                    id = videoId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumId = null,
                    discNumber = song.discNumber,
                    trackNumber = song.trackNumber,
                    durationMillis = song.durationMillis,
                    sourceType = "YOUTUBE",
                    audioUri = song.audioUri ?: videoId,
                    artistId = null,
                    releaseYear = null,
                    localLastModifiedMillis = null,
                    localFileSizeBytes = null,
                )

                val rawLocalSongEntity = LocalMusicScanner.buildSongEntityFromFile(
                    context = context,
                    uri = fileUri,
                    name = targetFile.name,
                    lastModified = targetFile.lastModified(),
                    fileSize = targetFile.length(),
                    existing = existingStreamingEntity,
                )
                val localSongEntity = rawLocalSongEntity.copy(sourceType = "YOUTUBE_DOWNLOAD")

                val artistId = localSongEntity.artistId
                val albumId = localSongEntity.albumId
                if (artistId != null && localSongEntity.artist.isNotBlank()) {
                    artistDao.upsertAll(listOf(ArtistEntity(id = artistId, name = localSongEntity.artist, sourceType = "YOUTUBE_DOWNLOAD")))
                }
                if (albumId != null && localSongEntity.album != null) {
                    albumDao.upsertAll(listOf(AlbumEntity(id = albumId, name = localSongEntity.album, artist = localSongEntity.artist.takeIf { it.isNotBlank() }, sourceType = "YOUTUBE_DOWNLOAD", artistId = artistId)))
                }

                songDao.upsertAll(listOf(localSongEntity))
                playlistDao.updateSongIdForAllPlaylists(oldSongId = videoId, newSongId = fileUri.toString())

                if (localSongEntity.id != videoId) {
                    songDao.deleteByIds(listOf(videoId))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return true
    } finally {
        tmpFile?.delete()
    }
}