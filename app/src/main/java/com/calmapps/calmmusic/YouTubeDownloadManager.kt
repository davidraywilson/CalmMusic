package com.calmapps.calmmusic

import android.content.Context
import android.os.Environment
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

/**
 * Central manager for YouTube downloads.
 * * FEATURES:
 * 1. App-Specific Storage: Saves directly to /Android/data/.../files/Music (No SAF permissions).
 * 2. Chunked Downloading: Uses concurrent byte-range requests for faster speeds.
 * 3. Auto-Library Update: Immediately inserts the song/artist/album into the Room database.
 */
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

            val ok = performYouTubeDownloadInternal(
                app = app,
                song = song,
                targetDir = musicDir,
                context = context,
                client = client,
                onProgress = { progress ->
                    updateDownload(id) { status -> status.copy(progress = progress.coerceIn(0f, 1f)) }
                },
            )

            updateDownload(id) { status ->
                status.copy(
                    progress = if (ok) 1f else status.progress,
                    state = if (ok) YouTubeDownloadStatus.State.COMPLETED else YouTubeDownloadStatus.State.FAILED,
                    errorMessage = if (ok) null else status.errorMessage,
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
    return try {
        val videoId = song.id

        val streamUrl = withContext(Dispatchers.IO) {
            app.youTubeStreamResolver.getDownloadAudioUrl(videoId)
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
            val probeRequest = Request.Builder()
                .url(streamUrl)
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
            try {
                TagOptionSingleton.getInstance().isAndroid = true
                val audioFile = AudioFileIO.read(tmpFile)
                val tag = audioFile.tagAndConvertOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, song.title)
                tag.setField(FieldKey.ARTIST, song.artist)

                if (!song.album.isNullOrBlank()) {
                    tag.setField(FieldKey.ALBUM, song.album)
                }

                song.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
                song.discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }

                audioFile.commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        withContext(Dispatchers.IO) {
            FileInputStream(tmpFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        onProgress(1f)

        withContext(Dispatchers.IO) {
            val settings = app.settingsManager
            if (!settings.includeLocalMusic.value) {
                settings.setIncludeLocalMusic(true)
            }
            val database = CalmMusicDatabase.getDatabase(app)
            val songDao = database.songDao()
            val albumDao = database.albumDao()
            val artistDao = database.artistDao()
            val playlistDao = database.playlistDao()

            val fileUri = android.net.Uri.fromFile(targetFile)
            val fileUriString = fileUri.toString()
            val localLastModified = targetFile.lastModified()
            val localSize = targetFile.length()

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

            val localSongEntity = LocalMusicScanner.buildSongEntityFromFile(
                context = context,
                uri = fileUri,
                name = targetFile.name,
                lastModified = localLastModified,
                fileSize = localSize,
                existing = existingStreamingEntity,
            )

            val artistId = localSongEntity.artistId
            val albumId = localSongEntity.albumId
            val displayArtistName = localSongEntity.artist
            val displayAlbumName = localSongEntity.album

            if (artistId != null && displayArtistName.isNotBlank()) {
                val artistEntity = ArtistEntity(
                    id = artistId,
                    name = displayArtistName,
                    sourceType = "LOCAL_FILE"
                )
                artistDao.upsertAll(listOf(artistEntity))
            }

            if (albumId != null && displayAlbumName != null) {
                val albumEntity = AlbumEntity(
                    id = albumId,
                    name = displayAlbumName,
                    artist = displayArtistName.takeIf { it.isNotBlank() },
                    sourceType = "LOCAL_FILE",
                    artistId = artistId,
                )
                albumDao.upsertAll(listOf(albumEntity))
            }

            songDao.upsertAll(listOf(localSongEntity))
            playlistDao.updateSongIdForAllPlaylists(oldSongId = videoId, newSongId = fileUriString)

            if (localSongEntity.id != videoId) {
                songDao.deleteByIds(listOf(videoId))
            }
        }

        true
    } catch (_: Exception) {
        false
    } finally {
        tmpFile?.delete()
    }
}