package com.calmapps.calmtunes.data

import android.net.Uri
import android.os.Environment
import com.calmapps.calmtunes.CalmTunes
import com.calmapps.calmtunes.ui.AlbumUiModel
import com.calmapps.calmtunes.ui.ArtistUiModel
import com.calmapps.calmtunes.ui.SongUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for performing library-related data work against the
 * local Room database and filesystem scanners.
 */
class LibraryRepository(
    private val app: CalmTunes,
) {

    private val database: CalmTunesDatabase by lazy { CalmTunesDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val albumDao by lazy { database.albumDao() }
    private val artistDao by lazy { database.artistDao() }

    data class LocalResyncStats(
        val totalDiscovered: Int,
        val skippedUnchanged: Int,
        val indexedNewOrUpdated: Int,
        val deletedMissing: Int,
    )

    data class LocalResyncResult(
        val songs: List<SongUiModel>,
        val albums: List<AlbumUiModel>,
        val artists: List<ArtistUiModel>,
        val errorMessage: String?,
        val stats: LocalResyncStats? = null,
    )

    suspend fun resyncLocalLibrary(
        includeLocal: Boolean,
        folders: Set<String>,
        onScanProgress: (Float) -> Unit,
        onIngestProgress: (Float) -> Unit,
    ): LocalResyncResult {
        var error: String? = null
        var stats: LocalResyncStats? = null

        try {
            if (!includeLocal) {
                onScanProgress(1f)
                onIngestProgress(0f)
                withContext(Dispatchers.IO) {
                    songDao.deleteBySourceType(SourceType.LOCAL_FILE)
                    albumDao.deleteBySourceType(SourceType.LOCAL_FILE)
                    artistDao.deleteBySourceType(SourceType.LOCAL_FILE)
                }
                onIngestProgress(1f)
            } else {
                if (folders.isNotEmpty()) {
                    try {
                        val lastScanMillis = app.settingsManager.getLastLocalLibraryScanMillis()

                        val existingAlbumsMap = withContext(Dispatchers.IO) {
                            albumDao.getAllAlbums()
                                .filter { it.sourceType == SourceType.LOCAL_FILE }
                                .associateBy { it.id }
                        }

                        val (scannedAudio, existingLocalSongs) = withContext(Dispatchers.IO) {
                            val existingLocalSongs = songDao.getSongsBySourceType(SourceType.LOCAL_FILE)
                            val existingByUri = existingLocalSongs.associateBy { it.audioUri }
                            val scanned = LocalMusicScanner.scanFolders(
                                context = app,
                                folderUris = folders,
                                existingSongsByUri = existingByUri,
                                lastScanMillis = lastScanMillis,
                            ) { processed, total ->
                                val progress = if (total > 0) {
                                    (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    1f
                                }
                                onScanProgress(progress)
                            }
                            scanned to existingLocalSongs
                        }

                        onIngestProgress(0f)

                        val normalizedLocalEntities = scannedAudio.map { it.song }

                        withContext(Dispatchers.IO) {
                            val artistEntities = mutableListOf<ArtistEntity>()

                            fun String.normalize() = trim().replace(Regex("\\s+"), " ").lowercase()

                            normalizedLocalEntities.forEach { entity ->
                                val id = entity.artistId ?: return@forEach
                                val name = entity.artist.takeIf { it.isNotBlank() } ?: id.removePrefix("LOCAL_FILE:")
                                artistEntities.add(ArtistEntity(id, name, entity.sourceType))
                            }

                            scannedAudio.forEach { wrapper ->
                                val entity = wrapper.song
                                val explicit = wrapper.albumArtist

                                // If explicit is missing (unchanged file), check our preserved map
                                val effectiveAlbumArtist = explicit?.takeIf { it.isNotBlank() }
                                    ?: entity.albumId?.let { existingAlbumsMap[it]?.artist }

                                if (!effectiveAlbumArtist.isNullOrBlank()) {
                                    val id = "LOCAL_FILE:" + effectiveAlbumArtist.normalize()
                                    artistEntities.add(ArtistEntity(id, effectiveAlbumArtist, wrapper.song.sourceType))
                                }
                            }

                            val uniqueArtists = artistEntities.distinctBy { it.id }

                            onIngestProgress(0.1f)

                            // For albums with an explicit Album Artist tag, the
                            // albumId includes the artist component. For albums
                            // without one, the albumId is name-only (LOCAL_FILE::name)
                            // and we need to infer the best display artist.

                            // First, collect all (albumId -> list of track artists)
                            // so we can pick the most common one for name-only albums.
                            val trackArtistsByAlbumId = normalizedLocalEntities
                                .filter { it.albumId != null && it.artist.isNotBlank() }
                                .groupBy { it.albumId!! }
                                .mapValues { (_, songs) ->
                                    songs.map { it.artist }
                                }

                            val albumEntities: List<AlbumEntity> = scannedAudio
                                .mapNotNull { wrapper ->
                                    val entity = wrapper.song
                                    val id = entity.albumId ?: return@mapNotNull null
                                    val name = entity.album ?: return@mapNotNull null

                                    val hasExplicitAlbumArtist = wrapper.albumArtist?.isNotBlank() == true

                                    val artistName = if (hasExplicitAlbumArtist) {
                                        wrapper.albumArtist!!
                                    } else {
                                        // For name-only album IDs, pick the most common
                                        // track artist across all songs in this album.
                                        val artists = trackArtistsByAlbumId[id].orEmpty()
                                        val mostCommon = artists
                                            .groupingBy { it }
                                            .eachCount()
                                            .maxByOrNull { it.value }
                                            ?.key
                                        mostCommon
                                            ?: existingAlbumsMap[id]?.artist
                                            ?: entity.artist
                                    }

                                    val albumArtistId = "LOCAL_FILE:" + artistName.normalize()

                                    id to AlbumEntity(
                                        id = id,
                                        name = name,
                                        artist = artistName,
                                        sourceType = entity.sourceType,
                                        artistId = albumArtistId,
                                    )
                                }
                                .distinctBy { it.first }
                                .map { it.second }

                            onIngestProgress(0.2f)

                            if (normalizedLocalEntities.isEmpty() && albumEntities.isEmpty() && uniqueArtists.isEmpty()) {
                                onIngestProgress(1f)
                                songDao.deleteBySourceType(SourceType.LOCAL_FILE)
                                albumDao.deleteBySourceType(SourceType.LOCAL_FILE)
                                artistDao.deleteBySourceType(SourceType.LOCAL_FILE)
                                return@withContext
                            }

                            val existingById = existingLocalSongs.associateBy { it.id }
                            val scannedById = normalizedLocalEntities.associateBy { it.id }

                            val songsToDelete = existingById.keys - scannedById.keys
                            val songsToUpsert = scannedById.values.filter { newEntity ->
                                val existing = existingById[newEntity.id]
                                existing == null || existing != newEntity
                            }

                            val totalDiscovered = normalizedLocalEntities.size
                            val indexedNewOrUpdated = songsToUpsert.size
                            val skippedUnchanged = (totalDiscovered - indexedNewOrUpdated).coerceAtLeast(0)
                            val deletedMissing = songsToDelete.size
                            stats = LocalResyncStats(
                                totalDiscovered = totalDiscovered,
                                skippedUnchanged = skippedUnchanged,
                                indexedNewOrUpdated = indexedNewOrUpdated,
                                deletedMissing = deletedMissing,
                            )

                            val totalWriteItems = songsToDelete.size + songsToUpsert.size + albumEntities.size + uniqueArtists.size
                            var writtenItems = 0

                            fun reportWriteProgress() {
                                if (totalWriteItems <= 0) return
                                val writeFraction = (writtenItems.toFloat() / totalWriteItems.toFloat()).coerceIn(0f, 1f)
                                val progress = 0.2f + 0.8f * writeFraction
                                onIngestProgress(progress.coerceIn(0f, 1f))
                            }

                            if (songsToDelete.isNotEmpty()) {
                                songDao.deleteByIds(songsToDelete.toList())
                                writtenItems += songsToDelete.size
                                reportWriteProgress()
                            }

                            if (songsToUpsert.isNotEmpty()) {
                                val chunkSize = 100.coerceAtMost(songsToUpsert.size)
                                songsToUpsert.chunked(chunkSize).forEach { chunk ->
                                    songDao.upsertAll(chunk)
                                    writtenItems += chunk.size
                                    reportWriteProgress()
                                }
                            }

                            albumDao.deleteBySourceType(SourceType.LOCAL_FILE)
                            artistDao.deleteBySourceType(SourceType.LOCAL_FILE)

                            if (albumEntities.isNotEmpty()) {
                                albumDao.upsertAll(albumEntities)
                                writtenItems += albumEntities.size
                                reportWriteProgress()
                            }
                            if (uniqueArtists.isNotEmpty()) {
                                artistDao.upsertAll(uniqueArtists)
                                writtenItems += uniqueArtists.size
                                reportWriteProgress()
                            }

                            onIngestProgress(1f)
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to scan local music"
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        songDao.deleteBySourceType(SourceType.LOCAL_FILE)
                        albumDao.deleteBySourceType(SourceType.LOCAL_FILE)
                        artistDao.deleteBySourceType(SourceType.LOCAL_FILE)
                    }
                }
            }

            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                val songsFromDb = songDao.getAllSongs()
                val albumsFromDb = albumDao.getAllAlbums()
                songsFromDb to albumsFromDb
            }
            val allArtistsWithCounts = withContext(Dispatchers.IO) {
                artistDao.getAllArtistsWithCounts()
            }

            val songModels = allSongs.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = com.calmapps.calmtunes.formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    discNumber = entity.discNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                    remoteId = entity.remoteId,
                )
            }
            val albumIdToYear: Map<String, Int?> = allSongs
                .mapNotNull { entity ->
                    val albumId = entity.albumId ?: return@mapNotNull null
                    albumId to entity.releaseYear
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
                .mapValues { (_, years) ->
                    years.filterNotNull().maxOrNull()
                }

            val albumModels = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
                    releaseYear = albumIdToYear[album.id],
                )
            }
            val artistModels = allArtistsWithCounts.map { artist ->
                ArtistUiModel(
                    id = artist.id,
                    name = artist.name,
                    songCount = artist.songCount,
                    albumCount = artist.albumCount,
                )
            }

            app.settingsManager.updateLastLocalLibraryScanMillis(System.currentTimeMillis())

            return LocalResyncResult(
                songs = songModels,
                albums = albumModels,
                artists = artistModels,
                errorMessage = error,
                stats = stats,
            )
        } catch (e: Exception) {
            val message = e.message ?: "Failed to scan local music"
            return LocalResyncResult(
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                errorMessage = message,
                stats = null,
            )
        }
    }

    suspend fun ingestAppDownloadsIfMissing(): Int {
        return withContext(Dispatchers.IO) {
            val downloadsDir = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return@withContext 0
            val files = downloadsDir.listFiles()?.filter { it.isFile } ?: emptyList()
            if (files.isEmpty()) return@withContext 0

            val existingDownloads = songDao.getSongsBySourceType(SourceType.YOUTUBE_DOWNLOAD)
            val existingByUri = existingDownloads.associateBy { it.audioUri }

            val toInsert = mutableListOf<SongEntity>()
            val artistsToUpsert = mutableListOf<ArtistEntity>()
            val albumsToUpsert = mutableListOf<AlbumEntity>()

            fun String.toIdComponent(): String =
                trim().replace(Regex("\\s+"), " ").lowercase()

            for (file in files) {
                val uri = Uri.fromFile(file)
                val uriString = uri.toString()
                if (existingByUri.containsKey(uriString)) continue

                val scanned = LocalMusicScanner.buildSongEntityFromFile(
                    context = app,
                    uri = uri,
                    name = file.name,
                    lastModified = file.lastModified(),
                    fileSize = file.length(),
                    existing = null,
                )
                val trackArtist = scanned.song.artist.takeIf { it.isNotBlank() }
                val albumArtist = scanned.albumArtist?.takeIf { it.isNotBlank() } ?: trackArtist
                val albumArtistKey = albumArtist?.toIdComponent()
                val albumArtistEntityId = if (albumArtistKey != null && scanned.song.albumId != null) {
                    "YOUTUBE_DOWNLOAD:$albumArtistKey"
                } else null

                val song = scanned.song.copy(
                    sourceType = SourceType.YOUTUBE_DOWNLOAD,
                    albumArtistId = albumArtistEntityId,
                )
                toInsert += song

                if (trackArtist == null) continue
                val trackArtistKey = trackArtist.toIdComponent()
                val artistId = song.artistId ?: "YOUTUBE_DOWNLOAD:$trackArtistKey"

                artistsToUpsert += ArtistEntity(
                    id = artistId,
                    name = trackArtist,
                    sourceType = SourceType.YOUTUBE_DOWNLOAD,
                )

                val albumName = song.album?.takeIf { it.isNotBlank() } ?: continue
                val albumId = song.albumId ?: continue

                // Reuse albumArtist/albumArtistKey computed above for albumArtistId
                val effectiveAlbumArtistId = albumArtistEntityId ?: "YOUTUBE_DOWNLOAD:${(albumArtist ?: trackArtist).toIdComponent()}"
                val effectiveAlbumArtistName = albumArtist ?: trackArtist

                if (effectiveAlbumArtistId != artistId) {
                    artistsToUpsert += ArtistEntity(
                        id = effectiveAlbumArtistId,
                        name = effectiveAlbumArtistName,
                        sourceType = SourceType.YOUTUBE_DOWNLOAD,
                    )
                }

                albumsToUpsert += AlbumEntity(
                    id = albumId,
                    name = albumName,
                    artist = effectiveAlbumArtistName,
                    sourceType = SourceType.YOUTUBE_DOWNLOAD,
                    artistId = effectiveAlbumArtistId,
                )
            }

            if (toInsert.isNotEmpty()) {
                songDao.upsertAll(toInsert)
            }
            if (artistsToUpsert.isNotEmpty()) {
                artistDao.upsertAll(artistsToUpsert.distinctBy { it.id })
            }
            if (albumsToUpsert.isNotEmpty()) {
                albumDao.upsertAll(albumsToUpsert.distinctBy { it.id })
            }

            toInsert.size
        }
    }
}