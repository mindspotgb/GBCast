package com.flycast.emulator.shell

import gb.frame.DataFile as File
import java.io.InputStream
import java.util.Locale

/** The two Dreamcast firmware files are validated independently before replacing an installed file. */
object DreamcastBios {
    val sizes = linkedMapOf("dc_boot.bin" to 2 * 1024 * 1024, "dc_flash.bin" to 128 * 1024)

    fun present(directory: java.io.File, name: String): Boolean =
        File(directory, name).length() == sizes[name]?.toLong() ||
            (name == "dc_boot.bin" && File(directory, "$name.bak").length() == sizes[name]?.toLong())

    fun install(directory: java.io.File, sourceName: String, input: InputStream): String {
        val name = sourceName.lowercase(Locale.ROOT)
        val expected = requireNotNull(sizes[name]) { "Choose dc_boot.bin and dc_flash.bin" }
        directory.mkdirs()
        val temp = File.createTempFile("bios-", ".tmp", directory)
        try {
            temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= expected) { "$name must be ${expected / 1024} KiB" }
                    output.write(buffer, 0, count)
                }
                require(total == expected) { "$name must be ${expected / 1024} KiB" }
            }
            check(temp.renameTo(File(directory, name))) { "Could not save $name" }
            return name
        } finally { temp.delete() }
    }
}
