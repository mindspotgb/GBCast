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
        val dataPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        ) { uri -> if (uri != null) scope.launch { GBCastBridge.dataFolder.choose(uri) } }
        val gamesPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                Storage.takeFolder(uri)
                GBCastBridge.setGamesDir(ctx, uri.toString())
            }
        }
        val chooseDataFolder = {
            val start = GBCastBridge.dataFolder.folder?.let(android.net.Uri::parse)
                ?: gb.frame.StorageDefaults.treePickerUri(gb.frame.StorageDefaults.EMULATORS_DIRECTORY)
            dataPicker.launch(start)
        }
        val chooseGamesFolder = {
            val start = GBCastBridge.gamesDir().takeIf { it.isNotBlank() }?.let(android.net.Uri::parse)
                ?: gb.frame.StorageDefaults.treePickerUri(gb.frame.StorageDefaults.DREAMCAST_GAMES_DIRECTORY)
            gamesPicker.launch(start)
        }
        androidx.compose.runtime.LaunchedEffect(setup) {
            if (!setup) {
                GBCastBridge.applyDefaultSettings() // first run starts with the defaults; the wizard button applies the recommendation
                recommendation = GBCastBridge.recommendedPresetName()
            }
        }
        // Game requested by an intent (frontends, `am start`): matched to the library by file name, then started like a
        // tap on its cover (auto load included)
        val requested = gb.frame.LaunchIntent.pending.value
        androidx.compose.runtime.LaunchedEffect(requested, setup) {
            if (requested == null || !setup) return@LaunchedEffect
            android.util.Log.i("GBCast-Intent", "processing launch request: ${requested.name}; source=${requested.source?.substringBefore(':') ?: "title"}")
            gb.frame.LaunchIntent.done() // changes this effect's key: the work runs in the root scope so it isn't cancelled
            scope.launch {
                val gamesDir = Storage.gamesDir
                val game = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Storage.resolveLaunchRequest(requested, Storage.cachedScan(gamesDir)) {
                        Storage.scan(gamesDir)
                    }
                }
                android.util.Log.i(
                    "GBCast-Intent",
                    "resolved launch request '${requested.name}' to ${game?.title ?: "no match"} source=${game?.path?.substringBefore(':')}",
                )
                val error = if (game == null) gb.frame.LaunchIntent.notFound(requested) else GBCastBridge.launch(ctx, game.path)
                if (error != null) {
                    android.util.Log.e("GBCast-Intent", "could not launch '${requested.name}': $error")
                    snackbar.showSnackbar(gb.frame.localizeAppText(ctx, error))
                }
            }
        }

        Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            if (!setup) {
                SetupWizard(
                    steps = listOf(
                        gb.frame.dataFolderWizardStep(GBCastBridge.dataFolder, chooseDataFolder),
                        gb.frame.gamesFolderWizardStep(
                            gb.frame.StorageDefaults.DREAMCAST_GAMES_DIRECTORY,
                            "Pick the folder with your Dreamcast, Naomi, Naomi 2 or Atomiswave images. GDI, CUE, CDI, CHD, ZIP and 7Z are indexed. Track BIN files next to a GDI or CUE are kept with that disc.",
                            { GBCastBridge.gamesDir().takeIf { it.isNotBlank() }?.let(Storage::folderLabel) },
                            chooseGamesFolder,
                        ),
                        gb.frame.performanceWizardStep(recommendation, GBCastBridge.presetWizardOptions(recommendation),
                            onApply = { GBCastBridge.applyRecommendedPreset(recommendation) }),
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
                gb.frame.AppUpdatePrompt(UPDATE_CONFIG)
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = androidx.compose.ui.graphics.Color.Transparent) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (screen) {
                            Screen.Library -> LibraryScreen(
                                onOpenSettings = { settingsPage = it; screen = Screen.Settings },
                                onQuit = onQuit,
                                launch = { path, slot, fresh, progress -> GBCastBridge.launch(ctx, path, slot, fresh, progress) },
                                onLaunchError = { message -> scope.launch { snackbar.showSnackbar(gb.frame.localizeAppText(ctx, message)) } },
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
                                toast = { msg -> scope.launch { snackbar.showSnackbar(gb.frame.localizeAppText(ctx, msg)) } },
                                page = settingsPage,
                            )
                        }
                    }
                }
            }
        }
    }
}

