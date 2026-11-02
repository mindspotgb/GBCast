package com.flycast.emulator.shell.ui

import android.content.Context
import com.flycast.emulator.shell.DreamcastCheatCatalog
import com.flycast.emulator.shell.Storage
import gb.frame.LibraryView
import java.io.File

/** Offline Dreamcast catalog; only the native Dreamcast parser applies its RAM operations. */
object GBCastCheatDatabase {
    private lateinit var context: Context
    private var names = emptyList<String>()
    private val frameRateCheatsByEntry = mutableMapOf<String, List<String>>()
    fun initialize(context: Context) {
        this.context = context.applicationContext
        names = context.assets.list("cheats").orEmpty().filter { it.endsWith(".cht") }.map { it.removeSuffix(".cht") }
        frameRateCheatsByEntry.clear()
    }
    fun entry(): String? = LibraryView.lastGame?.let { DreamcastCheatCatalog.find(names, Storage.fileName(it)) }
    fun text(entry: String): String = context.assets.open("cheats/$entry.cht").bufferedReader().use { it.readText() }
    fun frameRateCheats(entry: String): List<String> = frameRateCheatsByEntry.getOrPut(entry) {
        Regex(
            "(?m)^cheat\\d+_desc\\s*=\\s*\"([^\"]*\\b60\\s*FPS\\b[^\"]*)\"",
            RegexOption.IGNORE_CASE,
        ).findAll(text(entry)).map { it.groupValues[1] }.toList()
    }
    fun importText(text: String): Int {
        require(text.toByteArray().size < 1024 * 1024) { "Cheat file is too large." }
        val file = File.createTempFile("cheat-import-", ".cht", context.cacheDir)
        return try {
            file.writeText(text)
            com.flycast.emulator.emu.JNIdc.importCheatFile(file.absolutePath)
        } finally { file.delete() }
    }
}
