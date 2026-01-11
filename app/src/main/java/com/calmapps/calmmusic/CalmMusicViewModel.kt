package com.calmapps.calmmusic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.data.ArtistEntity
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.LibraryRepository
import com.calmapps.calmmusic.data.PlaylistManager
import com.calmapps.calmmusic.data.SongEntity
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.RepeatMode
import com.calmapps.calmmusic.ui.SongUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel responsible for owning long-lived CalmMusic library state and
 * running initial database loads. Behavior is intended to match the pre-refactor
 * MainActivity/CalmMusic composable logic.
 */
class CalmMusicViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app: CalmMusic
        get() = getApplication() as CalmMusic

    private val database: CalmMusicDatabase by lazy { CalmMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val albumDao by lazy { database.albumDao() }
    private val artistDao by lazy { database.artistDao() }
    private val playlistDao by lazy { database.playlistDao() }
    private val libraryRepository: LibraryRepository by lazy { LibraryRepository(app) }
    private val playlistManager: PlaylistManager by lazy { PlaylistManager(songDao, playlistDao) }

    private val settingsManager
        get() = app.settingsManager

    private val _librarySongs = MutableStateFlow<List<SongUiModel>>(emptyList())
    val librarySongs: StateFlow<List<SongUiModel>> = _librarySongs

    private val _libraryAlbums = MutableStateFlow<List<AlbumUiModel>>(emptyList())
    val libraryAlbums: StateFlow<List<AlbumUiModel>> = _libraryAlbums

    private val _libraryArtists = MutableStateFlow<List<ArtistUiModel>>(emptyList())
    val libraryArtists: StateFlow<List<ArtistUiModel>> = _libraryArtists

    private val _libraryPlaylists = MutableStateFlow<List<PlaylistUiModel>>(emptyList())
    val libraryPlaylists: StateFlow<List<PlaylistUiModel>> = _libraryPlaylists

    private val _isLoadingSongs = MutableStateFlow(true)
    val isLoadingSongs: StateFlow<Boolean> = _isLoadingSongs

    private val _isLoadingAlbums = MutableStateFlow(true)
    val isLoadingAlbums: StateFlow<Boolean> = _isLoadingAlbums
 
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    suspend fun getAlbumSongs(albumId: String): List<SongUiModel> {
        return withContext(Dispatchers.IO) {
            val entities = songDao.getSongsByAlbumId(albumId)
            entities.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    discNumber = entity.discNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }
        }
    }

    data class ArtistContent(
        val songs: List<SongUiModel>,
        val albums: List<AlbumUiModel>
    )

    suspend fun getArtistContent(artistId: String): ArtistContent {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getSongsByArtistId(artistId)
            val albumEntities = albumDao.getAlbumsByArtistId(artistId)

            val songs = songEntities.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    discNumber = entity.discNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }

            // Derive an approximate release year per album from the artist's songs, when available.
            val albumIdToYear: Map<String, Int?> = songEntities
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

            val albums = albumEntities
                .sortedWith(
                    compareByDescending<com.calmapps.calmmusic.data.AlbumEntity> { album ->
                        // Newest release first; albums without a year go last.
                        albumIdToYear[album.id] ?: Int.MIN_VALUE
                    }.thenBy { album -> album.name },
                )
                .map { album ->
                    AlbumUiModel(
                        id = album.id,
                        title = album.name,
                        artist = album.artist,
                        sourceType = album.sourceType,
                        releaseYear = albumIdToYear[album.id],
                    )
                }

            ArtistContent(songs, albums)
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): List<SongUiModel> {
        return withContext(Dispatchers.IO) {
            val entities = playlistDao.getSongsForPlaylist(playlistId)
            entities.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    discNumber = entity.discNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }
        }
    }

    suspend fun updatePlaylistOrder(playlistId: String, songs: List<SongUiModel>) {
        withContext(Dispatchers.IO) {
            songs.forEachIndexed { index, song ->
                playlistDao.updateTrackPosition(playlistId, song.id, index)
            }
        }
    }
 
    /**
     * Temporary bridge: mirror the composable's playback-related state into
     * ViewModel-backed state so other parts of the app can eventually observe
     * playback without owning the source of truth yet.
     */
    fun updatePlaybackStateFromUi(
        queue: List<SongUiModel>,
        queueIndex: Int?,
        originalQueue: List<SongUiModel>,
        repeatMode: RepeatMode,
        isShuffleOn: Boolean,
        currentSongId: String?,
        nowPlayingSong: SongUiModel?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        _playbackState.value = PlaybackState(
            playbackQueue = queue,
            playbackQueueIndex = queueIndex,
            originalPlaybackQueue = originalQueue,
            repeatMode = repeatMode,
            isShuffleOn = isShuffleOn,
            currentSongId = currentSongId,
            nowPlayingSong = nowPlayingSong,
            isPlaybackPlaying = isPlaying,
            nowPlayingPositionMs = positionMs,
            nowPlayingDurationMs = durationMs,
        )
    }
 
    suspend fun resyncLocalLibrary(
        includeLocal: Boolean,
        folders: Set<String>,
        onScanProgress: (Float) -> Unit,
        onIngestProgress: (Float) -> Unit,
    ): LibraryRepository.LocalResyncResult {
        val result = libraryRepository.resyncLocalLibrary(includeLocal, folders, onScanProgress, onIngestProgress)
        updateLibrary(
            songs = result.songs,
            albums = result.albums,
            artists = result.artists,
        )
        return result
    }

    fun updateLibrary(
        songs: List<SongUiModel>,
        albums: List<AlbumUiModel>,
        artists: List<ArtistUiModel>,
    ) {
        _librarySongs.value = songs
        _libraryAlbums.value = albums
        _libraryArtists.value = artists
    }

    suspend fun syncAppleMusicIfNeeded(
        isAuthenticated: Boolean,
        hasExistingSongs: Boolean,
    ): String? {
        if (!isAuthenticated) {
            withContext(Dispatchers.IO) {
                songDao.deleteBySourceType("APPLE_MUSIC")
                albumDao.deleteBySourceType("APPLE_MUSIC")
                artistDao.deleteBySourceType("APPLE_MUSIC")
            }

            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                songDao.getAllSongs() to albumDao.getAllAlbums()
            }
            val allArtistsWithCounts = withContext(Dispatchers.IO) {
                artistDao.getAllArtistsWithCounts()
            }

            val songModels = allSongs.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
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

            updateLibrary(
                songs = songModels,
                albums = albumModels,
                artists = artistModels,
            )
            return null
        }

        val now = System.currentTimeMillis()
        val lastSync = settingsManager.getLastAppleMusicSyncMillis()
        val minSyncIntervalMillis = 6L * 60L * 60L * 1000L

        if (hasExistingSongs && now - lastSync < minSyncIntervalMillis) {
            return null
        }

        return try {
            val songs = app.appleMusicApiClient.getLibrarySongs(limit = 200)
            withContext(Dispatchers.IO) {
                val entities = songs.map { song ->
                    val albumName = song.albumName?.takeIf { it.isNotBlank() }
                    val artistName = song.artistName
                    val albumId = if (albumName != null) "APPLE_MUSIC:${artistName.trim()}:${albumName.trim()}" else null
                    val artistId = if (artistName.isNotBlank()) "APPLE_MUSIC:${artistName.trim()}" else null

                    SongEntity(
                        id = song.id,
                        title = song.name,
                        artist = artistName,
                        album = albumName,
                        albumId = albumId,
                        discNumber = null,
                        trackNumber = null,
                        durationMillis = song.durationMillis,
                        sourceType = "APPLE_MUSIC",
                        audioUri = song.id,
                        artistId = artistId,
                        releaseYear = song.releaseYear,
                    )
                }

                val artistEntities: List<ArtistEntity> = entities
                    .mapNotNull { entity ->
                        val id = entity.artistId ?: return@mapNotNull null
                        val name = entity.artist.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        id to ArtistEntity(
                            id = id,
                            name = name,
                            sourceType = entity.sourceType,
                        )
                    }
                    .distinctBy { it.first }
                    .map { it.second }

                val albumEntities: List<AlbumEntity> = entities
                    .mapNotNull { entity ->
                        val id = entity.albumId ?: return@mapNotNull null
                        val name = entity.album ?: return@mapNotNull null
                        id to AlbumEntity(
                            id = id,
                            name = name,
                            artist = entity.artist,
                            sourceType = entity.sourceType,
                            artistId = entity.artistId,
                        )
                    }
                    .distinctBy { it.first }
                    .map { it.second }

                songDao.deleteBySourceType("APPLE_MUSIC")
                albumDao.deleteBySourceType("APPLE_MUSIC")
                artistDao.deleteBySourceType("APPLE_MUSIC")
                if (entities.isNotEmpty()) songDao.upsertAll(entities)
                if (albumEntities.isNotEmpty()) albumDao.upsertAll(albumEntities)
                if (artistEntities.isNotEmpty()) artistDao.upsertAll(artistEntities)
            }

            settingsManager.updateLastAppleMusicSyncMillis(System.currentTimeMillis())

            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                songDao.getAllSongs() to albumDao.getAllAlbums()
            }
            val allArtistsWithCounts = withContext(Dispatchers.IO) {
                artistDao.getAllArtistsWithCounts()
            }

            val songModels = allSongs.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
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

            updateLibrary(
                songs = songModels,
                albums = albumModels,
                artists = artistModels,
            )
            null
        } catch (e: Exception) {
            e.message ?: "Failed to load Apple Music library"
        }
    }

    suspend fun addSongToPlaylist(
        song: SongUiModel,
        playlist: PlaylistUiModel,
    ): PlaylistManager.AddSongResult {
        return withContext(Dispatchers.IO) {
            playlistManager.addSongToPlaylist(song, playlist.id)
        }
    }

    init {
        // Initial load from the database so songs/albums/playlists appear
        // immediately on app start. Mirrors the previous LaunchedEffect(Unit)
        // in CalmMusic.
        viewModelScope.launch {
            val allSongs = withContext(Dispatchers.IO) { songDao.getAllSongs() }
            val allAlbums = withContext(Dispatchers.IO) { albumDao.getAllAlbums() }
            val allArtistsWithCounts = withContext(Dispatchers.IO) { artistDao.getAllArtistsWithCounts() }
            val allPlaylistsWithCounts = withContext(Dispatchers.IO) { playlistDao.getAllPlaylistsWithSongCount() }

            _librarySongs.value = allSongs.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
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

            _libraryAlbums.value = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
                    releaseYear = albumIdToYear[album.id],
                )
            }
            _libraryArtists.value = allArtistsWithCounts.map { artist ->
                ArtistUiModel(
                    id = artist.id,
                    name = artist.name,
                    songCount = artist.songCount,
                    albumCount = artist.albumCount,
                )
            }
            _libraryPlaylists.value = allPlaylistsWithCounts.map { playlist ->
                PlaylistUiModel(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    songCount = playlist.songCount,
                )
            }
            _isLoadingSongs.value = false
            _isLoadingAlbums.value = false
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CalmMusicViewModel::class.java)) {
                        return CalmMusicViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class ${'$'}modelClass")
                }
            }
    }
}
 
/**
 * Snapshot of app-wide playback state, exposed from CalmMusicViewModel so that
 * UI and other layers can observe now-playing information without needing to
 * know about the underlying players.
 */
data class PlaybackState(
    val playbackQueue: List<SongUiModel> = emptyList(),
    val playbackQueueIndex: Int? = null,
    val originalPlaybackQueue: List<SongUiModel> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleOn: Boolean = false,
    val currentSongId: String? = null,
    val nowPlayingSong: SongUiModel? = null,
    val isPlaybackPlaying: Boolean = false,
    val nowPlayingPositionMs: Long = 0L,
    val nowPlayingDurationMs: Long = 0L,
)
