package com.flycast.emulator.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gbcast.emulator.R
import com.flycast.emulator.shell.GBCastBridge
import com.flycast.emulator.shell.GameIndex
import com.flycast.emulator.shell.Storage
import gb.frame.HomeHelpBar
import gb.frame.Covers
import gb.frame.rememberAppIconPainter
import gb.frame.CoverImageOptions
import gb.frame.HomeQuitDialog
import gb.frame.homeQuitKeys
import gb.frame.HomeActions
import gb.frame.HomeGame
import gb.frame.HomeLibrary
import gb.frame.HomeMenuItem
import gb.frame.HomeMenuPanel
import gb.frame.HomeTheme
import gb.frame.VisualCustomizationPurchaseHost
import gb.frame.HomeTopBar
import gb.frame.LibraryBack
import gb.frame.LibraryView
import gb.frame.LaunchLoadingOverlay
import gb.frame.MenuState
import gb.frame.MenuIcon
import gb.frame.PlayTime
import gb.frame.RaUserHeader
import gb.frame.ResumeEntry
import gb.frame.formatPlayTime
import gb.frame.palette
import gb.frame.rememberHasGamepad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    onOpenSettings: (SettingsPage) -> Unit,
    onQuit: () -> Unit,
    launch: suspend (String, Int, Boolean) -> String?,
    onLaunchError: (String) -> Unit,
    visualCustomizationProductId: String? = null,
) {
    var menuOpen by MenuState.open
    val style by HomeTheme.style
    val fullscreen by HomeTheme.fullscreen
    val menuFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val hasPad = rememberHasGamepad()
    val composeView = LocalView.current
    suspend fun focusMenu() {
        repeat(8) {
            if (MenuState.menuHasFocus) return
            composeView.requestFocusFromTouch()
            runCatching { menuFocus.requestFocus() }
            delay(60)
        }
    }
    LaunchedEffect(menuOpen, hasPad) {
        if (menuOpen) {
            if (hasPad) {
                delay(120)
                focusMenu()
            }
        } else {
            MenuState.menuHasFocus = false
            if (hasPad) MenuState.refocus.intValue++
        }
    }
    LaunchedEffect(MenuState.menuRefocus.intValue) { if (menuOpen && hasPad) focusMenu() }
    DisposableEffect(Unit) {
        MenuState.libraryShown = true
        onDispose {
            MenuState.libraryShown = false
            MenuState.hasFocus = false
            MenuState.menuHasFocus = false
        }
    }
    var confirmQuit by MenuState.quitOpen

    // The last scan's list at once (no "no games" flash, no spinner when coming back from a game); scanned again below
    var games by remember {
        mutableStateOf(Storage.cachedScan(GBCastBridge.gamesDir()).let { found ->
            val kept = GameIndex.filterTracks(found.map { it.path }).toSet()
            found.filter { it.path in kept }
        })
    }
    var loading by remember { mutableStateOf(games.isEmpty() && GBCastBridge.gamesDir().isNotBlank()) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var states by remember { mutableStateOf(GBCastBridge.lastStates) }
    var coverVersion by remember { mutableIntStateOf(0) }
    var openingGame by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }
    LaunchedEffect(GBCastBridge.gamesDir(), refreshKey) {
        val dir = GBCastBridge.gamesDir()
        if (dir.isBlank()) {
            games = emptyList()
            scanError = null
            return@LaunchedEffect
        }
        if (games.isNotEmpty() && scanError == null && !Storage.scanStale(dir)) return@LaunchedEffect // scanned a moment ago
        loading = games.isEmpty()
        val scanned = withContext(Dispatchers.IO) {
            runCatching {
                val found = Storage.scan(dir)
                val kept = GameIndex.filterTracks(found.map { it.path }).toSet()
                found.filter { it.path in kept }
            }
        }
        scanned.onSuccess {
            if (it != games) games = it
            scanError = null
        }.onFailure { scanError = it.message ?: "Could not read the games folder" }
        loading = false
    }
    LaunchedEffect(games, refreshKey) {
        val found = withContext(Dispatchers.IO) { runCatching { GBCastBridge.states(games) }.getOrDefault(emptyList()) }
        if (found != states) states = found
    }
    val entries = remember(states) {
        states.map { s ->
            ResumeEntry(
                s.gamePath + "#" + s.slot,
                s.title,
                if (s.slot == GBCastBridge.AUTO_SAVE_SLOT) "Auto save" else "Slot ${s.slot + 1}",
                s.time,
                null,
                game = s.gamePath,
            )
        }
    }
    val homeGames = remember(games, refreshKey) {
        games.map { HomeGame(it.path, it.title, PlayTime.of(it.path)) }
    }
    var gameMenu by remember { mutableStateOf<HomeGame?>(null) }
    val picker = rememberLauncherForActivityResultImage { title ->
        coverVersion++
        gameMenu = null
        title
    }

    BackHandler {
        LibraryBack.handle {
            if (gameMenu != null) {
                gameMenu = null
                true
            } else {
                false
            }
        }
    }

    val openPage: (SettingsPage) -> Unit = {
        MenuState.selected = it
        MenuState.menuHasFocus = false
        onOpenSettings(it)
    }
    fun showLaunchLoading(path: String, slot: Int, fresh: Boolean) {
        if (openingGame) return
        openingGame = true
        LibraryView.lastGame = path
        MenuState.selected = null
        scope.launch {
            val error = launch(path, slot, fresh)
            openingGame = false
            if (error != null) onLaunchError(error)
        }
    }
    val actions = HomeActions(
        play = { showLaunchLoading(it.key, -1, false) },
        fresh = { showLaunchLoading(it.key, -1, true) },
        options = { gameMenu = it },
        resume = { entry ->
            val slot = entry.key.substringAfterLast('#').toIntOrNull() ?: 0
            showLaunchLoading(entry.game, slot, false)
        },
        openMenu = { menuOpen = true },
    )
    val emptyKind = when {
        scanError != null -> EmptyKind.Error
        GBCastBridge.gamesDir().isBlank() -> EmptyKind.Folder
        games.isEmpty() -> if (loading) EmptyKind.Loading else EmptyKind.NoGames
        else -> null
    }
    val empty: (@Composable () -> Unit)? = if (emptyKind == null) null else {
        { EmptyContent(emptyKind, scanError, { openPage(SettingsPage.System) }) }
    }

    Box(
        Modifier.fillMaxSize().background(palette.backgroundBrush).onFocusChanged { MenuState.hasFocus = it.hasFocus }.onPreviewKeyEvent { e ->
            openingGame
        }.homeQuitKeys(onQuit),
    ) {
        Column(
            Modifier.fillMaxSize().then(if (fullscreen) Modifier.padding(horizontal = 10.dp, vertical = 8.dp) else Modifier.safeDrawingPadding().padding(12.dp)),
            verticalArrangement = Arrangement.spacedBy(if (fullscreen) 0.dp else 12.dp),
        ) {
            if (!fullscreen) {
                HomeTopBar(
                    rememberAppIconPainter(R.drawable.ic_launcher_foreground),
                    "GBCast",
                    onClick = {
                        if (!menuOpen) MenuState.selected = null
                        menuOpen = !menuOpen
                    },
                    menuOpen = menuOpen,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                HomeLibrary(
                    style,
                    homeGames,
                    entries,
                    actions,
                    coverVersion,
                    empty,
                    refocus = MenuState.refocus.intValue,
                    fullscreen = fullscreen,
                    focusEnabled = !menuOpen && gameMenu == null,
                )
            }
            if (!fullscreen && hasPad && HomeTheme.buttonHelp.value) HomeHelpBar()
        }
        if (fullscreen && !menuOpen) {
            Box(
                Modifier.align(Alignment.CenterStart).zIndex(5f).padding(start = 2.dp)
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(Color(0xCC102033))
                    .clickable {
                        MenuState.selected = null
                        menuOpen = true
                    }
                    .padding(horizontal = 5.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Default.Menu, "Open menu", tint = Color(0xFFFFB15A), modifier = Modifier.size(18.dp))
            }
        }
        HomeMenuPanel(
            open = menuOpen,
            onDismiss = { menuOpen = false },
            focus = menuFocus,
            appIcon = rememberAppIconPainter(R.drawable.ic_launcher_foreground),
            appName = "GBCast",
            themeLabel = style.label,
            fullscreen = fullscreen,
            onCycleTheme = { HomeTheme.cycleStyle() },
            onToggleFullscreen = { HomeTheme.fullscreen.value = !HomeTheme.fullscreen.value },
            items = menuItems(openPage),
            extras = listOf(HomeMenuItem(MenuIcon.About, "About", selected = MenuState.selected == SettingsPage.About) { openPage(SettingsPage.About) }),
            onQuit = { confirmQuit = true },
            header = { RaUserHeader() },
            onFocusChanged = { MenuState.menuHasFocus = it },
        )
        if (openingGame) {
            LaunchLoadingOverlay("Preparing the disc image. A cached copy is created only when direct access is unavailable on this device.")
        }
        visualCustomizationProductId?.let { VisualCustomizationPurchaseHost("GBCast", it) }
        gameMenu?.let { game ->
            GameOptions(
                game,
                entries.firstOrNull { it.game == game.key },
                onDismiss = { gameMenu = null },
                onContinue = { entry ->
                    gameMenu = null
                    actions.resume(entry)
                },
                onPlay = {
                    gameMenu = null
                    actions.play(game)
                },
                onCover = { picker(game.title) },
                onCoverApplied = { coverVersion++; gameMenu = null },
            )
        }
        HomeQuitDialog(onQuit)
    }
}

private fun menuItems(openPage: (SettingsPage) -> Unit) = listOf(
    SettingsPage.System to MenuIcon.System,
    SettingsPage.Video to MenuIcon.Video,
    SettingsPage.Audio to MenuIcon.Audio,
    SettingsPage.Performance to MenuIcon.Performance,
    SettingsPage.Controls to MenuIcon.Controls,
    SettingsPage.Extras to MenuIcon.Extras,
).map { (page, icon) ->
    HomeMenuItem(icon, page.label, selected = MenuState.selected == page) { openPage(page) }
}

private enum class EmptyKind { Folder, Loading, NoGames, Error }

@Composable
private fun EmptyContent(kind: EmptyKind, error: String?, toSystem: () -> Unit) {
    when (kind) {
        EmptyKind.Folder -> EmptyState(Icons.Default.Folder, "Choose your games folder", "GBCast reads Dreamcast and Naomi images through the folder you pick. No broad storage permission is required.", "Choose folder", toSystem)
        EmptyKind.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        EmptyKind.NoGames -> EmptyState(Icons.Default.SearchOff, "No games found", "No GBCast-compatible disc image was found in ${Storage.folderLabel(GBCastBridge.gamesDir())}.", null, {})
        EmptyKind.Error -> EmptyState(Icons.Default.Folder, "Could not read the folder", error ?: "The folder permission may have been revoked.", "Choose folder", toSystem)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .7f), textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun rememberLauncherForActivityResultImage(onPicked: (String) -> Unit): (String) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val name = title
        if (uri != null && name != null) scope.launch {
            Covers.setCustom(ctx, name, uri)
            onPicked(name)
        }
        title = null
    }
    return { name ->
        title = name
        picker.launch(arrayOf("image/*"))
    }
}

@Composable
private fun GameOptions(
    game: HomeGame,
    latest: ResumeEntry?,
    onDismiss: () -> Unit,
    onContinue: (ResumeEntry) -> Unit,
    onPlay: () -> Unit,
    onCover: () -> Unit,
    onCoverApplied: () -> Unit,
) {
    var coverOptions by remember(game.key) { mutableStateOf(false) }
    if (coverOptions) {
        CoverImageOptions(game.title, onLocalSearch = onCover, onDismiss = { coverOptions = false }, onApplied = onCoverApplied)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(game.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(if (game.playSeconds >= 60) "Played ${formatPlayTime(game.playSeconds)}" else "Not played yet")
                Spacer(Modifier.height(12.dp))
                gb.frame.GameOptionsActionRow(Icons.Default.PlayCircle, if (latest != null) "Continue" else "Continue (no save state)", enabled = latest != null) {
                    latest?.let(onContinue)
                }
                gb.frame.GameOptionsActionRow(Icons.Default.PlayArrow, "Play", onClick = onPlay)
                gb.frame.GameOptionsActionRow(Icons.Default.Image, "Cover Image") { coverOptions = true }
                gb.frame.GameOptionsActionRow(Icons.Default.Close, "Close", onClick = onDismiss)
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}


