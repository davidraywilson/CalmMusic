package com.calmapps.calmmusic.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class PlaylistWithSongCount(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val songCount: Int,
)

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query(
        "SELECT p.id, p.name, p.description, p.createdAt, " +
            "COUNT(pt.songId) AS songCount " +
            "FROM playlists p " +
            "LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id " +
            "GROUP BY p.id " +
            "ORDER BY p.createdAt DESC",
    )
    suspend fun getAllPlaylistsWithSongCount(): List<PlaylistWithSongCount>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getSongCountForPlaylist(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    /**
     * Update the editable metadata for an existing playlist without triggering
     * ON DELETE CASCADE on playlist_tracks. Using REPLACE for updates would
     * delete and reinsert the PlaylistEntity row, which in turn would cascade
     * and wipe all playlist_tracks rows for that playlist.
     */
    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :id")
    suspend fun updatePlaylistMetadata(id: String, name: String, description: String?)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: String)

    /**
     * Delete specific songs from a playlist without affecting the playlist
     * itself or other playlists that may reference the same songs.
     */
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND songId IN (:songIds)")
    suspend fun deleteTracksForPlaylistAndSongIds(playlistId: String, songIds: List<String>)

    /**
     * Update the explicit position of a single song within a playlist. This is
     * used when the user reorders songs in a playlist details editing mode.
     */
    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateTrackPosition(playlistId: String, songId: String, position: Int)

    /**
     * Fetch all songs for a playlist, ordered by playlist position.
     */
    @Transaction
    @Query(
        "SELECT s.* FROM songs s " +
            "INNER JOIN playlist_tracks pt ON pt.songId = s.id " +
            "WHERE pt.playlistId = :playlistId " +
            "ORDER BY pt.position"
    )
    suspend fun getSongsForPlaylist(playlistId: String): List<SongEntity>
}
