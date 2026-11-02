package com.flycast.emulator.shell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.flycast.emulator.emu.JNIdc
import com.flycast.emulator.shell.DreamcastFrameRatePatches
import com.flycast.emulator.shell.Storage
import gb.frame.CheatPatchControl
import gb.frame.CheatActionResult
import gb.frame.CheatUiItem
import gb.frame.CheatsBackend
import gb.frame.GameHackPolicy

/**
 * Flycast adapter for GBFrame's common Cheats screen.
 *
 * Flycast parses GameShark codes natively and owns the active game's cheat list and persistence.
 * The shared screen owns all presentation and interaction; this adapter only translates operations.
 */
object GBCastCheatsBackend : CheatsBackend {
    private var revision by mutableIntStateOf(0)
    private val frameRatePatchPrefix = "GBCast frame-rate patch:"
    private var frameRatePatchCacheKey: Pair<Int, String?>? = null
    private var frameRatePatchCacheValue: CheatPatchControl? = null

    private fun frameRatePatch() = gb.frame.LibraryView.lastGame?.let { game ->
        DreamcastFrameRatePatches.find(Storage.fileName(game))
    }

    private fun databaseFrameRateEntry() = GBCastCheatDatabase.entry()?.let { entry ->
        GBCastCheatDatabase.frameRateCheats(entry).takeIf { it.isNotEmpty() }?.let { entry to it }
    }

    private fun frameRatePatchCheatIndices(): List<Int> {
        val patch = frameRatePatch()
        val databaseCheats = if (patch == null) databaseFrameRateEntry()?.second.orEmpty() else emptyList()
        if (patch == null && databaseCheats.isEmpty()) return emptyList()
        val prefix = patch?.let { "$frameRatePatchPrefix ${it.title}:" }
        return (0 until JNIdc.cheatCount()).filter { index ->
            val description = JNIdc.cheatDescription(index).orEmpty()
            if (prefix != null) description.startsWith(prefix) else description in databaseCheats
        }
    }

    /** Apply the shared on-by-default policy once a game has loaded, without overriding a saved off state. */
    fun applyDefaultFrameRatePatch() {
        if (!GameHackPolicy.shouldApply(GameHackPolicy.DEFAULT_ENABLED, blockedMessage != null)) return
        if (frameRatePatch == null || frameRatePatchCheatIndices().isNotEmpty()) return
        setFrameRatePatchEnabled(true)
    }

    override val frameRatePatch: CheatPatchControl?
        get() {
            val gameFile = gb.frame.LibraryView.lastGame?.let { Storage.fileName(it) }
            val cacheKey = revision to gameFile
            if (cacheKey == frameRatePatchCacheKey) return frameRatePatchCacheValue
            val patch = frameRatePatch()
            val databaseEntry = if (patch == null) databaseFrameRateEntry() else null
            if (patch == null && databaseEntry == null) {
                frameRatePatchCacheKey = cacheKey
                frameRatePatchCacheValue = null
                return null
            }
            val indices = frameRatePatchCheatIndices()
            return CheatPatchControl(
                label = "60 FPS patch",
                description = when {
                    patch != null -> "Community patch for ${patch.title}. It can cause instability or change game speed."
                    else -> "Database patch for ${databaseEntry!!.first.removeSuffix(".cht")}. It can cause instability or change game speed."
                },
                enabled = indices.isNotEmpty() && indices.all { JNIdc.cheatEnabled(it) },
            ).also {
                frameRatePatchCacheKey = cacheKey
                frameRatePatchCacheValue = it
            }
        }

    override val cheats: List<CheatUiItem>
        get() = revision.let {
            // Observe mutations so GBFrame recomposes its list after enable/add/delete/import.
            (0 until JNIdc.cheatCount()).mapNotNull { index ->
                if (JNIdc.cheatBuiltIn(index)) return@mapNotNull null
                CheatUiItem(
                    id = index.toString(),
                    name = JNIdc.cheatDescription(index).orEmpty().ifBlank { "Cheat ${index + 1}" },
                    code = "GameShark",
                    enabled = JNIdc.cheatEnabled(index),
                    sourceLabel = "GBCast",
                )
            }
        }

    override val databaseStatus: String
        get() = GBCastCheatDatabase.entry()?.let { "Offline cheat database: $it" }
            ?: "No unambiguous Dreamcast cheat database entry for this game."

    override val canAddFromDatabase: Boolean
        get() = GBCastCheatDatabase.entry() != null

    override fun addFromDatabase(): CheatActionResult {
        blockedMessage?.let { return CheatActionResult.failure(it) }
        val entry = GBCastCheatDatabase.entry() ?: return CheatActionResult.failure("No matching database entry.")
        return importNative(GBCastCheatDatabase.text(entry))
    }

    override fun setFrameRatePatchEnabled(enabled: Boolean): CheatActionResult {
        blockedMessage?.let { return CheatActionResult.failure(it) }
        val patch = frameRatePatch()
        val databaseEntry = if (patch == null) databaseFrameRateEntry() else null
        if (patch == null && databaseEntry == null) return CheatActionResult.failure("No 60 FPS patch is listed for this game version.")
        var indices = frameRatePatchCheatIndices()
        if (enabled && indices.isEmpty()) {
            if (patch != null) {
                for ((index, code) in patch.codes.withIndex()) {
                    val error = runCatching {
                        JNIdc.addGameSharkCheat("$frameRatePatchPrefix ${patch.title}: ${index + 1}", code)
                    }.getOrElse { it.message ?: "Could not add the 60 FPS patch." }
                    if (error != null) {
                        frameRatePatchCheatIndices().forEach { JNIdc.setCheatEnabled(it, false) }
                        revision++
                        return CheatActionResult.failure(error)
                    }
                }
            } else {
                val imported = importNative(GBCastCheatDatabase.text(databaseEntry!!.first))
                if (!imported.success) return imported
            }
            indices = frameRatePatchCheatIndices()
        }
        if (indices.isEmpty()) return CheatActionResult.failure("Flycast did not accept this game's 60 FPS patch.")
        indices.forEach { JNIdc.setCheatEnabled(it, enabled) }
        revision++
        return CheatActionResult.success(if (enabled) "60 FPS patch enabled." else "60 FPS patch disabled.")
    }

    private fun importNative(text: String): CheatActionResult {
        val count = runCatching { GBCastCheatDatabase.importText(text) }.getOrElse {
            return CheatActionResult.failure(it.message ?: "Could not import cheats.")
        }
        revision++
        return if (count < 0) CheatActionResult.failure("Could not import cheats for the active game.")
        else CheatActionResult.success(if (count == 0) "No new supported cheats; duplicates are preserved." else "Added $count cheat(s), disabled. Enable the ones you want.")
    }

    override val codeHint: String
        get() = "GameShark code (hex words separated by spaces)"

    override val blockedMessage: String?
        get() = if (JNIdc.configGetBool("achievements", "HardcoreMode")) {
            "Cheats are disabled while RetroAchievements hardcore mode is enabled."
        } else {
            null
        }

    override fun add(name: String, code: String): CheatActionResult {
        blockedMessage?.let { return CheatActionResult.failure(it) }
        if (code.isBlank()) return CheatActionResult.failure("Enter a GameShark code.")
        val error: String? = runCatching {
            JNIdc.addGameSharkCheat(name.trim().ifBlank { "Cheat" }, code.trim())
        }.getOrElse { it.message ?: "GBCast could not parse that code." }
        if (error != null) return CheatActionResult.failure(error)
        revision++
        return CheatActionResult.success("Cheat added to GBCast.")
    }

    override fun setEnabled(id: String, enabled: Boolean) {
        if (blockedMessage != null) return
        val index = id.toIntOrNull() ?: return
        if (JNIdc.setCheatEnabled(index, enabled)) revision++
    }

    override fun delete(id: String) {
        if (blockedMessage != null) return
        val index = id.toIntOrNull() ?: return
        if (index !in 0 until JNIdc.cheatCount() || JNIdc.cheatBuiltIn(index)) return
        JNIdc.removeCheat(index)
        revision++
    }

    override fun importFile(contents: String): CheatActionResult {
        blockedMessage?.let { return CheatActionResult.failure(it) }
        if (Regex("(?m)^\\s*cheat\\d+_address\\s*=").containsMatchIn(contents)) return importNative(contents)
        val sourceLines = contents.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") && !it.startsWith("[") }
            .toList()
        if (sourceLines.isEmpty()) return CheatActionResult.failure("No GameShark codes were found in that file.")
        if (sourceLines.any { it.startsWith("cheat", ignoreCase = true) && "=" in it }) {
            return CheatActionResult.failure("GBCast .cht configuration files are not text-code lists. Import a text file with one GameShark code per line; use Name=code to label entries.")
        }

        var imported = 0
        for ((lineIndex, line) in sourceLines.withIndex()) {
            val equals = line.indexOf('=')
            val named = equals > 0
            val name = if (named) line.substringBefore('=').trim() else "Imported cheat ${lineIndex + 1}"
            val code = if (named) line.substringAfter('=').trim() else line
            val result = add(name, code)
            if (!result.success) {
                return if (imported == 0) result else CheatActionResult.success(
                    "Imported ${imported} code(s); stopped at line ${lineIndex + 1}: ${result.message.orEmpty()}",
                )
            }
            imported++
        }
        return CheatActionResult.success("Imported ${imported} GameShark code(s).")
    }
}
