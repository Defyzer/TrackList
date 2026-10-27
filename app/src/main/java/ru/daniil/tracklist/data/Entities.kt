package ru.daniil.tracklist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val favorite: Boolean = false,
    val sourceUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists", indices = [Index(value = ["nameKey"], unique = true)])
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys =
        [
            ForeignKey(
                entity = Playlist::class,
                parentColumns = ["id"],
                childColumns = ["playlistId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Song::class,
                parentColumns = ["id"],
                childColumns = ["songId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("songId")],
)
data class PlaylistSong(val playlistId: Long, val songId: String, val position: Int)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String,
    val songCount: Int,
    val durationMs: Long,
)
