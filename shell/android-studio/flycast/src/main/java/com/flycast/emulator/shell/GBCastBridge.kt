package com.flycast.emulator.shell

import app.gbcast.emulator.R as GBCastR
import android.content.Context
import android.content.Intent
import com.flycast.emulator.Emulator
import com.flycast.emulator.NativeGLActivity
import com.flycast.emulator.emu.HttpClient
import com.flycast.emulator.emu.JNIdc
import gb.frame.Covers
import gb.frame.DeviceTier
import gb.frame.enhancedResolutionLabels
import gb.frame.HomeTheme
import gb.frame.LibraryView
import gb.frame.OpenDisc
import gb.frame.PlayTime
import gb.frame.RetroAchievements
import gb.frame.SaveData
import gb.frame.DataFile as File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Coordinates the Compose shell with the Flycast core. One staged disc stays open until the next launch. */
object GBCastBridge {
    const val AUTO_SAVE_SLOT = SaveData.MANUAL_SAVE_STATE_SLOTS
    const val EXTRA_PATH = "gbcast_game_path"
    const val EXTRA_SLOT = "gbcast_state_slot"
    const val EXTRA_LOAD = "gbcast_load_state"
    const val EXTRA_RESTORE_AUTO_LOAD = "gbcast_restore_auto_load"
    const val EXTRA_AUTO_LOAD_VALUE = "gbcast_auto_load_value"

    private const val PREF_GAMES = "gbcast.gamesDir"
    private const val PREF_SETUP = "gbcast.setupDone"
    private var sharedRaInitialized = false
    lateinit var dataFolder: gb.frame.AppDataFolder
        private set
    private lateinit var textures: File
    fun texturesFolder(): java.io.File = textures

    @JvmStatic
    fun prepareDataFolder(context: Context, home: String) {
        if (::dataFolder.isInitialized) return
        // The data folder holds only bios, saves, savestates, textures and covers; Flycast's caches stay in app storage
        textures = File(home, "data/textures")
        dataFolder = gb.frame.AppDataFolder(context, "GBCast", mapOf(
            "bios" to File(home, "data/bios"),
            "saves" to File(home, "data/saves"),
            "savestates" to File(home, "data/savestates"),
            "textures" to textures,
            "covers" to File(context.filesDir, "gbcast/covers"),
        ), texturesRelativePath = "textures")
        System.loadLibrary("flycast")
        dataFolder.openAtStartup()
        File(home, "data/textures").mkdirs()
    }

    var disc: OpenDisc? = null
        private set

    private var appContext: Context? = null

    fun start(context: Context) {
        appContext = context.applicationContext
        val app = context.applicationContext as Emulator
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(app)
        displayPrefs = prefs
        gb.frame.GameProfiles.init(app)
        Storage.gamesDir = prefs.getString(PREF_GAMES, "") ?: ""
        Storage.start(app)
        HomeTheme.init(prefs)
        LibraryView.init(prefs)
        PlayTime.init(prefs)
        Thread({ DeviceTier.detect() }, "gbcast-device-tier").start()
        Covers.init(File(app.filesDir, "gbcast"), "Sega - Dreamcast", frameAspectRatio = 1.0f)
        // What the library's first frames need, ready before it shows (no decoding while it appears)
        gb.frame.preloadAppIcon(app.resources, GBCastR.drawable.ic_launcher_foreground)
        Covers.preloadCached(Storage.cachedScan(Storage.gamesDir).take(40).map { it.title })
        app.ensureCore()?.let { error -> throw IllegalStateException(error) }
        // Flycast's SH-4 dynarec is required for usable Android performance; migrate older saved Off values.
        setBool("config", "Dynarec.Enabled", true)
        JNIdc.initAutoSkip()
        // The global display mode as Settings last left it (before the per-game choices existed it lived only in the
        // emulator config, which a game's own choice overwrites)
        if (!prefs.contains("display")) {
            val mode = DisplayMode.current(::bool)
            prefs.edit()
                .putString("display", mode.name)
                .putBoolean(GLOBAL_SUPER_WIDESCREEN, mode.supportsSuperWidescreen && bool("config", "rend.SuperWideScreen"))
                .apply()
        } else if (prefs.getString("display", null) == "SuperWide") {
            // Migrate the former "SuperWide" display mode into 16:9 plus the separate Extended screen toggle.
            prefs.edit().putString("display", DisplayMode.Wide.name).putBoolean(GLOBAL_SUPER_WIDESCREEN, true).apply()
        }
        com.flycast.emulator.shell.ui.GBCastOverlayOptions.initialize(app)
        com.flycast.emulator.shell.ui.GBCastCheatDatabase.initialize(app)
        val savedSlot = int("config", "Dreamcast.SavestateSlot")
        if (savedSlot !in 0 until SaveData.MANUAL_SAVE_STATE_SLOTS) {
            setInt("config", "Dreamcast.SavestateSlot", 0)
        }
        HttpClient.initialize()
        if (!sharedRaInitialized) {
            RetroAchievements.init(app, "GBCast", appVersion(app), com.flycast.emulator.shell.ui.GBCastRaBackend)
            sharedRaInitialized = true
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"

    fun gamesDir(): String = Storage.gamesDir

    fun setGamesDir(context: Context, uri: String) {
        Storage.gamesDir = uri
        android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(PREF_GAMES, uri).apply()
    }

    fun setupDone(context: Context) =
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_SETUP, false)

    fun finishSetup(context: Context) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_SETUP, true).apply()
    }

    /**
     * Internal resolution (vertical lines) that matches the screen: the 4:3 picture fills the short side, so it needs
     * short * 3/4 lines, rounded up to 30. A 720x720 screen (RG Rotate) needs 540; 2x (960) renders 3.2x more pixels
     * than it shows and used 75-80% of its Mali-G52, while 540 uses ~25% with the same picture.
     */
    fun screenResolution(): Int {
        // The real display size: the app runs immersive, and the system metrics leave out the bars (680 of 720 px)
        val m = android.util.DisplayMetrics()
        val wm = appContext?.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        @Suppress("DEPRECATION")
        if (wm != null) wm.defaultDisplay.getRealMetrics(m)
        else m.setTo(android.content.res.Resources.getSystem().displayMetrics)
        val lines = (minOf(m.widthPixels, m.heightPixels) * 3 / 4 + 29) / 30 * 30
        return lines.coerceIn(480, 1920)
    }

    /** Resolution choices for the menus, the kit's list: 1x to 4x (Flycast's multiples of native), then Best = this screen. */
    fun resolutionChoices(): List<Pair<Int, String>> {
        val screen = screenResolution()
        return listOf(480, 960, 1440, 1920, screen).zip(enhancedResolutionLabels("${screen}p"))
    }

    suspend fun applyRecommendedPreset(): String {
        val device = withContext(Dispatchers.IO) { DeviceTier.detect() }
        val preset = when {
            device.cpuLimited -> "Compatible"
            device.gpuMaxScale >= 2 -> "Quality"
            else -> "Balanced"
        }
        when (preset) {
            "Compatible" -> {
                setInt("config", "pvr.rend", 0)
                setInt("config", "rend.Resolution", 480)
            }
            "Quality" -> {
                setInt("config", "pvr.rend", 4)
                setInt("config", "rend.Resolution", device.gpuMaxScale.coerceIn(2, 4) * 480)
            }
            else -> {
                setInt("config", "pvr.rend", 0)
                setInt("config", "rend.Resolution", screenResolution())
                setBool("config", "rend.vsync", true)
                setBool("config", "rend.FramePacing", true)
            }
        }
        setBool("config", "Dynarec.Enabled", true)
        return preset // its name
    }

    fun presetWizardOptions(preset: String): List<String> {
        val device = DeviceTier.detect()
        val (renderer, resolution) = when (preset) {
            "Compatible" -> "CPU (OpenGL)" to "480p"
            "Quality" -> "GPU (Vulkan)" to "${device.gpuMaxScale.coerceIn(2, 4)}x native"
            else -> "CPU (OpenGL)" to "${screenResolution()}p (matches the screen)"
        }
        val options = mutableListOf(
            "Renderer: $renderer",
            "Internal resolution: $resolution",
            "Flycast dynarec: On",
        )
        if (preset == "Balanced") {
            options += "VSync: On"
            options += "Frame pacing: On"
        }
        return options
    }

    fun deviceRecommendation(): String {
        val device = DeviceTier.detect()
        val preset = when {
            device.cpuLimited -> "Compatible"
            device.gpuMaxScale >= 2 -> "Quality"
            else -> "Balanced"
        }
        return "$preset · ${device.summary}"
    }

    // Display mode: the global choice (Settings) and one per game (the in-game menu), applied when a game starts
    private const val GLOBAL_SUPER_WIDESCREEN = "superWidescreen"
    private lateinit var displayPrefs: android.content.SharedPreferences
    val useRecommendedSettingsPerGame: Boolean
        get() = ::displayPrefs.isInitialized && displayPrefs.getBoolean("recommendedSettingsPerGame", true)

    fun setRecommendedSettingsPerGame(enabled: Boolean) {
        displayPrefs.edit().putBoolean("recommendedSettingsPerGame", enabled).apply()
    }

    private fun displayKey(game: String) = "display:" + SaveData.gameFolderName(game)
    private fun superWidescreenKey(game: String) = "superWidescreen:" + SaveData.gameFolderName(game)
    private fun parseDisplayMode(value: String?): DisplayMode? = when (value) {
        // Previous releases saved "SuperWide" as a display mode. Keep it as 16:9 plus the separate toggle.
        "SuperWide" -> DisplayMode.Wide
        else -> DisplayMode.entries.firstOrNull { it.name == value }
    }

    fun setGlobalDisplay(mode: DisplayMode) {
        val superWidescreen = globalSuperWidescreen()
        displayPrefs.edit().putString("display", mode.name).apply()
        mode.apply(::setBool, superWidescreen)
    }
    fun setGameDisplay(game: String, mode: DisplayMode) {
        val superWidescreen = mode.supportsSuperWidescreen && gameSuperWidescreen(game)
        displayPrefs.edit().putString(displayKey(game), mode.name).apply()
        mode.apply(::setBool, superWidescreen)
    }
    fun globalDisplay(): DisplayMode = parseDisplayMode(displayPrefs.getString("display", null)) ?: DisplayMode.current(::bool)
    fun globalSuperWidescreen(): Boolean {
        if (displayPrefs.contains(GLOBAL_SUPER_WIDESCREEN)) return displayPrefs.getBoolean(GLOBAL_SUPER_WIDESCREEN, false)
        val savedMode = displayPrefs.getString("display", null)
        return if (savedMode == null) bool("config", "rend.SuperWideScreen") else savedMode == "SuperWide"
    }
    fun setGlobalSuperWidescreen(enabled: Boolean) {
        val mode = globalDisplay()
        val value = mode.supportsSuperWidescreen && enabled
        displayPrefs.edit().putBoolean(GLOBAL_SUPER_WIDESCREEN, value).apply()
        mode.apply(::setBool, value)
    }
    fun gameSuperWidescreen(game: String): Boolean {
        val key = superWidescreenKey(game)
        if (displayPrefs.contains(key)) return displayPrefs.getBoolean(key, false)
        if (displayPrefs.getString(displayKey(game), null) == "SuperWide") {
            displayPrefs.edit().putBoolean(key, true).apply()
            return true
        }
        return globalSuperWidescreen()
    }
    fun setGameSuperWidescreen(game: String, enabled: Boolean) {
        val mode = displayFor(game)
        val value = mode.supportsSuperWidescreen && enabled
        displayPrefs.edit().putBoolean(superWidescreenKey(game), value).apply()
        mode.apply(::setBool, value)
    }
    private fun displayFor(game: String): DisplayMode {
        return parseDisplayMode(displayPrefs.getString(displayKey(game), null)) ?: parseDisplayMode(displayPrefs.getString("display", null))
            ?: DisplayMode.current(::bool)
    }
    private fun applyDisplayFor(game: String) {
        val mode = displayFor(game)
        mode.apply(::setBool, mode.supportsSuperWidescreen && gameSuperWidescreen(game))
    }

    fun bool(section: String, name: String) = JNIdc.configGetBool(section, name)

    fun setBool(section: String, name: String, value: Boolean): Boolean = JNIdc.configSetBool(section, name, value)

    fun int(section: String, name: String) = JNIdc.configGetInt(section, name)

    fun setInt(section: String, name: String, value: Int): Boolean = JNIdc.configSetInt(section, name, value)

    fun dataDir(): File = File(JNIdc.dataDirectory() ?: "")

    fun biosDir(): File = File(dataDir(), "bios")
    fun biosPresent(): Boolean = DreamcastBios.present(biosDir(), "dc_boot.bin")

    /** Opens [safPath] and starts [NativeGLActivity]. [slot] loads that Flycast state after boot when auto-load is off. */
    suspend fun launch(context: Context, safPath: String, slot: Int = -1, fresh: Boolean = false): String? {
        if (slot != -1 && slot !in 0..AUTO_SAVE_SLOT) {
            return "Save slot must be between 1 and ${SaveData.MANUAL_SAVE_STATE_SLOTS}."
        }
        val opened = withContext(Dispatchers.IO) { Storage.openForCore(safPath) }
            ?: return "Could not open this game. Check the folder permission and the disc files."
        queueRecommendedProfile(safPath)
        disc?.close()
        disc = opened
        PlayTime.update(safPath, true)
        applyDisplayFor(safPath)
        val auto = bool("config", "Dreamcast.AutoLoadState")
        val launchSlot = withContext(Dispatchers.IO) {
            when {
                slot >= 0 -> slot
                auto && !fresh -> latestStateSlot(safPath) ?: -1
                else -> -1
            }
        }
        android.util.Log.i("GBCast-Load", "launch game=${Storage.fileName(safPath)} autoLoad=$auto fresh=$fresh requestedSlot=$slot selectedSlot=$launchSlot")
        // The native boot reloads per-game slot settings. Load the chosen latest slot explicitly
        // after that reload, rather than letting boot load a stale numbered slot.
        val restoreAutoLoad = auto
        if (restoreAutoLoad) setBool("config", "Dreamcast.AutoLoadState", false)
        val intent = Intent(context, NativeGLActivity::class.java)
            .putExtra(EXTRA_PATH, opened.path)
            .putExtra(EXTRA_SLOT, launchSlot)
            .putExtra(EXTRA_LOAD, launchSlot >= 0)
            .putExtra(EXTRA_RESTORE_AUTO_LOAD, restoreAutoLoad)
            .putExtra(EXTRA_AUTO_LOAD_VALUE, auto)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            LibraryView.gameOpened(safPath)
        } catch (_: Exception) {
            if (restoreAutoLoad) setBool("config", "Dreamcast.AutoLoadState", auto)
            opened.close()
            disc = null
            return "Could not start this game."
        }
        return null
    }

    private fun queueRecommendedProfile(safPath: String) {
        if (!useRecommendedSettingsPerGame) {
            android.util.Log.i("GBCastProfile", "Measured per-game settings are disabled")
            JNIdc.setRecommendedProfile(false, -1, -1, -1, -1, -1, -1, -1, -1)
            return
        }
        val title = Storage.cachedScan(Storage.gamesDir).firstOrNull { it.path == safPath }?.title
            ?: java.io.File(safPath.substringBefore('?')).nameWithoutExtension
        val tier = DeviceTier.detect().tier
        val profile = gb.frame.GameProfiles.lookup(title, null, tier)
        android.util.Log.i("GBCastProfile", if (profile.isEmpty()) {
            "No measured profile for '$title' on ${tier.key}"
        } else {
            "Queued measured profile for '$title' on ${tier.key}: ${profile.keys.sorted().joinToString()}"
        })
        val renderer = when (profile["renderer"] as? String) {
            "opengl" -> 0
            "opengl_oit" -> 3
            "vulkan" -> 4
            "vulkan_oit" -> 5
            else -> -1
        }
        JNIdc.setRecommendedProfile(
            profile.isNotEmpty(), renderer,
            (profile["resolution"] as? Number)?.toInt() ?: -1,
            (profile["autoFrameSkip"] as? Number)?.toInt() ?: -1,
            (profile["threadedRendering"] as? Boolean)?.let { if (it) 1 else 0 } ?: -1,
            (profile["textureFiltering"] as? Number)?.toInt() ?: -1,
            (profile["framePacing"] as? Boolean)?.let { if (it) 1 else 0 } ?: -1,
            (profile["fastCdLoad"] as? Boolean)?.let { if (it) 1 else 0 } ?: -1,
            (profile["cdReadSpeed"] as? Number)?.toInt() ?: -1,
        )
    }

    private fun stateLabel(slot: Int) = if (slot == AUTO_SAVE_SLOT) "auto" else (slot + 1).toString()
    private fun stateFile(game: String, slot: Int) = gb.frame.DataLayout.stateFile(dataDir(), game, stateLabel(slot))

    /** Previous builds keyed states by the cached disc filename (a hash); restore them to the stable game folder. */
    private fun migrateLegacyCachedStates(gamePaths: Collection<String>) {
        if (gamePaths.isEmpty()) return
        val root = File(dataDir(), "savestates")
        val prefixToGame = gamePaths.associateBy { legacyCachePrefix(it) }
        var migrated = 0
        for (legacyDir in root.listFiles().orEmpty().filter { it.isDirectory }) {
            val gamePath = prefixToGame.entries.firstOrNull { (prefix, game) ->
                legacyDir.name.startsWith(prefix) && legacyDir.name != SaveData.gameFolderName(game)
            }?.value ?: continue
            val oldPrefix = "${legacyDir.name}_"
            val targetFolder = File(root, SaveData.gameFolderName(gamePath))
            for (legacyState in legacyDir.listFiles().orEmpty().filter {
                it.isFile && it.name.startsWith(oldPrefix) && it.name.endsWith(".state")
            }) {
                val label = legacyState.name.removePrefix(oldPrefix).removeSuffix(".state")
                if (label != "auto" && label.toIntOrNull()?.let { it in 1..SaveData.MANUAL_SAVE_STATE_SLOTS } != true) continue
                val target = gb.frame.DataLayout.stateFile(dataDir(), gamePath, label)
                runCatching {
                    val expectedBytes = legacyState.length()
                    val targetBytes = target.length()
                    val targetHasState = target.isFile && targetBytes > 0L &&
                        (expectedBytes <= 0L || targetBytes == expectedBytes)
                    if (!targetHasState || legacyState.lastModified() > target.lastModified()) {
                        targetFolder.mkdirs()
                        val copiedBytes = legacyState.inputStream().buffered().use { input ->
                            target.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                        }
                        check(copiedBytes > 0L && (expectedBytes <= 0L || copiedBytes == expectedBytes)) {
                            "Save-state migration size mismatch: expected=$expectedBytes copied=$copiedBytes"
                        }
                        migrated++
                    }
                    legacyState.delete()
                }.onFailure {
                    android.util.Log.w("GBCast-Load", "Could not migrate legacy state ${legacyState.name}: ${it.message}")
                }
            }
            if (legacyDir.listFiles().isNullOrEmpty()) legacyDir.delete()
        }
        if (migrated > 0) android.util.Log.i("GBCast-Load", "migrated $migrated save states from cached-disc keys")
    }

    private fun legacyCachePrefix(gamePath: String): String = MessageDigest.getInstance("SHA-256")
        .digest(gamePath.toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it) } + "-"

    /** The last [states] result, shown at once when the library opens again. */
    @Volatile var lastStates: List<SaveSlot> = emptyList()
        private set
    fun states(games: List<gb.frame.Game>): List<SaveSlot> {
        migrateLegacyCachedStates(games.map { it.path })
        val byName = games.associateBy { SaveData.gameFolderName(it.path) }
        val out = mutableListOf<SaveSlot>()
        for (dir in File(dataDir(), "savestates").listFiles().orEmpty().filter { it.isDirectory }) {
            val game = byName[dir.name] ?: continue
            for (file in dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".state") }) {
                val label = file.name.removePrefix("${dir.name}_").removeSuffix(".state")
                val slot = if (label == "auto") AUTO_SAVE_SLOT else (label.toIntOrNull() ?: continue) - 1
                if (slot in 0..AUTO_SAVE_SLOT) out += SaveSlot(game.path, game.title, slot, file.lastModified())
            }
        }
        return out.sortedByDescending { it.time }.also { lastStates = it }
    }

    data class SaveSlot(val gamePath: String, val title: String, val slot: Int, val time: Long)

    private fun latestStateSlot(gamePath: String): Int? {
        migrateLegacyCachedStates(listOf(gamePath))
        return (0..AUTO_SAVE_SLOT).mapNotNull { slot ->
            stateFile(gamePath, slot).takeIf { it.isFile }?.let { slot to it.lastModified() }
        }.maxByOrNull { it.second }?.first
    }

}



