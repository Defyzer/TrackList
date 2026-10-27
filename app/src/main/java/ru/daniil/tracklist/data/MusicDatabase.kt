package ru.daniil.tracklist.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Song::class, Playlist::class, PlaylistSong::class],
    version = 1,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
}
