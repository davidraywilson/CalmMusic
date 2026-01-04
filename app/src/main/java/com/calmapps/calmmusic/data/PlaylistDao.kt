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

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: String)

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
