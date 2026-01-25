package com.calmapps.calmmusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Index

/**
 * Unified song representation used for both Apple Music and local files.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("sourceType"),
        Index("albumId"),
        Index("artistId"),
    ],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMillis: Long?,
    val sourceType: String,
    val audioUri: String,
    val artistId: String? = null,
    val releaseYear: Int? = null,
    val localLastModifiedMillis: Long? = null,
    val localFileSizeBytes: Long? = null,
)

@Entity(
    tableName = "albums",
    indices = [
        Index("artistId"),
    ],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String?,
    val sourceType: String,
    val artistId: String? = null,
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceType: String, // <--- Ensure this parameter exists
)

data class ArtistWithCounts(
    val id: String,
    val name: String,
    val sourceType: String,
    val songCount: Int,
    val albumCount: Int,
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE sourceType = :sourceType ORDER BY title")
    suspend fun getSongsBySourceType(sourceType: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber, trackNumber, title")
    suspend fun getSongsByAlbumId(albumId: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY albumId, trackNumber, title")
    suspend fun getSongsByArtist(artist: String): List<SongEntity>

    @Query(
        "SELECT s.* FROM songs s " +
                "LEFT JOIN albums a ON s.albumId = a.id " +
                "WHERE s.artistId = :artistId OR a.artistId = :artistId " +
                "ORDER BY s.albumId, s.trackNumber, s.title"
    )
    suspend fun getSongsByArtistId(artistId: String): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE sourceType = :sourceType")
    suspend fun deleteBySourceType(sourceType: String)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE artist = :artist ORDER BY name")
    suspend fun getAlbumsByArtist(artist: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY name")
    suspend fun getAlbumsByArtistId(artistId: String): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE sourceType = :sourceType")
    suspend fun deleteBySourceType(sourceType: String)
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    suspend fun getAllArtists(): List<ArtistEntity>

    @Query(
        "SELECT a.id, a.name, a.sourceType, " +
                "COUNT(DISTINCT s.id) AS songCount, " +
                "COUNT(DISTINCT al.id) AS albumCount " +
                "FROM artists a " +
                "LEFT JOIN albums al ON al.artistId = a.id " +
                "LEFT JOIN songs s ON (s.artistId = a.id OR s.albumId = al.id) " +
                "GROUP BY a.id " +
                "ORDER BY a.name COLLATE NOCASE",
    )
    suspend fun getAllArtistsWithCounts(): List<ArtistWithCounts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE sourceType = :sourceType")
    suspend fun deleteBySourceType(sourceType: String)
}