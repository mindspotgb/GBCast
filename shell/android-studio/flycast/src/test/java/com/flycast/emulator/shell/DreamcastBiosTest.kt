package com.flycast.emulator.shell

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DreamcastBiosTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun importsBothFilesAndNormalizesCase() {
        val dir = folder.newFolder()
        DreamcastBios.sizes.forEach { (name, size) ->
            DreamcastBios.install(dir, name.uppercase(), ByteArray(size).inputStream())
            assertTrue(DreamcastBios.present(dir, name))
        }
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test fun rejectsTruncatedFileWithoutDamagingExistingBios() {
        val dir = folder.newFolder()
        val original = ByteArray(128 * 1024) { 42 }
        DreamcastBios.install(dir, "dc_flash.bin", original.inputStream())
        try {
            DreamcastBios.install(dir, "dc_flash.bin", ByteArray(100).inputStream())
            fail("Truncated firmware was accepted")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(original, java.io.File(dir, "dc_flash.bin").readBytes())
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test fun rejectsUnknownAndOversizedFiles() {
        val dir = folder.newFolder()
        for ((name, size) in listOf("other.bin" to 5, "dc_flash.bin" to 131073)) {
            try {
                DreamcastBios.install(dir, name, ByteArray(size).inputStream())
                fail("Invalid firmware was accepted")
            } catch (_: IllegalArgumentException) { }
        }
        assertTrue(dir.listFiles()!!.isEmpty())
        assertFalse(DreamcastBios.present(dir, "dc_boot.bin"))
    }
}
