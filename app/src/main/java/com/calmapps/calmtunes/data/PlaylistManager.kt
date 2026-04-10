package com.calmapps.calmtunes.data

import com.calmapps.calmtunes.ui.SongUiModel

/**
 * Helper class responsible for playlist-related database operations.
 * Initially this only encapsulates "add song to playlist" behavior, mirroring
 * the existing logic from CalmTunes.
 */
class PlaylistManager(
    private val playlistDao: PlaylistDao,
) {

    data class AddSongResult(
        val newSongCount: Int?,
        val wasAdded: Boolean,
        val alreadyInPlaylist: Boolean,
    )

    /**
     * Add the given song to the specified playlist by creating a
     * PlaylistTrackEntity join row. The song must already exist in the songs
     * table. Returns information about whether the song was newly added or
     * already present, along with the updated playlist song count.
     */
    suspend fun addSongToPlaylist(
        song: SongUiModel,
        playlistId: String,
    ): AddSongResult {
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
