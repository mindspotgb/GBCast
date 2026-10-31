package com.flycast.emulator.shell

import android.app.Activity
import gb.frame.MonitoringQuickMenuRows
import gb.frame.FastForwardQuickMenuAction
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.flycast.emulator.emu.JNIdc
import com.flycast.emulator.shell.ui.DreamcastControls
import com.flycast.emulator.shell.ui.GBCastTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
private fun StateSlot(index: Int, save: Boolean, onClick: () -> Unit) {
    val state by androidx.compose.runtime.produceState<Pair<Long?, android.graphics.Bitmap?>>(null to null, index, save) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val time = JNIdc.stateTimestamp(index).takeIf { it > 0 }
            val thumb = JNIdc.stateThumbnail(index)?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            time to thumb
        }
    }
    gb.frame.QuickMenuStateSlot(
        id = "slot-$index", label = "Slot ${index + 1}", time = state.first,
        thumbnail = state.second, enabled = save || state.first != null, onClick = onClick,
    )
}

/** In-game quick menu. Pausing uses Flycast's gui_togglePause, which stops the CPU and keeps the last frame. */
object GbcastGameUi {
    const val CLOSED = 0
    const val COMMANDS = 1
    const val PAUSE = 2
    const val SETTINGS = 3
    const val MAIN = 4
    const val VJOY = 6
    const val VJOY_CMD = 7
    const val DISK = 8
    const val LOADING = 9
    const val CHEATS = 11
    const val ACHIEVEMENTS = 12

    @JvmStatic var menuOwnsKeys: Boolean = false
    private var visible by mutableStateOf(false)
    private var compose: ComposeView? = null
    private var glView: View? = null
    private var attachedActivity: Activity? = null
    private var message by mutableStateOf<String?>(null)

    @JvmStatic
    fun attach(activity: Activity, layout: ViewGroup, gameView: View) {
        attachedActivity = activity
        glView = gameView
        Storage.start(activity)
        com.flycast.emulator.shell.ui.GBCastOverlayOptions.initialize(activity)
        val view = ComposeView(activity).apply {
            // Keyboard focus belongs to Compose's child owner, not this Android wrapper.
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GBCastTheme {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        com.flycast.emulator.shell.ui.GBCastGameplayOverlays { onAndroidBack(activity) }
                        if (visible) QuickMenu(activity)
                    }
                    androidx.compose.runtime.LaunchedEffect(activity) {
                        repeat(16) {
                            if (JNIdc.currentGamePath().isNotBlank() && gb.frame.LibraryView.lastGame != null) {
                                com.flycast.emulator.shell.ui.GBCastCheatsBackend.applyDefaultFrameRatePatch()
                                kotlinx.coroutines.delay(500)
                                // Retry after core game initialization in case the first poll was early.
                                com.flycast.emulator.shell.ui.GBCastCheatsBackend.applyDefaultFrameRatePatch()
                                return@LaunchedEffect
                            }
                            delay(250)
                        }
                    }
                }
            }
            visibility = View.VISIBLE
            isFocusable = false
            isFocusableInTouchMode = false
        }
        compose = view
        layout.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /**
     * Release static references to the game Activity and its Views when the session is destroyed.
     * ComposeView's lifecycle strategy disposes its composition; this clears the process-wide owner.
     */
    @JvmStatic
    fun detach(activity: Activity) {
        if (attachedActivity !== activity) return
        visible = false
        menuOwnsKeys = false
        compose = null
        glView = null
        attachedActivity = null
    }

    @JvmStatic
    fun noteGamepadB(event: KeyEvent) {
        GbcastInput.note(event)
    }

    @JvmStatic
    fun prepareMenuKey(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (KeyEvent.isGamepadButton(event.keyCode) || event.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER)) {
            // Exit Android touch mode before redispatching the same physical key into Compose.
            if (compose?.isInTouchMode == true) compose?.requestFocusFromTouch()
        }
    }

    @JvmStatic
    fun onAndroidBack(activity: Activity) {
        when (GbcastBackPolicy.decide(JNIdc.guiState(), visible)) {
            GbcastBackPolicy.Action.OPEN_NATIVE_SETTINGS -> JNIdc.guiOpenSettings()
            GbcastBackPolicy.Action.RETURN_TO_PAUSE_MENU -> {
                JNIdc.guiSetState(PAUSE)
                show()
            }
            GbcastBackPolicy.Action.SHOW_PAUSE_MENU -> show()
            GbcastBackPolicy.Action.RESUME_GAME -> resume()
            GbcastBackPolicy.Action.FINISH_ACTIVITY -> activity.finish()
            GbcastBackPolicy.Action.TOGGLE_PAUSE_AND_SHOW -> {
                JNIdc.guiTogglePause()
                show()
            }
        }
    }

    private fun show() {
        visible = true
        menuOwnsKeys = true
        glView?.isFocusable = false
        glView?.isFocusableInTouchMode = false
        compose?.isFocusable = true
        compose?.isFocusableInTouchMode = true
        compose?.visibility = View.VISIBLE
        compose?.requestFocusFromTouch()
        compose?.requestFocus()
    }

    private fun resume() {
        visible = false
        menuOwnsKeys = false
        compose?.isFocusable = false
        compose?.isFocusableInTouchMode = false
        if (JNIdc.guiState() == PAUSE) JNIdc.guiTogglePause()
        glView?.isFocusable = true
        glView?.isFocusableInTouchMode = true
        glView?.requestFocus()
    }

    @JvmStatic
    fun restartCurrentGame() {
        val activity = attachedActivity ?: return
        val path = JNIdc.currentGamePath().takeIf { it.isNotBlank() } ?: return
        // Recreate the GL surface before starting the same disc. Hot-reloading a Flycast session
        // under the active PVR queue can leave the renderer queue bound to the previous context.
        activity.intent.putExtra(GBCastBridge.EXTRA_PATH, path)
        activity.intent.putExtra(GBCastBridge.EXTRA_SLOT, -1)
        activity.intent.putExtra(GBCastBridge.EXTRA_LOAD, false)
        activity.intent.putExtra(GBCastBridge.EXTRA_RESTORE_AUTO_LOAD, false)
        visible = false
        menuOwnsKeys = false
        activity.recreate()
    }

    private fun hideForNative(state: Int) {
        JNIdc.guiSetState(state)
        visible = false
        menuOwnsKeys = false
        compose?.isFocusable = false
        compose?.isFocusableInTouchMode = false
        glView?.requestFocus()
    }

    @Composable
    private fun QuickMenu(activity: Activity) {
        val saveScope = androidx.compose.runtime.rememberCoroutineScope()
        var savingState by androidx.compose.runtime.remember { mutableStateOf(false) }
        var tick by androidx.compose.runtime.remember { mutableIntStateOf(0) }
        val toast: (String) -> Unit = { text ->
            android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_SHORT).show()
        }

        fun currentBool(section: String, name: String): Boolean {
            return tick.let { JNIdc.configGetBool(section, name) }
        }
        fun currentInt(section: String, name: String): Int {
            return tick.let { JNIdc.configGetInt(section, name) }
        }
        fun setBool(section: String, name: String, value: Boolean) {
            JNIdc.configSetBool(section, name, value)
            tick++
        }
        fun setInt(section: String, name: String, value: Int) {
            JNIdc.configSetInt(section, name, value)
            tick++
        }
        val discPickerPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("disc-picker", "Swap disc", fullScreen = true) {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri != null) {
                        val copied = Storage.importFile(uri, File(activity.cacheDir, "disc-swap"))
                        message = if (copied == null) "Could not read that disc"
                        else JNIdc.insertDisk(copied) ?: "Disc inserted. Resume to continue."
                    }
                    back()
                }
                androidx.compose.runtime.LaunchedEffect(Unit) { picker.launch(arrayOf("*/*")) }
            }
        }
        val savePage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("save-state", "Save state") {
                for (index in 0 until gb.frame.SaveData.MANUAL_SAVE_STATE_SLOTS) {
                    StateSlot(index, save = true) {
                        if (!savingState) saveScope.launch {
                            savingState = true
                            try {
                                val error = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { JNIdc.saveSlotWithPreview(index) }
                                if (error == null) { toast("Saved slot " + (index + 1)); dismiss() }
                                else toast(error)
                            } finally { savingState = false }
                        }
                    }
                }
            }
        }
        val loadPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("load-state", "Load state") {

                for (index in 0 until gb.frame.SaveData.MANUAL_SAVE_STATE_SLOTS) {
                    StateSlot(index, save = false) {
                        JNIdc.configSetInt("config", "Dreamcast.SavestateSlot", index)

                        if (JNIdc.loadCurrentSlot()) {
                            toast("Loaded slot " + (index + 1))
                            dismiss()
                        } else {
                            toast("Could not load slot " + (index + 1))
                        }
                    }
                }
            }
        }

        val videoPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("video", "Video") {
                val displayMode = DisplayMode.current(::currentBool)
                val displayModes = DisplayMode.entries.map { mode ->
                    gb.frame.DisplayModeChoice(mode.ordinal, mode.label, supportsSuperWidescreen = mode == DisplayMode.Wide)
                }
                val currentGame = JNIdc.currentGamePath().orEmpty()
                gb.frame.DisplayModeQuickMenuItem(
                    value = displayMode.ordinal,
                    options = displayModes,
                ) { selected ->
                    val next = DisplayMode.entries.first { it.ordinal == selected }
                    // Kept for this game only (Settings holds the default for the others)
                    GBCastBridge.setGameDisplay(currentGame, next)
                    tick++
                    setInt("config", "rend.ScreenStretching", 100)
                }

                gb.frame.SuperWidescreenQuickMenuItem(
                    displayModeValue = displayMode.ordinal,
                    displayModes = displayModes,
                    value = GBCastBridge.gameSuperWidescreen(currentGame),
                ) { enabled ->
                    GBCastBridge.setGameSuperWidescreen(currentGame, enabled)
                    tick++
                }

                if ((displayMode == DisplayMode.Wide || displayMode == DisplayMode.Fullscreen) && JNIdc.hasWidescreenHack()) {
                    gb.frame.WidescreenGameHacksItem(value = currentBool("config", "rend.WidescreenGameHacks")) {
                        setBool("config", "rend.WidescreenGameHacks", it)
                    }
                }

                val rendererValues = listOf(0 to "GLES", 3 to "GLES OIT", 4 to "Vulkan", 5 to "Vulkan OIT")
                val renderer = currentInt("config", "pvr.rend")
                gb.frame.RendererQuickMenuItem(
                    value = renderer,
                    options = rendererValues.map { gb.frame.RendererChoice(it.first, it.second) },
                ) { setInt("config", "pvr.rend", it) }

                val resolutionValues = listOf(480 to "Native", 960 to "2x", 1440 to "3x", 1920 to "4x")
                val resolution = currentInt("config", "rend.Resolution")
                gb.frame.EnhancedResolutionQuickMenuItem(
                    value = resolution,
                    values = resolutionValues.map { it.first },
                    labels = resolutionValues.map { it.second },
                    id = "resolution",
                ) { setInt("config", "rend.Resolution", it) }

                gb.frame.TextureFilterQuickMenuItem(currentInt("config", "rend.TextureFiltering"), id = "filter", title = "Filter") {
                    setInt("config", "rend.TextureFiltering", it)
                }
                gb.frame.ShaderQuickMenuItem(currentInt("config", "GBCast.Shader")) {
                    setInt("config", "GBCast.Shader", it)
                }

                for ((key, label) in listOf(
                    "rend.vsync" to "VSync",
                )) {
                    gb.frame.QuickMenuItemRow(
                        id = key, icon = androidx.compose.material.icons.Icons.Default.Tv,
                        label = label, value = if (currentBool("config", key)) "On" else "Off",
                    ) { setBool("config", key, !currentBool("config", key)) }
                }

                val anisotropic = listOf(1, 2, 4, 8, 16)
                val anisoValue = currentInt("config", "rend.AnisotropicFiltering")
                val anisoIndex = anisotropic.indexOf(anisoValue).let { if (it < 0) 0 else it }
                gb.frame.QuickMenuItemRow(
                    id = "anisotropic", icon = androidx.compose.material.icons.Icons.Default.Tune,
                    label = "Anisotropic filtering", value = if (anisotropic[anisoIndex] == 1) "Off" else anisotropic[anisoIndex].toString() + "x",
                ) {
                    setInt("config", "rend.AnisotropicFiltering", anisotropic[(anisoIndex + 1) % anisotropic.size])
                }

                for ((key, label) in listOf(
                    "rend.PerStripSorting" to "Per-strip sorting",
                    "rend.Fog" to "Fog",
                    "rend.UseMipmaps" to "Mipmaps",
                    "rend.DelayFrameSwapping" to "Delay frame swapping",
                    "rend.EmulateFramebuffer" to "Emulate framebuffer",
                )) {
                    gb.frame.QuickMenuItemRow(
                        id = key, icon = androidx.compose.material.icons.Icons.Default.Tune,
                        label = label, value = if (currentBool("config", key)) "On" else "Off",
                    ) { setBool("config", key, !currentBool("config", key)) }
                }
            }
        }

        val audioPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("audio", "Audio") {
                // The kit's standard rows first, then Flycast's own
                gb.frame.AudioQuickMenuRows(com.flycast.emulator.shell.ui.GBCastAudio)
                for ((key, label) in listOf(
                    "aica.DSPEnabled" to "DSP",
                    "aica.AutoLatency" to "Automatic latency",
                )) {
                    gb.frame.QuickMenuItemRow(
                        id = key, icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp,
                        label = label, value = if (currentBool("config", key)) "On" else "Off",
                    ) { setBool("config", key, !currentBool("config", key)) }
                }

                val buffers = listOf(64, 96, 128)
                val actualBuffer = currentInt("config", "aica.BufferSize")
                val bufferIndex = buffers.indices.minByOrNull {
                    kotlin.math.abs((buffers[it] * 44100 / 1000) - actualBuffer)
                } ?: 1
                gb.frame.QuickMenuItemRow(
                    id = "audio-buffer", icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Buffer latency", value = buffers[bufferIndex].toString() + " ms",
                ) { setInt("config", "aica.BufferSize", buffers[(bufferIndex + 1) % buffers.size] * 44100 / 1000) }

                gb.frame.QuickMenuItemRow(
                    id = "vmu-sound", icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp,
                    label = "VMU sound", value = if (currentBool("audio", "VmuSound")) "On" else "Off",
                ) { setBool("audio", "VmuSound", !currentBool("audio", "VmuSound")) }
            }
        }

        val performancePage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("performance", "Performance") {
                val skip = currentInt("config", "pvr.AutoSkipFrame")
                gb.frame.AutoFrameSkipQuickMenuItem(skip) { setInt("config", "pvr.AutoSkipFrame", it) }
                gb.frame.CpuRecompilerQuickMenuItem(currentBool("config", "Dynarec.Enabled")) {
                    setBool("config", "Dynarec.Enabled", it)
                }
                gb.frame.FastCdLoadQuickMenuItem(currentBool("config", "FastGDRomLoad")) {
                    setBool("config", "FastGDRomLoad", it)
                }
                val cdSpeeds = listOf(
                    gb.frame.CdReadSpeedOption(2, "Original"),
                    gb.frame.CdReadSpeedOption(4, "4x"),
                    gb.frame.CdReadSpeedOption(8, "8x"),
                    gb.frame.CdReadSpeedOption(16, "16x"),
                )
                val cdSpeed = currentInt("config", "Dreamcast.CdReadSpeed")
                gb.frame.CdReadSpeedQuickMenuItem(cdSpeed, cdSpeeds) {
                    setInt("config", "Dreamcast.CdReadSpeed", it)
                }
                val frameRatePatch = com.flycast.emulator.shell.ui.GBCastCheatsBackend.frameRatePatch
                if (frameRatePatch != null) {
                    gb.frame.FrameRateHackItem(
                        control = frameRatePatch,
                        enabled = com.flycast.emulator.shell.ui.GBCastCheatsBackend.blockedMessage == null,
                    ) { enabled ->
                        val result = com.flycast.emulator.shell.ui.GBCastCheatsBackend.setFrameRatePatchEnabled(enabled)
                        result.message?.let(toast)
                        tick++
                    }
                }

                // Flycast-specific rendering controls follow the GBFrame performance options.
                for ((key, label) in listOf(
                    "rend.ThreadedRendering" to "Threaded rendering",
                    "rend.FramePacing" to "Frame pacing",
                )) {
                    gb.frame.QuickMenuItemRow(
                        id = key, icon = androidx.compose.material.icons.Icons.Default.Speed,
                        label = label, value = if (currentBool("config", key)) "On" else "Off",
                    ) { setBool("config", key, !currentBool("config", key)) }
                }
            }
        }

        val overlaysPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("overlays", "Overlays") {

                MonitoringQuickMenuRows(com.flycast.emulator.shell.ui.GBCastOverlayOptions)
                gb.frame.TouchControlsQuickMenuItem(
                    id = "touch-controls", value = com.flycast.emulator.shell.ui.DreamcastControls.touchControlsMode,
                ) { mode ->
                    com.flycast.emulator.shell.ui.DreamcastControls.touchControlsMode = mode
                    tick++
                    (activity as? com.flycast.emulator.NativeGLActivity)?.refreshTouchControlsVisibility()
                }
            }
        }

        val cheatsPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("cheats", "Cheats", fullScreen = true) {
                gb.frame.CheatsScreen(
                    backend = com.flycast.emulator.shell.ui.GBCastCheatsBackend,
                    onBack = { back() },
                )
            }
        }

        val achievementsPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("achievements", "Achievements", fullScreen = true) {
                gb.frame.AchievementsScreen(onBack = { back() })
            }
        }

        val gamePage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("game", "Game") {
                // Standard GBFrame destinations first; Dreamcast session controls follow.
                gb.frame.QuickMenuItemRow(
                    id = "cheats", icon = androidx.compose.material.icons.Icons.Default.Memory,
                    label = "Cheats", hasSubmenu = true,
                ) { openPage(cheatsPage, returnFocusItemId = "cheats") }
                gb.frame.QuickMenuItemRow(
                    id = "achievements", icon = androidx.compose.material.icons.Icons.Default.SportsEsports,
                    label = "Achievements", hasSubmenu = true,
                ) { openPage(achievementsPage, returnFocusItemId = "achievements") }
                gb.frame.QuickMenuItemRow(
                    id = "disc-swap", icon = androidx.compose.material.icons.Icons.Default.Eject,
                    label = "Swap disc", hasSubmenu = false,
                ) {
                    openPage(discPickerPage, returnFocusItemId = "disc-swap")
                }
                gb.frame.ResetQuickMenuItem {
                    restartCurrentGame()
                }

                gb.frame.QuickMenuItemRow(
                    id = "per-game", icon = androidx.compose.material.icons.Icons.Default.Tune,
                    label = "Per-game overrides", value = if (JNIdc.hasPerGameConfig()) "On" else "Off",
                ) {
                    JNIdc.setPerGameConfig(!JNIdc.hasPerGameConfig())
                    tick++
                }
                gb.frame.QuickMenuItemRow(
                    id = "native-settings", icon = androidx.compose.material.icons.Icons.Default.Tune,
                    label = "Advanced emulator settings",
                ) { hideForNative(SETTINGS) }
                message?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        }

        val rootPage = androidx.compose.runtime.remember {
            gb.frame.QuickMenuPage("gbcast-root", "Paused") {

                gb.frame.QuickMenuItemRow(
                    id = "resume", icon = androidx.compose.material.icons.Icons.Default.PlayArrow,
                    label = "Resume",
                ) { dismiss() }
                gb.frame.QuickMenuItemRow(
                    id = "save", icon = androidx.compose.material.icons.Icons.Default.Save,
                    label = "Save state", hasSubmenu = true,
                ) { openPage(savePage, returnFocusItemId = "save") }
                gb.frame.QuickMenuItemRow(
                    id = "load", icon = androidx.compose.material.icons.Icons.Default.Restore,
                    label = "Load state", hasSubmenu = true,
                ) { openPage(loadPage, returnFocusItemId = "load") }
                FastForwardQuickMenuAction(com.flycast.emulator.shell.ui.GBCastOverlayOptions,
                    com.flycast.emulator.shell.ui.DreamcastControls.fastForwardActive) {
                    com.flycast.emulator.shell.ui.DreamcastControls.setFastForward(!com.flycast.emulator.shell.ui.DreamcastControls.fastForwardActive)
                }
                gb.frame.QuickMenuItemRow(
                    id = "video", icon = androidx.compose.material.icons.Icons.Default.Tv,
                    label = "Video", hasSubmenu = true,
                ) { openPage(videoPage, returnFocusItemId = "video") }
                gb.frame.QuickMenuItemRow(
                    id = "audio", icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Audio", hasSubmenu = true,
                ) { openPage(audioPage, returnFocusItemId = "audio") }
                gb.frame.QuickMenuItemRow(
                    id = "performance", icon = androidx.compose.material.icons.Icons.Default.Speed,
                    label = "Performance", hasSubmenu = true,
                ) { openPage(performancePage, returnFocusItemId = "performance") }
                gb.frame.QuickMenuItemRow(
                    id = "overlays", icon = androidx.compose.material.icons.Icons.Default.Layers,
                    label = "Overlays", hasSubmenu = true,
                ) { openPage(overlaysPage, returnFocusItemId = "overlays") }
                gb.frame.QuickMenuItemRow(
                    id = "game", icon = androidx.compose.material.icons.Icons.Default.SportsEsports,
                    label = "Game", hasSubmenu = true,
                ) { openPage(gamePage, returnFocusItemId = "game") }
                message?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                gb.frame.QuickMenuItemRow(
                    id = "exit", icon = androidx.compose.material.icons.Icons.Default.ExitToApp,
                    label = "Exit",
                ) { activity.finish() }
            }
        }

        // The full ComposeView is already wrapped in GBCastTheme in attach(). Applying it again
        // here scales LocalDensity twice, making the shared quick menu's rows shorter than GBSat.
        gb.frame.QuickMenuHost(
            rootPage = rootPage,
            gameTitle = File(JNIdc.currentGamePath().orEmpty()).nameWithoutExtension.ifBlank { null },
            onDismiss = ::resume,
        )
    }
}

object GbcastInput {
    private var at = 0L
    private var device = -1

    @JvmStatic
    fun note(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            at = event.eventTime
            device = event.deviceId
        }
    }

    @JvmStatic
    fun consumePairedBack(event: KeyEvent): Boolean {
        val paired = event.deviceId == device && event.eventTime >= at && event.eventTime - at <= 150L
        if (paired) {
            at = 0L
            return true
        }
        return false
    }
}

