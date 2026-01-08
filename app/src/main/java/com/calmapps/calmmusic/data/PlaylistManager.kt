package com.calmapps.calmmusic.data

import com.calmapps.calmmusic.ui.SongUiModel

/**
 * Helper class responsible for playlist-related database operations.
 * Initially this only encapsulates "add song to playlist" behavior, mirroring
 * the existing logic from CalmMusic.
 */
class PlaylistManager(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
) {

    data class AddSongResult(
        val newSongCount: Int?,
        val wasAdded: Boolean,
        val alreadyInPlaylist: Boolean,
    )

    /**
     * Add the given song to the specified playlist, creating the SongEntity and
     * PlaylistTrackEntity entries as needed. Returns information about whether
     * the song was newly added or already present, along with the updated
     * playlist song count if available.
     */
    suspend fun addSongToPlaylist(
        song: SongUiModel,
        playlistId: String,
    ): AddSongResult {
        val entity = SongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = null,
            albumId = null,
            trackNumber = song.trackNumber,
            durationMillis = song.durationMillis,
            sourceType = song.sourceType,
            audioUri = song.audioUri ?: song.id,
            artistId = null,
            discNumber = null,
        )
        songDao.upsertAll(listOf(entity))

        val existing = playlistDao.getSongsForPlaylist(playlistId)
        val existsAlready = existing.any { it.id == song.id }
        return if (existsAlready) {
            AddSongResult(
                newSongCount = existing.size,
                wasAdded = false,
                alreadyInPlaylist = true,
            )
        } else {
            val position = existing.size
            val track = PlaylistTrackEntity(
                playlistId = playlistId,
                songId = song.id,
                position = position,
            )
            playlistDao.upsertTracks(listOf(track))
            val newSongCount = playlistDao.getSongCountForPlaylist(playlistId)
            AddSongResult(
                newSongCount = newSongCount,
                wasAdded = true,
                alreadyInPlaylist = false,
            )
        }
    }
}
