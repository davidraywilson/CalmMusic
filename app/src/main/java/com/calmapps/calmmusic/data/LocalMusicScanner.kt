package com.calmapps.calmmusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object LocalMusicScanner {
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "mp4")

    suspend fun scanFolders(
        context: Context,
        folderUris: Set<String>,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<SongEntity> {
        val result = mutableListOf<SongEntity>()
        val total = countAudioFiles(context, folderUris)
        if (total <= 0) {
            onProgress(0, 0)
            return emptyList()
        }
        var processed = 0

        for (uriString in folderUris) {
            val treeUri = try { Uri.parse(uriString) } catch (_: Exception) { continue }
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            val stack = ArrayDeque<DocumentFile>()
            stack.add(root)

            while (stack.isNotEmpty()) {
                val dir = stack.removeFirst()
                val children = try { dir.listFiles().toList() } catch (_: Exception) { emptyList() }

                for (child in children) {
                    if (child.isDirectory) {
                        stack.add(child)
                    } else if (child.isFile) {
                        val name = child.name ?: continue
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in AUDIO_EXTENSIONS) {
                            val uri = child.uri
                            val meta = extractMetadata(context, uri)
                            val titleFromName = name.substringBeforeLast('.', name)

                            // The "Display Artist" for the track (e.g., "NF & mgk")
                            val trackArtist = meta.artist.orEmpty().trim()

                            // The "Primary Artist" for the library (prefers Album Artist tag)
                            val primaryArtist = (meta.albumArtist ?: meta.artist).orEmpty().trim()

                            val albumName = meta.album?.trim()?.takeIf { it.isNotBlank() }

                            // Grouping is now strictly by Album Artist + Album Name
                            val albumId = if (albumName != null) {
                                "LOCAL_FILE:${primaryArtist}:${albumName}"
                            } else null

                            val artistId = if (primaryArtist.isNotBlank()) {
                                "LOCAL_FILE:$primaryArtist"
                            } else null

                            result.add(
                                SongEntity(
                                    id = uri.toString(),
                                    title = meta.title ?: titleFromName,
                                    artist = trackArtist, // Keep featured info for track display
                                    album = meta.album,
                                    albumId = albumId,
                                    trackNumber = meta.trackNumber,
                                    durationMillis = meta.durationMillis,
                                    sourceType = "LOCAL_FILE",
                                    audioUri = uri.toString(),
                                    artistId = artistId, // Group under primary artist
                                ),
                            )

                            processed++
                            onProgress(processed, total)
                        }
                    }
                }
            }
        }
        return result
    }

    private suspend fun countAudioFiles(context: Context, folderUris: Set<String>): Int {
        var total = 0
        for (uriString in folderUris) {
            val treeUri = try { Uri.parse(uriString) } catch (_: Exception) { continue }
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            val stack = ArrayDeque<DocumentFile>()
            stack.add(root)
            while (stack.isNotEmpty()) {
                val dir = stack.removeFirst()
                val children = try { dir.listFiles().toList() } catch (_: Exception) { emptyList() }
                for (child in children) {
                    if (child.isDirectory) stack.add(child)
                    else if (child.isFile && child.name?.substringAfterLast('.', "")?.lowercase() in AUDIO_EXTENSIONS) total++
                }
            }
        }
        return total
    }

    private data class LocalMetadata(
        val title: String?,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val trackNumber: Int?,
        val durationMillis: Long?,
    )

    private fun extractMetadata(context: Context, uri: Uri): LocalMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            LocalMetadata(
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                trackNumber = trackStr?.substringBefore('/')?.toIntOrNull(),
                durationMillis = durationStr?.toLongOrNull(),
            )
        } catch (_: Exception) {
            LocalMetadata(null, null, null, null, null, null)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}