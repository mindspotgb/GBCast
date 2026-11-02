package com.flycast.emulator.shell

/** Filename matching is conservative: different regions/revisions must never be guessed. */
object DreamcastCheatCatalog {
    private fun normalized(title: String) = title.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .lowercase().replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()

    fun find(names: List<String>, filename: String): String? {
        val title = filename.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("\\s*\\((Disc|Disk|Track)\\s*\\d+[^)]*\\)", RegexOption.IGNORE_CASE), "").trim()
        names.firstOrNull { it.equals(title, true) }?.let { return it }
        val candidates = names.filter { normalized(it) == normalized(title) }
        val tags = Regex("\\(([^)]*)\\)").findAll(title).map { it.groupValues[1].lowercase() }.toSet()
        val regionNames = setOf("usa", "europe", "japan", "world", "korea", "brazil")
        val requested = tags.flatMap { it.split(",").map(String::trim) }.filter { it in regionNames }
        val compatible = if (requested.isEmpty()) candidates else candidates.filter { candidate ->
            requested.all { region -> Regex("\\([^)]*\\b$region\\b[^)]*\\)", RegexOption.IGNORE_CASE).containsMatchIn(candidate) }
        }
        return compatible.singleOrNull()
    }
}
