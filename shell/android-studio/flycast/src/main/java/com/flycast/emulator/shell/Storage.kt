package com.flycast.emulator.shell

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import gb.frame.OpenDisc
import gb.frame.SafGames
import gb.frame.DataFile as File
import java.nio.file.Files

/** Games folder through the Storage Access Framework, preferring direct descriptor-backed access. */
private object GamesFolder {
    var path: String = ""
}

object Storage : SafGames(
    GameIndex.extensions,
    { GamesFolder.path },
    zipDiscExt = setOf("gdi", "cdi", "chd", "cue"),
) {
    /** Import a user-selected SAF tree; the native core requires real local data paths. */
    fun importBiosFolder(uri: Uri, destination: java.io.File): Int {
        val root = requireNotNull(DocumentFile.fromTreeUri(app, uri)) { "Could not open BIOS folder" }
        var copied = 0
        fun copyDirectory(source: DocumentFile, target: File) {
            check(target.isDirectory || target.mkdirs()) { "Could not create data folder" }
            for (child in source.listFiles()) {
                val name = requireNotNull(child.name) { "Could not read filename" }
                require(name != "." && name != ".." && '/' !in name && '\\' !in name) { "Invalid filename" }
                val output = File(target, name)
                require(output.canonicalPath.startsWith(destination.canonicalPath + File.separator)) { "Invalid data path" }
                if (child.isDirectory) {
                    copyDirectory(child, output)
                } else if (child.isFile) {
                    requireNotNull(app.contentResolver.openInputStream(child.uri)) { "Could not read $name" }.use { input ->
                        if (target == destination && name.lowercase(java.util.Locale.ROOT) in DreamcastBios.sizes) {
                            DreamcastBios.install(destination, name, input)
                        } else {
                            val temp = File.createTempFile("import-", ".tmp", target)
                            try {
                                temp.outputStream().use { input.copyTo(it) }
                                check(temp.renameTo(output)) { "Could not save $name" }
                            } finally { temp.delete() }
                        }
                    }
                    copied++
                }
            }
        }
        copyDirectory(root, File(destination.path))
        return copied
    }
    var gamesDir: String
        get() = GamesFolder.path
        set(value) { GamesFolder.path = value }
    private lateinit var app: Context

    fun start(context: Context) {
        app = context.applicationContext
        super.init(app)
    }

    fun openForCore(path: String): OpenDisc? {
        val ext = fileName(path).substringAfterLast('.', "").lowercase()
        val opened = open(path) ?: return null
        val src = File(opened.path)
        val parent = src.parentFile
        if (parent == null) return opened
        val links = parent.listFiles()?.filter { Files.isSymbolicLink(it.toPath()) }.orEmpty()
        if (opened.descriptorCount == 0) return opened

        // Match GBFrame's descriptor strategy without trying to reopen each link. RG DS may stat these
        // /proc/self/fd symlinks but reject FileInputStream/fopen on them; Flycast's native hostfs adapter
        // now duplicates and reads the original descriptor directly.
        val directAccess = links.size == opened.descriptorCount && links.all { link ->
            runCatching {
                val target = Files.readSymbolicLink(link.toPath()).toString()
                target.startsWith("/proc/self/fd/") &&
                    target.removePrefix("/proc/self/fd/").toIntOrNull()?.let { it >= 0 } == true
            }.getOrDefault(false)
        }
        if (directAccess) {
            clearDiscCopies()
            return opened
        }
        opened.close()
        return if (ext == "chd" || ext == "iso") open(path, stageCopy = true) else null
    }

    fun importFile(uri: Uri, destDir: java.io.File): String? {
        destDir.mkdirs()
        val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: return null
        val safe = name.replace(Regex("[\\\\/]+"), "_")
        val dest = File(destDir, safe)
        app.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return dest.absolutePath
    }
}
