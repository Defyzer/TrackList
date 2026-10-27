package ru.daniil.tracklist.data

import androidx.room.withTransaction
import ru.daniil.tracklist.domain.TrackRules

class MusicRepository(val db: MusicDatabase, private val api: ItunesApi) {
    private val dao = db.musicDao()
    val songs = dao.observeSongs()
    val playlists = dao.observePlaylists()

    fun playlistSongs(id: Long) = dao.observePlaylistSongs(id)

    suspend fun search(query: String): List<Song> =
        api.search(query.trim()).results.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }

    suspend fun importSong(song: Song): Boolean = dao.insertSong(song) != -1L

    suspend fun saveSong(song: Song) {
        require(
            TrackRules.validate(song.title, song.artist, TrackRules.duration(song.durationMs)) ==
                null
        )
        val clean =
            song.copy(
                title = song.title.trim(),
                artist = song.artist.trim(),
                album = song.album.trim(),
            )
        db.withTransaction {
            if (dao.song(song.id) == null) dao.insertSong(clean) else dao.updateSong(clean)
        }
    }

    suspend fun toggleFavorite(id: String) = dao.toggleFavorite(id)

    suspend fun deleteSong(id: String) = dao.deleteSong(id)

    suspend fun savePlaylist(id: Long?, name: String, description: String) {
        require(name.isNotBlank() && name.trim().length <= 80)
        val key = TrackRules.key(name)
        if (id == null)
            dao.insertPlaylist(
                Playlist(name = name.trim(), nameKey = key, description = description.trim())
            )
        else dao.updatePlaylist(id, name.trim(), key, description.trim())
    }

    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    suspend fun addToPlaylist(playlist: Long, song: String): Boolean =
        db.withTransaction {
            val next = (dao.links(playlist).maxOfOrNull { it.position } ?: -1) + 1
            dao.insertLink(PlaylistSong(playlist, song, next)) != -1L
        }

    suspend fun removeFromPlaylist(playlist: Long, song: String) = dao.removeLink(playlist, song)

    suspend fun move(playlist: Long, song: String, offset: Int) =
        db.withTransaction {
            val links = dao.links(playlist)
            val current = links.indexOfFirst { it.songId == song }
            val target = current + offset
            if (current >= 0 && target in links.indices) {
                val mutable = links.toMutableList()
                java.util.Collections.swap(mutable, current, target)
                mutable.forEachIndexed { position, link ->
                    dao.setPosition(playlist, link.songId, position)
                }
            }
        }
}
