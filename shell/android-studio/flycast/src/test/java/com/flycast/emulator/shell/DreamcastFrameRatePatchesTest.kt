package com.flycast.emulator.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DreamcastFrameRatePatchesTest {
    @Test fun matchesKnownPatchAndRegion() {
        assertEquals(
            listOf("0103337C 00000009"),
            DreamcastFrameRatePatches.find("Ecco the Dolphin - Defender of the Future (USA).gdi")?.codes,
        )
        assertEquals(
            listOf("01103DC8 00000009"),
            DreamcastFrameRatePatches.find("Tony Hawk's Pro Skater (Europe).chd")?.codes,
        )
    }

    @Test fun rejectsUnsupportedRegionAndUnknownTitle() {
        assertNull(DreamcastFrameRatePatches.find("Speed Devils (Europe).gdi"))
        assertNull(DreamcastFrameRatePatches.find("Sonic Adventure (USA).gdi"))
    }

    @Test fun ignoresDiscTagsAndReturnsAllCodesAsOnePatch() {
        assertEquals(
            listOf("010D79E8 0000E400", "0105B6EC 00000444"),
            DreamcastFrameRatePatches.find("Speed Devils (USA) (Disc 1).gdi")?.codes,
        )
    }
}
