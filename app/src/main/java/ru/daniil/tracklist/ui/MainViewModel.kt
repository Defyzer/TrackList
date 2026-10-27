package ru.daniil.tracklist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import ru.daniil.tracklist.data.*
import ru.daniil.tracklist.domain.TrackRules

sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Content(val songs: List<Song>) : SearchState

    data class Error(val message: String) : SearchState
}

enum class SongOrder(val label: String) {
    NEWEST("Сначала новые"),
    TITLE("По названию"),
    ARTIST("По исполнителю"),
}

class MainViewModel(
    private val repo: MusicRepository,
    private val settings: SettingsStore,
    private val backup: BackupManager,
) : ViewModel() {
    private val sharing = SharingStarted.WhileSubscribed(5000)
    val songs = repo.songs.stateIn(viewModelScope, sharing, emptyList())
    val playlists = repo.playlists.stateIn(viewModelScope, sharing, emptyList())
    val theme = settings.theme.stateIn(viewModelScope, sharing, "system")
    val query = MutableStateFlow("")
    val favoritesOnly = MutableStateFlow(false)
    val order = MutableStateFlow(SongOrder.NEWEST)
    val visibleSongs =
        combine(songs, query, favoritesOnly, order) { all, q, favorites, sort ->
                val filtered =
                    all.filter {
                        (!favorites || it.favorite) &&
                            TrackRules.matches(it.title, it.artist, it.album, q)
                    }
                when (sort) {
                    SongOrder.NEWEST -> filtered
                    SongOrder.TITLE -> filtered.sortedBy { TrackRules.key(it.title) }
                    SongOrder.ARTIST -> filtered.sortedBy { TrackRules.key(it.artist) }
                }
            }
            .stateIn(viewModelScope, sharing, emptyList())
    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search = _search.asStateFlow()
    private var searchJob: Job? = null
    val message = MutableStateFlow<String?>(null)

    fun clearMessage() {
        message.value = null
    }

    fun notify(text: String) {
        message.value = text
    }

    fun playlistSongs(id: Long) = repo.playlistSongs(id)

    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _search.value = SearchState.Error("Введите минимум два символа")
            return
        }
        searchJob =
            viewModelScope.launch {
                _search.value = SearchState.Loading
                try {
                    _search.value = SearchState.Content(repo.search(query))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    _search.value =
                        SearchState.Error(
                            "Не удалось подключиться. Проверьте интернет и повторите поиск."
                        )
                } catch (_: HttpException) {
                    _search.value =
                        SearchState.Error("Каталог временно недоступен. Повторите поиск позже.")
                } catch (_: Exception) {
                    _search.value = SearchState.Error("Не удалось прочитать ответ каталога.")
                }
            }
    }

    private fun action(
        success: String? = null,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                if (success != null) notify(success)
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                notify("Подборка с таким названием уже существует")
            } catch (_: Exception) {
                notify("Не удалось сохранить изменения. Проверьте данные и свободное место.")
            }
        }
    }

    fun saveSong(song: Song, onSuccess: () -> Unit) =
        action("Песня сохранена", onSuccess) { repo.saveSong(song) }

    fun importSong(song: Song) = action {
        notify(
            if (repo.importSong(song)) "Добавлено в фонотеку" else "Эта песня уже есть в фонотеке"
        )
    }

    fun favorite(song: Song) = action { repo.toggleFavorite(song.id) }

    fun deleteSong(song: Song, onSuccess: () -> Unit) =
        action("Песня удалена", onSuccess) { repo.deleteSong(song.id) }

    fun savePlaylist(id: Long?, name: String, description: String, onSuccess: () -> Unit) =
        action("Подборка сохранена", onSuccess) { repo.savePlaylist(id, name, description) }

    fun deletePlaylist(id: Long, onSuccess: () -> Unit) =
        action("Подборка удалена", onSuccess) { repo.deletePlaylist(id) }

    fun addToPlaylist(id: Long, song: String) = action {
        notify(
            if (repo.addToPlaylist(id, song)) "Добавлено в подборку"
            else "Песня уже есть в этой подборке"
        )
    }

    fun removeFromPlaylist(id: Long, song: String) =
        action("Песня убрана из подборки") { repo.removeFromPlaylist(id, song) }

    fun move(id: Long, song: String, offset: Int) = action { repo.move(id, song, offset) }

    fun setTheme(theme: String) = action { settings.setTheme(theme) }

    suspend fun exportJson() = backup.exportJson()

    suspend fun restoreJson(text: String) = backup.restoreJson(text)

    fun addExamples() =
        action("Учебные примеры добавлены") {
            listOf(
                    Song(
                        id = "demo:1",
                        title = "Утро у моря",
                        artist = "Учебная группа",
                        album = "Демо",
                        durationMs = 205000,
                        favorite = true,
                    ),
                    Song(
                        id = "demo:2",
                        title = "Городские огни",
                        artist = "Северный ветер",
                        album = "Демо",
                        durationMs = 242000,
                    ),
                    Song(
                        id = "demo:3",
                        title = "Тихая дорога",
                        artist = "Учебная группа",
                        album = "Демо",
                        durationMs = 187000,
                    ),
                )
                .forEach { repo.importSong(it) }
        }
}
