package ru.daniil.tracklist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.daniil.tracklist.data.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var db: MusicDatabase
    private lateinit var repo: MusicRepository
    private val api =
        object : ItunesApi {
            override suspend fun search(
                term: String,
                media: String,
                entity: String,
                limit: Int,
                country: String,
            ) = SearchResponse(emptyList())
        }

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MusicDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        repo = MusicRepository(db, api)
    }

    @After
    fun close() {
        db.close()
    }

    private fun song(id: String) =
        Song(id = id, title = "Песня $id", artist = "Автор", durationMs = 120000)

    @Test
    fun duplicateImportPreservesUserChanges() = runBlocking {
        val original = song("itunes:1")
        assertTrue(repo.importSong(original))
        repo.saveSong(original.copy(title = "Моё название", favorite = true))
        assertFalse(repo.importSong(original))
        assertEquals("Моё название", db.musicDao().song(original.id)!!.title)
        assertTrue(db.musicDao().song(original.id)!!.favorite)
    }

    @Test
    fun editChangesExistingSongInsteadOfAddingAnother() = runBlocking {
        val s = song("one")
        repo.saveSong(s)
        repo.saveSong(s.copy(title = "Изменено"))
        assertEquals(1, db.musicDao().songs().size)
        assertEquals("Изменено", db.musicDao().song("one")!!.title)
    }

    @Test
    fun deletingSongRemovesPlaylistLinks() = runBlocking {
        repo.importSong(song("one"))
        repo.savePlaylist(null, "Дорога", "")
        val p = db.musicDao().playlists().single().id
        repo.addToPlaylist(p, "one")
        repo.deleteSong("one")
        assertTrue(db.musicDao().links(p).isEmpty())
        assertEquals(1, db.musicDao().playlists().size)
    }

    @Test
    fun deletingPlaylistPreservesSongs() = runBlocking {
        repo.importSong(song("one"))
        repo.savePlaylist(null, "Дорога", "")
        val p = db.musicDao().playlists().single().id
        repo.addToPlaylist(p, "one")
        repo.deletePlaylist(p)
        assertEquals(1, db.musicDao().songs().size)
        assertTrue(db.musicDao().allLinks().isEmpty())
    }

    @Test
    fun sameSongCannotBeAddedToPlaylistTwice() = runBlocking {
        repo.importSong(song("one"))
        repo.savePlaylist(null, "Дорога", "")
        val p = db.musicDao().playlists().single().id
        assertTrue(repo.addToPlaylist(p, "one"))
        assertFalse(repo.addToPlaylist(p, "one"))
        assertEquals(1, db.musicDao().links(p).size)
    }

    @Test
    fun playlistNamesAreUniqueIgnoringCyrillicCase() = runBlocking {
        repo.savePlaylist(null, " Дорога ", "")
        try {
            repo.savePlaylist(null, "ДОРОГА", "")
            fail("Expected conflict")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {}
        assertEquals(1, db.musicDao().playlists().size)
    }

    @Test
    fun movingSongPersistsOrderAndBoundaryMoveIsSafe() = runBlocking {
        repo.savePlaylist(null, "Дорога", "")
        val p = db.musicDao().playlists().single().id
        listOf("one", "two", "three").forEach {
            repo.importSong(song(it))
            repo.addToPlaylist(p, it)
        }
        repo.move(p, "three", -1)
        repo.move(p, "one", -1)
        assertEquals(listOf("one", "three", "two"), db.musicDao().links(p).map { it.songId })
    }

    @Test
    fun backupRoundTripRetainsFavoritesAndPlaylistOrder() = runBlocking {
        repo.importSong(song("one").copy(favorite = true))
        repo.importSong(song("two"))
        repo.savePlaylist(null, "Дорога", "На выходные")
        val p = db.musicDao().playlists().single().id
        repo.addToPlaylist(p, "two")
        repo.addToPlaylist(p, "one")
        val backup = BackupManager(db)
        val json = backup.exportJson()
        repo.deleteSong("one")
        repo.deletePlaylist(p)
        backup.restoreJson(json)
        assertEquals(2, db.musicDao().songs().size)
        assertTrue(db.musicDao().song("one")!!.favorite)
        assertEquals(listOf("two", "one"), db.musicDao().links(p).map { it.songId })
    }

    @Test
    fun invalidBackupLeavesCurrentLibraryUntouched() = runBlocking {
        repo.importSong(song("one"))
        val backup = BackupManager(db)
        val bad = backup.exportJson().replace("\"durationMs\": 120000", "\"durationMs\": -1000")
        try {
            backup.restoreJson(bad)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1, db.musicDao().songs().size)
        assertEquals(120000L, db.musicDao().song("one")!!.durationMs)
    }

    @Test
    fun unknownBackupVersionIsRejected() = runBlocking {
        repo.importSong(song("one"))
        val backup = BackupManager(db)
        try {
            backup.restoreJson("{\"app\":\"TrackList\",\"version\":9}")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1, db.musicDao().songs().size)
    }
}
