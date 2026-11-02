package com.flycast.emulator.shell

/**
 * Disc images Flycast actually loads. BIN/ISO/RAW tracks that sit next to a GDI or CUE descriptor are not separate games.
 */
object GameIndex {
    val extensions = setOf("gdi", "cdi", "chd", "cue", "lst", "zip", "7z", "dat", "bin")
    private val trackExtensions = setOf("bin", "iso", "raw", "img")

    fun filterTracks(paths: List<String>): List<String> {
        val byFolder = paths.groupBy { parentOf(it) }
        val hidden = HashSet<String>()
        for ((_, files) in byFolder) {
            val hasDescriptor = files.any { extOf(it) == "gdi" || extOf(it) == "cue" }
            if (!hasDescriptor) continue
            files.filter { extOf(it) in trackExtensions }.forEach { hidden += it }
        }
        return paths.filter { it !in hidden }
    }

    private fun parentOf(path: String) = path.substringBeforeLast('/', "")
    private fun extOf(path: String) = path.substringAfterLast('.', "").lowercase()
}
