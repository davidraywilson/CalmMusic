package com.calmapps.calmmusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local playlist definition. These playlists are stored only in the app's
 * database but can reference both local files and Apple Music songs via
 * SongEntity IDs.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
