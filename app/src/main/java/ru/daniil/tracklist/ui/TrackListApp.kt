package ru.daniil.tracklist.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.daniil.tracklist.data.PlaylistSummary
import ru.daniil.tracklist.data.Song
import ru.daniil.tracklist.domain.TrackRules

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs =
    listOf(
        Tab("library", "Фонотека", Icons.Default.LibraryMusic),
        Tab("playlists", "Подборки", Icons.AutoMirrored.Filled.QueueMusic),
        Tab("catalog", "Каталог", Icons.Default.Search),
        Tab("settings", "Настройки", Icons.Default.Settings),
    )

@Composable
fun TrackListApp(vm: MainViewModel) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val activity = LocalContext.current as? Activity
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFF74D9BF),
                secondary = Color(0xFFB4CBC2),
                primaryContainer = Color(0xFF005045),
                onPrimaryContainer = Color(0xFFB6F2E1),
                secondaryContainer = Color(0xFF304B42),
                onSecondaryContainer = Color(0xFFD9EDE4),
                background = Color(0xFF101512),
                surface = Color(0xFF101512),
            )
        else
            lightColorScheme(
                primary = Color(0xFF006B5E),
                secondary = Color(0xFF4C635B),
                primaryContainer = Color(0xFFB6F2E1),
                onPrimaryContainer = Color(0xFF004D42),
                secondaryContainer = Color(0xFFD9EDE4),
                onSecondaryContainer = Color(0xFF12332A),
                background = Color(0xFFFAFCF9),
                surface = Color(0xFFFAFCF9),
            )
    MaterialTheme(colorScheme = colors) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "library"
        val snackbar = remember { SnackbarHostState() }
        val message by vm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                vm.clearMessage()
            }
        }
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    snackbar,
                    modifier =
                        Modifier.padding(
                            bottom = if (route == "library" || route == "playlists") 80.dp else 0.dp
                        ),
                )
            },
            bottomBar = {
                if (tabs.any { it.route == route })
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
            },
        ) { padding ->
            NavHost(nav, "library", Modifier.padding(padding)) {
                composable("library") {
                    LibraryScreen(
                        vm,
                        { nav.navigate("song/${Uri.encode(it)}") },
                        { nav.navigate("edit/new") },
                    )
                }
                composable("catalog") { CatalogScreen(vm) }
                composable("playlists") { PlaylistsScreen(vm) { nav.navigate("playlist/$it") } }
                composable("settings") { SettingsScreen(vm) }
                composable(
                    "song/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { back ->
                    val id = back.arguments?.getString("id").orEmpty()
                    SongScreen(
                        vm,
                        id,
                        { nav.popBackStack() },
                        { nav.navigate("edit/${Uri.encode(id)}") },
                    )
                }
                composable(
                    "edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { back ->
                    EditSongScreen(vm, back.arguments?.getString("id").orEmpty()) {
                        nav.popBackStack()
                    }
                }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { back ->
                    PlaylistScreen(vm, back.arguments?.getLong("id") ?: 0, { nav.popBackStack() }) {
                        nav.navigate("song/${Uri.encode(it)}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null)
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        },
        actions = actions,
    )
}

@Composable
private fun EmptyState(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.MusicNote,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${song.artist} · ${TrackRules.duration(song.durationMs)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
        trailingContent = trailing,
    )
}

@Composable
private fun LibraryScreen(vm: MainViewModel, openSong: (String) -> Unit, addSong: () -> Unit) {
    val songs by vm.visibleSongs.collectAsStateWithLifecycle()
    val all by vm.songs.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val favorites by vm.favoritesOnly.collectAsStateWithLifecycle()
    val order by vm.order.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column {
            Header("ТрекЛист", "Моя фонотека · ${TrackRules.songCount(all.size)}") {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.Default.Sort, "Сортировка")
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        SongOrder.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = {
                                    vm.order.value = item
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                query,
                { vm.query.value = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Название, исполнитель, альбом") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    !favorites,
                    { vm.favoritesOnly.value = false },
                    label = { Text("Все песни") },
                )
                FilterChip(
                    favorites,
                    { vm.favoritesOnly.value = true },
                    label = { Text("Избранное") },
                    leadingIcon = { Icon(Icons.Default.Favorite, null, Modifier.size(16.dp)) },
                )
            }
            Text(
                order.label,
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (songs.isEmpty())
                EmptyState(
                    if (all.isEmpty()) "Фонотека пока пуста" else "Песни не найдены",
                    if (all.isEmpty())
                        "Добавьте песню кнопкой + или найдите её во вкладке «Каталог»."
                    else "Измените запрос или переключитесь на все песни.",
                )
            LazyColumn(contentPadding = PaddingValues(bottom = 92.dp)) {
                items(songs, key = { it.id }) { song ->
                    SongRow(song, { openSong(song.id) }) {
                        IconButton(onClick = { vm.favorite(song) }) {
                            Icon(
                                if (song.favorite) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                if (song.favorite) "Убрать из избранного" else "В избранное",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = addSong,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Добавить песню") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun CatalogScreen(vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val state by vm.search.collectAsStateWithLifecycle()
    val saved by vm.songs.collectAsStateWithLifecycle()
    val ids = remember(saved) { saved.map { it.id }.toSet() }
    Column(Modifier.fillMaxSize()) {
        Header("Каталог", "Поиск через iTunes Search API")
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text("Песня или исполнитель") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            trailingIcon = {
                IconButton(onClick = { vm.search(query) }) { Icon(Icons.Default.Search, "Найти") }
            },
        )
        Text(
            "Добавляйте сведения о песнях в личную фонотеку.",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val s = state) {
            SearchState.Idle ->
                EmptyState(
                    "Найдите свою музыку",
                    "Введите название песни или имя исполнителя.",
                    Icons.Default.Search,
                )
            SearchState.Loading ->
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            is SearchState.Error -> {
                EmptyState("Поиск недоступен", s.message, Icons.Default.CloudOff)
                Button(
                    onClick = { vm.search(query) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Повторить")
                }
            }
            is SearchState.Content -> {
                if (s.songs.isEmpty())
                    EmptyState(
                        "Ничего не найдено",
                        "Попробуйте уточнить запрос.",
                        Icons.Default.Search,
                    )
                LazyColumn {
                    items(s.songs, key = { it.id }) { song ->
                        SongRow(song, { vm.importSong(song) }) {
                            IconButton(
                                onClick = { vm.importSong(song) },
                                enabled = song.id !in ids,
                            ) {
                                Icon(
                                    if (song.id in ids) Icons.Default.Check else Icons.Default.Add,
                                    if (song.id in ids) "Уже добавлено" else "Добавить в фонотеку",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(vm: MainViewModel, open: (Long) -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column {
            Header("Подборки", "Музыка для разных моментов")
            if (playlists.isEmpty())
                EmptyState(
                    "Создайте первую подборку",
                    "Например, «В дорогу» или «Для учёбы».",
                    Icons.AutoMirrored.Filled.QueueMusic,
                )
            LazyColumn(contentPadding = PaddingValues(bottom = 92.dp)) {
                items(playlists, key = { it.id }) { p ->
                    ListItem(
                        modifier = Modifier.clickable { open(p.id) },
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text(
                                "${TrackRules.songCount(p.songCount)} · ${TrackRules.duration(p.durationMs)}"
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { create = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Новая подборка") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (create)
        PlaylistDialog(null, { create = false }) { name, description ->
            vm.savePlaylist(null, name, description) { create = false }
        }
}

@Composable
private fun PlaylistDialog(
    playlist: PlaylistSummary?,
    dismiss: () -> Unit,
    save: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(playlist?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(playlist?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (playlist == null) "Новая подборка" else "Изменить подборку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name,
                    { name = it.take(80) },
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    description,
                    { description = it.take(500) },
                    label = { Text("Описание") },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { save(name, description) }, enabled = name.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } },
    )
}

@Composable
private fun PlaylistScreen(
    vm: MainViewModel,
    id: Long,
    back: () -> Unit,
    openSong: (String) -> Unit,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.find { it.id == id }
    val flow = remember(id) { vm.playlistSongs(id) }
    val songs by flow.collectAsStateWithLifecycle(emptyList())
    val all by vm.songs.collectAsStateWithLifecycle()
    var choose by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<Song?>(null) }
    Column(Modifier.fillMaxSize()) {
        Header(
            playlist?.name ?: "Подборка",
            "${TrackRules.songCount(songs.size)} · ${TrackRules.duration(songs.sumOf {it.durationMs})}",
            back,
        ) {
            IconButton(onClick = { edit = true }, enabled = playlist != null) {
                Icon(Icons.Default.Edit, "Изменить подборку")
            }
            IconButton(onClick = { delete = true }, enabled = playlist != null) {
                Icon(Icons.Default.DeleteOutline, "Удалить подборку")
            }
        }
        playlist
            ?.description
            ?.takeIf { it.isNotBlank() }
            ?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        OutlinedButton(
            onClick = { choose = true },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Add, null)
            Text("Добавить из фонотеки")
        }
        if (songs.isEmpty())
            EmptyState(
                "В подборке пока нет песен",
                "Добавьте песни из фонотеки. Их порядок можно менять стрелками.",
            )
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                Column {
                    SongRow(song, { openSong(song.id) }) {
                        IconButton(onClick = { remove = song }) {
                            Icon(Icons.Default.RemoveCircleOutline, "Убрать из подборки")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(end = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        val index = songs.indexOf(song)
                        TextButton(onClick = { vm.move(id, song.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, null, Modifier.size(16.dp))
                            Text("Выше")
                        }
                        TextButton(
                            onClick = { vm.move(id, song.id, 1) },
                            enabled = index < songs.lastIndex,
                        ) {
                            Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp))
                            Text("Ниже")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (choose) {
        val existing = songs.map { it.id }.toSet()
        AlertDialog(
            onDismissRequest = { choose = false },
            title = { Text("Добавить песни") },
            text = {
                if (all.isEmpty()) Text("Сначала добавьте песни в фонотеку.")
                else
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(all, key = { it.id }) { s ->
                            TextButton(
                                onClick = { vm.addToPlaylist(id, s.id) },
                                enabled = s.id !in existing,
                            ) {
                                Text(
                                    if (s.id in existing) "✓ ${s.title}" else "+ ${s.title}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
            },
            confirmButton = { TextButton(onClick = { choose = false }) { Text("Готово") } },
        )
    }
    if (edit && playlist != null)
        PlaylistDialog(playlist, { edit = false }) { name, description ->
            vm.savePlaylist(id, name, description) { edit = false }
        }
    if (delete)
        ConfirmDialog("Удалить подборку?", "Песни останутся в фонотеке.", { delete = false }) {
            vm.deletePlaylist(id, back)
            delete = false
        }
    remove?.let { s ->
        ConfirmDialog("Убрать песню?", "«${s.title}» останется в фонотеке.", { remove = null }) {
            vm.removeFromPlaylist(id, s.id)
            remove = null
        }
    }
}

@Composable
private fun SongScreen(vm: MainViewModel, id: String, back: () -> Unit, edit: () -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val song = songs.find { it.id == id }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var choose by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("О песне", onBack = back)
        if (song == null) {
            EmptyState("Песня недоступна", "Вернитесь в фонотеку.")
            return@Column
        }
        Surface(
            Modifier.align(Alignment.CenterHorizontally).padding(24.dp).size(144.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Альбом: ${song.album.ifBlank {"не указан"}}")
            Text("Длительность: ${TrackRules.duration(song.durationMs)}")
            OutlinedButton(onClick = { vm.favorite(song) }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (song.favorite) "Убрать из избранного" else "В избранное")
            }
            Button(onClick = { choose = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить в подборку")
            }
            OutlinedButton(onClick = edit, modifier = Modifier.fillMaxWidth()) {
                Text("Редактировать сведения")
            }
            if (
                song.sourceUrl.startsWith("https://") &&
                    Uri.parse(song.sourceUrl).host?.let {
                        it == "music.apple.com" || it == "itunes.apple.com"
                    } == true
            )
                TextButton(
                    onClick = {
                        runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(song.sourceUrl))
                                )
                            }
                            .onFailure { vm.notify("Не найдено приложение для открытия ссылки") }
                    }
                ) {
                    Text("Открыть страницу в каталоге Apple")
                }
            TextButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Удалить из фонотеки", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (choose && song != null)
        AlertDialog(
            onDismissRequest = { choose = false },
            title = { Text("Выберите подборку") },
            text = {
                if (playlists.isEmpty()) Text("Создайте подборку на вкладке «Подборки».")
                else
                    LazyColumn {
                        items(playlists, key = { it.id }) { p ->
                            TextButton(
                                onClick = {
                                    vm.addToPlaylist(p.id, song.id)
                                    choose = false
                                }
                            ) {
                                Text(p.name)
                            }
                        }
                    }
            },
            confirmButton = { TextButton(onClick = { choose = false }) { Text("Закрыть") } },
        )
    if (delete && song != null)
        ConfirmDialog(
            "Удалить песню?",
            "«${song.title}» будет удалена из фонотеки и всех подборок.",
            { delete = false },
        ) {
            vm.deleteSong(song, back)
            delete = false
        }
}

@Composable
private fun EditSongScreen(vm: MainViewModel, id: String, back: () -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val existing = songs.find { it.id == id }
    // Retain edits on rotation and initialise only once when a restored route loads its song.
    var title by rememberSaveable(id) { mutableStateOf(existing?.title.orEmpty()) }
    var artist by rememberSaveable(id) { mutableStateOf(existing?.artist.orEmpty()) }
    var album by rememberSaveable(id) { mutableStateOf(existing?.album.orEmpty()) }
    var time by
        rememberSaveable(id) { mutableStateOf(TrackRules.duration(existing?.durationMs ?: 0)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable(id) { mutableStateOf(id == "new" || existing != null) }
    val draftId = rememberSaveable(id) { existing?.id ?: java.util.UUID.randomUUID().toString() }
    LaunchedEffect(existing?.id) {
        if (!initialized && existing != null) {
            title = existing.title
            artist = existing.artist
            album = existing.album
            time = TrackRules.duration(existing.durationMs)
            initialized = true
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(if (id == "new") "Новая песня" else "Редактирование", onBack = back)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Обязательные поля: название и исполнитель.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                title,
                { title = it.take(160) },
                Modifier.fillMaxWidth(),
                label = { Text("Название песни") },
                singleLine = true,
            )
            OutlinedTextField(
                artist,
                { artist = it.take(160) },
                Modifier.fillMaxWidth(),
                label = { Text("Исполнитель") },
                singleLine = true,
            )
            OutlinedTextField(
                album,
                { album = it.take(200) },
                Modifier.fillMaxWidth(),
                label = { Text("Альбом") },
                singleLine = true,
            )
            OutlinedTextField(
                time,
                { time = it.take(8) },
                Modifier.fillMaxWidth(),
                label = { Text("Длительность, мин:сек") },
                supportingText = { Text("Например, 3:25. Если неизвестна, оставьте 0:00.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = TrackRules.validate(title, artist, time)
                    if (error == null) {
                        val s =
                            (existing ?: Song(id = draftId, title = title, artist = artist)).copy(
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = TrackRules.parseDuration(time)!!,
                            )
                        vm.saveSong(s, back)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = id == "new" || existing != null,
            ) {
                Text("Сохранить песню")
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreText by remember { mutableStateOf<String?>(null) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val text = vm.exportJson()
                            context.contentResolver.openOutputStream(uri, "wt")?.use {
                                it.write(text.toByteArray(Charsets.UTF_8))
                            } ?: error("Нет доступа к файлу")
                        }
                        vm.notify("Резервная копия сохранена")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        vm.notify("Не удалось сохранить копию")
                    }
                }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        restoreText =
                            withContext(Dispatchers.IO) {
                                context.contentResolver
                                    .openInputStream(uri)
                                    ?.bufferedReader()
                                    ?.use { reader ->
                                        val buffer = CharArray(8192)
                                        val text = StringBuilder()
                                        while (true) {
                                            val count = reader.read(buffer)
                                            if (count < 0) break
                                            text.append(buffer, 0, count)
                                            require(text.length <= 5_000_000)
                                        }
                                        text.toString()
                                    } ?: error("Нет доступа к файлу")
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        vm.notify("Не удалось прочитать копию. Максимальный размер — 5 МБ.")
                    }
                }
        }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Настройки")
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            listOf("system" to "Как в системе", "light" to "Светлая тема", "dark" to "Тёмная тема")
                .forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setTheme(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(theme == value, { vm.setTheme(value) })
                        Text(label)
                    }
                }
            HorizontalDivider()
            Text("Резервная копия", style = MaterialTheme.typography.titleMedium)
            Text(
                "Копия содержит песни, избранное, подборки и порядок песен. Сохраните файл вне приложения, чтобы перенести данные на другое устройство.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { export.launch("tracklist_backup.json") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить в файл")
            }
            OutlinedButton(
                onClick = {
                    restore.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Восстановить из файла")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            val text =
                                withContext(Dispatchers.IO) {
                                    java.io
                                        .File(context.filesDir, "tracklist_backup.json")
                                        .readText()
                                }
                            restoreText = text
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            vm.notify("Автоматическая копия пока не создана")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть автоматическую копию")
            }
            Text(
                "Автоматическая копия во внутренней папке создаётся примерно раз в сутки. Android выбирает время запуска. При удалении приложения эта копия также удаляется.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text("Обучение", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = vm::addExamples) { Text("Добавить учебные примеры песен") }
            Text(
                "Примеры вымышлены и служат для демонстрации. Приложение хранит сведения о песнях. Воспроизведение аудио в эту версию не входит.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "ТрекЛист 1.0",
                Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    restoreText?.let { text ->
        ConfirmDialog(
            "Восстановить фонотеку?",
            "Текущие песни и подборки будут заменены данными из копии. Сначала сохраните текущую фонотеку в отдельный файл.",
            { restoreText = null },
        ) {
            restoreText = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { vm.restoreJson(text) }
                    vm.notify("Фонотека восстановлена")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    vm.notify(
                        "Копия повреждена или имеет неподдерживаемый формат. Фонотека не изменена."
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } },
    )
}
