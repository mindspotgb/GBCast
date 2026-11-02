package com.flycast.emulator.shell

import org.junit.Assert.*
import org.junit.Test

class DreamcastCheatCatalogTest {
    private val names = listOf("Daytona USA (USA)", "Sonic Adventure (USA)", "Sonic Adventure (Japan)")
    @Test fun matchesUniqueTitleAndTaggedRegion() {
        assertEquals("Daytona USA (USA)", DreamcastCheatCatalog.find(names, "Daytona USA.chd"))
        assertEquals("Sonic Adventure (Japan)", DreamcastCheatCatalog.find(names, "Sonic Adventure (Japan).gdi"))
    }
    @Test fun neverGuessesRegionOrPartialTitle() {
        assertNull(DreamcastCheatCatalog.find(names, "Sonic Adventure.chd"))
        assertNull(DreamcastCheatCatalog.find(names, "Daytona USA (Europe).chd"))
        assertNull(DreamcastCheatCatalog.find(names, "Sonic.chd"))
    }
    @Test fun ignoresDiscTagButPreservesRevisionMatch() {
        val entries = listOf("Game (USA) (Rev A)", "Game (USA) (Rev B)")
        assertEquals(entries[1], DreamcastCheatCatalog.find(entries, "Game (USA) (Rev B) (Disc 1).chd"))
        assertNull(DreamcastCheatCatalog.find(entries, "Game (USA).chd"))
    }
}
