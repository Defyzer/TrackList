package ru.daniil.tracklist.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import ru.daniil.tracklist.domain.TrackRules

class BackupManager(private val db: MusicDatabase) {
    suspend fun exportJson(): String =
        db.withTransaction {
            val dao = db.musicDao()
            JSONObject()
                .put("version", 1)
                .put("app", "TrackList")
                .put(
                    "songs",
                    JSONArray().apply {
                        dao.songs().forEach { s ->
                            put(
                                JSONObject()
                                    .put("id", s.id)
                                    .put("title", s.title)
                                    .put("artist", s.artist)
                                    .put("album", s.album)
                                    .put("durationMs", s.durationMs)
                                    .put("favorite", s.favorite)
                                    .put("sourceUrl", s.sourceUrl)
                                    .put("createdAt", s.createdAt)
                            )
                        }
                    },
                )
                .put(
                    "playlists",
                    JSONArray().apply {
                        dao.playlists().forEach { p ->
                            put(
                                JSONObject()
                                    .put("id", p.id)
                                    .put("name", p.name)
                                    .put("description", p.description)
                                    .put("createdAt", p.createdAt)
                            )
                        }
                    },
                )
                .put(
                    "links",
                    JSONArray().apply {
                        dao.allLinks().forEach { l ->
                            put(
                                JSONObject()
                                    .put("playlistId", l.playlistId)
                                    .put("songId", l.songId)
                                    .put("position", l.position)
                            )
                        }
                    },
                )
                .toString(2)
        }

    suspend fun restoreJson(text: String) {
        require(text.length <= 5_000_000) { "Файл слишком большой" }
        val root = JSONObject(text)
        require(root.optString("app") == "TrackList" && root.getInt("version") == 1) {
            "Неизвестный формат копии"
        }
        val songs =
            root.getJSONArray("songs").objects().map { o ->
                Song(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    album = o.optString("album"),
                    durationMs = o.getLong("durationMs"),
                    favorite = o.getBoolean("favorite"),
                    sourceUrl = o.optString("sourceUrl"),
                    createdAt = o.getLong("createdAt"),
                )
            }
        require(songs.size <= 10_000 && songs.map { it.id }.distinct().size == songs.size)
        require(
            songs.all {
                it.id.isNotBlank() &&
                    it.durationMs in 0..359_999_000 &&
                    TrackRules.validate(it.title, it.artist, TrackRules.duration(it.durationMs)) ==
                        null
            }
        ) {
            "Некорректные песни в копии"
        }
        val playlists =
            root.getJSONArray("playlists").objects().map { o ->
                Playlist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    nameKey = TrackRules.key(o.getString("name")),
                    description = o.optString("description"),
                    createdAt = o.getLong("createdAt"),
                )
            }
        require(
            playlists.size <= 1000 &&
                playlists.all { it.id > 0 && it.name.isNotBlank() && it.name.length <= 80 }
        )
        require(
            playlists.map { it.id }.distinct().size == playlists.size &&
                playlists.map { it.nameKey }.distinct().size == playlists.size
        )
        val links =
            root.getJSONArray("links").objects().map {
                PlaylistSong(
                    it.getLong("playlistId"),
                    it.getString("songId"),
                    it.getInt("position"),
                )
            }
        val songIds = songs.map { it.id }.toSet()
        val playlistIds = playlists.map { it.id }.toSet()
        require(
            links.size <= 50_000 &&
                links.all {
                    it.playlistId in playlistIds && it.songId in songIds && it.position >= 0
                }
        )
        require(links.map { it.playlistId to it.songId }.distinct().size == links.size)
        db.withTransaction {
            val dao = db.musicDao()
            dao.clearLinks()
            dao.clearPlaylists()
            dao.clearSongs()
            songs.forEach { dao.insertSong(it) }
            playlists.forEach { dao.insertPlaylist(it) }
            links.forEach { dao.insertLink(it) }
        }
    }

    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
}
