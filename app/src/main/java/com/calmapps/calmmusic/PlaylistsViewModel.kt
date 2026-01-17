package com.calmapps.calmmusic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.PlaylistManager
import com.calmapps.calmmusic.data.PlaylistTrackEntity
import com.calmapps.calmmusic.data.SongEntity
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.SongUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated ViewModel for playlist-related state and operations. This pulls
 * playlist concerns out of CalmMusicViewModel/MainActivity while preserving
 * existing behavior.
 */
class PlaylistsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app: CalmMusic
        get() = getApplication() as CalmMusic

    private val database: CalmMusicDatabase by lazy { CalmMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val playlistDao by lazy { database.playlistDao() }
    private val playlistManager: PlaylistManager by lazy { PlaylistManager(songDao, playlistDao) }

    private val _playlists = MutableStateFlow<List<PlaylistUiModel>>(emptyList())
    val playlists: StateFlow<List<PlaylistUiModel>> = _playlists

    private suspend fun loadPlaylistsFromDb(): List<PlaylistUiModel> {
        val allPlaylistsWithCounts = playlistDao.getAllPlaylistsWithSongCount()
        return allPlaylistsWithCounts.map { playlist ->
            PlaylistUiModel(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                songCount = playlist.songCount,
            )
        }
    }

    suspend fun refreshPlaylists(): List<PlaylistUiModel> {
        return withContext(Dispatchers.IO) {
            val updated = loadPlaylistsFromDb()
            _playlists.value = updated
            updated
        }
    }

    data class AddSongsToPlaylistResult(
        val addedCount: Int,
        val totalSongCount: Int,
        val allSelectedAlreadyPresent: Boolean,
    )

    data class EditPlaylistParams(
        val playlistId: String?, // null = new
        val name: String,
        val description: String? = null,
        val songToAdd: SongUiModel? = null,
    )

    data class EditPlaylistResult(
        val playlistId: String,
        val songCount: Int,
    )

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

    suspend fun addSongToPlaylist(
        song: SongUiModel,
        playlist: PlaylistUiModel,
    ): PlaylistManager.AddSongResult {
        return withContext(Dispatchers.IO) {
            playlistManager.addSongToPlaylist(song, playlist.id)
        }
    }

    suspend fun addSongsToPlaylist(
        playlistId: String,
        selectedSongIds: Set<String>,
    ): AddSongsToPlaylistResult {
        return withContext(Dispatchers.IO) {
            val existingEntities = playlistDao.getSongsForPlaylist(playlistId)
            val existingIds = existingEntities.map { it.id }.toSet()

            // For now, build SongModels from DB directly.
            val allSongs = songDao.getAllSongs()
            val currentSongs = allSongs.map { entity ->
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

            val songsToAdd = currentSongs.filter { it.id in selectedSongIds && it.id !in existingIds }

            if (songsToAdd.isEmpty()) {
                AddSongsToPlaylistResult(
                    addedCount = 0,
                    totalSongCount = existingEntities.size,
                    allSelectedAlreadyPresent = true,
                )
            } else {
                val songEntities = songsToAdd.map { song ->
                    SongEntity(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = null,
                        albumId = null,
                        discNumber = null,
                        trackNumber = song.trackNumber,
                        durationMillis = song.durationMillis,
                        sourceType = song.sourceType,
                        audioUri = song.audioUri ?: song.id,
                        artistId = null,
                        releaseYear = null,
                    )
                }
                songDao.upsertAll(songEntities)

                val startingPosition = existingEntities.size
                val tracks = songsToAdd.mapIndexed { index, song ->
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        songId = song.id,
                        position = startingPosition + index,
                    )
                }
                playlistDao.upsertTracks(tracks)

                val newEntities = playlistDao.getSongsForPlaylist(playlistId)
                AddSongsToPlaylistResult(
                    addedCount = songsToAdd.size,
                    totalSongCount = newEntities.size,
                    allSelectedAlreadyPresent = false,
                )
            }
        }
    }

    suspend fun removeSongsFromPlaylist(
        playlistId: String,
        songIds: Set<String>,
    ): Int {
        return withContext(Dispatchers.IO) {
            if (songIds.isEmpty()) {
                return@withContext playlistDao.getSongsForPlaylist(playlistId).size
            }
            playlistDao.deleteTracksForPlaylistAndSongIds(
                playlistId = playlistId,
                songIds = songIds.toList(),
            )
            val songs = playlistDao.getSongsForPlaylist(playlistId)
            songs.size
        }
    }

    suspend fun createOrUpdatePlaylist(params: EditPlaylistParams): EditPlaylistResult {
        return withContext(Dispatchers.IO) {
            val playlistId = params.playlistId ?: "LOCAL_PLAYLIST:" + java.util.UUID.randomUUID().toString()

            if (params.playlistId != null) {
                // Update existing playlist metadata
                playlistDao.updatePlaylistMetadata(
                    id = playlistId,
                    name = params.name,
                    description = params.description,
                )
            } else {
                // Create a new playlist
                val entity = com.calmapps.calmmusic.data.PlaylistEntity(
                    id = playlistId,
                    name = params.name,
                    description = params.description,
                )
                playlistDao.upsertPlaylist(entity)
            }

            // Optionally add a single song to the playlist (used when creating from Now Playing).
            params.songToAdd?.let { songToAdd ->
                val songEntity = SongEntity(
                    id = songToAdd.id,
                    title = songToAdd.title,
                    artist = songToAdd.artist,
                    album = null,
                    albumId = null,
                    discNumber = null,
                    trackNumber = songToAdd.trackNumber,
                    durationMillis = songToAdd.durationMillis,
                    sourceType = songToAdd.sourceType,
                    audioUri = songToAdd.audioUri ?: songToAdd.id,
                    artistId = null,
                    releaseYear = null,
                )
                songDao.upsertAll(listOf(songEntity))
                val existing = playlistDao.getSongsForPlaylist(playlistId)
                val position = existing.size
                val track = PlaylistTrackEntity(
                    playlistId = playlistId,
                    songId = songToAdd.id,
                    position = position,
                )
                playlistDao.upsertTracks(listOf(track))
            }

            val updatedSongs = playlistDao.getSongsForPlaylist(playlistId)
            EditPlaylistResult(
                playlistId = playlistId,
                songCount = updatedSongs.size,
            )
        }
    }

    suspend fun deletePlaylists(playlistsToDelete: List<PlaylistUiModel>): List<PlaylistUiModel> {
        return withContext(Dispatchers.IO) {
            playlistsToDelete.forEach { playlist ->
                playlistDao.deleteTracksForPlaylist(playlist.id)
                val entity = com.calmapps.calmmusic.data.PlaylistEntity(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                )
                playlistDao.deletePlaylist(entity)
            }
            val remaining = loadPlaylistsFromDb()
            _playlists.value = remaining
            remaining
        }
    }

    init {
        // Initial load of playlists
        viewModelScope.launch(Dispatchers.IO) {
            val updated = loadPlaylistsFromDb()
            _playlists.value = updated
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(PlaylistsViewModel::class.java)) {
                        return PlaylistsViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class $modelClass")
                }
            }
    }
}
