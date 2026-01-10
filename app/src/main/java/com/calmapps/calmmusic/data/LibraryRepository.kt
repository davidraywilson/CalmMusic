package com.calmapps.calmmusic.data

import com.calmapps.calmmusic.CalmMusic
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.SongUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for performing library-related data work against the
 * local Room database and filesystem scanners. Initially this only encapsulates
 * local library rescan logic, mirroring the behavior that previously lived
 * inside the CalmMusic composable.
 */
class LibraryRepository(
    private val app: CalmMusic,
) {

    private val database: CalmMusicDatabase by lazy { CalmMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val albumDao by lazy { database.albumDao() }
    private val artistDao by lazy { database.artistDao() }

    data class LocalResyncResult(
        val songs: List<SongUiModel>,
        val albums: List<AlbumUiModel>,
        val artists: List<ArtistUiModel>,
        val errorMessage: String?,
    )

    /**
     * Resync the local library based on the current includeLocal flag and the
     * set of folders selected by the user. This function performs the same
     * work as the original resyncLocalLibrary() in CalmMusic: it updates the
     * database and then returns an updated snapshot of UI models.
     */
    suspend fun resyncLocalLibrary(
        includeLocal: Boolean,
        folders: Set<String>,
        onScanProgress: (Float) -> Unit,
        onIngestProgress: (Float) -> Unit,
    ): LocalResyncResult {
        var error: String? = null

        try {
            if (!includeLocal) {
                // No local files included: clear any existing LOCAL_FILE rows. Treat this as a
                // quick ingestion phase so the UI can reflect that work completed.
                onScanProgress(1f)
                onIngestProgress(0f)
                withContext(Dispatchers.IO) {
                    songDao.deleteBySourceType("LOCAL_FILE")
                    albumDao.deleteBySourceType("LOCAL_FILE")
                    artistDao.deleteBySourceType("LOCAL_FILE")
                }
                onIngestProgress(1f)
            } else {
                if (folders.isNotEmpty()) {
                    try {
                        // Phase 1: crawl folders via SAF and build an in-memory list of song entities.
                        val localEntities = withContext(Dispatchers.IO) {
                            LocalMusicScanner.scanFolders(app, folders) { processed, total ->
                                val progress = if (total > 0) {
                                    (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    // If we didn't find any audio files, treat the crawl as
                                    // immediately complete so the scan progress bar can finish.
                                    1f
                                }
                                onScanProgress(progress)
                            }
                        }

                        // Phase 2: ingest results into the Room database. We break this into
                        // three conceptual subphases for progress:
                        //  - 10%: building artists
                        //  - 10%: building albums
                        //  - 80%: writing songs/albums/artists to the database
                        onIngestProgress(0f)

                        withContext(Dispatchers.IO) {
                            // 10%: build unique artists from the scanned song entities.
                            val artistEntities: List<ArtistEntity> = localEntities
                                .mapNotNull { entity ->
                                    val id = entity.artistId ?: return@mapNotNull null

                                    // For local files, derive the display name from the Album Artist
                                    // (which we encoded into artistId) so that artist rows and album
                                    // rows are labeled consistently by album artist, not per-track
                                    // artist strings that may contain features.
                                    val name = if (entity.sourceType == "LOCAL_FILE" && id.startsWith("LOCAL_FILE:")) {
                                        id.removePrefix("LOCAL_FILE:")
                                    } else {
                                        entity.artist.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                    }

                                    id to ArtistEntity(
                                        id = id,
                                        name = name,
                                        sourceType = entity.sourceType,
                                    )
                                }
                                .distinctBy { it.first }
                                .map { it.second }

                            onIngestProgress(0.1f)

                            // 10%: build unique albums from the scanned song entities.
                            val albumEntities: List<AlbumEntity> = localEntities
                                .mapNotNull { entity ->
                                    val id = entity.albumId ?: return@mapNotNull null
                                    val name = entity.album ?: return@mapNotNull null

                                    // For local files, use the same album-artist-derived name that
                                    // we store in ArtistEntity so albums are grouped and labeled by
                                    // album artist. For other sources, fall back to the track artist
                                    // string.
                                    val artistName = if (entity.sourceType == "LOCAL_FILE" && (entity.artistId ?: "").startsWith("LOCAL_FILE:")) {
                                        entity.artistId!!.removePrefix("LOCAL_FILE:")
                                    } else {
                                        entity.artist
                                    }

                                    id to AlbumEntity(
                                        id = id,
                                        name = name,
                                        artist = artistName,
                                        sourceType = entity.sourceType,
                                        artistId = entity.artistId,
                                    )
                                }
                                .distinctBy { it.first }
                                .map { it.second }

                            onIngestProgress(0.2f)

                            // If there is nothing to ingest, mark the ingest phase as complete
                            // immediately.
                            if (localEntities.isEmpty() && albumEntities.isEmpty() && artistEntities.isEmpty()) {
                                onIngestProgress(1f)
                                songDao.deleteBySourceType("LOCAL_FILE")
                                albumDao.deleteBySourceType("LOCAL_FILE")
                                artistDao.deleteBySourceType("LOCAL_FILE")
                                return@withContext
                            }

                            // 80% of the ingest phase is reserved for actually writing songs,
                            // albums, and artists into the database.
                            val totalWriteItems = localEntities.size + albumEntities.size + artistEntities.size
                            var writtenItems = 0

                            fun reportWriteProgress() {
                                if (totalWriteItems <= 0) return
                                val writeFraction = (writtenItems.toFloat() / totalWriteItems.toFloat()).coerceIn(0f, 1f)
                                val progress = 0.2f + 0.8f * writeFraction
                                onIngestProgress(progress.coerceIn(0f, 1f))
                            }

                            songDao.deleteBySourceType("LOCAL_FILE")
                            albumDao.deleteBySourceType("LOCAL_FILE")
                            artistDao.deleteBySourceType("LOCAL_FILE")

                            if (localEntities.isNotEmpty()) {
                                val chunkSize = 100.coerceAtMost(localEntities.size)
                                localEntities.chunked(chunkSize).forEach { chunk ->
                                    songDao.upsertAll(chunk)
                                    writtenItems += chunk.size
                                    reportWriteProgress()
                                }
                            }
                            if (albumEntities.isNotEmpty()) {
                                albumDao.upsertAll(albumEntities)
                                writtenItems += albumEntities.size
                                reportWriteProgress()
                            }
                            if (artistEntities.isNotEmpty()) {
                                artistDao.upsertAll(artistEntities)
                                writtenItems += artistEntities.size
                                reportWriteProgress()
                            }

                            // Ensure we finish at 100%.
                            onIngestProgress(1f)
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to scan local music"
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        songDao.deleteBySourceType("LOCAL_FILE")
                        albumDao.deleteBySourceType("LOCAL_FILE")
                        artistDao.deleteBySourceType("LOCAL_FILE")
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
                    durationText = com.calmapps.calmmusic.formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }
            // Derive a best-effort release year per album from its songs, if available.
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

            return LocalResyncResult(
                songs = songModels,
                albums = albumModels,
                artists = artistModels,
                errorMessage = error,
            )
        } catch (e: Exception) {
            val message = e.message ?: "Failed to scan local music"
            return LocalResyncResult(
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                errorMessage = message,
            )
        }
    }
}
