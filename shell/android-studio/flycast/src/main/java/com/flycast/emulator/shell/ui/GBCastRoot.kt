package com.flycast.emulator.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.flycast.emulator.shell.GBCastBridge
import com.flycast.emulator.shell.Storage
import gb.frame.SetupWizard
import gb.frame.WizardStep
import gb.frame.MenuState
import kotlinx.coroutines.launch

private enum class Screen { Library, Settings }

@Composable
fun GBCastRoot(onQuit: () -> Unit) {
    GBCastTheme {
        gb.frame.DataFolderWarning(GBCastBridge.dataFolder)
        val ctx = LocalContext.current
        var setup by remember { mutableStateOf(GBCastBridge.setupDone(ctx)) }
        var screen by remember { mutableStateOf(Screen.Library) }
        var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var recommendation by remember { mutableStateOf("…") }
        androidx.compose.runtime.LaunchedEffect(setup) {
            if (!setup) recommendation = GBCastBridge.applyRecommendedPreset()
        }
        // Game requested by an intent (frontends, `am start`): matched to the library by file name, then started like a
        // tap on its cover (auto load included)
        val requested = gb.frame.LaunchIntent.pending.value
        androidx.compose.runtime.LaunchedEffect(requested, setup) {
            if (requested == null || !setup) return@LaunchedEffect
            android.util.Log.i("GBCast-Intent", "processing launch request: $requested")
            gb.frame.LaunchIntent.done() // changes this effect's key: the work runs in the root scope so it isn't cancelled
            scope.launch {
                val gamesDir = Storage.gamesDir
                val game = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    gb.frame.LaunchIntent.resolveOrRefresh(requested, Storage.cachedScan(gamesDir)) {
                        Storage.scan(gamesDir)
                    }
                }
                android.util.Log.i(
                    "GBCast-Intent",
                    "resolved launch request '$requested' to ${game?.title ?: "no library match"} path=${game?.path}",
                )
                val error = if (game == null) gb.frame.LaunchIntent.notFound(requested) else GBCastBridge.launch(ctx, game.path)
                if (error != null) {
                    android.util.Log.e("GBCast-Intent", "could not launch '$requested': $error")
                    snackbar.showSnackbar(error)
                }
            }
        }

        Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            if (!setup) {
                SetupWizard(
                    steps = listOf(
                        gb.frame.dataFolderWizardStep(GBCastBridge.dataFolder),
                        WizardStep(
                            Icons.Default.Folder,
                            "Games",
                            listOf(Color(0xFF3EC6FF), Color(0xFF1B6F99)),
                            "Pick the folder with your Dreamcast, Naomi, Naomi 2 or Atomiswave images. GDI, CUE, CDI, CHD, ZIP and 7Z are indexed. Track BIN files next to a GDI or CUE are kept with that disc.",
                        ) {
                            val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
                            ) { uri ->
                                if (uri != null) {
                                    com.flycast.emulator.shell.Storage.takeFolder(uri)
                                    GBCastBridge.setGamesDir(ctx, uri.toString())
                                }
                            }
                            androidx.compose.material3.Button(onClick = { picker.launch(null) }) {
                                Text(if (GBCastBridge.gamesDir().isBlank()) "Choose games folder" else "Folder selected")
                            }
                        },
                        gb.frame.performanceWizardStep(recommendation, GBCastBridge.presetWizardOptions(recommendation)),
                    ),
                    onFinish = {
                        GBCastBridge.finishSetup(ctx)
                        setup = true
                    },
                )
            } else {
                BackHandler(screen == Screen.Settings) {
                    screen = Screen.Library
                    if (MenuState.open.value) {
                        MenuState.menuHasFocus = false
                        MenuState.menuRefocus.intValue++
                    }
                }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = androidx.compose.ui.graphics.Color.Transparent) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (screen) {
                            Screen.Library -> LibraryScreen(
                                onOpenSettings = { settingsPage = it; screen = Screen.Settings },
                                onQuit = onQuit,
                                launch = { path, slot, fresh -> GBCastBridge.launch(ctx, path, slot, fresh) },
                                onLaunchError = { message -> scope.launch { snackbar.showSnackbar(message) } },
                                visualCustomizationProductId = "gbcast_gold",
                            )
                            Screen.Settings -> SettingsScreen(
                                onBack = {
                                    screen = Screen.Library
                                    if (MenuState.open.value) {
                                        MenuState.menuHasFocus = false
                                        MenuState.menuRefocus.intValue++
                                    }
                                },
                                toast = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                                page = settingsPage,
                            )
                        }
                    }
                }
            }
        }
    }
}

