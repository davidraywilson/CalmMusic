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
        onProgress: (Float) -> Unit,
    ): LocalResyncResult {
        var error: String? = null

        try {
            if (!includeLocal) {
                withContext(Dispatchers.IO) {
                    songDao.deleteBySourceType("LOCAL_FILE")
                    albumDao.deleteBySourceType("LOCAL_FILE")
                    artistDao.deleteBySourceType("LOCAL_FILE")
                }
            } else {
                if (folders.isNotEmpty()) {
                    try {
                        val localEntities = withContext(Dispatchers.IO) {
                            LocalMusicScanner.scanFolders(app, folders) { processed, total ->
                                val progress = if (total > 0) {
                                    (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                onProgress(progress)
                            }
                        }
                        withContext(Dispatchers.IO) {
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

                            songDao.deleteBySourceType("LOCAL_FILE")
                            albumDao.deleteBySourceType("LOCAL_FILE")
                            artistDao.deleteBySourceType("LOCAL_FILE")
                            if (localEntities.isNotEmpty()) {
                                songDao.upsertAll(localEntities)
                            }
                            if (albumEntities.isNotEmpty()) {
                                albumDao.upsertAll(albumEntities)
                            }
                            if (artistEntities.isNotEmpty()) {
                                artistDao.upsertAll(artistEntities)
                            }
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
                )
            }
            val albumModels = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
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
