package ru.daniil.tracklist.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY createdAt DESC, id") fun observeSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC, id") suspend fun songs(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id") suspend fun song(id: String): Song?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSong(song: Song): Long

    @Update suspend fun updateSong(song: Song)

    @Query("UPDATE songs SET favorite = NOT favorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("DELETE FROM songs WHERE id = :id") suspend fun deleteSong(id: String)

    @Query(
        "SELECT p.id, p.name, p.description, COUNT(s.id) AS songCount, COALESCE(SUM(s.durationMs),0) AS durationMs FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlistId=p.id LEFT JOIN songs s ON s.id=ps.songId GROUP BY p.id ORDER BY p.createdAt DESC, p.id DESC"
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun playlists(): List<Playlist>

    @Insert suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name=:name, nameKey=:key, description=:description WHERE id=:id")
    suspend fun updatePlaylist(id: Long, name: String, key: String, description: String)

    @Query("DELETE FROM playlists WHERE id=:id") suspend fun deletePlaylist(id: Long)

    @Query(
        "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON ps.songId=s.id WHERE ps.playlistId=:id ORDER BY ps.position, ps.songId"
    )
    fun observePlaylistSongs(id: Long): Flow<List<Song>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId=:id ORDER BY position, songId")
    suspend fun links(id: Long): List<PlaylistSong>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position, songId")
    suspend fun allLinks(): List<PlaylistSong>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLink(link: PlaylistSong): Long

    @Query("DELETE FROM playlist_songs WHERE playlistId=:playlist AND songId=:song")
    suspend fun removeLink(playlist: Long, song: String)

    @Query(
        "UPDATE playlist_songs SET position=:position WHERE playlistId=:playlist AND songId=:song"
    )
    suspend fun setPosition(playlist: Long, song: String, position: Int)

    @Query("DELETE FROM playlist_songs") suspend fun clearLinks()

    @Query("DELETE FROM playlists") suspend fun clearPlaylists()

    @Query("DELETE FROM songs") suspend fun clearSongs()
}
