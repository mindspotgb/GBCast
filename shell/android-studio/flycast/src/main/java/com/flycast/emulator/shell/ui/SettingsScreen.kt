package com.flycast.emulator.shell.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gbcast.emulator.R
import com.flycast.emulator.shell.GBCastBridge
import com.flycast.emulator.shell.DreamcastBios
import com.flycast.emulator.shell.DisplayMode
import com.flycast.emulator.shell.Storage
import gb.frame.AboutCredit
import gb.frame.AboutLicenseDocument
import gb.frame.AboutPage
import gb.frame.rememberAppIconPainter
import gb.frame.ActionRow
import gb.frame.ChoiceRow
import gb.frame.ControlsPage
import gb.frame.ControlsCapturePrompts
import gb.frame.SettingsDivider
import gb.frame.SettingsPageBackButton
import gb.frame.SettingsGroup
import gb.frame.ToggleRow
import gb.frame.SystemSettings
import gb.frame.SystemFolderSetting
import gb.frame.SystemBiosFolderSetting
import gb.frame.SystemBiosFile
import gb.frame.SystemStartupOptions
import gb.frame.SystemToggleSetting
import gb.frame.SystemCdReadSpeedSetting
import gb.frame.CdReadSpeedOption
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class SettingsPage(val label: String, val icon: ImageVector) {
    System("System", Icons.Default.Tune),
    Video("Video", Icons.Default.Tv),
    Audio("Audio", Icons.AutoMirrored.Filled.VolumeUp),
    Performance("Performance", Icons.Default.Speed),
    Controls("Controls", Icons.Default.SportsEsports),
    Extras("Extras", Icons.Default.Info),
    About("About", Icons.Default.Info),
}

private class Cfg {
    var tick by mutableIntStateOf(0)
    fun bool(section: String, name: String) = GBCastBridge.bool(section, name)
    fun toggle(section: String, name: String) {
        GBCastBridge.setBool(section, name, !bool(section, name))
        tick++
    }
    fun setBool(section: String, name: String, value: Boolean) {
        check(GBCastBridge.setBool(section, name, value)) { "Unsupported setting: $name" }
        tick++
    }
    fun int(section: String, name: String) = GBCastBridge.int(section, name)
    fun setInt(section: String, name: String, value: Int) {
        GBCastBridge.setInt(section, name, value)
        tick++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, toast: (String) -> Unit, page: SettingsPage?) {
    val ctx = LocalContext.current
    val current = page ?: SettingsPage.System
    val cfg = remember { Cfg() }
    val tick = cfg.tick
    val scope = rememberCoroutineScope()
    var importingBios by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            Storage.takeFolder(uri)
            GBCastBridge.setGamesDir(ctx, uri.toString())
            toast("Games folder saved")
        }
    }
    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null && !importingBios) scope.launch {
            importingBios = true
            try {
                val count = withContext(Dispatchers.IO) { Storage.importBiosFolder(uri, GBCastBridge.biosDir()) }
                cfg.tick++
                toast(if (count == 0) "No files found in that folder" else "Copied $count files into the GBCast data folder")
            } catch (error: Exception) {
                cfg.tick++
                toast("Import stopped: ${error.message}. Files already copied were kept.")
            } finally { importingBios = false }
        }
    }
    val dreamcastBiosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !importingBios) scope.launch {
            importingBios = true
            try {
                val results = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        runCatching {
                            val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) it.getString(0) else null
                            } ?: error("Could not read filename")
                            requireNotNull(ctx.contentResolver.openInputStream(uri)).use { input ->
                                DreamcastBios.install(GBCastBridge.biosDir(), name, input)
                            }
                        }.fold({ "Imported $it" }, { it.message ?: "Could not import BIOS" })
                    }
                }
                cfg.tick++
                toast(results.joinToString("\n"))
            } finally { importingBios = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(current.icon, null, tint = gb.frame.palette.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(current.label, fontWeight = FontWeight.Bold)
                }
            },
            navigationIcon = {
                SettingsPageBackButton(current, onBack)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (current) {
                    SettingsPage.System -> SystemPage(tick, cfg, { folderPicker.launch(null) }, { if (!importingBios) biosPicker.launch(null) }, importingBios) {
                        if (!importingBios) dreamcastBiosPicker.launch(arrayOf("*/*"))
                    }
                    SettingsPage.Video -> VideoPage(tick, cfg)
                    SettingsPage.Audio -> key(tick) { AudioPage(tick, cfg) }
                    SettingsPage.Performance -> key(tick) { PerformancePage(tick, cfg) }
                    SettingsPage.Controls -> ControlsPage(DreamcastControls) {
                        val devices = intArrayOf(0, 18, 5, 6, 7, 8, 10)
                        val selected = indexOf(tick, devices, cfg.int("input", "device1"))
                        val vibeValues = intArrayOf(0, 20, 50, 80)
                        val vibe = indexOf(tick, vibeValues, cfg.int("input", "VirtualGamepadVibration"))
                        ChoiceRow("Port A", "Controller type for the first Dreamcast port", listOf("Pad", "XL", "Key", "Mouse", "Gun", "Twin", "None"), selected) {
                            cfg.setInt("input", "device1", devices[it])
                        }
                        SettingsDivider()
                        ChoiceRow("Game rumble", "Vibration strength for rumble effects triggered by the game. Touch button feedback is configured separately.", listOf("Off", "Low", "Mid", "High"), vibe) {
                            cfg.setInt("input", "VirtualGamepadVibration", vibeValues[it])
                        }
                    }
                    SettingsPage.Extras -> key(tick) { ExtrasPage(tick, cfg) }
                    SettingsPage.About -> AboutPage(
                        appName = "GBCast",
                        manufacturer = "Sega",
                        console = "Dreamcast",
                        icon = rememberAppIconPainter(R.drawable.ic_launcher_foreground),
                        credits = listOf(
                            AboutCredit("Flycast", "Dreamcast, Naomi, Naomi 2 and Atomiswave core (GPL-2.0-or-later; distributed here under GPLv3)."),
                            AboutCredit("rcheevos", "RetroAchievements client library (MIT)."),
                            AboutCredit("libretro-database", "Bundled Dreamcast cheat database (CC BY-SA 4.0; license included in the APK)."),
                        ),
                        license = "GBCast source code is distributed under GNU GPL v3. Flycast files that permit GPL-2.0-or-later and GBFrame are used under GPLv3 here; third-party components keep their own licenses. Google Play Billing and Play Services are supplied under Google's separate SDK terms. See the notices below. Dreamcast is a Sega trademark; GBCast is not affiliated with Sega.",
                        privacyPolicyUrl = "https://raw.githubusercontent.com/mindspotgb/GBCast/main/PRIVACY.md",
                        tagline = "Brought to you by GameBeacon.dev: pure speed and nostalgia for the definitive Sega Dreamcast experience.",
                        licenseDocuments = listOf(
                            AboutLicenseDocument("GNU GPL v3 — GBCast source", "licenses/GNU-GPL-3.0.txt"),
                            AboutLicenseDocument("Third-party dependency notices", "licenses/THIRD-PARTY-NOTICES.txt"),
                            AboutLicenseDocument("MIT — rcheevos", "licenses/rcheevos-MIT.txt"),
                            AboutLicenseDocument("CC BY-SA 4.0 — libretro-database", "cheats/LICENSE-libretro-database.txt"),
                        ),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
      }
      ControlsCapturePrompts(DreamcastControls)
    }
}

@Composable
private fun SystemPage(tick: Int, cfg: Cfg, onFolder: () -> Unit, onBios: () -> Unit, importingBios: Boolean, onDreamcastBios: () -> Unit) {
    val biosFiles = remember(tick) {
        DreamcastBios.sizes.keys.map { name ->
            val present = DreamcastBios.present(GBCastBridge.biosDir(), name)
            val actualName = if (name == "dc_boot.bin" && present && !gb.frame.DataFile(GBCastBridge.biosDir(), name).isFile) "$name.bak" else name
            SystemBiosFile(actualName, present)
        }
    }
    SystemSettings(
        dataFolder = GBCastBridge.dataFolder,
        gamesFolder = SystemFolderSetting(GBCastBridge.gamesDir().takeIf { it.isNotBlank() }?.let(Storage::folderLabel),
            "Choose the folder containing your Dreamcast and arcade disc images.", onFolder),
        biosFolder = SystemBiosFolderSetting(
            path = if (GBCastBridge.dataFolder.folder != null || biosFiles.any { it.loaded }) GBCastBridge.dataFolder.displayPath(GBCastBridge.biosDir()) else null,
            description = "Choose a folder containing dc_boot.bin, dc_flash.bin and any other required BIOS files.",
            files = biosFiles, onChooseFolder = onBios, onChooseFiles = onDreamcastBios, busy = importingBios,
        ),
        autoSave = cfg.bool("config", "Dreamcast.AutoSaveState"),
        autoLoad = cfg.bool("config", "Dreamcast.AutoLoadState"),
        onAutoSave = { cfg.setBool("config", "Dreamcast.AutoSaveState", it) },
        onAutoLoad = { cfg.setBool("config", "Dreamcast.AutoLoadState", it) },
        hleBios = {
            ToggleRow(Icons.Default.Tune, "HLE BIOS", "Uses Flycast's built-in BIOS instead of the console BIOS. It can start games without a BIOS file, but some games may need the original BIOS.", cfg.bool("config", "UseReios")) {
                cfg.setBool("config", "UseReios", it)
            }
        },
        startup = SystemStartupOptions(
            fastBoot = SystemToggleSetting(cfg.bool("config", "GBFrame.FastBoot"), { cfg.setBool("config", "GBFrame.FastBoot", it) }, "Skip the BIOS intro and its clock and language screens"),
            skipLoadingScreens = SystemToggleSetting(cfg.bool("config", "GBFrame.SkipLoadingScreens"), { cfg.setBool("config", "GBFrame.SkipLoadingScreens", it) }),
            skipTransitions = SystemToggleSetting(cfg.bool("config", "GBFrame.SkipTransitions"), { cfg.setBool("config", "GBFrame.SkipTransitions", it) }),
            skipSaveWaits = SystemToggleSetting(cfg.bool("config", "GBFrame.SkipSaveWaits"), { cfg.setBool("config", "GBFrame.SkipSaveWaits", it) }),
        ),
        recommendedSettingsPerGame = SystemToggleSetting(
            GBCastBridge.useRecommendedSettingsPerGame,
            {
                GBCastBridge.setRecommendedSettingsPerGame(it)
                cfg.tick++
            },
        ),
        cdReadSpeed = SystemCdReadSpeedSetting(
            value = cfg.int("config", "Dreamcast.CdReadSpeed"),
            options = listOf(2, 4, 8, 16).mapIndexed { index, speed ->
                CdReadSpeedOption(speed, listOf("Original", "4x", "8x", "16x")[index])
            },
            onChange = { cfg.setInt("config", "Dreamcast.CdReadSpeed", it) },
            description = "Faster settings shorten disc loading. Use Original if streamed video or music goes out of sync.",
        ),
        startupExtras = {
            gb.frame.FastCdLoadSettingsRow(
                cfg.bool("config", "FastGDRomLoad"),
                { cfg.setBool("config", "FastGDRomLoad", it) },
                "Shortens GD-ROM DMA transfer delays. Separate from CD read speed and automatic skips; some games need original timing.",
            )
        },
    )
}

@Composable
private fun VideoPage(tick: Int, cfg: Cfg) {
    val rendererValues = intArrayOf(0, 3, 4, 5)
    val renderer = indexOf(tick, rendererValues, cfg.int("config", "pvr.rend"))
    val resolutionChoices = GBCastBridge.resolutionChoices()
    val resolutionValues = resolutionChoices.map { it.first }.toIntArray()
    val resolution = indexOf(tick, resolutionValues, cfg.int("config", "rend.Resolution"))
    SettingsGroup("Picture", Icons.Default.AspectRatio) {
        val displayModes = DisplayMode.entries.map { mode ->
            gb.frame.DisplayModeChoice(mode.ordinal, mode.label, supportsSuperWidescreen = mode.supportsSuperWidescreen)
        }
        val displayMode = GBCastBridge.globalDisplay()
        gb.frame.DisplayModeSettingsRow(
            displayMode.ordinal,
            displayModes,
            "Choose the game's aspect ratio. Fullscreen stretches the picture.",
        ) { selected ->
            val mode = DisplayMode.entries.first { it.ordinal == selected }
            GBCastBridge.setGlobalDisplay(mode)
            cfg.setInt("config", "rend.ScreenStretching", 100)
        }
        if (displayMode == DisplayMode.Wide || displayMode == DisplayMode.Fullscreen) {
            gb.frame.WidescreenGameHacksRow(cfg.bool("config", "rend.WidescreenGameHacks")) { cfg.toggle("config", "rend.WidescreenGameHacks") }
            gb.frame.SuperWidescreenSettingsRow(
                displayMode.ordinal,
                displayModes,
                GBCastBridge.globalSuperWidescreen(),
            ) { enabled ->
                GBCastBridge.setGlobalSuperWidescreen(enabled)
                cfg.tick++
            }
        }
        SettingsDivider()
        gb.frame.TextureFilterSettingsRow(cfg.int("config", "rend.TextureFiltering"), title = "Filter") {
            cfg.setInt("config", "rend.TextureFiltering", it)
        }
        SettingsDivider()
        gb.frame.ShaderSettingsRow(cfg.int("config", "GBCast.Shader")) {
            cfg.setInt("config", "GBCast.Shader", it)
        }
    }
    SettingsGroup("Renderer", Icons.Default.Tv) {
        gb.frame.RendererSettingsRow(
            rendererValues[renderer],
            rendererValues.mapIndexed { index, id -> gb.frame.RendererChoice(id, listOf("GLES", "GLES OIT", "Vulkan", "Vulkan OIT")[index]) },
            "GLES and Vulkan are graphics APIs. OIT (order-independent transparency) improves transparent polygon ordering at higher GPU/memory cost.",
        ) {
            cfg.setInt("config", "pvr.rend", it)
        }
        SettingsDivider()
        gb.frame.EnhancedResolutionSettingsRow(
            resolutionValues[resolution], resolutionValues.toList(), resolutionChoices.map { it.second },
            "Sharper 3D sprites and polygons; higher values need a faster GPU. Best matches this device's display: more than that only costs GPU time.",
        ) {
            cfg.setInt("config", "rend.Resolution", it)
        }
        ToggleRow(Icons.Default.Tv, "VSync", "Matches drawing to the screen refresh to reduce tearing. It can add a little input delay or limit speed.", cfg.bool("config", "rend.vsync")) { cfg.setBool("config", "rend.vsync", it) }
        SettingsDivider()
        ChoiceRow("Anisotropic filtering", "Sharpens textures viewed at a steep angle. Higher values use more GPU time; the visual gain is most visible in 3D scenes.", listOf("Off", "2x", "4x", "8x", "16x"), indexOf(tick, intArrayOf(1, 2, 4, 8, 16), cfg.int("config", "rend.AnisotropicFiltering"))) {
            cfg.setInt("config", "rend.AnisotropicFiltering", intArrayOf(1, 2, 4, 8, 16)[it])
        }
    }
    SettingsGroup("Overlays", Icons.Default.Info) {
        gb.frame.MonitoringSettingsRows(GBCastOverlayOptions)
    }
    SettingsGroup("Textures", Icons.Default.Tv) {
        ToggleRow(Icons.Default.Tv, "Custom textures", "Load texture packs from data/textures/<game ID> in the selected data folder after reopening the app.",
            cfg.bool("config", "rend.CustomTextures")) { cfg.setBool("config", "rend.CustomTextures", it) }
        SettingsDivider()
        gb.frame.TexturePackImportRow(GBCastBridge.texturesFolder())
    }
    SettingsGroup("Accuracy", null) {
        ToggleRow(Icons.Default.Tune, "Per-strip sorting", "Sorts transparent polygons by strip. It can be faster, but may show incorrect overlap; turn it off for per-triangle sorting if a game looks wrong.", cfg.bool("config", "rend.PerStripSorting")) { cfg.setBool("config", "rend.PerStripSorting", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Tune, "Fog effects", "Keeps distance haze and fog effects used by some games. Disabling it may improve speed slightly but changes the picture.", cfg.bool("config", "rend.Fog")) { cfg.setBool("config", "rend.Fog", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Tune, "Mipmaps", "Uses smaller versions of textures at a distance to reduce shimmering. This can use extra memory and a little GPU time.", cfg.bool("config", "rend.UseMipmaps")) { cfg.setBool("config", "rend.UseMipmaps", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Tune, "Delay frame swap", "Waits for rendering to finish before showing a frame. This can fix missing or flickering effects, but may reduce speed.", cfg.bool("config", "rend.DelayFrameSwapping")) { cfg.setBool("config", "rend.DelayFrameSwapping", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Tune, "Framebuffer emulation", "More accurately reproduces games that read the screen back as they draw. It can be demanding; enable it only if a game needs it.", cfg.bool("config", "rend.EmulateFramebuffer")) { cfg.setBool("config", "rend.EmulateFramebuffer", it) }
    }
}

@Composable
private fun AudioPage(tick: Int, cfg: Cfg) {
    val samples = cfg.int("config", "aica.BufferSize")
    val latency = if (samples == Int.MIN_VALUE) 2 else listOf(64, 96, 128).indices.minByOrNull { kotlin.math.abs(listOf(64, 96, 128)[it] - samples * 1000 / 44100) } ?: 2
    // The kit's standard options first, then Flycast's own
    gb.frame.AudioSettingsGroups(GBCastAudio)
    SettingsGroup("Sound chip (AICA)", Icons.AutoMirrored.Filled.VolumeUp) {
        ToggleRow(Icons.AutoMirrored.Filled.VolumeUp, "DSP effects", "Enables the sound chip's digital signal processor for extra audio effects. It is more demanding; turn it off if performance is low.", cfg.bool("config", "aica.DSPEnabled")) { cfg.setBool("config", "aica.DSPEnabled", it) }
        SettingsDivider()
        ToggleRow(Icons.AutoMirrored.Filled.VolumeUp, "Automatic audio latency", "Adjusts the audio buffer while you play to avoid crackling. Lower latency responds faster but needs more CPU headroom.", cfg.bool("config", "aica.AutoLatency")) { cfg.setBool("config", "aica.AutoLatency", it) }
        SettingsDivider()
        ChoiceRow("Audio buffer", "Smaller buffers reduce audio delay but are more likely to crackle on a busy device. Larger buffers are steadier and add delay.", listOf("64 ms", "96 ms", "128 ms"), latency) {
            cfg.setInt("config", "aica.BufferSize", listOf(64, 96, 128)[it] * 44100 / 1000)
        }
        SettingsDivider()
        ToggleRow(Icons.AutoMirrored.Filled.VolumeUp, "VMU sounds", "Plays the memory card's beeps. This has negligible performance cost.", cfg.bool("audio", "VmuSound")) { cfg.setBool("audio", "VmuSound", it) }
    }
}

@Composable
private fun PerformancePage(tick: Int, cfg: Cfg) {
    SettingsGroup("Performance", Icons.Default.Speed) {
        ToggleRow(Icons.Default.Speed, "Threaded rendering", "Moves graphics work to another thread. It may improve speed on some devices, but can add latency or cause glitches.", cfg.bool("config", "rend.ThreadedRendering")) { cfg.setBool("config", "rend.ThreadedRendering", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Speed, "Frame pacing", "Smooths uneven frame delivery. It may improve motion but can add a small amount of waiting.", cfg.bool("config", "rend.FramePacing")) { cfg.setBool("config", "rend.FramePacing", it) }
        SettingsDivider()
        gb.frame.AutoFrameSkipSettingsRow(cfg.int("config", "pvr.AutoSkipFrame")) {
            cfg.setInt("config", "pvr.AutoSkipFrame", it)
        }
        SettingsDivider()
    }
}


@Composable
private fun ExtrasPage(tick: Int, cfg: Cfg) {
    gb.frame.AppLanguageSettings()
    SettingsGroup("Arcade", null) {
        ToggleRow(Icons.Default.Tune, "Arcade free play", "Starts supported Naomi and Atomiswave games without requiring coins.", cfg.bool("config", "ForceFreePlay")) { cfg.setBool("config", "ForceFreePlay", it) }
    }
    SettingsGroup("Network", Icons.Default.Wifi) {
        ToggleRow(Icons.Default.Wifi, "Broadband adapter", "Emulates the Dreamcast network adapter for games that support online or LAN features.", cfg.bool("network", "EmulateBBA")) { cfg.setBool("network", "EmulateBBA", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Wifi, "Naomi network", "Enables network features for compatible Naomi arcade games.", cfg.bool("network", "Enable")) { cfg.setBool("network", "Enable", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Wifi, "Battle cable", "Connects compatible arcade games for local multiplayer. Both emulated systems need matching network settings.", cfg.bool("network", "BattleCable")) { cfg.setBool("network", "BattleCable", it) }
        SettingsDivider()
        ToggleRow(Icons.Default.Wifi, "GGPO", "Rollback netplay. Host, delay and chat options are in the advanced network screen.", cfg.bool("network", "GGPO")) { cfg.setBool("network", "GGPO", it) }
    }
    gb.frame.RetroAchievementsSettings("GBCast")
}

/** Find a native enum value in the labels list, falling back to the first available option. */
private fun indexOf(@Suppress("UNUSED_PARAMETER") tick: Int, values: IntArray, value: Int): Int =
    values.indexOf(value).takeIf { it >= 0 } ?: 0


