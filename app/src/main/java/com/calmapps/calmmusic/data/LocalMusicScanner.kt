package com.calmapps.calmmusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri

object LocalMusicScanner {
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "mp4", "opus")

    /**
     * Scan the given folders for audio files.
     *
     * Implementation note: this performs a SAF traversal to discover candidate
     * audio files, then processes them in a second pass where files that are
     * new or changed since the last scan are prioritized.
     */
    suspend fun scanFolders(
        context: Context,
        folderUris: Set<String>,
        existingSongsByUri: Map<String, SongEntity> = emptyMap(),
        lastScanMillis: Long = 0L,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<SongEntity> {
        val result = mutableListOf<SongEntity>()

        data class Candidate(
            val uri: Uri,
            val name: String,
            val lastModified: Long,
            val fileSize: Long,
            val existing: SongEntity?,
        )

        val candidates = mutableListOf<Candidate>()

        for (uriString in folderUris) {
            val treeUri = try {
                uriString.toUri() } catch (_: Exception) { continue }
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
                            val uriString = uri.toString()
                            val lastModified = child.lastModified()
                            val fileSize = child.length()

                            val existing = existingSongsByUri[uriString]
                            candidates.add(
                                Candidate(
                                    uri = uri,
                                    name = name,
                                    lastModified = lastModified,
                                    fileSize = fileSize,
                                    existing = existing,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            onProgress(0, 0)
            return emptyList()
        }

        val (unchanged, changed) = candidates.partition { candidate ->
            val existing = candidate.existing
            existing != null &&
                existing.sourceType == "LOCAL_FILE" &&
                existing.localLastModifiedMillis == candidate.lastModified &&
                existing.localFileSizeBytes == candidate.fileSize
        }

        val (recentChanged, olderChanged) = changed.partition { candidate ->
            candidate.lastModified > lastScanMillis
        }

        val orderedCandidates =
            (recentChanged.sortedByDescending { it.lastModified } +
                olderChanged.sortedByDescending { it.lastModified } +
                unchanged)

        val total = orderedCandidates.size
        var processed = 0
        var lastProgressUpdateTime = 0L

        suspend fun maybeReportProgress() {
            val now = System.currentTimeMillis()
            if (processed == total || now - lastProgressUpdateTime > 200L) {
                lastProgressUpdateTime = now
                onProgress(processed, total)
            }
        }

        for (candidate in orderedCandidates) {
            val existing = candidate.existing
            if (existing != null &&
                existing.sourceType == "LOCAL_FILE" &&
                existing.localLastModifiedMillis == candidate.lastModified &&
                existing.localFileSizeBytes == candidate.fileSize
            ) {
                result.add(existing)
                processed++
                maybeReportProgress()
                continue
            }

            val meta = extractMetadata(context, candidate.uri)
            val titleFromName = candidate.name.substringBeforeLast('.', candidate.name)

            val trackArtist = meta.artist.orEmpty().trim()
            val primaryArtist = (meta.albumArtist ?: meta.artist).orEmpty().trim()
            val albumName = meta.album?.trim()?.takeIf { it.isNotBlank() }

            val albumId = if (albumName != null) {
                "LOCAL_FILE:${primaryArtist}:${albumName}"
            } else null

            val artistId = if (primaryArtist.isNotBlank()) {
                "LOCAL_FILE:$primaryArtist"
            } else null

            val uriString = candidate.uri.toString()
            result.add(
                SongEntity(
                    id = uriString,
                    title = meta.title ?: titleFromName,
                    artist = trackArtist,
                    album = meta.album,
                    albumId = albumId,
                    discNumber = meta.discNumber,
                    trackNumber = meta.trackNumber,
                    durationMillis = meta.durationMillis,
                    sourceType = "LOCAL_FILE",
                    audioUri = uriString,
                    artistId = artistId,
                    releaseYear = meta.year,
                    localLastModifiedMillis = candidate.lastModified,
                    localFileSizeBytes = candidate.fileSize,
                ),
            )

            processed++
            maybeReportProgress()
        }

        onProgress(processed, total.coerceAtLeast(processed))

        return result
    }

    private data class LocalMetadata(
        val title: String?,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val discNumber: Int?,
        val trackNumber: Int?,
        val durationMillis: Long?,
        val year: Int?,
    )

    private fun extractMetadata(context: Context, uri: Uri): LocalMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val rawAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            val title = rawTitle.normalizeTagString()
            val artist = rawArtist.normalizeTagString()
            val albumArtist = rawAlbumArtist.normalizeTagString()
            val album = rawAlbum.normalizeTagString()

            val discStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)

            LocalMetadata(
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                discNumber = discStr?.substringBefore('/')?.trim()?.toIntOrNull(),
                trackNumber = trackStr?.substringBefore('/')?.trim()?.toIntOrNull(),
                durationMillis = durationStr?.toLongOrNull(),
                year = yearStr?.take(4)?.trim()?.toIntOrNull(),
            )
        } catch (_: Exception) {
            LocalMetadata(null, null, null, null, null, null, null, null)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}

private fun String?.normalizeTagString(): String? {
    if (this == null) return null
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return trimmed.fixCommonTagMojibake()
}

/**
 * Attempt to fix common mojibake sequences in ID3 tags without re-decoding
 * bytes. We only replace known bad sequences so valid text is left alone.
 */
private fun String.fixCommonTagMojibake(): String {
    var fixed = this

    // Map of common UTF-8-as-Latin1 sequences to their intended characters.
    val replacements = mapOf(
        "â€™" to "’", // right single quotation mark
        "â€˜" to "‘", // left single quotation mark
        "â€œ" to "“", // left double quotation mark
        "â€" to "”", // right double quotation mark
        "â€“" to "–", // en dash
        "â€”" to "—", // em dash
    )

    for ((bad, good) in replacements) {
        if (fixed.contains(bad)) {
            fixed = fixed.replace(bad, good)
        }
    }

    return fixed
}
