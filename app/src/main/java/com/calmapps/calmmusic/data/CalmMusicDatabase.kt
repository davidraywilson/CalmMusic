package com.calmapps.calmmusic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class CalmMusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: CalmMusicDatabase? = null

        fun getDatabase(context: Context): CalmMusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CalmMusicDatabase::class.java,
                    "calmmusic.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
