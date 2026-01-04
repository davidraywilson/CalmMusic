package com.calmapps.calmmusic.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table connecting playlists to songs (SongEntity) so that a playlist
 * can contain both local and Apple Music songs.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        // Keep cascading delete only from playlists → playlist_tracks so that
        // removing a playlist cleans up its rows, but do NOT cascade from
        // songs → playlist_tracks. We rely on stable song IDs and resyncs
        // (which delete and reinsert songs by sourceType) should not wipe
        // playlist memberships.
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("songId"),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val songId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)
