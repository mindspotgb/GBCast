package com.flycast.emulator.shell

/** Community-listed Dreamcast GameShark frame-rate codes. Keep titles and regions conservative. */
object DreamcastFrameRatePatches {
    data class Patch(val title: String, val regions: Set<String>, val codes: List<String>)

    // Source: https://github.com/nexus382/Flycast-Widescreen-Compatability-And-Cheat-Chart
    // The chart is community-maintained; these optional codes may be unstable or alter game speed.
    private val patches = listOf(
        Patch("Disney's Donald Duck: Goin' Quackers", setOf("na"), listOf("010E1B1E 00000009")),
        Patch("Ecco the Dolphin: Defender of the Future", setOf("na"), listOf("0103337C 00000009")),
        Patch("Grandia II", setOf("na"), listOf("0D301582 00008BFB", "01301582 00000009")),
        Patch("MDK2", setOf("na"), listOf("01012CF6 00000009")),
        Patch("Speed Devils", setOf("na"), listOf("010D79E8 0000E400", "0105B6EC 00000444")),
        Patch("Tony Hawk's Pro Skater", setOf("na", "eu"), listOf("01103DC8 00000009")),
        Patch("Tony Hawk's Pro Skater 2", setOf("na"), listOf("010CC248 00000009")),
        Patch("Toy Racer", setOf("eu"), listOf("0101FCDC 0000E401")),
        Patch("TrickStyle", setOf("na"), listOf("010F50C8 0000E400")),
    )

    private fun normalized(value: String): String = value
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    fun find(fileName: String): Patch? {
        val title = fileName.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("\\s*\\((Disc|Disk|Track)\\s*\\d+[^)]*\\)", RegexOption.IGNORE_CASE), " ")
        val key = normalized(title)
        val candidates = patches.filter { normalized(it.title) == key }
        if (candidates.isEmpty()) return null

        val region = when {
            Regex("\\(([^)]*\\b(usa|north america|na)\\b[^)]*)\\)", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "na"
            Regex("\\(([^)]*\\b(europe|eu)\\b[^)]*)\\)", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "eu"
            Regex("\\(([^)]*\\b(japan|japanese|jp)\\b[^)]*)\\)", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "jp"
            else -> null
        }
        val compatible = if (region == null) candidates else candidates.filter { region in it.regions }
        return compatible.singleOrNull()
            ?: compatible.takeIf { it.isNotEmpty() && it.map(Patch::codes).distinct().size == 1 }?.firstOrNull()
    }
}
